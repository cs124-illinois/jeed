@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.google.common.base.Objects
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import io.github.classgraph.ClassGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinToJVMBytecodeCompiler
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.configureContentRootsFromClassPath
import org.jetbrains.kotlin.cli.jvm.configureJavaModulesContentRoots
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileListener
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.com.intellij.util.LocalTimeCounter
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileSystems
import java.time.Instant
import kotlin.math.min

val systemKompilerVersion = KotlinVersion.CURRENT.toString()

private val classpath = ClassGraph().classpathFiles.joinToString(separator = File.pathSeparator)

private const val KOTLIN_EMPTY_LOCATION = "/"

@Serializable
@Suppress("MatchingDeclarationName")
data class KompilationArguments(
    @Transient var parentClassLoader: ClassLoader? = null,
    val verbose: Boolean = DEFAULT_VERBOSE,
    val allWarningsAsErrors: Boolean = DEFAULT_ALLWARNINGSASERRORS,
    val useCache: Boolean? = null,
    val waitForCache: Boolean = false,
    @Transient val parentFileManager: JeedFileManager? = null,
    val parameters: Boolean = DEFAULT_PARAMETERS,
    val jvmTarget: String = DEFAULT_JVM_TARGET,
    val isolatedClassLoader: Boolean = false,
    // K2 compiler uses internal APIs via reflection since they're not publicly exposed
    val useK2: Boolean = true,
) {
    @Transient
    private val additionalCompilerArguments: List<String> = listOf(
        "-opt-in=kotlin.ExperimentalStdlibApi",
        "-opt-in=kotlin.time.ExperimentalTime",
        "-opt-in=kotlin.RequiresOptIn",
        "-opt-in=kotlin.ExperimentalUnsignedTypes",
        "-opt-in=kotlin.contracts.ExperimentalContracts",
        "-opt-in=kotlin.experimental.ExperimentalTypeInference",
        "-Xcontext-receivers",
        "-XXLanguage:+RangeUntilOperator",
    )

    @Transient
    val arguments: K2JVMCompilerArguments = K2JVMCompilerArguments()

    init {
        check(!waitForCache || useCache == true) {
            "waitForCache can only be used if useCache is true"
        }

        if (parentClassLoader == null) {
            parentClassLoader = ClassLoader.getSystemClassLoader()
        }

        parseCommandLineArguments(additionalCompilerArguments, arguments)

        arguments.classpath = classpath
        arguments.verbose = verbose
        arguments.allWarningsAsErrors = allWarningsAsErrors
        arguments.noStdlib = true
        arguments.javaParameters = parameters
        arguments.useK2 = useK2
    }

    companion object {
        const val DEFAULT_VERBOSE = false

        @Suppress("SpellCheckingInspection")
        const val DEFAULT_ALLWARNINGSASERRORS = false
        const val DEFAULT_PARAMETERS = false

        private const val MAX_KOTLIN_SUPPORTED_JAVA_VERSION = 21
        val DEFAULT_JVM_TARGET = min(systemCompilerVersion, MAX_KOTLIN_SUPPORTED_JAVA_VERSION).toCompilerVersion()
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true

        javaClass != other?.javaClass -> false

        else -> {
            other as KompilationArguments
            when {
                verbose != other.verbose -> false
                allWarningsAsErrors != other.allWarningsAsErrors -> false
                parameters != other.parameters -> false
                jvmTarget != other.jvmTarget -> false
                isolatedClassLoader != other.isolatedClassLoader -> false
                parentFileManager !== other.parentFileManager -> false
                else -> true
            }
        }
    }

    override fun hashCode(): Int {
        var result = Objects.hashCode(verbose, allWarningsAsErrors, parameters, jvmTarget, isolatedClassLoader)
        // Include content-based hash of parent file manager if present
        result = 31 * result + when (val parent = parentFileManager) {
            null -> 0
            else -> parent.contentHashCode()
        }
        return result
    }
}

internal class JeedMessageCollector(val source: Source, private val allWarningsAsErrors: Boolean) : MessageCollector {
    private val messages: MutableList<CompilationMessage> = mutableListOf()

    override fun clear() {
        messages.clear()
    }

    val errors: List<CompilationError>
        get() = messages.filter {
            (it.kind == CompilerMessageSeverity.ERROR.presentableName) ||
                (
                    allWarningsAsErrors &&
                        (
                            it.kind == CompilerMessageSeverity.WARNING.presentableName ||
                                it.kind == CompilerMessageSeverity.STRONG_WARNING.presentableName
                            )
                    )
        }.map {
            CompilationError(it.location, it.message)
        }.distinctBy {
            if (it.location != null) {
                "${it.location}: ${it.message}"
            } else {
                it.message
            }
        }.sortedBy {
            if (it.location == null) {
                0
            } else {
                it.location.line * 1000 + it.location.column
            }
        }

    val warnings: List<CompilationMessage>
        get() = messages.filter {
            it.kind != CompilerMessageSeverity.ERROR.presentableName
        }.map {
            CompilationMessage("warning", it.location, it.message)
        }

    override fun hasErrors(): Boolean = errors.isNotEmpty()

    override fun report(severity: CompilerMessageSeverity, message: String, location: CompilerMessageSourceLocation?) {
        if (severity == CompilerMessageSeverity.LOGGING || severity == CompilerMessageSeverity.INFO) {
            return
        }
        val sourceLocation = location
            ?.let {
                when {
                    source is Snippet -> SNIPPET_SOURCE
                    it.path != KOTLIN_EMPTY_LOCATION -> it.path.removePrefix(FileSystems.getDefault().separator)
                    else -> null
                }
            }?.let {
                @Suppress("SwallowedException")
                try {
                    source.mapLocation(SourceLocation(it, location.line, location.column))
                } catch (_: SourceMappingException) {
                    null
                }
            }
        messages.add(CompilationMessage(severity.presentableName, sourceLocation, message))
    }
}

internal fun kompileToFileManager(
    kompilationArguments: KompilationArguments,
    source: Source,
    parentFileManager: JeedFileManager? = kompilationArguments.parentFileManager,
): Pair<JeedFileManager, List<CompilationMessage>> {
    val kotlinSource = when (source.type) {
        Source.SourceType.KOTLIN -> source
        Source.SourceType.MIXED -> source.kotlinSource
        else -> error("Can't kompile Java-only sources")
    }
    val javaSource = when (source.type) {
        Source.SourceType.MIXED -> source.javaSource
        else -> null
    }

    val rootDisposable = Disposer.newDisposable()

    try {
        val messageCollector = JeedMessageCollector(source, kompilationArguments.arguments.allWarningsAsErrors)
        val configuration = CompilerConfiguration().apply {
            put(CommonConfigurationKeys.MESSAGE_COLLECTOR_KEY, messageCollector)
            put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
            put(JVMConfigurationKeys.PARAMETERS_METADATA, kompilationArguments.parameters)
            put(JVMConfigurationKeys.JVM_TARGET, kompilationArguments.jvmTarget.toJvmTarget())
            put(JVMConfigurationKeys.JDK_HOME, java.io.File(System.getProperty("java.home")))

            kompilationArguments.parentFileManager?.toVirtualFile()?.also { virtualRoot ->
                add(CLIConfigurationKeys.CONTENT_ROOTS, VirtualJvmClasspathRoot(virtualRoot))
            }

            javaSource?.sources?.entries
                ?.map { (path, contents) -> SimpleVirtualFile(path, contents = contents.toByteArray()) }
                ?.forEach { virtualFile ->
                    add(CLIConfigurationKeys.CONTENT_ROOTS, VirtualJvmClasspathRoot(virtualFile))
                }

            configureJavaModulesContentRoots(kompilationArguments.arguments)
            configureContentRootsFromClassPath(kompilationArguments.arguments)
            configureAdvancedJvmOptions(kompilationArguments.arguments)
            configureJdkClasspathRoots()
        }

        // Silence scaring warning on Windows
        setIdeaIoUseFallback()

        // K1 API - will be deprecated in Kotlin 2.3 but no public K2 API is available in kotlin-compiler-embeddable yet.
        // See: https://youtrack.jetbrains.com/issue/KT-60276
        @Suppress("DEPRECATION")
        val environment = KotlinCoreEnvironment.createForProduction(
            rootDisposable,
            configuration,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )

        val psiFileFactory = PsiFileFactory.getInstance(environment.project) as PsiFileFactoryImpl
        val psiFiles = kotlinSource.sources.map { (name, contents) ->
            psiFileFactory.trySetupPsiForFile(
                LightVirtualFile(name, KotlinLanguage.INSTANCE, contents),
                KotlinLanguage.INSTANCE,
                true,
                false,
            ) as KtFile?
                ?: error("couldn't parse source to psiFile")
        }.toMutableList()

        val state = if (kompilationArguments.useK2) {
            kompileWithK2(environment, configuration, psiFiles, messageCollector)
        } else {
            kompileWithK1(environment, psiFiles)
        }

        if (messageCollector.errors.isNotEmpty()) {
            throw CompilationFailed(messageCollector.errors)
        }
        check(state != null) { "compilation should have succeeded" }

        return Pair(
            JeedFileManager(
                parentFileManager ?: standardFileManager,
                GeneratedClassLoader(state.factory, kompilationArguments.parentClassLoader),
            ),
            messageCollector.warnings,
        )
    } finally {
        Disposer.dispose(rootDisposable)
    }
}

// K1 compilation using legacy API
@Suppress("DEPRECATION")
private fun kompileWithK1(
    environment: KotlinCoreEnvironment,
    psiFiles: MutableList<KtFile>,
): org.jetbrains.kotlin.codegen.state.GenerationState? {
    // Inject source files using reflection
    environment::class.java.getDeclaredField("sourceFiles").also { field ->
        field.isAccessible = true
        field.set(environment, psiFiles)
    }

    return try {
        KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment)
    } catch (e: Throwable) {
        throw CompilationFailed(listOf(CompilationError(null, "Kotlin internal compiler error: ${e.message}")))
    }
}

// K2 compilation using reflection to access internal APIs
@Suppress("DEPRECATION")
private fun kompileWithK2(
    environment: KotlinCoreEnvironment,
    configuration: CompilerConfiguration,
    psiFiles: List<KtFile>,
    messageCollector: JeedMessageCollector,
): org.jetbrains.kotlin.codegen.state.GenerationState? = try {
    K2CompilerReflection.compile(environment, configuration, psiFiles, messageCollector)
} catch (e: K2CompilationException) {
    // If K2 fails, fall back to K1
    kompileWithK1(environment, psiFiles.toMutableList())
} catch (e: Throwable) {
    // Any other error also falls back to K1
    kompileWithK1(environment, psiFiles.toMutableList())
}

class K2CompilationException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * K2 compiler implementation using reflection to access internal Kotlin compiler APIs.
 * These APIs are not publicly exposed in kotlin-compiler-embeddable.
 *
 * K2 compilation pipeline (based on K2CompilerFacade from Compose):
 * 1. Create VfsBasedProjectEnvironment and diagnostic reporter
 * 2. Create FirJvmSessionFactory.Context
 * 3. Create shared library session
 * 4. Create library session
 * 5. Create FirSourceModuleData
 * 6. Create source session
 * 7. Build and resolve FIR via buildResolveAndCheckFirFromKtFiles
 * 8. Convert to IR via convertToIrAndActualizeForJvm
 * 9. Create GenerationState and BackendInput
 * 10. Generate bytecode via JvmIrCodegenFactory.generateModule
 */
@Suppress("TooManyFunctions", "LargeClass")
private object K2CompilerReflection {
    // Class references (lazy loaded)
    private val diagnosticReporterFactoryClass by lazy {
        Class.forName("org.jetbrains.kotlin.diagnostics.DiagnosticReporterFactory")
    }
    private val baseDiagnosticsCollectorClass by lazy {
        Class.forName("org.jetbrains.kotlin.diagnostics.impl.BaseDiagnosticsCollector")
    }
    private val firSessionClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.FirSession")
    }
    private val fir2IrActualizedResultClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.pipeline.Fir2IrActualizedResult")
    }
    private val jvmIrCodegenFactoryClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory")
    }
    private val backendInputClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.jvm.JvmIrCodegenFactory\$BackendInput")
    }
    private val classBuilderFactoriesClass by lazy {
        Class.forName("org.jetbrains.kotlin.codegen.ClassBuilderFactories")
    }
    private val firJvmSessionFactoryClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.session.FirJvmSessionFactory")
    }
    private val firJvmSessionFactoryContextClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.session.FirJvmSessionFactory\$Context")
    }
    private val firSourceModuleDataClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.FirSourceModuleData")
    }
    private val firModuleDataClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.FirModuleData")
    }
    private val dependencyListForCliModuleClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.session.DependencyListForCliModule")
    }
    private val dependencyListBuilderClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.session.DependencyListForCliModule\$Builder")
    }
    private val abstractProjectFileSearchScopeClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.session.environment.AbstractProjectFileSearchScope")
    }
    private val psiBasedProjectFileSearchScopeClass by lazy {
        Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.PsiBasedProjectFileSearchScope")
    }
    private val vfsBasedProjectEnvironmentClass by lazy {
        Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.VfsBasedProjectEnvironment")
    }
    private val abstractProjectEnvironmentClass by lazy {
        Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.AbstractProjectEnvironment")
    }
    private val nameClass by lazy {
        Class.forName("org.jetbrains.kotlin.name.Name")
    }
    private val languageVersionSettingsClass by lazy {
        Class.forName("org.jetbrains.kotlin.config.LanguageVersionSettings")
    }
    private val moduleDataProviderClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.resolve.providers.impl.ModuleDataProvider")
    }
    private val targetPlatformClass by lazy {
        Class.forName("org.jetbrains.kotlin.platform.TargetPlatform")
    }
    private val jvmPlatformsClass by lazy {
        Class.forName("org.jetbrains.kotlin.platform.jvm.JvmPlatforms")
    }
    private val projectScopeClass by lazy {
        Class.forName("com.intellij.psi.search.ProjectScope")
    }
    private val globalSearchScopeClass by lazy {
        Class.forName("com.intellij.psi.search.GlobalSearchScope")
    }
    private val firExtensionRegistrarClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar")
    }
    private val allModulesFrontendOutputClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput")
    }
    private val moduleCompilerAnalyzedOutputClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.pipeline.ModuleCompilerAnalyzedOutput")
    }
    private val jvmFir2IrExtensionsClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.backend.jvm.JvmFir2IrExtensions")
    }
    private val jvmIrDeserializerImplClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.jvm.JvmIrDeserializerImpl")
    }
    private val irGenerationExtensionClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.common.extensions.IrGenerationExtension")
    }
    private val projectClass by lazy {
        Class.forName("com.intellij.openapi.project.Project")
    }
    private val moduleDescriptorClass by lazy {
        Class.forName("org.jetbrains.kotlin.descriptors.ModuleDescriptor")
    }
    private val irModuleFragmentClass by lazy {
        Class.forName("org.jetbrains.kotlin.ir.declarations.IrModuleFragment")
    }
    private val fir2IrComponentsClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.backend.Fir2IrComponents")
    }
    private val firJvmBackendClassResolverClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendClassResolver")
    }
    private val firJvmBackendExtensionClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.backend.jvm.FirJvmBackendExtension")
    }
    private val jvmBackendExtensionClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.jvm.extensions.JvmBackendExtension")
    }
    private val jvmGeneratorExtensionsClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.jvm.JvmGeneratorExtensions")
    }
    private val irBuiltInsClass by lazy {
        Class.forName("org.jetbrains.kotlin.ir.IrBuiltIns")
    }
    private val symbolTableClass by lazy {
        Class.forName("org.jetbrains.kotlin.ir.util.SymbolTable")
    }
    private val irProviderClass by lazy {
        Class.forName("org.jetbrains.kotlin.ir.providers.IrProvider")
    }
    private val irPluginContextClass by lazy {
        Class.forName("org.jetbrains.kotlin.backend.common.extensions.IrPluginContext")
    }
    private val virtualFileManagerClass by lazy {
        Class.forName("com.intellij.openapi.vfs.VirtualFileManager")
    }
    private val virtualFileSystemClass by lazy {
        Class.forName("com.intellij.openapi.vfs.VirtualFileSystem")
    }
    private val standardFileSystemsClass by lazy {
        Class.forName("com.intellij.openapi.vfs.StandardFileSystems")
    }
    private val function1Class by lazy {
        Class.forName("kotlin.jvm.functions.Function1")
    }
    private val classBuilderFactoryClass by lazy {
        Class.forName("org.jetbrains.kotlin.codegen.ClassBuilderFactory")
    }
    private val jvmBackendClassResolverClass by lazy {
        Class.forName("org.jetbrains.kotlin.codegen.JvmBackendClassResolver")
    }
    private val topDownAnalyzerFacadeClass by lazy {
        Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM")
    }
    private val allJavaSourcesInProjectScopeClass by lazy {
        Class.forName("org.jetbrains.kotlin.cli.jvm.compiler.TopDownAnalyzerFacadeForJVM\$AllJavaSourcesInProjectScope")
    }
    private val firBuiltinSyntheticFunctionInterfaceProviderClass by lazy {
        Class.forName("org.jetbrains.kotlin.fir.resolve.providers.impl.FirBuiltinSyntheticFunctionInterfaceProvider")
    }

    @Suppress("DEPRECATION", "LongMethod", "ComplexMethod")
    fun compile(
        environment: KotlinCoreEnvironment,
        configuration: CompilerConfiguration,
        psiFiles: List<KtFile>,
        messageCollector: JeedMessageCollector,
    ): org.jetbrains.kotlin.codegen.state.GenerationState? {
        val project = environment.project

        // Step 1: Create diagnostic reporter
        val reporter = createDiagnosticsCollector(messageCollector)

        // Step 2: Create VfsBasedProjectEnvironment
        val projectEnvironment = createProjectEnvironment(environment)

        // Step 3: Create libraries scope
        val librariesScope = createLibrariesScope(project)

        // Step 4: Create FirJvmSessionFactory.Context
        val context = createSessionContext(configuration, projectEnvironment, librariesScope)

        // Step 5: Build dependency list and module data
        val moduleName = createName("main")
        val dependencyList = buildDependencyList(moduleName)

        // Step 6: Create shared library session
        val sharedLibrarySession = createSharedLibrarySession(
            moduleName,
            configuration,
            context,
        )

        // Step 7: Create library session
        val librarySession = createLibrarySession(
            sharedLibrarySession,
            dependencyList,
            configuration,
            context,
        )

        // Step 8: Create module data
        val moduleData = createModuleData(moduleName, dependencyList)

        // Step 9: Create source session
        val sourceSession = createSourceSession(
            moduleData,
            project,
            configuration,
            context,
            librarySession,
        )

        // Step 10: Build and resolve FIR
        val firOutput = buildAndResolveFir(sourceSession, psiFiles, reporter)

        // Step 11: Create AllModulesFrontendOutput and convert to IR
        val frontendOutput = createFrontendOutput(firOutput)
        val fir2IrResult = convertToIr(frontendOutput, configuration, reporter, project)

        // Step 12: Generate bytecode
        return generateBytecode(fir2IrResult, project, configuration)
    }

    private fun createDiagnosticsCollector(messageCollector: JeedMessageCollector): Any {
        val createReporterMethod = diagnosticReporterFactoryClass.getDeclaredMethod(
            "createReporter",
            MessageCollector::class.java,
        )
        return createReporterMethod.invoke(null, messageCollector)
    }

    @Suppress("DEPRECATION")
    private fun createProjectEnvironment(environment: KotlinCoreEnvironment): Any {
        val project = environment.project

        // Get VirtualFileManager instance
        val getInstanceMethod = virtualFileManagerClass.getDeclaredMethod("getInstance")
        val vfm = getInstanceMethod.invoke(null)

        // Get file system
        val fileProtocolField = standardFileSystemsClass.getDeclaredField("FILE_PROTOCOL")
        fileProtocolField.isAccessible = true
        val fileProtocol = fileProtocolField.get(null) as String

        val getFileSystemMethod = virtualFileManagerClass.getDeclaredMethod("getFileSystem", String::class.java)
        val localFileSystem = getFileSystemMethod.invoke(vfm, fileProtocol)

        // Create package part provider function
        val packagePartProviderFunc = java.lang.reflect.Proxy.newProxyInstance(
            function1Class.classLoader,
            arrayOf(function1Class),
        ) { _, method, args ->
            if (method.name == "invoke") {
                environment.createPackagePartProvider(args[0] as org.jetbrains.kotlin.com.intellij.psi.search.GlobalSearchScope)
            } else {
                null
            }
        }

        // Create VfsBasedProjectEnvironment
        val constructor = vfsBasedProjectEnvironmentClass.getConstructor(
            projectClass,
            virtualFileSystemClass,
            function1Class,
        )
        return constructor.newInstance(project, localFileSystem, packagePartProviderFunc)
    }

    private fun createLibrariesScope(project: Any): Any {
        // Get ProjectScope.getLibrariesScope(project)
        val getLibrariesScopeMethod = projectScopeClass.getDeclaredMethod(
            "getLibrariesScope",
            projectClass,
        )
        val globalSearchScope = getLibrariesScopeMethod.invoke(null, project)

        // Wrap in PsiBasedProjectFileSearchScope
        val constructor = psiBasedProjectFileSearchScopeClass.getConstructor(globalSearchScopeClass)
        return constructor.newInstance(globalSearchScope)
    }

    private fun createSessionContext(
        configuration: CompilerConfiguration,
        projectEnvironment: Any,
        librariesScope: Any,
    ): Any {
        val constructor = firJvmSessionFactoryContextClass.getConstructor(
            CompilerConfiguration::class.java,
            abstractProjectEnvironmentClass,
            abstractProjectFileSearchScopeClass,
        )
        return constructor.newInstance(configuration, projectEnvironment, librariesScope)
    }

    private fun createName(name: String): Any {
        val identifierMethod = nameClass.getDeclaredMethod("identifier", String::class.java)
        return identifierMethod.invoke(null, name)
    }

    private fun buildDependencyList(moduleName: Any): Any {
        // DependencyListForCliModule.build(moduleName)
        val buildMethod = dependencyListForCliModuleClass.getDeclaredMethod("build", nameClass, function1Class)

        // Empty configurator
        val emptyConfigurator = java.lang.reflect.Proxy.newProxyInstance(
            function1Class.classLoader,
            arrayOf(function1Class),
        ) { _, method, _ ->
            if (method.name == "invoke") {
                Unit
            } else {
                null
            }
        }

        return buildMethod.invoke(null, moduleName, emptyConfigurator)
    }

    private fun createSharedLibrarySession(
        moduleName: Any,
        configuration: CompilerConfiguration,
        context: Any,
    ): Any {
        val createMethod = firJvmSessionFactoryClass.getDeclaredMethod(
            "createSharedLibrarySession",
            nameClass,
            List::class.java,
            languageVersionSettingsClass,
            firJvmSessionFactoryContextClass,
        )
        createMethod.isAccessible = true

        // Get language version settings from configuration
        val languageVersionSettings = configuration.get(
            org.jetbrains.kotlin.config.CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
        ) ?: org.jetbrains.kotlin.config.LanguageVersionSettingsImpl.DEFAULT

        return createMethod.invoke(
            firJvmSessionFactoryClass.kotlin.objectInstance,
            moduleName,
            emptyList<Any>(), // extensionRegistrars
            languageVersionSettings,
            context,
        )
    }

    private fun createLibrarySession(
        sharedLibrarySession: Any,
        dependencyList: Any,
        configuration: CompilerConfiguration,
        context: Any,
    ): Any {
        // Get moduleDataProvider from dependencyList
        val moduleDataProviderField = dependencyListForCliModuleClass.getDeclaredField("moduleDataProvider")
        moduleDataProviderField.isAccessible = true
        val moduleDataProvider = moduleDataProviderField.get(dependencyList)

        val createMethod = firJvmSessionFactoryClass.getDeclaredMethod(
            "createLibrarySession",
            firSessionClass,
            moduleDataProviderClass,
            List::class.java,
            languageVersionSettingsClass,
            firJvmSessionFactoryContextClass,
        )
        createMethod.isAccessible = true

        val languageVersionSettings = configuration.get(
            org.jetbrains.kotlin.config.CommonConfigurationKeys.LANGUAGE_VERSION_SETTINGS,
        ) ?: org.jetbrains.kotlin.config.LanguageVersionSettingsImpl.DEFAULT

        return createMethod.invoke(
            firJvmSessionFactoryClass.kotlin.objectInstance,
            sharedLibrarySession,
            moduleDataProvider,
            emptyList<Any>(), // extensionRegistrars
            languageVersionSettings,
            context,
        )
    }

    private fun createModuleData(moduleName: Any, dependencyList: Any): Any {
        // Get dependencies from dependencyList
        val regularDepsField = dependencyListForCliModuleClass.getDeclaredField("regularDependencies")
        regularDepsField.isAccessible = true
        val regularDeps = regularDepsField.get(dependencyList) as List<*>

        val dependsOnDepsField = dependencyListForCliModuleClass.getDeclaredField("dependsOnDependencies")
        dependsOnDepsField.isAccessible = true
        val dependsOnDeps = dependsOnDepsField.get(dependencyList) as List<*>

        val friendDepsField = dependencyListForCliModuleClass.getDeclaredField("friendDependencies")
        friendDepsField.isAccessible = true
        val friendDeps = friendDepsField.get(dependencyList) as List<*>

        // Get JvmPlatforms.jvm8
        val jvm8Field = jvmPlatformsClass.getDeclaredField("INSTANCE")
        val jvmPlatformsInstance = jvm8Field.get(null)
        val jvm8Method = jvmPlatformsInstance::class.java.getDeclaredMethod("getJvm8")
        val jvm8 = jvm8Method.invoke(jvmPlatformsInstance)

        // Create FirSourceModuleData
        val constructor = firSourceModuleDataClass.getConstructor(
            nameClass,
            List::class.java,
            List::class.java,
            List::class.java,
            targetPlatformClass,
        )
        return constructor.newInstance(moduleName, regularDeps, dependsOnDeps, friendDeps, jvm8)
    }

    @Suppress("LongParameterList")
    private fun createSourceSession(
        moduleData: Any,
        project: Any,
        configuration: CompilerConfiguration,
        context: Any,
        librarySession: Any,
    ): Any {
        // Create java sources scope using TopDownAnalyzerFacadeForJVM.AllJavaSourcesInProjectScope
        val allJavaSourcesConstructor = allJavaSourcesInProjectScopeClass.getConstructor(projectClass)
        val allJavaSources = allJavaSourcesConstructor.newInstance(project)

        val psiSearchScopeConstructor = psiBasedProjectFileSearchScopeClass.getConstructor(globalSearchScopeClass)
        val javaSourcesScope = psiSearchScopeConstructor.newInstance(allJavaSources)

        // Create null provider function for incremental compilation
        val nullProviderFunc = java.lang.reflect.Proxy.newProxyInstance(
            function1Class.classLoader,
            arrayOf(function1Class),
        ) { _, method, _ ->
            if (method.name == "invoke") {
                null
            } else {
                null
            }
        }

        // Empty session configurator
        val emptyConfigurator = java.lang.reflect.Proxy.newProxyInstance(
            function1Class.classLoader,
            arrayOf(function1Class),
        ) { _, method, args ->
            if (method.name == "invoke") {
                // Register builtin synthetic function interface provider from library session
                val configurator = args[0]
                try {
                    val syntheticProviderMethod = firSessionClass.getDeclaredMethod("getSyntheticFunctionInterfacesSymbolProvider")
                    syntheticProviderMethod.isAccessible = true
                    val syntheticProvider = syntheticProviderMethod.invoke(librarySession)

                    if (syntheticProvider != null) {
                        val registerMethod = configurator::class.java.getDeclaredMethod(
                            "registerComponent",
                            Class::class.java,
                            Class.forName("org.jetbrains.kotlin.fir.FirSessionComponent"),
                        )
                        registerMethod.isAccessible = true
                        registerMethod.invoke(configurator, firBuiltinSyntheticFunctionInterfaceProviderClass, syntheticProvider)
                    }
                } catch (_: Exception) {
                    // Ignore - not critical
                }
                Unit
            } else {
                null
            }
        }

        val createMethod = firJvmSessionFactoryClass.getDeclaredMethod(
            "createSourceSession",
            firModuleDataClass,
            abstractProjectFileSearchScopeClass,
            function1Class, // createIncrementalCompilationSymbolProviders
            List::class.java, // extensionRegistrars
            CompilerConfiguration::class.java,
            firJvmSessionFactoryContextClass,
            Boolean::class.javaPrimitiveType, // needRegisterJavaElementFinder
            Boolean::class.javaPrimitiveType, // isForLeafHmppModule
            function1Class, // init (session configurator)
        )
        createMethod.isAccessible = true

        return createMethod.invoke(
            firJvmSessionFactoryClass.kotlin.objectInstance,
            moduleData,
            javaSourcesScope,
            nullProviderFunc,
            emptyList<Any>(), // extensionRegistrars
            configuration,
            context,
            true, // needRegisterJavaElementFinder
            false, // isForLeafHmppModule
            emptyConfigurator,
        )
    }

    private fun buildAndResolveFir(
        session: Any,
        psiFiles: List<KtFile>,
        reporter: Any,
    ): Any {
        // Find the buildResolveAndCheckFirFromKtFiles method
        val buildMethod = Class.forName("org.jetbrains.kotlin.fir.pipeline.FirUtilsKt")
            .getDeclaredMethod(
                "buildResolveAndCheckFirFromKtFiles",
                firSessionClass,
                List::class.java,
                baseDiagnosticsCollectorClass,
            )
        buildMethod.isAccessible = true

        return buildMethod.invoke(null, session, psiFiles, reporter)
    }

    private fun createFrontendOutput(firOutput: Any): Any {
        // Wrap in AllModulesFrontendOutput(listOf(firOutput))
        val constructor = allModulesFrontendOutputClass.getConstructor(List::class.java)
        return constructor.newInstance(listOf(firOutput))
    }

    private fun convertToIr(
        frontendOutput: Any,
        configuration: CompilerConfiguration,
        reporter: Any,
        project: Any,
    ): Any {
        // Create JvmFir2IrExtensions
        val jvmIrDeserializerConstructor = jvmIrDeserializerImplClass.getConstructor()
        val jvmIrDeserializer = jvmIrDeserializerConstructor.newInstance()

        val fir2IrExtensionsConstructor = jvmFir2IrExtensionsClass.getConstructor(
            CompilerConfiguration::class.java,
            Class.forName("org.jetbrains.kotlin.backend.jvm.JvmIrDeserializer"),
        )
        val fir2IrExtensions = fir2IrExtensionsConstructor.newInstance(configuration, jvmIrDeserializer)

        // Get IrGenerationExtension.getInstances(project) - returns empty list for us
        val irGenerationExtensions = emptyList<Any>()

        // Call convertToIrAndActualizeForJvm extension function
        val convertMethod = allModulesFrontendOutputClass.getDeclaredMethod(
            "convertToIrAndActualizeForJvm",
            Class.forName("org.jetbrains.kotlin.fir.backend.Fir2IrExtensions"),
            CompilerConfiguration::class.java,
            baseDiagnosticsCollectorClass,
            Collection::class.java,
        )
        convertMethod.isAccessible = true

        return convertMethod.invoke(frontendOutput, fir2IrExtensions, configuration, reporter, irGenerationExtensions)
    }

    @Suppress("LongMethod")
    private fun generateBytecode(
        fir2IrResult: Any,
        project: Any,
        configuration: CompilerConfiguration,
    ): org.jetbrains.kotlin.codegen.state.GenerationState {
        // Get irModuleFragment
        val irModuleFragmentField = fir2IrActualizedResultClass.getDeclaredField("irModuleFragment")
        irModuleFragmentField.isAccessible = true
        val irModuleFragment = irModuleFragmentField.get(fir2IrResult)

        // Get descriptor from irModuleFragment
        val descriptorMethod = irModuleFragmentClass.getDeclaredMethod("getDescriptor")
        val moduleDescriptor = descriptorMethod.invoke(irModuleFragment)

        // Get components
        val componentsField = fir2IrActualizedResultClass.getDeclaredField("components")
        componentsField.isAccessible = true
        val components = componentsField.get(fir2IrResult)

        // Create FirJvmBackendClassResolver
        val classResolverConstructor = firJvmBackendClassResolverClass.getConstructor(fir2IrComponentsClass)
        val classResolver = classResolverConstructor.newInstance(components)

        // Get ClassBuilderFactories.BINARIES
        val binariesField = classBuilderFactoriesClass.getField("BINARIES")
        val binaries = binariesField.get(null)

        // Create GenerationState
        val generationStateConstructor = org.jetbrains.kotlin.codegen.state.GenerationState::class.java.constructors
            .find { it.parameterCount >= 4 }
            ?: throw K2CompilationException("Could not find GenerationState constructor")
        generationStateConstructor.isAccessible = true

        // Find the constructor with named parameters:
        // GenerationState(project, descriptor, configuration, classBuilderFactory, jvmBackendClassResolver, ...)
        val generationState = org.jetbrains.kotlin.codegen.state.GenerationState(
            project as org.jetbrains.kotlin.com.intellij.openapi.project.Project,
            moduleDescriptor as org.jetbrains.kotlin.descriptors.ModuleDescriptor,
            configuration,
            binaries as org.jetbrains.kotlin.codegen.ClassBuilderFactory,
            jvmBackendClassResolver = classResolver as org.jetbrains.kotlin.codegen.JvmBackendClassResolver,
        )

        // Get pluginContext
        val pluginContextField = fir2IrActualizedResultClass.getDeclaredField("pluginContext")
        pluginContextField.isAccessible = true
        val pluginContext = pluginContextField.get(fir2IrResult)

        // Get irBuiltIns from pluginContext
        val irBuiltInsMethod = irPluginContextClass.getDeclaredMethod("getIrBuiltIns")
        val irBuiltIns = irBuiltInsMethod.invoke(pluginContext)

        // Get symbolTable
        val symbolTableField = fir2IrActualizedResultClass.getDeclaredField("symbolTable")
        symbolTableField.isAccessible = true
        val symbolTable = symbolTableField.get(fir2IrResult)

        // Get irProviders from components
        val irProvidersMethod = fir2IrComponentsClass.getDeclaredMethod("getIrProviders")
        val irProviders = irProvidersMethod.invoke(components)

        // Get irActualizedResult for FirJvmBackendExtension
        val irActualizedResultField = fir2IrActualizedResultClass.getDeclaredField("irActualizedResult")
        irActualizedResultField.isAccessible = true
        val irActualizedResult = irActualizedResultField.get(fir2IrResult)

        // Create FirJvmBackendExtension - it needs actualizedExpectDeclarations
        val extractFirDeclarationsMethod = Class.forName("org.jetbrains.kotlin.fir.backend.utils.ExtractFirDeclarationsKt")
            .getDeclaredMethod("extractFirDeclarations", Collection::class.java)
        extractFirDeclarationsMethod.isAccessible = true

        val actualizedExpectDeclarations = if (irActualizedResult != null) {
            val actualizedField = irActualizedResult::class.java.getDeclaredField("actualizedExpectDeclarations")
            actualizedField.isAccessible = true
            val actualizedExpect = actualizedField.get(irActualizedResult)
            if (actualizedExpect != null) {
                extractFirDeclarationsMethod.invoke(null, actualizedExpect)
            } else {
                null
            }
        } else {
            null
        }

        val backendExtensionConstructor = firJvmBackendExtensionClass.getConstructor(
            fir2IrComponentsClass,
            Set::class.java,
        )
        val backendExtension = backendExtensionConstructor.newInstance(
            components,
            actualizedExpectDeclarations ?: emptySet<Any>(),
        )

        // Create JvmGeneratorExtensions - use default
        val generatorExtensions = jvmFir2IrExtensionsClass.getConstructor(
            CompilerConfiguration::class.java,
            Class.forName("org.jetbrains.kotlin.backend.jvm.JvmIrDeserializer"),
        ).newInstance(configuration, jvmIrDeserializerImplClass.getConstructor().newInstance())

        // Create BackendInput
        val backendInputConstructor = backendInputClass.constructors.first()
        backendInputConstructor.isAccessible = true

        val backendInput = backendInputConstructor.newInstance(
            irModuleFragment,
            irBuiltIns,
            symbolTable,
            irProviders,
            generatorExtensions,
            backendExtension,
            pluginContext,
        )

        // Create JvmIrCodegenFactory and generate
        val codegenFactory = jvmIrCodegenFactoryClass
            .getConstructor(CompilerConfiguration::class.java)
            .newInstance(configuration)

        val generateMethod = jvmIrCodegenFactoryClass.getDeclaredMethod(
            "generateModule",
            org.jetbrains.kotlin.codegen.state.GenerationState::class.java,
            backendInputClass,
        )
        generateMethod.isAccessible = true
        generateMethod.invoke(codegenFactory, generationState, backendInput)

        return generationState
    }
}

@Throws(CompilationFailed::class)
private fun kompile(
    source: Source,
    kompilationArguments: KompilationArguments,
    parentFileManager: JeedFileManager? = kompilationArguments.parentFileManager,
    parentClassLoader: ClassLoader? = kompilationArguments.parentClassLoader,
): CompiledSource {
    require(source.type == Source.SourceType.KOTLIN) { "Kotlin compiler needs Kotlin sources" }

    val started = Instant.now()
    source.tryCache(kompilationArguments, started, systemCompilerName)?.let { return it }

    val (fileManager, messages) = kompileToFileManager(kompilationArguments, source, parentFileManager)
    val actualParentClassloader = if (kompilationArguments.isolatedClassLoader) {
        IsolatingClassLoader(fileManager.classFiles.keys.map { pathToClassName(it) }.toSet())
    } else {
        parentClassLoader
    }

    return CompiledSource(
        source,
        messages,
        started,
        Interval(started, Instant.now()),
        JeedClassLoader(fileManager, actualParentClassloader),
        fileManager,
    ).also { compiledSource ->
        compiledSource.cache(kompilationArguments)
    }
}

fun Source.kompile(kompilationArguments: KompilationArguments = KompilationArguments()) = kompile(this, kompilationArguments)

private val KOTLIN_COROUTINE_IMPORTS = setOf("kotlinx.coroutines", "kotlin.coroutines")
const val KOTLIN_COROUTINE_MIN_TIMEOUT = 600L
const val KOTLIN_COROUTINE_MIN_EXTRA_THREADS = 4

fun CompiledSource.usesCoroutines(): Boolean = source.sources.keys
    .map { source.getParsed(it).tree }
    .any { tree ->
        tree as? KotlinParser.KotlinFileContext ?: error("Parse tree is not from a Kotlin file")
        tree.importList().importHeader().any { importName ->
            KOTLIN_COROUTINE_IMPORTS.any { importName.identifier().text.startsWith(it) }
        }
    }

fun JeedFileManager.toVirtualFile(): VirtualFile {
    val root = SimpleVirtualFile("", listOf(), true)
    allFiles.forEach { (path, file) ->
        var workingDirectory = root
        path.split("/").also { parts ->
            parts.dropLast(1).forEach { directory ->
                workingDirectory = workingDirectory.children.find { it.name == directory }
                    ?: workingDirectory.addChild(SimpleVirtualFile(directory))
            }
            workingDirectory.addChild(
                SimpleVirtualFile(
                    parts.last(),
                    contents = file.openInputStream().readAllBytes(),
                    up = workingDirectory,
                ),
            )
        }
    }
    return root
}

@Suppress("TooManyFunctions")
object SimpleVirtualFileSystem : VirtualFileSystem() {
    override fun getProtocol() = ""

    override fun deleteFile(p0: Any?, p1: VirtualFile) = TODO("deleteFile")
    override fun createChildDirectory(p0: Any?, p1: VirtualFile, p2: String) = TODO("createChildDirectory")
    override fun addVirtualFileListener(p0: VirtualFileListener) = TODO("addVirtualFileListener")
    override fun isReadOnly() = TODO("isReadOnly")
    override fun findFileByPath(p0: String) = TODO("findFileByPath")
    override fun renameFile(p0: Any?, p1: VirtualFile, p2: String) = TODO("renameFile")
    override fun createChildFile(p0: Any?, p1: VirtualFile, p2: String) = TODO("createChildFile")
    override fun refreshAndFindFileByPath(p0: String) = TODO("refreshAndFindFileByPath")
    override fun removeVirtualFileListener(p0: VirtualFileListener) = TODO("removeVirtualFileListener")
    override fun copyFile(p0: Any?, p1: VirtualFile, p2: VirtualFile, p3: String) = TODO("copyFile")
    override fun moveFile(p0: Any?, p1: VirtualFile, p2: VirtualFile) = TODO("moveFile")
    override fun refresh(p0: Boolean) = TODO("refresh")
}

@Suppress("TooManyFunctions")
class SimpleVirtualFile(
    private val name: String,
    children: List<SimpleVirtualFile> = listOf(),
    private val directory: Boolean? = null,
    val contents: ByteArray? = null,
    val up: SimpleVirtualFile? = null,
) : VirtualFile() {
    private val created = LocalTimeCounter.currentTime()

    private val children = children.toMutableList()
    fun addChild(directory: SimpleVirtualFile): SimpleVirtualFile {
        children.add(directory)
        return directory
    }

    override fun getName() = name
    override fun getChildren() = children.toTypedArray()

    override fun isValid() = true
    override fun isDirectory() = directory ?: children.isNotEmpty()
    override fun contentsToByteArray() = contents!!

    override fun getModificationStamp() = created
    override fun getFileSystem() = SimpleVirtualFileSystem

    override fun toString() = prefixedString("").joinToString(separator = "\n")
    private fun prefixedString(path: String): List<String> = if (!isDirectory) {
        listOf("$path$name")
    } else {
        mutableListOf<String>().also { paths ->
            children.forEach { child ->
                paths.addAll(child.prefixedString("$path/$name"))
            }
        }
    }

    override fun getPath() = name // FIXME to include directory
    override fun getParent() = up

    override fun getTimeStamp() = TODO("getTimeStamp")
    override fun refresh(p0: Boolean, p1: Boolean, p2: Runnable?) = TODO("refresh")
    override fun getLength() = contents!!.size.toLong()
    override fun getInputStream() = TODO("getInputStream")
    override fun isWritable() = TODO("isWritable")
    override fun getOutputStream(p0: Any?, p1: Long, p2: Long) = TODO("getOutputStream")
}

@Suppress("unused")
fun Class<*>.isKotlin() = getAnnotation(Metadata::class.java) != null

private fun String.toJvmTarget() = when (this) {
    "1.6" -> JvmTarget.JVM_1_6
    "1.8" -> JvmTarget.JVM_1_8
    "10" -> JvmTarget.JVM_10
    "11" -> JvmTarget.JVM_11
    "12" -> JvmTarget.JVM_12
    "13" -> JvmTarget.JVM_13
    "14" -> JvmTarget.JVM_14
    "15" -> JvmTarget.JVM_15
    "16" -> JvmTarget.JVM_16
    "17" -> JvmTarget.JVM_17
    "18" -> JvmTarget.JVM_18
    "19" -> JvmTarget.JVM_19
    "20" -> JvmTarget.JVM_20
    "21" -> JvmTarget.JVM_21
    else -> error("Bad JVM target: $this")
}

@Suppress("MagicNumber")
private fun Int.toCompilerVersion() = when (this) {
    6 -> "1.6"
    8 -> "1.8"
    in 10..21 -> toString()
    else -> error("Bad JVM target: $this")
}

fun getEmptyKotlinClassSize() = Source(mapOf("Test.kt" to "fun test() = true")).kompile().classLoader.sizeInBytes
