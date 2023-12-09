@file:Suppress("unused")

package edu.illinois.cs.cs125.jeed.core

import edu.illinois.cs.cs125.jeed.core.antlr.JavaLexer
import edu.illinois.cs.cs125.jeed.core.antlr.JavaParser
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinLexer
import edu.illinois.cs.cs125.jeed.core.antlr.KotlinParser
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetLexer
import edu.illinois.cs.cs125.jeed.core.antlr.SnippetParser
import org.antlr.v4.runtime.BaseErrorListener
import org.antlr.v4.runtime.CharStreams
import org.antlr.v4.runtime.CommonTokenStream
import org.antlr.v4.runtime.Parser
import org.antlr.v4.runtime.RecognitionException
import org.antlr.v4.runtime.Recognizer
import org.antlr.v4.runtime.atn.ParserATNSimulator
import org.antlr.v4.runtime.atn.PredictionContextCache
import org.antlr.v4.runtime.dfa.DFA
import org.antlr.v4.runtime.misc.Utils
import org.antlr.v4.runtime.tree.Tree
import org.antlr.v4.runtime.tree.Trees
import kotlin.concurrent.getOrSet

class JeedParseError(location: SourceLocation?, message: String) : SourceError(location, message)
class JeedParsingException(errors: List<SourceError>) : JeedError(errors)

private val JEED_PARSER_MAX_CONTEXT_SIZE = System.getenv("JEED_PARSER_MAX_CONTEXT_SIZE")?.toInt() ?: 0
private val JEED_PARSER_MAX_DFA_SIZE = System.getenv("JEED_PARSER_MAX_DFA_SIZE")?.toInt() ?: 0

@Suppress("ArrayInDataClass")
private data class ANTLR4Cache(
    var cache: PredictionContextCache = PredictionContextCache(),
    var javaDFA: Array<DFA>? = null,
    var kotlinDFA: Array<DFA>? = null,
    var snippetDFA: Array<DFA>? = null,
) {
    var javaCount = 0
    var kotlinCount = 0
    var snippetCount = 0
}

private val threadLocalANTLR4Cache = ThreadLocal<ANTLR4Cache>()
fun Parser.makeThreadSafe() {
    val threadCache = threadLocalANTLR4Cache.getOrSet { ANTLR4Cache() }

    val dfa = when (this) {
        is JavaParser -> {
            if (threadCache.javaDFA == null || ++threadCache.javaCount > JEED_PARSER_MAX_DFA_SIZE) {
                threadCache.javaDFA =
                    interpreter.decisionToDFA.mapIndexed { i, _ -> DFA(atn.getDecisionState(i), i) }.toTypedArray()
                threadCache.javaCount = 0
            }
            threadCache.javaDFA
        }

        is KotlinParser -> {
            if (threadCache.kotlinDFA == null || ++threadCache.kotlinCount > JEED_PARSER_MAX_DFA_SIZE) {
                threadCache.kotlinDFA =
                    interpreter.decisionToDFA.mapIndexed { i, _ -> DFA(atn.getDecisionState(i), i) }.toTypedArray()
                threadCache.kotlinCount = 0
            }
            threadCache.kotlinDFA
        }

        is SnippetParser -> {
            if (threadCache.snippetDFA == null || ++threadCache.snippetCount > JEED_PARSER_MAX_DFA_SIZE) {
                threadCache.snippetDFA =
                    interpreter.decisionToDFA.mapIndexed { i, _ -> DFA(atn.getDecisionState(i), i) }.toTypedArray()
                threadCache.snippetCount = 0
            }
            threadCache.snippetDFA
        }

        else -> error("No DFA configured for $this")
    }

    if (threadCache.cache.size() > JEED_PARSER_MAX_CONTEXT_SIZE) {
        threadCache.cache = PredictionContextCache()
    }

    interpreter = ParserATNSimulator(
        this,
        this.atn,
        dfa,
        threadCache.cache,
    )
}

class JeedErrorListener(val source: Source, entry: Map.Entry<String, String>) : BaseErrorListener() {
    private val name = entry.key

    @Suppress("unused")
    private val contents = entry.value

    private val errors = mutableListOf<JeedParseError>()
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        val location = try {
            source.mapLocation(SourceLocation(name, line, charPositionInLine))
        } catch (e: Exception) {
            null
        }
        errors.add(JeedParseError(location, msg))
    }

    fun check() {
        if (errors.size > 0) {
            throw JeedParsingException(errors)
        }
    }
}

fun Source.parseJavaFile(entry: Map.Entry<String, String>): Source.ParsedSource {
    check(sourceFilenameToFileType(entry.key) == Source.FileType.JAVA) { "Must be called on a Java file" }
    val errorListener = JeedErrorListener(this, entry)
    val charStream = CharStreams.fromString(entry.value)
    val tokenStream = JavaLexer(charStream).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }
    val (parseTree, parser) = tokenStream.let {
        val parser = JavaParser(it).apply { makeThreadSafe() }

        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        parser.trimParseTree = true
        try {
            Pair(parser.compilationUnit(), parser)
        } finally {
            // parser.interpreter.clearDFA()
        }
    }.also {
        errorListener.check()
    }

    return Source.ParsedSource(parseTree, charStream, entry.value, parser, tokenStream)
}

fun Source.parseKotlinFile(entry: Map.Entry<String, String>): Source.ParsedSource {
    check(sourceFilenameToFileType(entry.key) == Source.FileType.KOTLIN) { "Must be called on a Kotlin file" }
    val errorListener = JeedErrorListener(this, entry)
    val charStream = CharStreams.fromString(entry.value)
    val tokenStream = KotlinLexer(charStream).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }.also {
        errorListener.check()
    }
    val (parseTree, parser) = tokenStream.let {
        val parser = KotlinParser(it).apply { makeThreadSafe() }

        parser.trimParseTree = true

        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        try {
            Pair(parser.kotlinFile(), parser)
        } catch (e: StackOverflowError) {
            throw JeedParsingException(listOf(SourceError(null, "Code is too complicated to determine complexity")))
        } finally {
            // parser.interpreter.clearDFA()
        }
    }.also {
        errorListener.check()
    }

    return Source.ParsedSource(parseTree, charStream, entry.value, parser, tokenStream)
}

fun Parser.profile() {
    print(String.format("%-" + 35 + "s", "rule"))
    print(String.format("%-" + 15 + "s", "time"))
    print(String.format("%-" + 15 + "s", "invocations"))
    print(String.format("%-" + 15 + "s", "---"))
    println()
    parseInfo.decisionInfo
        .filter { it.timeInPrediction > 0 }
        .sortedBy { it.timeInPrediction }.reversed()
        .forEach { info ->
            val ds = atn.getDecisionState(info.decision)
            val rule: String = ruleNames[ds.ruleIndex]
            print(String.format("%-" + 35 + "s", rule))
            print(String.format("%-" + 15 + "s", info.timeInPrediction))
            print(String.format("%-" + 15 + "s", info.invocations))
            print(String.format("%-" + 15 + "s", info.SLL_TotalLook))
            println()
        }
}

class DistinguishErrorListener : BaseErrorListener() {
    override fun syntaxError(
        recognizer: Recognizer<*, *>?,
        offendingSymbol: Any?,
        line: Int,
        charPositionInLine: Int,
        msg: String,
        e: RecognitionException?,
    ) {
        check(!msg.trim().startsWith("extraneous input"))
        if (e != null) {
            throw (e)
        }
    }
}

fun String.isJavaSource(): Boolean {
    val errorListener = DistinguishErrorListener()
    @Suppress("TooGenericExceptionCaught")
    return try {
        CharStreams.fromString(this).let { charStream ->
            JavaLexer(charStream).let { lexer ->
                lexer.removeErrorListeners()
                lexer.addErrorListener(errorListener)
                CommonTokenStream(lexer)
            }
        }.also { tokenStream ->
            JavaParser(tokenStream).apply { makeThreadSafe() }.let { parser ->
                parser.removeErrorListeners()
                parser.addErrorListener(errorListener)
                parser.compilationUnit()
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun String.isJavaSnippet(): Boolean {
    val errorListener = DistinguishErrorListener()
    @Suppress("TooGenericExceptionCaught")
    return try {
        CharStreams.fromString("{$this}").let { charStream ->
            SnippetLexer(charStream).let { lexer ->
                lexer.removeErrorListeners()
                lexer.addErrorListener(errorListener)
                CommonTokenStream(lexer)
            }
        }.also { tokenStream ->
            SnippetParser(tokenStream).apply { makeThreadSafe() }.let { parser ->
                parser.removeErrorListeners()
                parser.addErrorListener(errorListener)
                parser.block()
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun String.isKotlinSource(): Boolean {
    val errorListener = DistinguishErrorListener()
    @Suppress("TooGenericExceptionCaught")
    return try {
        CharStreams.fromString(this).let { charStream ->
            KotlinLexer(charStream).let { lexer ->
                lexer.removeErrorListeners()
                lexer.addErrorListener(errorListener)
                CommonTokenStream(lexer)
            }
        }.also { tokenStream ->
            KotlinParser(tokenStream).apply { makeThreadSafe() }.let { parser ->
                parser.removeErrorListeners()
                parser.addErrorListener(errorListener)
                parser.kotlinFile()
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun String.isKotlinSnippet(): Boolean {
    val errorListener = DistinguishErrorListener()
    @Suppress("TooGenericExceptionCaught")
    return try {
        CharStreams.fromString(this).let { charStream ->
            KotlinLexer(charStream).let { lexer ->
                lexer.removeErrorListeners()
                lexer.addErrorListener(errorListener)
                CommonTokenStream(lexer)
            }
        }.also { tokenStream ->
            KotlinParser(tokenStream).apply { makeThreadSafe() }.let { parser ->
                parser.removeErrorListeners()
                parser.addErrorListener(errorListener)
                parser.script()
            }
        }
        true
    } catch (e: Exception) {
        false
    }
}

fun String.parseKnippet(): Source.ParsedSource {
    val errorListener = DistinguishErrorListener()
    val charStream = CharStreams.fromString(this)
    val tokenStream = KotlinLexer(charStream).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }
    val (parseTree, parser) = tokenStream.let {
        val parser = KotlinParser(it)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        Pair(parser.script(), parser)
    }
    return Source.ParsedSource(parseTree, charStream, this, parser, tokenStream)
}

fun String.parseSnippet(): Source.ParsedSource {
    val errorListener = DistinguishErrorListener()
    val charStream = CharStreams.fromString(this)
    val tokenStream = SnippetLexer(charStream).let {
        it.removeErrorListeners()
        it.addErrorListener(errorListener)
        CommonTokenStream(it)
    }
    val (parseTree, parser) = tokenStream.let {
        val parser = SnippetParser(it)
        parser.removeErrorListeners()
        parser.addErrorListener(errorListener)
        Pair(parser.block(), parser)
    }
    return Source.ParsedSource(parseTree, charStream, this, parser, tokenStream)
}

enum class SourceType {
    JAVA_SOURCE,
    JAVA_SNIPPET,
    KOTLIN_SOURCE,
    KOTLIN_SNIPPET,
}

fun String.distinguish(language: String) = when {
    language == "java" && isJavaSource() -> SourceType.JAVA_SOURCE
    language == "java" && isJavaSnippet() -> SourceType.JAVA_SNIPPET
    language == "kotlin" && isKotlinSource() -> SourceType.KOTLIN_SOURCE
    language == "kotlin" && isKotlinSnippet() -> SourceType.KOTLIN_SNIPPET
    else -> null
}

fun Tree.toPrettyTree(ruleNames: List<String>): String {
    var level = 0
    fun lead(level: Int): String {
        val sb = StringBuilder()
        if (level > 0) {
            sb.append(System.lineSeparator())
            repeat(level) {
                sb.append("  ")
            }
        }
        return sb.toString()
    }

    fun process(t: Tree, ruleNames: List<String>): String {
        if (t.childCount == 0) {
            return Utils.escapeWhitespace(Trees.getNodeText(t, ruleNames), false)
        }
        val sb = StringBuilder()
        sb.append(lead(level))
        level++
        val s: String = Utils.escapeWhitespace(Trees.getNodeText(t, ruleNames), false)
        sb.append("$s ")
        for (i in 0 until t.childCount) {
            sb.append(process(t.getChild(i), ruleNames))
        }
        level--
        sb.append(lead(level))
        return sb.toString()
    }

    return process(this, ruleNames).replace("(?m)^\\s+$", "").replace("\\r?\\n\\r?\\n", System.lineSeparator())
}
