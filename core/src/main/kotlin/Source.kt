package edu.illinois.cs.cs125.jeed.core

import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import mu.KotlinLogging
import org.antlr.v4.runtime.CharStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.TokenStream
import org.antlr.v4.runtime.tree.ParseTree
import java.io.PrintWriter
import java.io.StringWriter
import java.lang.reflect.Method
import java.security.MessageDigest
import java.time.Instant
import java.util.Locale

@Suppress("UNUSED")
val logger = KotlinLogging.logger {}

data class Sources(val sources: Map<String, String>) : Map<String, String> by sources {
    val contents: String
        get() = sources.values.let {
            check(it.size == 1) { "Can only retrieve contents for sources with single file" }
            it.first()
        }
    val name: String
        get() = sources.keys.let {
            check(it.size == 1) { "Can only retrieve name for sources with single file" }
            it.first()
        }
}

open class Source(
    sourceMap: Map<String, String>,
    checkSourceNames: (Sources) -> SourceType = ::defaultCheckSourceNames,
    @Transient val sourceMappingFunction: (SourceLocation) -> SourceLocation = { it },
    @Transient val leadingIndentationFunction: (SourceLocation) -> Int = { 0 },
) {
    val sources = Sources(sourceMap.mapValues { (_, value) -> value.replace("""\r\n?""".toRegex(), "\n") })

    val contents: String
        get() = sources.contents

    val name: String
        get() = sources.name

    val javaSource: Source
        get() {
            check(type == SourceType.MIXED) { "Need mixed sources to extract Java sources" }
            return Source(sources.filter { (path) -> sourceFilenameToFileType(path) == FileType.JAVA })
        }
    val kotlinSource: Source
        get() {
            check(type == SourceType.MIXED) { "Need mixed sources to extract Kotlin sources" }
            return Source(sources.filter { (path) -> sourceFilenameToFileType(path) == FileType.KOTLIN })
        }

    operator fun get(filename: String) = sources[filename]
    override fun toString() = if (sources.keys.size == 1 && name == "") {
        contents
    } else {
        sources.toString()
    }

    enum class FileType(val type: String) {
        JAVA("Java"),
        KOTLIN("Kotlin"),
    }

    enum class SourceType(val type: String) {
        JAVA("Java"),
        KOTLIN("Kotlin"),
        MIXED("Mixed"),
    }

    val type: SourceType

    init {
        require(sources.keys.isNotEmpty())
        type = checkSourceNames(sources)
    }

    fun mapLocation(input: SourceLocation): SourceLocation = sourceMappingFunction(input)

    fun leadingIndentation(input: SourceLocation): Int = leadingIndentationFunction(input)

    fun mapLocation(source: String, input: Location): Location {
        val resultSourceLocation = sourceMappingFunction(SourceLocation(source, input.line, input.column))
        return Location(resultSourceLocation.line, resultSourceLocation.column)
    }

    data class ParsedSource(
        val tree: ParseTree,
        val stream: CharStream,
        val contents: String,
        val parser: Parser,
        val tokenStream: TokenStream,
    )

    var parsed = false

    @Suppress("MemberVisibilityCanBePrivate")
    var parseInterval: Interval? = null
    private val parsedSources: Map<String, ParsedSource> by lazy {
        parsed = true
        val parseStart = Instant.now()
        sources.mapValues { entry ->
            val (filename, _) = entry
            when (sourceFilenameToFileType(filename)) {
                FileType.JAVA -> parseJavaFile(entry)
                FileType.KOTLIN -> parseKotlinFile(entry)
            }
        }.also {
            parseInterval = Interval(parseStart, Instant.now())
        }
    }

    fun parse() {
        parsedSources
    }

    fun getParsed(filename: String): ParsedSource = parsedSources[filename] ?: error("$filename not in sources")

    fun sourceFilenameToFileType(filename: String): FileType {
        if (this is Snippet) {
            check(filename.isEmpty()) { "Snippets should not have a filename" }
            return type.toFileType()
        }
        return filenameToFileType(filename)
    }

    val md5: String by lazy {
        MessageDigest.getInstance("MD5")?.let { digest ->
            sources.toSortedMap().forEach { (path, contents) ->
                digest.update(path.toByteArray())
                digest.update(contents.toByteArray())
            }
            digest.digest()
        }?.joinToString(separator = "") {
            String.format(Locale.US, "%02x", it)
        } ?: error("Problem computing hash")
    }

    override fun equals(other: Any?): Boolean = when {
        this === other -> true
        javaClass != other?.javaClass -> false
        else -> md5 == (other as Source).md5
    }

    override fun hashCode(): Int = md5.toBigInteger(radix = 16).toInt()

    companion object {
        private fun filenameToFileType(filename: String): FileType = when (val extension = filename.split("/").last().split(".").last()) {
            "java" -> FileType.JAVA
            "kt" -> FileType.KOTLIN
            else -> error("invalid extension: $extension")
        }

        fun filenamesToFileTypes(filenames: Set<String>): List<FileType> = filenames.map { filename ->
            filenameToFileType(filename)
        }.distinct()

        private fun defaultCheckSourceNames(sources: Sources): SourceType {
            sources.keys.forEach { name ->
                require(name.isNotBlank()) { "filename cannot be blank" }
            }
            val fileTypes = filenamesToFileTypes(sources.keys)
            require(fileTypes.size == 1) {
                return SourceType.MIXED
            }
            if (fileTypes.contains(FileType.JAVA)) {
                sources.keys.filter { filenameToFileType(it) == FileType.JAVA }.forEach { name ->
                    require(name.split("/").last()[0].isUpperCase()) {
                        "Java filenames must begin with an uppercase character"
                    }
                }
            }
            return fileTypes.first().toSourceType()
        }

        fun fromJava(contents: String) = Source(mapOf("Main.java" to contents.trimStart()))

        @Suppress("unused")
        fun fromKotlin(contents: String) = Source(mapOf("Main.kt" to contents.trimStart()))
    }
}

@Serializable
data class SourceLocation(
    val source: String,
    val line: Int,
    val column: Int,
) {
    override fun toString(): String = if (source != SNIPPET_SOURCE) {
        "$source ($line:$column)"
    } else {
        "($line:$column)"
    }
}

@Suppress("unused")
@Serializable
data class Location(val line: Int, val column: Int) {
    fun asSourceLocation(source: String) = SourceLocation(source, line, column)
}

@Serializable
data class SourceRange(
    val source: String?,
    val start: Location,
    val end: Location,
)

open class LocatedClassOrMethod(
    val name: String,
    @Suppress("unused") val range: SourceRange?,
    val classes: MutableMap<String, LocatedClassOrMethod> = mutableMapOf(),
    val methods: MutableMap<String, LocatedClassOrMethod> = mutableMapOf(),
)

@Serializable
open class SourceError(
    @Contextual open val location: SourceLocation?,
    val message: String,
) {
    override fun toString(): String = if (location == null) message else "$location: $message"
}

open class AlwaysLocatedSourceError(
    final override val location: SourceLocation,
    message: String,
) : SourceError(location, message)

abstract class JeedError(open val errors: List<SourceError>) : Exception() {
    override fun toString(): String = javaClass.name + ":\n" + errors.joinToString(separator = "\n")
}

abstract class AlwaysLocatedJeedError(final override val errors: List<AlwaysLocatedSourceError>) : JeedError(errors)

@Serializable
data class Interval(@Contextual val start: Instant, @Contextual val end: Instant, val length: Long = end.toEpochMilli() - start.toEpochMilli())

fun Throwable.getStackTraceAsString(): String {
    val stringWriter = StringWriter()
    val printWriter = PrintWriter(stringWriter)
    this.printStackTrace(printWriter)
    return stringWriter.toString()
}

val stackTraceLineRegex = Regex("""^at ([\w$]+)\.(\w+)\(([\w.]*):(\d+)\)$""")

@Suppress("unused", "ComplexMethod")
fun Throwable.getStackTraceForSource(
    source: Source,
    boundaries: List<String> = listOf(
        """at java.base/jdk.internal""",
        """at MainKt.main()""",
    ),
): String {
    val originalStackTrace = this.getStackTraceAsString().lines().toMutableList()
    val firstLine = originalStackTrace.removeAt(0)

    val betterStackTrace = mutableListOf(firstLine)
    @Suppress("LoopWithTooManyJumpStatements")
    for (line in originalStackTrace) {
        val l = line.trim()
        if (boundaries.any { l.startsWith(it) }) {
            break
        }
        if (!(source is Snippet || source is TemplatedSource)) {
            betterStackTrace.add("  $l")
            continue
        }
        val parsedLine = stackTraceLineRegex.find(l)
        if (parsedLine == null) {
            betterStackTrace.add("  $l")
            continue
        }
        val (klass, method, name, correctLine) = parsedLine.destructured
        val fixedKlass = if (source is Snippet &&
            (klass == source.wrappedClassName || klass == $$"$${source.wrappedClassName}$Companion")
        ) {
            ""
        } else {
            "$klass."
        }
        val fixedMethod = if (source is Snippet && method == source.looseCodeMethodName) {
            ""
        } else {
            method
        }
        val correctLocation = source.mapLocation(SourceLocation(name, correctLine.toInt(), 0))
        betterStackTrace.add("  at $fixedKlass$fixedMethod(:${correctLocation.line})")
    }
    return betterStackTrace.joinToString(separator = "\n")
}

fun Method.getQualifiedName(): String = "$name(${parameters.joinToString(separator = ", ")})"

class SourceMappingException(message: String) : Exception(message)

fun Source.SourceType.extension() = when {
    this === Source.SourceType.KOTLIN -> ".kt"
    this === Source.SourceType.JAVA -> ".java"
    else -> error("Unknown filetype: $this")
}

internal fun Source.FileType.toSourceType() = when (this) {
    Source.FileType.JAVA -> Source.SourceType.JAVA
    Source.FileType.KOTLIN -> Source.SourceType.KOTLIN
}

fun Source.SourceType.toFileType() = when (this) {
    Source.SourceType.JAVA -> Source.FileType.JAVA
    Source.SourceType.KOTLIN -> Source.FileType.KOTLIN
    else -> error("Can't convert MIXED to file type")
}
