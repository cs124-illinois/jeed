package edu.illinois.cs.cs125.jeed.core

import com.squareup.moshi.JsonClass
import edu.illinois.cs.cs125.jeed.core.antlr.JavaLexer
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinLexer
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParserBaseListener
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParserBaseVisitor
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetLexer
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetParser
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetParserBaseVisitor
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.tree.ParseTreeWalker
import org.jetbrains.kotlin.backend.common.pop

const val SNIPPET_SOURCE = ""

@JsonClass(generateAdapter = true)
data class SnippetProperties(
    val importCount: Int,
    val looseCount: Int,
    val methodCount: Int,
    val classCount: Int,
)

@Suppress("LongParameterList")
class Snippet(
    sources: Sources,
    val originalSource: String,
    val rewrittenSource: String,
    val snippetRange: SourceRange,
    val wrappedClassName: String,
    val looseCodeMethodName: String,
    val fileType: FileType,
    val snippetProperties: SnippetProperties,
    @Transient private val remappedLineMapping: Map<Int, RemappedLine> = mapOf(),
    @Transient val entryClassName: String = wrappedClassName,
) : Source(
    sources,
    {
        require(sources.keys.size == 1) { "snippets should only provide a single source file" }
        require(sources.keys.first() == "") { "snippets should use a blank string as their filename" }
        fileType.toSourceType()
    },
    { mapLocation(it, remappedLineMapping) },
    { leadingIndentation(it, remappedLineMapping) },
) {
    data class RemappedLine(
        val sourceLineNumber: Int,
        val rewrittenLineNumber: Int,
        val addedIndentation: Int = 0,
        val leadingIndentation: Int = addedIndentation,
    )

    fun originalSourceFromMap(): String {
        val lines = rewrittenSource.lines()
        return remappedLineMapping.values.sortedBy { it.sourceLineNumber }.joinToString(separator = "\n") {
            val currentLine = lines[it.rewrittenLineNumber - 1]
            assert(it.addedIndentation <= currentLine.length) { "${it.addedIndentation} v. ${currentLine.length}" }
            currentLine.substring(it.addedIndentation)
        }
    }

    companion object {
        fun mapLocation(input: SourceLocation, remappedLineMapping: Map<Int, RemappedLine>): SourceLocation {
            if (input.source != SNIPPET_SOURCE) {
                return input
            }
            val remappedLineInfo = remappedLineMapping[input.line]
                ?: throw SourceMappingException(
                    "can't remap line ${input.line}: ${
                        remappedLineMapping.values.joinToString(
                            separator = ",",
                        )
                    }",
                )
            return SourceLocation(
                SNIPPET_SOURCE,
                remappedLineInfo.sourceLineNumber,
                input.column - remappedLineInfo.addedIndentation,
            )
        }

        fun leadingIndentation(input: SourceLocation, remappedLineMapping: Map<Int, RemappedLine>): Int {
            if (input.source != SNIPPET_SOURCE) {
                return 0
            }
            val remappedLineInfo = remappedLineMapping[input.line]
                ?: throw SourceMappingException(
                    "can't remap line ${input.line}: ${
                        remappedLineMapping.values.joinToString(
                            separator = ",",
                        )
                    }",
                )
            return remappedLineInfo.leadingIndentation
        }
    }
}

class SnippetTransformationError(
    line: Int,
    column: Int,
    message: String,
) : AlwaysLocatedSourceError(SourceLocation(SNIPPET_SOURCE, line, column), message) {
    constructor(location: SourceLocation, message: String) : this(location.line, location.column, message)
}

class SnippetTransformationFailed(errors: List<SnippetTransformationError>) : AlwaysLocatedJeedError(errors)

class SnippetErrorListener(
    private val sourceLines: List<Int>,
    private val decrement: Boolean = true,
) : BaseErrorListener() {
    private val errors = mutableListOf<SnippetTransformationError>()
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        // Decrement line number by 1 to account for added braces
        var actualLine = if (decrement) {
            line - 1
        } else {
            line
        }
        var actualCharPositionInLine = charPositionInLine
        var actualMsg = msg

        // HACK to repair broken error message at end of input
        if (actualLine - 1 == sourceLines.size && actualCharPositionInLine == 0 && msg == "missing ';' at '}'") {
            actualLine -= 1
            actualCharPositionInLine = sourceLines[actualLine - 1] + 1
            actualMsg = "missing ';'"
        } else if (msg.contains("extraneous input '<EOF>'")) {
            actualLine -= 1
            actualMsg = """reached end of file while parsing"""
        } else if (msg.contains("extraneous input") || msg.contains("mismatched input")) {
            actualMsg = """ expecting.*$""".toRegex().replace(msg, "")
        }

        errors.add(SnippetTransformationError(actualLine, actualCharPositionInLine, actualMsg))
    }

    fun check() {
        if (errors.size > 0) {
            val filteredErrors = errors.filter { it.location.line in 1..sourceLines.size }
            if (filteredErrors.isNotEmpty()) {
                throw SnippetTransformationFailed(filteredErrors)
            } else {
                throw SnippetTransformationFailed(errors)
            }
        }
    }
}

@JsonClass(generateAdapter = true)
data class SnippetArguments(
    val indent: Int = DEFAULT_SNIPPET_INDENT,
    var fileType: Source.FileType = Source.FileType.JAVA,
    val noEmptyMain: Boolean = false,
) {
    companion object {
        const val DEFAULT_SNIPPET_INDENT = 2
    }
}

@Suppress("LongMethod", "ComplexMethod")
@Throws(SnippetTransformationFailed::class)
fun Source.Companion.fromSnippet(
    originalSource: String,
    snippetArguments: SnippetArguments = SnippetArguments(),
    trim: Boolean = true,
): Snippet {
    val actualSource = if (trim) {
        originalSource.trimStart()
    } else {
        originalSource
    }
    require(actualSource.isNotEmpty()) { "Snippet cannot be a blank string" }
    return when (snippetArguments.fileType) {
        Source.FileType.JAVA -> sourceFromJavaSnippet(actualSource, snippetArguments)
        Source.FileType.KOTLIN -> sourceFromKotlinSnippet(actualSource, snippetArguments)
    }
}

fun Source.Companion.fromJavaSnippet(
    originalSource: String,
    snippetArguments: SnippetArguments = SnippetArguments(),
    trim: Boolean = true,
): Snippet {
    val actualSource = if (trim) {
        originalSource.trimStart()
    } else {
        originalSource
    }
    require(actualSource.isNotEmpty()) { "Snippet cannot be a blank string" }
    return sourceFromJavaSnippet(actualSource, snippetArguments.copy(fileType = Source.FileType.JAVA))
}

fun Source.Companion.fromKotlinSnippet(
    originalSource: String,
    snippetArguments: SnippetArguments = SnippetArguments(),
    trim: Boolean = true,
): Snippet {
    val actualSource = if (trim) {
        originalSource.trimStart()
    } else {
        originalSource
    }
    require(actualSource.isNotEmpty()) { "Snippet cannot be a blank string" }
    return sourceFromKotlinSnippet(actualSource, snippetArguments.copy(fileType = Source.FileType.KOTLIN))
}

@Suppress("LongMethod", "ComplexMethod", "ThrowsCount")
private fun sourceFromKotlinSnippet(originalSource: String, snippetArguments: SnippetArguments): Snippet {
    val sourceLines = originalSource.lines()
    val errorListener = SnippetErrorListener(sourceLines.map { it.trim().length }, false)

    val parseTree = KotlinLexer(CharStreams.fromString(originalSource + "\n")).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }.let {
        val parser = KotlinParser(it).apply { makeThreadSafe() }
        parser.trimParseTree = true
        parser
    }.let { parser ->
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        parser.script()
    }.also {
        errorListener.check()
    }

    val snippetRange = SourceRange(
        SNIPPET_SOURCE,
        Location(parseTree.start.line, 0),
        Location(parseTree.stop.line - 1, 0),
    )

    val multilineLines = mutableSetOf<Int>()
    object : KotlinParserBaseVisitor<Unit>() {
        override fun visitMultiLineStringLiteral(context: KotlinParser.MultiLineStringLiteralContext) {
            ((context.start.line + 1)..context.stop.line).forEach {
                multilineLines.add(it)
            }
        }
    }.also { it.visit(parseTree) }

    val rewrittenSourceLines: MutableList<String> = mutableListOf()
    var currentOutputLineNumber = 1
    val remappedLineMapping = hashMapOf<Int, Snippet.RemappedLine>()

    parseTree.packageHeader().let {
        if (it.identifier() != null) {
            throw SnippetTransformationFailed(
                listOf(
                    SnippetTransformationError(
                        SourceLocation(SNIPPET_SOURCE, it.start.line, it.start.charPositionInLine),
                        "package declarations not allowed in Kotlin snippets",
                    ),
                ),
            )
        }
    }
    parseTree.fileAnnotation().let {
        if (it.isNotEmpty()) {
            throw SnippetTransformationFailed(
                listOf(
                    SnippetTransformationError(
                        SourceLocation(SNIPPET_SOURCE, it.first().start.line, it.first().start.charPositionInLine),
                        "file annotations not allowed in Kotlin snippets",
                    ),
                ),
            )
        }
    }

    val importCount = parseTree.importList()?.importHeader()?.size ?: 0

    val preambleStart = parseTree.importList().importHeader().firstOrNull()?.start?.line ?: 0
    val preambleStop = parseTree.importList().importHeader()?.lastOrNull()?.stop?.line?.inc() ?: 0
    for (lineNumber in preambleStart until preambleStop) {
        rewrittenSourceLines.add(sourceLines[lineNumber - 1].trimEnd())
        remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
        currentOutputLineNumber++
    }

    """class MainKt {
${" ".repeat(snippetArguments.indent)}companion object {
${" ".repeat(snippetArguments.indent * 2)}@JvmStatic fun main() {""".lines().let {
        rewrittenSourceLines.addAll(it)
        currentOutputLineNumber += it.size
    }

    var sawMainMethod = false
    var mainMethodLine = -1
    val methodLines = mutableSetOf<IntRange>()
    val klassLines = mutableSetOf<IntRange>()

    var methodCount = 0
    var classCount = 0

    parseTree.statement().mapNotNull { it.declaration()?.functionDeclaration() }.forEach {
        if (it.simpleIdentifier().text == "main" && it.functionValueParameters().functionValueParameter().isEmpty()) {
            sawMainMethod = true
            mainMethodLine = it.start.line
        }
        methodCount++
        methodLines.add(it.start.line..it.stop.line)
    }
    parseTree.statement().mapNotNull { it.declaration()?.classDeclaration() }.forEach {
        classCount++
        klassLines.add(it.start.line..it.stop.line)
    }
    parseTree.emptyClassDeclaration().forEach {
        classCount++
        klassLines.add(it.start.line..it.stop.line)
    }
    parseTree.statement().mapNotNull { it.declaration()?.emptyClassDeclaration() }.forEach {
        classCount++
        klassLines.add(it.start.line..it.stop.line)
    }
    parseTree.statement().mapNotNull { it.declaration()?.interfaceDeclaration() }.forEach {
        classCount++
        klassLines.add(it.start.line..it.stop.line)
    }
    parseTree.statement().mapNotNull { it.declaration()?.objectDeclaration() }.forEach {
        classCount++
        klassLines.add(it.start.line..it.stop.line)
    }

    var sawMainLines = false
    val topLevelStart = parseTree.statement()?.firstOrNull()?.start?.line ?: 0
    val topLevelEnd = parseTree.statement()?.lastOrNull()?.stop?.line?.inc() ?: 0
    @Suppress("MagicNumber")
    for (lineNumber in topLevelStart until topLevelEnd) {
        if (methodLines.any { it.contains(lineNumber) } || klassLines.any { it.contains(lineNumber) }) {
            continue
        }
        sawMainLines = true
        val indentAmount = if (lineNumber in multilineLines) {
            0
        } else {
            snippetArguments.indent * 3
        }
        rewrittenSourceLines.add(" ".repeat(indentAmount) + sourceLines[lineNumber - 1].trimEnd())
        remappedLineMapping[currentOutputLineNumber] =
            Snippet.RemappedLine(lineNumber, currentOutputLineNumber, indentAmount)
        currentOutputLineNumber++
    }

    if (sawMainMethod && !sawMainLines) {
        rewrittenSourceLines.pop()
        currentOutputLineNumber--
    } else if (sawMainLines || !snippetArguments.noEmptyMain) {
        rewrittenSourceLines.add("""${" ".repeat(snippetArguments.indent * 2)}}""")
        currentOutputLineNumber++
    } else {
        rewrittenSourceLines.pop()
        currentOutputLineNumber--
    }

    @Suppress("MagicNumber")
    for (methodRange in methodLines) {
        for (lineNumber in methodRange) {
            val indentAmount = if (lineNumber in multilineLines) {
                0
            } else {
                snippetArguments.indent * 2
            }
            val pushAmount = if (lineNumber == mainMethodLine) {
                indentAmount + "@JvmStatic ".length
            } else {
                indentAmount
            }
            val line = " ".repeat(indentAmount) + if (lineNumber == mainMethodLine) {
                "@JvmStatic "
            } else {
                ""
            } + sourceLines[lineNumber - 1].trimEnd()
            rewrittenSourceLines.add(line)
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(lineNumber, currentOutputLineNumber, pushAmount, indentAmount)
            currentOutputLineNumber++
        }
    }

    rewrittenSourceLines.addAll(
        """${" ".repeat(snippetArguments.indent)}}
}""".lines(),
    )
    currentOutputLineNumber += 2

    for (klassRange in klassLines) {
        for (lineNumber in klassRange) {
            rewrittenSourceLines.add(sourceLines[lineNumber - 1].trimEnd())
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
            currentOutputLineNumber++
        }
    }

    val looseLines = mutableListOf<String>()
    val looseCodeMapping = mutableMapOf<Int, Int>()

    parseTree.statement()
        ?.filter {
            it.declaration()?.functionDeclaration() == null &&
                it.declaration()?.classDeclaration() == null &&
                it.declaration()?.emptyClassDeclaration() == null
        }
        ?.forEach {
            val looseStart = it.start!!.line
            val looseEnd = it.stop!!.line
            for (lineNumber in looseStart..looseEnd) {
                looseCodeMapping[looseLines.size + 1] = lineNumber
                looseLines.add(sourceLines[lineNumber - 1])
            }
        }

    val anonymousObjectLines = object : KotlinParserBaseListener() {
        val anonymousObjectLines = mutableSetOf<Int>()
        override fun enterObjectDeclaration(ctx: KotlinParser.ObjectDeclarationContext) {
            val objectStart = ctx.start!!.line
            val objectEnd = ctx.stop!!.line
            for (lineNumber in objectStart..objectEnd) {
                anonymousObjectLines += lineNumber
            }
        }

        override fun enterObjectLiteral(ctx: KotlinParser.ObjectLiteralContext) {
            val objectStart = ctx.start!!.line
            val objectEnd = ctx.stop!!.line
            for (lineNumber in objectStart..objectEnd) {
                anonymousObjectLines += lineNumber
            }
        }

        init {
            ParseTreeWalker.DEFAULT.walk(this, parseTree)
        }
    }.anonymousObjectLines

    KotlinLexer(CharStreams.fromString(looseLines.joinToString(separator = "\n"))).let {
        it.removeErrorListeners()
        CommonTokenStream(it)
    }.also {
        it.fill()
    }.tokens.filter {
        it.type == KotlinLexer.RETURN && looseCodeMapping[it.line] !in anonymousObjectLines
    }.map {
        SnippetTransformationError(
            SourceLocation(
                SNIPPET_SOURCE,
                looseCodeMapping[it.line] ?: error("Missing loose code mapping"),
                it.charPositionInLine,
            ),
            "return statements not allowed at top level in snippets",
        )
    }.let {
        if (it.isNotEmpty()) {
            throw SnippetTransformationFailed(it)
        }
    }

    parseTree.statement()?.map { it.declaration()?.classDeclaration() }?.find {
        it?.simpleIdentifier()?.text == "MainKt"
    }?.let {
        throw SnippetTransformationFailed(
            listOf(
                SnippetTransformationError(
                    SourceLocation(
                        SNIPPET_SOURCE,
                        it.start.line,
                        it.start.charPositionInLine,
                    ),
                    "A class named MainKt cannot be declared at the top level in a snippet",
                ),
            ),
        )
    }

    val rewrittenSource = rewrittenSourceLines.joinToString(separator = "\n") {
        when {
            it.isBlank() -> ""
            else -> it
        }
    }

    val looseCount = looseLines.joinToString("\n").countLines(Source.FileType.KOTLIN).source

    return Snippet(
        Sources(hashMapOf(SNIPPET_SOURCE to rewrittenSource)),
        originalSource,
        rewrittenSource,
        snippetRange,
        "MainKt",
        "main()",
        Source.FileType.KOTLIN,
        SnippetProperties(importCount, looseCount, methodCount, classCount),
        remappedLineMapping,
        "MainKt",
    )
}

private val javaVisibilityPattern = """^\s*(public|private|protected)""".toRegex()

@Suppress("LongMethod", "ComplexMethod")
private fun sourceFromJavaSnippet(originalSource: String, snippetArguments: SnippetArguments): Snippet {
    val sourceLines = originalSource.lines().map { it.trim().length }
    val errorListener = SnippetErrorListener(sourceLines)

    val parseTree = SnippetLexer(CharStreams.fromString("{\n$originalSource\n}")).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }.let {
        val parser = SnippetParser(it).apply { makeThreadSafe() }
        parser.trimParseTree = true
        parser
    }.let { parser ->
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        try {
            parser.snippet()
        } catch (e: StackOverflowError) {
            throw SnippetTransformationFailed(
                listOf(
                    SnippetTransformationError(
                        0,
                        0,
                        "Stack overflow caused by overly-complicated code",
                    ),
                ),
            )
        }
    }.also {
        errorListener.check()
    }

    lateinit var snippetRange: SourceRange
    val contentMapping = mutableMapOf<Int, String>()
    val classNames = mutableSetOf<String>()
    val methodNames = mutableSetOf<String>()

    var methodCount = 0
    var classCount = 0

    val visitorResults = object : SnippetParserBaseVisitor<Unit>() {
        val errors = mutableListOf<SnippetTransformationError>()
        var sawExampleMain = false
        var sawNonImport = false
        var statementDepth = 0

        fun markAs(start: Int, stop: Int, type: String) {
            check(statementDepth == 0) { "Shouldn't mark inside statement" }

            if (type != "import") {
                sawNonImport = true
            } else if (sawNonImport) {
                errors.add(
                    SnippetTransformationError(
                        start - 1,
                        0,
                        "import statements must be at the top of the snippet",
                    ),
                )
            }

            for (lineNumber in start - 1 until stop) {
                check(!contentMapping.containsKey(lineNumber)) { "line $lineNumber already marked" }
                contentMapping[lineNumber] = type
            }
        }

        override fun visitPackageDeclaration(context: SnippetParser.PackageDeclarationContext) {
            errors.add(
                SnippetTransformationError(
                    context.start.line - 1,
                    context.start.charPositionInLine,
                    "Snippets may not contain package declarations",
                ),
            )
        }

        // Always part of loose code
        override fun visitLocalVariableDeclaration(ctx: SnippetParser.LocalVariableDeclarationContext?) {
            return
        }

        override fun visitLambdaExpression(ctx: SnippetParser.LambdaExpressionContext?) {
            return
        }

        // Always part of loose code
        override fun visitStatement(context: SnippetParser.StatementContext) {
            context.RETURN()?.also {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Snippets may not contain top-level return statements",
                    ),
                )
            }
            check(statementDepth >= 0) { "Invalid statement depth" }
            statementDepth++
            super.visitStatement(context)
            statementDepth--
            check(statementDepth >= 0) { "Invalid statement depth" }
        }

        override fun visitClassDeclaration(context: SnippetParser.ClassDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            classCount++
            markAs(parent.start.line, parent.stop.line, "class")
            val className = context.identifier().text
            classNames.add(className)
            if (context.parent is SnippetParser.LocalTypeDeclarationContext &&
                (
                    (context.parent as SnippetParser.LocalTypeDeclarationContext).classOrInterfaceModifier()
                        ?.find { it.text == "public" } != null
                    ) &&
                context.identifier().text == "Example"
            ) {
                context.classBody()?.classBodyDeclaration()
                    ?.filter { it?.memberDeclaration()?.methodDeclaration() != null }
                    ?.any { c ->
                        c.modifier().find { it.text == "public" } != null &&
                            c.modifier().find { it.text == "static" } != null &&
                            c.memberDeclaration().methodDeclaration().identifier().text == "main" &&
                            c.memberDeclaration().methodDeclaration().typeTypeOrVoid().text == "void"
                    }?.also {
                        if (it) {
                            sawExampleMain = true
                        }
                    }
            }
        }

        override fun visitInterfaceDeclaration(context: SnippetParser.InterfaceDeclarationContext) {
            if (statementDepth > 0) {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Interface declarations must be at the top level",
                    ),
                )
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            classCount++
            markAs(parent.start.line, parent.stop.line, "class")
            val className = context.identifier().text
            classNames.add(className)
        }

        override fun visitRecordDeclaration(context: SnippetParser.RecordDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            val parent = context.parent as SnippetParser.LocalTypeDeclarationContext
            classCount++
            markAs(parent.start.line, parent.stop.line, "record")
            val className = context.identifier().text
            classNames.add(className)
        }

        override fun visitMethodDeclaration(context: SnippetParser.MethodDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            markAs(context.start.line, context.stop.line, "method")
            contentMapping[context.start.line - 1] = "method:start"
            methodCount++
            val methodName = context.identifier().text
            methodNames.add(methodName)
        }

        override fun visitGenericMethodDeclaration(context: SnippetParser.GenericMethodDeclarationContext) {
            if (statementDepth > 0) {
                return
            }
            markAs(context.start.line, context.stop.line, "method")
            contentMapping[context.start.line - 1] = "method:start"
            methodCount++
            val methodName = context.methodDeclaration().identifier().text
            methodNames.add(methodName)
        }

        override fun visitImportDeclaration(context: SnippetParser.ImportDeclarationContext) {
            if (statementDepth > 0) {
                errors.add(
                    SnippetTransformationError(
                        context.start.line - 1,
                        context.start.charPositionInLine,
                        "Imports must be at the top level",
                    ),
                )
                return
            }
            markAs(context.start.line, context.stop.line, "import")
        }

        override fun visitSnippet(ctx: SnippetParser.SnippetContext) {
            snippetRange = SourceRange(
                SNIPPET_SOURCE,
                Location(1, 0),
                Location(ctx.stop.line - 2, 0),
            )
            ctx.children.forEach {
                super.visit(it)
            }
        }
    }.also { it.visit(parseTree) }

    if (visitorResults.errors.isNotEmpty()) {
        throw SnippetTransformationFailed(visitorResults.errors)
    }

    val snippetClassName = generateName("Main", classNames)
    val snippetMainMethodName = generateName("main", methodNames)

    var currentOutputLineNumber = 1
    val remappedLineMapping = hashMapOf<Int, Snippet.RemappedLine>()

    val importStatements = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber] == "import") {
            importStatements.add(line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
            currentOutputLineNumber++
        }
    }

    val classDeclarations = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber] == "class" || contentMapping[lineNumber] == "record") {
            classDeclarations.add(line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] = Snippet.RemappedLine(lineNumber, currentOutputLineNumber)
            currentOutputLineNumber++
        }
    }

    // Adding public class $snippetClassName
    currentOutputLineNumber++

    val methodDeclarations = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (contentMapping[lineNumber]?.startsWith(("method")) == true) {
            val (actualLine, extraIndentation) =
                if ((contentMapping[lineNumber] == "method:start") && !line.contains("""\bstatic\b""".toRegex())) {
                    val matchVisibilityModifier = javaVisibilityPattern.find(line)
                    if (matchVisibilityModifier != null) {
                        val rewrittenLine = line.replace(javaVisibilityPattern, "").let {
                            "${matchVisibilityModifier.value} static$it"
                        }
                        Pair(rewrittenLine, "static ".length)
                    } else {
                        Pair("static $line", "static ".length)
                    }
                } else {
                    Pair(line, 0)
                }
            methodDeclarations.add(" ".repeat(snippetArguments.indent) + actualLine)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(
                    lineNumber,
                    currentOutputLineNumber,
                    snippetArguments.indent + extraIndentation,
                    snippetArguments.indent,
                )
            currentOutputLineNumber++
        }
    }

    // Adding public static void $snippetMainMethodName()
    @Suppress("UNUSED_VARIABLE")
    val looseCodeStart = currentOutputLineNumber
    currentOutputLineNumber++

    val looseCode = mutableListOf<String>()
    originalSource.lines().forEachIndexed { i, line ->
        val lineNumber = i + 1
        if (!contentMapping.containsKey(lineNumber)) {
            looseCode.add(" ".repeat(snippetArguments.indent * 2) + line)
            assert(!remappedLineMapping.containsKey(currentOutputLineNumber))
            remappedLineMapping[currentOutputLineNumber] =
                Snippet.RemappedLine(lineNumber, currentOutputLineNumber, snippetArguments.indent * 2)
            currentOutputLineNumber++
        }
    }

    val looseCount = looseCode.joinToString("\n").countLines(Source.FileType.JAVA).source
    val hasLooseCode = methodDeclarations.isNotEmpty() || looseCount > 0
    assert(originalSource.lines().size == remappedLineMapping.keys.size)

    val importCount = contentMapping.values.filter { it == "import" }.size

    var rewrittenSource = ""
    if (importStatements.size > 0) {
        rewrittenSource += importStatements.joinToString(separator = "\n", postfix = "\n")
    }
    if (classDeclarations.size > 0) {
        rewrittenSource += classDeclarations.joinToString(separator = "\n", postfix = "\n")
    }
    rewrittenSource += "public class $snippetClassName {\n"
    if (methodDeclarations.size > 0) {
        rewrittenSource += methodDeclarations.joinToString(separator = "\n", postfix = "\n")
    }

    rewrittenSource += """${
        " "
            .repeat(snippetArguments.indent)
    }public static void $snippetMainMethodName() throws Exception {""" + "\n"
    if (looseCode.size > 0) {
        rewrittenSource += looseCode.joinToString(separator = "\n", postfix = "\n")
    }
    rewrittenSource += """${" ".repeat(snippetArguments.indent)}}""" + "\n}"
    // Add final two braces
    currentOutputLineNumber += 1

    assert(currentOutputLineNumber == rewrittenSource.lines().size)

    val entryClassName = if (!hasLooseCode && visitorResults.sawExampleMain) {
        "Example"
    } else {
        snippetClassName
    }
    return Snippet(
        Sources(hashMapOf(SNIPPET_SOURCE to rewrittenSource)),
        originalSource,
        rewrittenSource,
        snippetRange,
        snippetClassName,
        "$snippetMainMethodName()",
        Source.FileType.JAVA,
        SnippetProperties(importCount, looseCount, methodCount, classCount),
        remappedLineMapping,
        entryClassName,
    )
}

// Used to be used to check for return statements, but that has been moved into the walker
@Suppress("unused")
private fun checkJavaLooseCode(
    looseCode: String,
    looseCodeStart: Int,
    remappedLineMapping: Map<Int, Snippet.RemappedLine>,
) {
    val charStream = CharStreams.fromString(looseCode)
    val javaLexer = JavaLexer(charStream)
    javaLexer.removeErrorListeners()
    val tokenStream = CommonTokenStream(javaLexer)
    tokenStream.fill()

    val validationErrors = tokenStream.tokens.filter {
        it.type == JavaLexer.RETURN
    }.map {
        SnippetTransformationError(
            Snippet.mapLocation(
                SourceLocation(SNIPPET_SOURCE, it.line + looseCodeStart, it.charPositionInLine),
                remappedLineMapping,
            ),
            "return statements not allowed at top level in snippets",
        )
    }
    if (validationErrors.isNotEmpty()) {
        throw SnippetTransformationFailed(validationErrors)
    }
}

private const val MAX_NAME_TRIES = 64
private fun generateName(prefix: String, existingNames: Set<String>): String {
    if (!existingNames.contains(prefix)) {
        return prefix
    } else {
        for (suffix in 1..MAX_NAME_TRIES) {
            val testClassName = "$prefix$suffix"
            if (!existingNames.contains(testClassName)) {
                return testClassName
            }
        }
    }
    throw IllegalStateException("couldn't generate $prefix class name")
}
