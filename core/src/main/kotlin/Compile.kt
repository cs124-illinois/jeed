package edu.illinois.cs.cs125.jeed.core

import com.google.common.base.Objects
import edu.illinois.cs.cs125.jeed.core.serializers.CompilationFailedSerializer
import edu.illinois.cs.cs125.jeed.core.serializers.CompilationMessageSerializer
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import org.jetbrains.kotlin.codegen.GeneratedClassLoader
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI
import java.nio.charset.Charset
import java.time.Instant
import java.util.Locale
import javax.tools.Diagnostic
import javax.tools.DiagnosticListener
import javax.tools.FileObject
import javax.tools.ForwardingJavaFileManager
import javax.tools.JavaFileManager
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import javax.tools.StandardLocation
import javax.tools.ToolProvider

private val systemCompiler = ToolProvider.getSystemJavaCompiler()
    ?: error("systemCompiler not found: you are probably running a JRE, not a JDK")

const val DEFAULT_JAVA_VERSION = 10
val systemCompilerName = systemCompiler.sourceVersions.maxOrNull().toString()
val systemCompilerVersion = systemCompilerName.let {
    @Suppress("TooGenericExceptionCaught")
    try {
        it.split("_")[1].toInt()
    } catch (_: Exception) {
        DEFAULT_JAVA_VERSION
    }
}

val standardFileManager: JavaFileManager = run {
    val results = Results()
    ToolProvider.getSystemJavaCompiler().getStandardFileManager(results, Locale.US, Charset.forName("UTF-8")).also {
        check(results.diagnostics.isEmpty()) {
            "fileManager generated errors ${results.diagnostics}"
        }
    }
}
private val standardFileManagerSyncRoot = Object()

@Suppress("SpellCheckingInspection")
private var lastMultireleaseOperand: String? = null

@Suppress("PropertyName", "SpellCheckingInspection")
@Serializable
data class CompilationArguments(
    val wError: Boolean = DEFAULT_WERROR,
    @Suppress("ConstructorParameterNaming") val Xlint: String = DEFAULT_XLINT,
    val enablePreview: Boolean = DEFAULT_ENABLE_PREVIEW,
    @Transient val parentFileManager: JavaFileManager? = null,
    @Transient val parentClassLoader: ClassLoader? = null,
    val useCache: Boolean? = null,
    val waitForCache: Boolean = false,
    val isolatedClassLoader: Boolean = false,
    val parameters: Boolean = DEFAULT_PARAMETERS,
    val debugInfo: Boolean = DEFAULT_DEBUG,
    @Transient val messageFilter: (message: CompilationMessage) -> Boolean = { true },
) {
    init {
        check(!waitForCache || useCache == true) {
            "waitForCache can only be used if useCache is true"
        }
    }

    companion object {
        const val DEFAULT_WERROR = false
        const val DEFAULT_XLINT = "all"
        const val DEFAULT_ENABLE_PREVIEW = true
        const val PREVIEW_STARTED = 11
        const val DEFAULT_PARAMETERS = false
        const val DEFAULT_DEBUG = false
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true

        javaClass != other?.javaClass -> false

        else -> {
            other as CompilationArguments
            when {
                wError != other.wError -> false
                Xlint != other.Xlint -> false
                enablePreview != other.enablePreview -> false
                parameters != other.parameters -> false
                debugInfo != other.debugInfo -> false
                isolatedClassLoader != other.isolatedClassLoader -> false
                parentFileManager !== other.parentFileManager -> false
                else -> true
            }
        }
    }

    override fun hashCode(): Int {
        var result = Objects.hashCode(wError, Xlint, enablePreview, parameters, debugInfo, isolatedClassLoader)
        // Include content-based hash of parent file manager if present
        result = 31 * result + when (val parent = parentFileManager) {
            null -> 0
            is JeedFileManager -> parent.contentHashCode()
            else -> 1 // Different non-null, non-Jeed parent
        }
        return result
    }
}

class CompilationError(location: SourceLocation?, message: String) : SourceError(location, message)

@Serializable(with = CompilationFailedSerializer::class)
class CompilationFailed(
    errors: List<CompilationError>,
    val compilationData: FailedCompilationData? = null,
) : JeedError(errors) {
    override fun toString(): String = "compilation errors were encountered: ${errors.joinToString(separator = ",")}"
}

@Serializable
data class FailedCompilationData(
    @Contextual val started: Instant,
    val interval: Interval,
    val cached: Boolean = false,
    val compilerName: String = systemCompilerName,
)

@Serializable(with = CompilationMessageSerializer::class)
class CompilationMessage(@Suppress("unused") val kind: String, location: SourceLocation?, message: String) : SourceError(location, message)

@Suppress("LongParameterList")
class CompiledSource(
    val source: Source,
    val messages: List<CompilationMessage>,
    val compiled: Instant,
    val interval: Interval,
    @Transient val classLoader: JeedClassLoader,
    @Transient val fileManager: JeedFileManager,
    @Suppress("unused") val compilerName: String = systemCompilerName,
    val cached: Boolean = false,
)

@Suppress("LongMethod", "ComplexMethod")
@Throws(CompilationFailed::class)
internal fun compileToFileManager(
    compilationArguments: CompilationArguments = CompilationArguments(),
    source: Source,
    parentFileManager: JavaFileManager? = compilationArguments.parentFileManager,
): Pair<JeedFileManager, List<CompilationMessage>> {
    val javaSource = when (source.type) {
        Source.SourceType.JAVA -> source
        Source.SourceType.MIXED -> source.javaSource
        else -> error("Can't compile Kotlin-only sources")
    }

    val units = javaSource.sources.entries.map { Unit(it) }
    val results = Results()
    val fileManager = JeedFileManager(parentFileManager ?: standardFileManager)

    val options = mutableSetOf<String>()
    options.add("-proc:none")
    options.add("-Xlint:${compilationArguments.Xlint}")
    if (compilationArguments.parameters) {
        options.add("-parameters")
    }
    if (compilationArguments.enablePreview && systemCompilerVersion >= CompilationArguments.PREVIEW_STARTED) {
        options.addAll(listOf("--enable-preview", "--release", systemCompilerVersion.toString()))
    }
    if (compilationArguments.debugInfo) {
        options.add("-g")
    }

    val runCompilation = {
        systemCompiler.getTask(null, fileManager, results, options.toList(), null, units).call()
    }
    if (parentFileManager == null || parentFileManager is JeedFileManager) {
        runCompilation()
    } else {
        // Custom file manager might not handle concurrency correctly
        synchronized(standardFileManagerSyncRoot) {
            runCompilation()
        }
    }

    fun getMappedLocation(diagnostic: Diagnostic<out JavaFileObject>): SourceLocation? = diagnostic
        .let { if (it.source == null) null else it }
        ?.let { msg -> SourceLocation(msg.source.name, msg.lineNumber.toInt(), msg.columnNumber.toInt()) }
        ?.let { loc -> source.mapLocation(loc) }

    val errors = results.diagnostics.filter {
        it.kind == Diagnostic.Kind.ERROR || (it.kind == Diagnostic.Kind.WARNING && compilationArguments.wError)
    }.map {
        @Suppress("SwallowedException")
        val location = try {
            getMappedLocation(it)
        } catch (_: SourceMappingException) {
            null
        }
        CompilationError(location, it.getMessage(Locale.US))
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
    if (errors.isNotEmpty()) {
        throw CompilationFailed(errors)
    }

    val messages = results.diagnostics.map {
        @Suppress("SwallowedException")
        val location = try {
            getMappedLocation(it)
        } catch (_: SourceMappingException) {
            null
        }
        CompilationMessage(it.kind.toString(), location, it.getMessage(Locale.US))
    }.filter(compilationArguments.messageFilter)

    return Pair(fileManager, messages)
}

@Throws(CompilationFailed::class)
private fun compile(
    source: Source,
    compilationArguments: CompilationArguments = CompilationArguments(),
    parentFileManager: JavaFileManager? = compilationArguments.parentFileManager,
    parentClassLoader: ClassLoader? = compilationArguments.parentClassLoader ?: ClassLoader.getSystemClassLoader(),
): CompiledSource {
    require(source.type == Source.SourceType.JAVA) { "Java compiler needs Java sources" }
    require(!compilationArguments.isolatedClassLoader || compilationArguments.parentClassLoader == null) {
        "Can't use parentClassLoader when isolatedClassLoader is set"
    }

    val started = Instant.now()
    source.tryCache(compilationArguments, started, systemCompilerName)?.let { return it }

    try {
        val (fileManager, messages) = compileToFileManager(compilationArguments, source, parentFileManager)
        val actualParentClassloader = if (compilationArguments.isolatedClassLoader) {
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
            compiledSource.cache(compilationArguments)
        }
    } catch (e: CompilationFailed) {
        // Cache failed compilation
        val failedData = FailedCompilationData(
            started,
            Interval(started, Instant.now()),
            cached = false,
            systemCompilerName,
        )
        val errors = e.errors.filterIsInstance<CompilationError>()
        source.cacheFailure(compilationArguments, errors, failedData)
        throw CompilationFailed(errors, failedData)
    }
}

fun Source.compile(
    compilationArguments: CompilationArguments = CompilationArguments(),
): CompiledSource = compile(this, compilationArguments)

fun Source.compileWith(
    compiledSource: CompiledSource,
    compilationArguments: CompilationArguments = CompilationArguments(),
): CompiledSource {
    require(compilationArguments.parentFileManager == null) {
        "compileWith overrides parentFileManager compilation argument"
    }
    require(compilationArguments.parentClassLoader == null) {
        "compileWith overrides parentClassLoader compilation argument"
    }
    return compile(this, compilationArguments, compiledSource.fileManager, compiledSource.classLoader)
}

private class Unit(val entry: Map.Entry<String, String>) : SimpleJavaFileObject(URI(entry.key), JavaFileObject.Kind.SOURCE) {
    override fun isNameCompatible(simpleName: String?, kind: JavaFileObject.Kind?): Boolean = kind != JavaFileObject.Kind.SOURCE || (simpleName != "module-info" && simpleName != "package-info")

    override fun getCharContent(ignoreEncodingErrors: Boolean): CharSequence = entry.value

    override fun toString(): String = entry.key
}

class Results : DiagnosticListener<JavaFileObject> {
    val diagnostics = mutableListOf<Diagnostic<out JavaFileObject>>()
    override fun report(diagnostic: Diagnostic<out JavaFileObject>) {
        diagnostics.add(diagnostic)
    }
}

fun classNameToPathWithClass(className: String): String = className.replace(".", "/") + ".class"

fun classNameToPath(className: String): String = className.replace(".", "/")

fun pathToClassName(path: String): String = path.removeSuffix(".class").replace("/", ".")

fun binaryNameToClassName(binaryClassName: String): String = binaryClassName.replace('/', '.').replace('$', '.')

@Suppress("unused")
class JeedFileManager(
    private val parentFileManager: JavaFileManager,
    val allFiles: MutableMap<String, JavaFileObject> = mutableMapOf(),
) : ForwardingJavaFileManager<JavaFileManager>(parentFileManager) {

    constructor(
        parentFileManager: JavaFileManager,
        generatedClassLoader: GeneratedClassLoader,
    ) :
        this(
            parentFileManager,
            generatedClassLoader.allGeneratedFiles.associate {
                val kind = when (".${File(it.relativePath).extension}") {
                    JavaFileObject.Kind.CLASS.extension -> JavaFileObject.Kind.CLASS
                    else -> JavaFileObject.Kind.OTHER
                }
                it.relativePath to ByteSource(it.relativePath, kind).also { simpleJavaFileObject ->
                    simpleJavaFileObject.openOutputStream().write(it.asByteArray())
                } as JavaFileObject
            }.toMutableMap(),
        )

    constructor(copy: JeedFileManager) : this(copy.parentFileManager, copy.allFiles)

    val classFiles
        get() = allFiles.filter { it.value.kind == JavaFileObject.Kind.CLASS }

    val allClassFiles: Map<String, JavaFileObject>
        get() = classFiles.toMutableMap().also { allClassFiles ->
            if (parentFileManager is JeedFileManager) {
                parentFileManager.allClassFiles.forEach {
                    allClassFiles[it.key] = it.value
                }
            }
        }

    val size: Int
        get() = classFiles.values.filterIsInstance<ByteSource>().sumOf { it.buffer.size() }

    @Transient
    private var cachedContentHashCode: Int? = null

    /**
     * Returns a stable, content-based hashCode for this file manager.
     * Used in cache key computation to differentiate parent file managers.
     * Based on the bytecode content, not memory address.
     */
    fun contentHashCode(): Int {
        if (cachedContentHashCode == null) {
            cachedContentHashCode = allClassFiles.toSortedMap().entries.fold(0) { acc, (path, fileObj) ->
                var hash = acc
                hash = 31 * hash + path.hashCode()
                hash = 31 * hash + fileObj.openInputStream().readAllBytes().contentHashCode()
                hash
            }
        }
        return cachedContentHashCode!!
    }

    fun merge(other: JeedFileManager) {
        check(allFiles.keys.intersect(other.allFiles.keys).isEmpty()) {
            "Attempting to merge JeedFileManagers with duplicate keys"
        }
        other.allFiles.forEach { (path, contents) ->
            allFiles[path] = contents
        }
    }

    fun replaceClass(name: String, newBytes: ByteArray) {
        val path = "${name.replace(".", "/")}${JavaFileObject.Kind.CLASS.extension}"
        check(allFiles[path] != null) { "$path does not exist" }
        allFiles[path] = ByteSource(path, JavaFileObject.Kind.CLASS).also {
            it.buffer.writeBytes(newBytes)
        }
    }

    fun addClass(path: String, bytecode: ByteArray) {
        require(path.endsWith(".class")) { "Path must end with .class: $path" }
        allFiles[path] = ByteSource(path, JavaFileObject.Kind.CLASS).also {
            it.buffer.writeBytes(bytecode)
        }
    }

    private class ByteSource(path: String, kind: JavaFileObject.Kind) : SimpleJavaFileObject(URI.create("bytearray:///$path"), kind) {
        init {
            check(kind != JavaFileObject.Kind.CLASS || path.endsWith(".class")) { "incorrect suffix for ByteSource path: $path" }
        }

        val buffer: ByteArrayOutputStream = ByteArrayOutputStream()
        override fun openInputStream(): InputStream = ByteArrayInputStream(buffer.toByteArray())
        override fun openOutputStream(): OutputStream = buffer
    }

    val bytecodeForPaths: Map<String, ByteArray>
        get() {
            return classFiles.mapValues {
                it.value.openInputStream().readAllBytes()
            }
        }

    override fun getJavaFileForOutput(
        location: JavaFileManager.Location?,
        className: String,
        kind: JavaFileObject.Kind?,
        sibling: FileObject?,
    ): JavaFileObject {
        val classPath = classNameToPathWithClass(className)
        return when {
            location != StandardLocation.CLASS_OUTPUT -> super.getJavaFileForOutput(location, className, kind, sibling)

            kind != JavaFileObject.Kind.CLASS -> throw UnsupportedOperationException()

            else -> {
                ByteSource(classPath, kind).also {
                    allFiles[classPath] = it
                }
            }
        }
    }

    override fun getJavaFileForInput(
        location: JavaFileManager.Location?,
        className: String,
        kind: JavaFileObject.Kind,
    ): JavaFileObject? = when {
        location != StandardLocation.CLASS_OUTPUT -> super.getJavaFileForInput(location, className, kind)

        kind != JavaFileObject.Kind.CLASS -> throw UnsupportedOperationException()

        else -> {
            allFiles[classNameToPathWithClass(className)]
        }
    }

    override fun list(
        location: JavaFileManager.Location?,
        packageName: String,
        kinds: MutableSet<JavaFileObject.Kind>,
        recurse: Boolean,
    ): MutableIterable<JavaFileObject> {
        val parentList = synchronized(standardFileManagerSyncRoot) {
            super.list(location, packageName, kinds, recurse)
        }
        return if (!kinds.contains(JavaFileObject.Kind.CLASS)) {
            parentList
        } else {
            val correctPackageName = if (packageName.isNotEmpty()) {
                packageName.replace(".", "/") + "/"
            } else {
                packageName
            }
            val myList = classFiles.filter { (name, _) ->
                if (!name.startsWith(correctPackageName)) {
                    false
                } else {
                    val nameSuffix = name.removePrefix(correctPackageName)
                    recurse || nameSuffix.split("/").size == 1
                }
            }.values
            parentList.plus(myList).toMutableList()
        }
    }

    override fun inferBinaryName(location: JavaFileManager.Location?, file: JavaFileObject): String = if (file is ByteSource) {
        file.name.substring(0, file.name.lastIndexOf('.')).replace('/', '.')
    } else {
        super.inferBinaryName(location, file)
    }

    @Suppress("SpellCheckingInspection")
    override fun handleOption(current: String?, remaining: MutableIterator<String>?): Boolean = if (parentFileManager === standardFileManager && current == "--multi-release") {
        val operand = remaining?.next() ?: error("MULTIRELEASE should have an operand")
        synchronized(standardFileManagerSyncRoot) {
            if (operand != lastMultireleaseOperand) {
                require(lastMultireleaseOperand == null) { "MULTIRELEASE should not have changed" }
                lastMultireleaseOperand = operand
                super.handleOption(current, listOf(operand).iterator())
            } else {
                // Prevent JavacFileManager from clearing its caches, which would break concurrent tasks
                true
            }
        }
    } else {
        super.handleOption(current, remaining)
    }

    override fun flush() {
        if (parentFileManager !== standardFileManager) {
            super.flush()
        }
    }
}

class JeedClassLoader(private val fileManager: JeedFileManager, parentClassLoader: ClassLoader?) :
    ClassLoader(parentClassLoader),
    Sandbox.SandboxableClassLoader,
    Sandbox.EnumerableClassLoader {

    override val bytecodeForClasses = fileManager.bytecodeForPaths.mapKeys { pathToClassName(it.key) }.toMap()
    override val classLoader: ClassLoader = this

    fun bytecodeForClass(name: String): ByteArray {
        require(bytecodeForClasses.containsKey(name)) { "class loader does not contain class $name" }
        return bytecodeForClasses[name] ?: error("")
    }

    override val definedClasses: Set<String> get() = bytecodeForClasses.keys.toSet()
    override var providedClasses: MutableSet<String> = mutableSetOf()
    override var loadedClasses: MutableSet<String> = mutableSetOf()

    override fun findClass(name: String): Class<*> {
        @Suppress("TooGenericExceptionCaught", "SwallowedException")
        try {
            val classFile = fileManager.getJavaFileForInput(
                StandardLocation.CLASS_OUTPUT,
                name,
                JavaFileObject.Kind.CLASS,
            ) ?: throw ClassNotFoundException()
            val byteArray = classFile.openInputStream().readAllBytes()
            loadedClasses += name
            providedClasses += name
            return defineClass(name, byteArray, 0, byteArray.size)
        } catch (_: Exception) {
            throw ClassNotFoundException(name)
        }
    }

    override fun loadClass(name: String): Class<*> {
        val klass = super.loadClass(name)
        loadedClasses += name
        return klass
    }

    val sizeInBytes = bytecodeForClasses.values.sumOf { it.size }
}

@Suppress("Unused")
class IsolatingClassLoader(private val klasses: Set<String>) : ClassLoader() {
    override fun loadClass(name: String?): Class<*> {
        if (klasses.contains(name)) {
            throw ClassNotFoundException()
        } else {
            return super.loadClass(name)
        }
    }

    override fun loadClass(name: String?, resolve: Boolean): Class<*> {
        if (klasses.contains(name)) {
            throw ClassNotFoundException()
        } else {
            return super.loadClass(name, resolve)
        }
    }
}

fun getEmptyJavaClassSize() = Source(mapOf("Test.java" to "class Test {}")).compile().classLoader.sizeInBytes
