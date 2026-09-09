@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.google.common.base.Objects
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import io.github.classgraph.ClassGraph
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlin.KtInMemoryTextSourceFile
import org.jetbrains.kotlin.KtSourceFile
import org.jetbrains.kotlin.backend.common.CommonBackendErrors
import org.jetbrains.kotlin.backend.common.actualizer.IrActualizationErrors
import org.jetbrains.kotlin.backend.common.diagnostics.SerializationErrors
import org.jetbrains.kotlin.backend.jvm.JvmBackendErrors
import org.jetbrains.kotlin.cli.common.CLIConfigurationKeys
import org.jetbrains.kotlin.cli.common.arguments.K2JVMCompilerArguments
import org.jetbrains.kotlin.cli.common.arguments.parseCommandLineArguments
import org.jetbrains.kotlin.cli.common.environment.setIdeaIoUseFallback
import org.jetbrains.kotlin.cli.common.fir.FirDiagnosticsCompilerResultsReporter
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSourceLocation
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.cli.common.moduleChunk
import org.jetbrains.kotlin.cli.common.modules.ModuleBuilder
import org.jetbrains.kotlin.cli.common.modules.ModuleChunk
import org.jetbrains.kotlin.cli.common.renderDiagnosticInternalName
import org.jetbrains.kotlin.cli.common.setupCommonArguments
import org.jetbrains.kotlin.cli.create
import org.jetbrains.kotlin.cli.diagnosticFactoriesStorage
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles
import org.jetbrains.kotlin.cli.jvm.compiler.prepareIncrementalCompilationContextAndLibrariesScope
import org.jetbrains.kotlin.cli.jvm.config.VirtualJvmClasspathRoot
import org.jetbrains.kotlin.cli.jvm.config.configureJdkClasspathRoots
import org.jetbrains.kotlin.cli.jvm.configureAdvancedJvmOptions
import org.jetbrains.kotlin.cli.jvm.configureContentRootsFromClassPath
import org.jetbrains.kotlin.cli.jvm.configureJavaModulesContentRoots
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmBackendPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFir2IrPipelinePhase
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelineArtifact
import org.jetbrains.kotlin.cli.pipeline.jvm.JvmFrontendPipelinePhase
import org.jetbrains.kotlin.codegen.ClassFileFactory
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileListener
import org.jetbrains.kotlin.com.intellij.openapi.vfs.VirtualFileSystem
import org.jetbrains.kotlin.com.intellij.util.LocalTimeCounter
import org.jetbrains.kotlin.config.CommonConfigurationKeys
import org.jetbrains.kotlin.config.CompilerConfiguration
import org.jetbrains.kotlin.config.JVMConfigurationKeys
import org.jetbrains.kotlin.config.JvmTarget
import org.jetbrains.kotlin.config.JvmWhenGenerationScheme
import org.jetbrains.kotlin.config.useLightTree
import org.jetbrains.kotlin.diagnostics.impl.DiagnosticsCollectorImpl
import org.jetbrains.kotlin.fir.pipeline.AllModulesFrontendOutput
import org.jetbrains.kotlin.fir.pipeline.buildFirViaLightTree
import org.jetbrains.kotlin.fir.pipeline.resolveAndCheckFir
import org.jetbrains.kotlin.fir.pipeline.runPlatformCheckers
import org.jetbrains.kotlin.idea.KotlinFileType
import org.jetbrains.kotlin.ir.validation.IrValidationDiagnostics
import org.jetbrains.kotlin.konan.file.File
import org.jetbrains.kotlin.metadata.deserialization.MetadataVersion
import org.jetbrains.kotlin.metadata.jvm.deserialization.JvmProtoBufUtil
import org.jetbrains.kotlin.name.Name
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
    // Ignored since Kotlin 2.4, which removed K1 (KT-80590); there is no other frontend to
    // select. Retained so that existing requests carrying it still deserialize.
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
        // Kotlin 2.4 moved unused-variable reporting (and friends) behind the extra checkers.
        // K1 reported those by default, and they are worth keeping for students, so ask for them
        // explicitly rather than quietly dropping a warning class in the upgrade.
        arguments.extraWarnings = true
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
 * Kotlin 2.4.20 deleted the legacy K2 CLI pipeline this used to call
 * (`cli.jvm.compiler.legacy.pipeline`), so the compilation is now composed out of the phased CLI
 * pipeline in `cli.pipeline.jvm`. The frontend phase cannot be called as a phase: it collects its
 * sources by walking source roots through the local filesystem. Its pieces are public, though, so
 * the project environment, library list and sessions are built here the way the phase builds them,
 * and the sources are handed over as `KtInMemoryTextSourceFile`s through the light tree, which is
 * the CLI default and needs no PSI and no VirtualFile. The fir2ir and backend phases are then
 * called through `executePhase` with the public artifact types.
 *
 * Calling `executePhase` directly skips each phase's pre- and post-actions, which is deliberate:
 * the error check among them throws `PipelineStepException`, and Jeed wants a `CompilationFailed`
 * carrying its own list of messages. So the two checks that matter are made explicitly here, after
 * parsing and after fir2ir. Nothing on this path opens a file for writing: `JvmWriteOutputsPhase`
 * is the only phase that writes class files, and it is never invoked, so the bytecode stays in the
 * `ClassFileFactory` that `JeedFileManager` reads.
 *
 * One behaviour change came with the port. Diagnostics reported during the backend phases
 * (`JvmBackendErrors`, so `CONFLICTING_JVM_DECLARATIONS`, `ACCIDENTAL_OVERRIDE`,
 * `INLINE_CALL_CYCLE` and friends) carry a source file the reporter only computes positions for
 * when it exists on disk, and these sources never do, so those messages arrive without a line or
 * column. They still count as errors. Frontend diagnostics -- syntax, resolution and every checker,
 * which is the overwhelming majority -- keep exact positions. Do not try to fix this by handing the
 * source an absolute path: `Source.mapLocation` keys on the source name.
 *
 * The alternative is the Build Tools API, which is what Kotlin's own playground server uses. It
 * is a supported API rather than an opt-in internal one, but it only speaks in filesystem paths,
 * so every compilation would have to round-trip sources and classes through a temporary
 * directory.
 */
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
        val moduleName = JvmProtoBufUtil.DEFAULT_MODULE_NAME
        // CompilerConfiguration.create is 2.4's supported way to build a configuration. It
        // registers the compiler-plugin extension storage and the diagnostic factories that the
        // FIR pipeline reads, both of which fail loudly at first use if they are missing. The FIR
        // pipeline also collects diagnostics into the reporter rather than reporting as it goes,
        // so they have to be forwarded to the message collector afterwards.
        val configuration = CompilerConfiguration.create(
            diagnosticsCollector = diagnosticsReporter,
            messageCollector = messageCollector,
        ).apply {
            // Derives languageVersionSettings from the arguments, which is what actually applies
            // the -opt-in flags in additionalCompilerArguments, and what the checkers consult to
            // decide which diagnostics to report. The CLI does this first.
            setupCommonArguments(kompilationArguments.arguments) { MetadataVersion(*it) }

            put(CommonConfigurationKeys.MODULE_NAME, moduleName)
            put(JVMConfigurationKeys.PARAMETERS_METADATA, kompilationArguments.parameters)
            put(JVMConfigurationKeys.JVM_TARGET, kompilationArguments.jvmTarget.toJvmTarget())
            put(JVMConfigurationKeys.JDK_HOME, java.io.File(System.getProperty("java.home")))

            // Already the default, and setupCommonArguments has just set it, but the backend phase
            // hard-requires it: its PSI branch casts every source to KtPsiSourceFile, which an
            // in-memory text source is not.
            useLightTree = true

            // Kotlin 2.4.20 generates a type-checking when as an invokedynamic to
            // java.lang.runtime.SwitchBootstraps.typeSwitch plus a tableswitch once the JVM target
            // is 21, where 2.4.10 generated a chain of type checks. It runs fine in the sandbox,
            // but the whole dispatch then sits on the line of the when subject instead of on each
            // branch, so a when with no else reports its unreachable default as a missed branch on
            // that line, and the LAST_WHEN_ENTRY coverage adjustment no longer has anything to
            // match. That is a line of student feedback, not an implementation detail, so keep the
            // shape Jeed has always produced. -Xwhen-expressions would not reach this: it is read
            // by setupJvmSpecificArguments, which this pipeline does not call.
            put(JVMConfigurationKeys.WHEN_GENERATION_SCHEME, JvmWhenGenerationScheme.INLINE)

            // JvmBackendPipelinePhase dereferences this and drives one codegen run per module in
            // it. The output directory it names is never read on this path, since that is only
            // consulted through JVMConfigurationKeys.MODULES, which stays empty -- which is also
            // what keeps an incremental-compilation scope from being created.
            moduleChunk = ModuleChunk(listOf(ModuleBuilder(moduleName, "", "java-production")))

            // What the CLI's configuration phase registers. Only -Xwarning-level reads this today,
            // and Jeed passes none, but registering the containers is one call and keeps a future
            // check over the storage working.
            diagnosticFactoriesStorage?.registerDiagnosticContainers(
                IrActualizationErrors,
                CommonBackendErrors,
                SerializationErrors,
                IrValidationDiagnostics,
                JvmBackendErrors,
            )

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

        // The frontend phase's own factory rather than KotlinCoreEnvironment: it returns a project
        // environment that registers the classpath roots with each package part provider it hands
        // out, which is what lets a previous compilation's META-INF/*.kotlin_module be read back
        // and therefore what makes its top-level declarations resolvable here. It also registers
        // the virtual file and metadata finders the FIR pipeline looks up.
        val projectEnvironment = JvmFrontendPipelinePhase.createProjectEnvironment(
            configuration,
            rootDisposable,
            EnvironmentConfigFiles.JVM_CONFIG_FILES,
        )

        // The path has to be non-null or every diagnostic location comes back null, and it has to
        // be the bare source name: it is reported verbatim, so anything else would have to be
        // stripped back off before Source.mapLocation could match it. Diagnostic locations and the
        // IR file entry read the path, not the name.
        //
        // The JVM file facade class is named from that path, but only from its last segment:
        // FileClassLowering takes File(fileEntry.name).name for a light-tree file. The PSI branch
        // it used to take passed the whole file name through, and Jeed was handing it a name with
        // directories in it, which a file on disk never has -- so a top-level declaration in
        // com/example/Util.kt used to land in com.example.Com_example_UtilKt. It now lands in
        // com.example.UtilKt, which is what kotlinc produces for that file on 2.4.10 and 2.4.20
        // alike. Two sources sharing a last segment and declaring no package collide as a result,
        // exactly as they do under kotlinc: "Duplicate JVM class name 'MainKt'".
        //
        // The name only has to end in .kt. The light-tree parser decides whether to parse a file as
        // a Kotlin script from its extension and treats everything else as one, and a snippet's
        // source name is the empty string, so a snippet would be parsed as a script: every
        // declaration wrapped in a synthetic script class, and the JVM backend then failing on the
        // first of them. PSI defaulted the other way round, so this restores the old behaviour
        // rather than choosing new. Jeed never compiles scripts here; every other source name
        // already ends in .kt and is unchanged.
        val sources: List<KtSourceFile> = kotlinSource.sources.map { (name, contents) ->
            KtInMemoryTextSourceFile(
                name = name.takeIf { it.endsWith(KotlinFileType.DOT_DEFAULT_EXTENSION) }
                    ?: (name + KotlinFileType.DOT_DEFAULT_EXTENSION),
                path = name,
                text = contents,
            )
        }

        // Diagnostics are drained exactly once: the collector holds every diagnostic from every
        // phase, and JeedMessageCollector doesn't deduplicate warnings, so forwarding twice would
        // report each one twice. Nothing below throws CompilationFailed, so the catch cannot drain
        // a second time; every stop point returns null instead.
        fun drainDiagnosticsIntoMessageCollector() = FirDiagnosticsCompilerResultsReporter
            .reportToMessageCollector(diagnosticsReporter, messageCollector, configuration.renderDiagnosticInternalName)

        fun compileInMemory(): ClassFileFactory? {
            val libraryList = JvmFrontendPipelinePhase.createLibraryListForJvm(
                moduleName,
                configuration,
                friendPaths = emptyList(),
            )
            // With no modules configured this is just the project libraries scope and a null
            // incremental context, which is what the frontend phase would compute too.
            val (librariesScope, incrementalContext) = prepareIncrementalCompilationContextAndLibrariesScope(
                configuration,
                projectEnvironment,
                incrementalExcludesScope = null,
            )
            val sessions = JvmFrontendPipelinePhase.prepareJvmSessions(
                files = sources,
                rootModuleName = Name.special("<$moduleName>"),
                configuration = configuration,
                projectEnvironment = projectEnvironment,
                librariesScope = librariesScope,
                libraryList = libraryList,
                isCommonSource = { false },
                isScript = { false },
                fileBelongsToModule = { _, _ -> true },
                incrementalCompilationContext = incrementalContext,
            )

            // Parse. Syntax errors are collected into the reporter and a FIR file is still built
            // for every source, so stopping has to be explicit -- and it is Jeed's own policy: the
            // light-tree CLI resolves regardless, only its PSI branch stops here. Resolving anyway
            // buries the real syntax error under whatever follow-on complaints the checkers make
            // about the half-parsed tree.
            val parsed = sessions.map { (session, files) ->
                session to session.buildFirViaLightTree(files, diagnosticsReporter, reportFilesAndLines = null)
            }
            if (diagnosticsReporter.hasErrors) {
                return null
            }

            val frontendOutput = parsed.map { (session, firFiles) ->
                resolveAndCheckFir(session, firFiles, diagnosticsReporter)
            }.also { outputs ->
                outputs.runPlatformCheckers(diagnosticsReporter)
            }
            if (diagnosticsReporter.hasErrors) {
                return null
            }

            val fir2Ir = checkNotNull(
                JvmFir2IrPipelinePhase.executePhase(
                    JvmFrontendPipelineArtifact(
                        AllModulesFrontendOutput(frontendOutput),
                        configuration,
                        projectEnvironment,
                        sources,
                    ),
                ),
            )
            // The CLI makes this check as a phase post-action. Without it the lowerings would run
            // over IR that fir2ir gave up on part-way through.
            if (diagnosticsReporter.hasErrors) {
                return null
            }

            return JvmBackendPipelinePhase.executePhase(fir2Ir).outputs.single().factory
        }

        val factory = try {
            compileInMemory()
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
        check(factory != null) { "compilation should have succeeded" }

        return Pair(
            JeedFileManager(
                parentFileManager ?: standardFileManager,
                GeneratedClassLoader(factory, kompilationArguments.parentClassLoader),
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
