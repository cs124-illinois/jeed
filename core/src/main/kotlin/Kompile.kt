@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.google.common.base.Objects
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import io.github.classgraph.ClassGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlin.KtPsiSourceFile
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.LegacyK2CliPipeline
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.fir.FirDiagnosticsCompilerResultsReporter
import org.jetbrains.kotlin.cli.common.messages.AnalyzerWithCompilerReport
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.prepareJvmSessions
import org.jetbrains.kotlin.cli.common.renderDiagnosticInternalName
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.MinimizedFrontendContext
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.ModuleCompilerEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.convertAnalyzedFirToIr
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.createProjectEnvironment
import org.jetbrains.kotlin.cli.jvm.compiler.legacy.pipeline.generateCodeFromIr
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.configureContentRootsFromClassPath
import org.jetbrains.kotlin.cli.jvm.configureJavaModulesContentRoots
import org.jetbrains.kotlin.cli.pipeline.jvm.asKtFilesList
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileListener
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.psi.PsiFileFactory
import org.jetbrains.kotlin.com.intellij.psi.impl.PsiFileFactoryImpl
import org.jetbrains.kotlin.com.intellij.testFramework.LightVirtualFile
import org.jetbrains.kotlin.com.intellij.util.LocalTimeCounter
import org.jetbrains.kotlin.compiler.plugin.getCompilerExtensions
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.fir.extensions.FirExtensionRegistrar
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.buildResolveAndCheckFirFromKtFiles
import org.jetbrains.kotlin.fir.pipeline.runPlatformCheckers
import org.jetbrains.kotlin.idea.KotlinLanguage
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.modules.TargetId
import org.jetbrains.kotlin.psi.KtFile
import java.nio.file.FileSystems
import java.nio.file.Path
import java.nio.file.Paths
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

/**
 * Drives the K2 (FIR) compiler pipeline directly, keeping sources and output in memory.
 *
 * Kotlin 2.4 removed the K1 frontend (KT-80590), which took
 * `KotlinToJVMBytecodeCompiler.analyzeAndGenerate` with it -- that entry point ran the classic
 * frontend regardless of the USE_FIR flag, and is itself slated for removal (KT-71729). The
 * replacement is to compose the three pipeline stages by hand: build and resolve FIR, convert it
 * to IR, then run codegen. `generateCodeFromIr` only writes class files when the configuration
 * names an output directory, so leaving that unset keeps everything in the GenerationState and
 * out of the filesystem, which is what JeedFileManager wants.
 *
 * The alternative is the Build Tools API, which is what Kotlin's own playground server uses. It
 * is a supported API rather than an opt-in internal one, but it only speaks in filesystem paths,
 * so every compilation would have to round-trip sources and classes through a temporary
 * directory.
 */
@OptIn(LegacyK2CliPipeline::class)
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
        val diagnosticsReporter = DiagnosticsCollectorImpl()
        // CompilerConfiguration.create is 2.4's supported way to build a configuration. It
        // registers the compiler-plugin extension storage and the diagnostic factories that the
        // FIR pipeline reads, both of which fail loudly at first use if they are missing. The FIR
        // pipeline also collects diagnostics into the reporter rather than reporting as it goes,
        // so they have to be forwarded to the message collector afterwards.
        val configuration = CompilerConfiguration.create(
            diagnosticsCollector = diagnosticsReporter,
            messageCollector = messageCollector,
        ).apply {
            put(CommonConfigurationKeys.MODULE_NAME, JvmProtoBufUtil.DEFAULT_MODULE_NAME)
            put(CommonConfigurationKeys.USE_FIR, kompilationArguments.useK2)
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

        // The legacy-pipeline factory rather than KotlinCoreEnvironment: it returns a project
        // environment that registers the classpath roots with each package part provider it hands
        // out, which is what lets a previous compilation's META-INF/*.kotlin_module be read back
        // and therefore what makes its top-level declarations resolvable here. It also registers
        // the virtual file and metadata finders the FIR pipeline looks up.
        val projectEnvironment = createProjectEnvironment(
            configuration,
            rootDisposable,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )

        val psiFileFactory = PsiFileFactory.getInstance(projectEnvironment.project) as PsiFileFactoryImpl
        val psiFiles = kotlinSource.sources.map { (name, contents) ->
            psiFileFactory.trySetupPsiForFile(
                LightVirtualFile(name, KotlinLanguage.INSTANCE, contents),
                KotlinLanguage.INSTANCE,
                true,
                false,
            ) as KtFile?
                ?: error("couldn't parse source to psiFile")
        }

        // Diagnostics are drained exactly once: the collector holds every diagnostic from every
        // phase, and JeedMessageCollector doesn't deduplicate warnings, so forwarding twice would
        // report each one twice.
        fun drainDiagnosticsIntoMessageCollector() = FirDiagnosticsCompilerResultsReporter
            .reportToMessageCollector(diagnosticsReporter, messageCollector, configuration.renderDiagnosticInternalName)

        // Parse errors are reported into the collector rather than thrown, so they have to be
        // gathered before anything tries to resolve the files.
        psiFiles.forEach { AnalyzerWithCompilerReport.reportSyntaxErrors(it, diagnosticsReporter) }

        // Stop at a parse failure instead of resolving code the compiler could not read: the CLI
        // does the same, and resolving anyway buries the real syntax error under whatever
        // follow-on complaints the checkers make about the half-parsed tree.
        if (diagnosticsReporter.hasErrors) {
            drainDiagnosticsIntoMessageCollector()
            throw CompilationFailed(messageCollector.errors)
        }

        val targetId = TargetId(JvmProtoBufUtil.DEFAULT_MODULE_NAME, "java-production")
        val moduleEnvironment = ModuleCompilerEnvironment(projectEnvironment, diagnosticsReporter)

        val state = try {
            val frontendContext = MinimizedFrontendContext(
                projectEnvironment,
                messageCollector,
                configuration.getCompilerExtensions(FirExtensionRegistrar),
                configuration,
            )
            val sessions = frontendContext.prepareJvmSessions(
                files = psiFiles.map { KtPsiSourceFile(it) },
                rootModuleNameAsString = targetId.name,
                friendPaths = emptyList(),
                librariesScope = projectEnvironment.getSearchScopeForProjectLibraries(),
                isCommonSource = { false },
                isScript = { false },
                fileBelongsToModule = { _, _ -> true },
                createProviderAndScopeForIncrementalCompilation = { null },
            )
            val frontendOutput = sessions.map { (session, sources) ->
                buildResolveAndCheckFirFromKtFiles(session, sources.asKtFilesList(), diagnosticsReporter)
            }.also { outputs ->
                outputs.runPlatformCheckers(diagnosticsReporter)
            }

            // Don't hand broken code to the backend; the CLI checks between these phases too.
            if (diagnosticsReporter.hasErrors) {
                null
            } else {
                val backendInput = convertAnalyzedFirToIr(
                    configuration,
                    targetId,
                    AllModulesFrontendOutput(frontendOutput),
                    moduleEnvironment,
                )
                generateCodeFromIr(backendInput, moduleEnvironment)
            }
        } catch (e: Throwable) {
            drainDiagnosticsIntoMessageCollector()
            // A diagnostic the student can act on beats "something went wrong inside the compiler".
            throw CompilationFailed(
                messageCollector.errors.ifEmpty {
                    listOf(CompilationError(null, "Kotlin internal compiler error: ${e.message}"))
                },
            )
        }

        drainDiagnosticsIntoMessageCollector()

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
                    ?: workingDirectory.addChild(SimpleVirtualFile(directory, up = workingDirectory))
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

    // createLibraryListForJvm runs every virtual classpath root through toNioPath to key the
    // library path filter, and that filter accepts a file by testing whether its path starts with
    // a root's path. These files only exist in memory, so synthesize a path per root and nest the
    // children underneath it, which is what makes classes in packages resolvable. Nothing reads
    // bytes through this path; the contents are served by contentsToByteArray.
    private val syntheticNioPath: Path by lazy {
        up?.toNioPath()?.resolve(name) ?: Paths.get("jeed-in-memory-${System.identityHashCode(this)}")
    }

    override fun toNioPath(): Path = syntheticNioPath

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

    // Must agree with toNioPath: ModuleDataProvider decides which module a library file belongs
    // to by prefix-matching this against the roots recorded in the dependency list, so returning
    // the bare name meant Kotlin metadata -- and therefore top-level declarations from a previous
    // compilation -- was attributed to no module at all and silently dropped.
    override fun getPath() = syntheticNioPath.toString()
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
