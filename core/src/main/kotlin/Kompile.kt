@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.core

import com.google.common.base.Objects
import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import io.github.classgraph.ClassGraph
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

@JsonClass(generateAdapter = true)
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
                parentFileManager !== other.parentFileManager -> false
                else -> true
            }
        }
    }

    override fun hashCode() = Objects.hashCode(verbose, allWarningsAsErrors, parameters, jvmTarget, parentFileManager)
}

internal class JeedMessageCollector(val source: Source, private val allWarningsAsErrors: Boolean) : MessageCollector {
    private val messages: MutableList<CompilationMessage> = mutableListOf()

    override fun clear() {
        messages.clear()
    }

    val errors: List<CompilationError>
        get() = messages.filter {
            it.kind == CompilerMessageSeverity.ERROR.presentableName ||
                allWarningsAsErrors &&
                (
                    it.kind == CompilerMessageSeverity.WARNING.presentableName ||
                        it.kind == CompilerMessageSeverity.STRONG_WARNING.presentableName
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

        environment::class.java.getDeclaredField("sourceFiles").also { field ->
            field.isAccessible = true
            field.set(environment, psiFiles)
        }

        val state = try {
            KotlinToJVMBytecodeCompiler.analyzeAndGenerate(environment)
        } catch (e: Throwable) {
            throw CompilationFailed(listOf(CompilationError(null, "Kotlin internal compiler error: ${e.message}")))
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
