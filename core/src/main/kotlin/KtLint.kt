package edu.illinois.cs.cs125.jeed.core

import com.pinterest.ktlint.rule.engine.api.Code
import com.pinterest.ktlint.rule.engine.api.EditorConfigOverride
import com.pinterest.ktlint.rule.engine.api.KtLintRuleEngine
import com.pinterest.ktlint.rule.engine.core.api.RuleProvider
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.EXPERIMENTAL_RULES_EXECUTION_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_SIZE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.INDENT_STYLE_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.MAX_LINE_LENGTH_PROPERTY
import com.pinterest.ktlint.rule.engine.core.api.editorconfig.RuleExecution
import com.pinterest.ktlint.ruleset.standard.rules.ChainWrappingRule
import com.pinterest.ktlint.ruleset.standard.rules.CommentSpacingRule
import com.pinterest.ktlint.ruleset.standard.rules.IfElseBracingRule
import com.pinterest.ktlint.ruleset.standard.rules.IfElseWrappingRule
import com.pinterest.ktlint.ruleset.standard.rules.IndentationRule
import com.pinterest.ktlint.ruleset.standard.rules.MaxLineLengthRule
import com.pinterest.ktlint.ruleset.standard.rules.ModifierOrderRule
import com.pinterest.ktlint.ruleset.standard.rules.NoEmptyClassBodyRule
import com.pinterest.ktlint.ruleset.standard.rules.NoLineBreakAfterElseRule
import com.pinterest.ktlint.ruleset.standard.rules.NoLineBreakBeforeAssignmentRule
import com.pinterest.ktlint.ruleset.standard.rules.NoMultipleSpacesRule
import com.pinterest.ktlint.ruleset.standard.rules.NoSemicolonsRule
import com.pinterest.ktlint.ruleset.standard.rules.NoTrailingSpacesRule
import com.pinterest.ktlint.ruleset.standard.rules.NoUnitReturnRule
import com.pinterest.ktlint.ruleset.standard.rules.ParameterListWrappingRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundColonRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundCommaRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundCurlyRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundDotRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundKeywordRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundOperatorsRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundParensRule
import com.pinterest.ktlint.ruleset.standard.rules.SpacingAroundRangeOperatorRule
import com.pinterest.ktlint.ruleset.standard.rules.StatementWrapping
import com.pinterest.ktlint.ruleset.standard.rules.StringTemplateRule
import com.squareup.moshi.JsonClass
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

@JsonClass(generateAdapter = true)
data class KtLintArguments(
    val sources: Set<String>? = null,
    val failOnError: Boolean = false,
    val indent: Int = SnippetArguments.DEFAULT_SNIPPET_INDENT,
    val maxLineLength: Int = DEFAULT_MAX_LINE_LENGTH,
    val script: Boolean = false,
) {
    companion object {
        const val DEFAULT_MAX_LINE_LENGTH = 100
    }
}

internal const val KTLINT_INDENTATION_RULE_NAME = "standard:indent"

@JsonClass(generateAdapter = true)
class KtLintError(
    val ruleId: String,
    @Suppress("MemberVisibilityCanBePrivate") val detail: String,
    location: SourceLocation,
) : AlwaysLocatedSourceError(location, "$ruleId: $detail")

class KtLintFailed(errors: List<KtLintError>) : AlwaysLocatedJeedError(errors) {
    override fun toString(): String {
        return "ktlint errors were encountered: ${errors.joinToString(separator = ",")}"
    }
}

@JsonClass(generateAdapter = true)
data class KtLintResults(val errors: List<KtLintError>)

val jeedRuleProviders = setOf(
    RuleProvider { ChainWrappingRule() },
    RuleProvider { CommentSpacingRule() },
    RuleProvider { IndentationRule() },
    RuleProvider { MaxLineLengthRule() },
    RuleProvider { ModifierOrderRule() },
    // RuleProvider { NoBlankLineBeforeRbraceRule() },
    // RuleProvider { NoConsecutiveBlankLinesRule() },
    RuleProvider { NoEmptyClassBodyRule() },
    RuleProvider { NoLineBreakAfterElseRule() },
    RuleProvider { NoLineBreakBeforeAssignmentRule() },
    RuleProvider { NoMultipleSpacesRule() },
    RuleProvider { NoSemicolonsRule() },
    RuleProvider { NoTrailingSpacesRule() },
    RuleProvider { NoUnitReturnRule() },
    RuleProvider { ParameterListWrappingRule() },
    RuleProvider { SpacingAroundColonRule() },
    RuleProvider { SpacingAroundCommaRule() },
    RuleProvider { SpacingAroundCurlyRule() },
    RuleProvider { SpacingAroundDotRule() },
    RuleProvider { SpacingAroundKeywordRule() },
    RuleProvider { SpacingAroundOperatorsRule() },
    RuleProvider { SpacingAroundParensRule() },
    RuleProvider { SpacingAroundRangeOperatorRule() },
    RuleProvider { StringTemplateRule() },
    RuleProvider { StatementWrapping() },
    RuleProvider { IfElseBracingRule() },
    RuleProvider { IfElseWrappingRule() },
)

private val limiter = Semaphore(
    System.getenv("JEED_LOCK_KTLINT")?.let {
        1
    } ?: 1024,
)

val defaultRuleEngine = KtLintRuleEngine(
    ruleProviders = jeedRuleProviders,
    editorConfigOverride = EditorConfigOverride.from(
        MAX_LINE_LENGTH_PROPERTY to KtLintArguments.DEFAULT_MAX_LINE_LENGTH,
        INDENT_STYLE_PROPERTY to "space",
        INDENT_SIZE_PROPERTY to SnippetArguments.DEFAULT_SNIPPET_INDENT,
        EXPERIMENTAL_RULES_EXECUTION_PROPERTY to RuleExecution.enabled,
    ),
)

suspend fun Source.ktFormat(ktLintArguments: KtLintArguments = KtLintArguments()): Source {
    require(type == Source.FileType.KOTLIN) { "Can't run ktlint on non-Kotlin sources" }

    val names = ktLintArguments.sources ?: sources.keys
    val source = this
    val formattedSources = sources
        .filter { (filename, _) -> filename !in names }
        .map { (filename, contents) -> filename to contents }
        .toMap().toMutableMap()

    val ktlintRuleEngine =
        if (ktLintArguments.maxLineLength == KtLintArguments.DEFAULT_MAX_LINE_LENGTH && ktLintArguments.indent == SnippetArguments.DEFAULT_SNIPPET_INDENT) {
            defaultRuleEngine
        } else {
            KtLintRuleEngine(
                ruleProviders = jeedRuleProviders,
                editorConfigOverride = EditorConfigOverride.from(
                    MAX_LINE_LENGTH_PROPERTY to ktLintArguments.maxLineLength,
                    INDENT_STYLE_PROPERTY to "space",
                    INDENT_SIZE_PROPERTY to ktLintArguments.indent,
                    EXPERIMENTAL_RULES_EXECUTION_PROPERTY to RuleExecution.enabled,
                ),
            )
        }

    sources.filter { (filename, _) ->
        filename in names
    }.forEach { (filename, contents) ->
        limiter.withPermit {
            val code = Code.fromSnippet(contents, script = ktLintArguments.script)
            formattedSources[
                if (source is Snippet) {
                    "MainKt.kt"
                } else {
                    filename
                },
            ] = ktlintRuleEngine.format(code) { e, corrected ->
                if (!corrected && ktLintArguments.failOnError) {
                    throw KtLintFailed(
                        listOf(
                            KtLintError(
                                e.ruleId.value,
                                e.detail,
                                mapLocation(SourceLocation(filename, e.line, e.col)),
                            ),
                        ),
                    )
                }
            }
        }
    }

    return Source(formattedSources)
}

private val unexpectedRegex = """Unexpected indentation \((\d+)\)""".toRegex()
private val shouldBeRegex = """should be (\d+)""".toRegex()

@Suppress("LongMethod")
suspend fun Source.ktLint(ktLintArguments: KtLintArguments = KtLintArguments()): KtLintResults {
    require(type == Source.FileType.KOTLIN) { "Can't run ktlint on non-Kotlin sources" }

    val names = ktLintArguments.sources ?: sources.keys
    val errors = mutableListOf<KtLintError>()

    val ktlintRuleEngine =
        if (ktLintArguments.maxLineLength == KtLintArguments.DEFAULT_MAX_LINE_LENGTH && ktLintArguments.indent == SnippetArguments.DEFAULT_SNIPPET_INDENT) {
            defaultRuleEngine
        } else {
            KtLintRuleEngine(
                ruleProviders = jeedRuleProviders,
                editorConfigOverride = EditorConfigOverride.from(
                    MAX_LINE_LENGTH_PROPERTY to ktLintArguments.maxLineLength,
                    INDENT_STYLE_PROPERTY to "space",
                    INDENT_SIZE_PROPERTY to ktLintArguments.indent,
                    EXPERIMENTAL_RULES_EXECUTION_PROPERTY to RuleExecution.enabled,
                ),
            )
        }

    try {
        sources.filter { (filename, _) ->
            filename in names
        }.forEach { (filename, contents) ->
            limiter.withPermit {
                val code = Code.fromSnippet(contents, script = ktLintArguments.script)
                ktlintRuleEngine.lint(code) { e ->
                    @Suppress("EmptyCatchBlock")
                    try {
                        val originalLocation = SourceLocation(filename, e.line, e.col)
                        val mappedLocation = mapLocation(originalLocation)

                        val detail = if (e.ruleId.value == KTLINT_INDENTATION_RULE_NAME) {
                            @Suppress("TooGenericExceptionCaught")
                            try {
                                val addedIndent = leadingIndentation(originalLocation)
                                val (incorrectMessage, incorrectAmount) =
                                    unexpectedRegex.find(e.detail)?.groups?.let { match ->
                                        Pair(match[0]?.value, match[1]?.value?.toInt())
                                    } ?: error("Couldn't parse indentation error")
                                val (expectedMessage, expectedAmount) =
                                    shouldBeRegex.find(e.detail)?.groups?.let { match ->
                                        Pair(match[0]?.value, match[1]?.value?.toInt())
                                    } ?: error("Couldn't parse indentation error")

                                e.detail
                                    .replace(
                                        incorrectMessage!!,
                                        "Unexpected indentation (${incorrectAmount!! - addedIndent})",
                                    )
                                    .replace(
                                        expectedMessage!!,
                                        "should be ${expectedAmount!! - addedIndent}",
                                    )
                            } catch (_: Exception) {
                                e.detail
                            }
                        } else {
                            e.detail
                        }
                        errors.add(
                            KtLintError(
                                e.ruleId.value,
                                detail,
                                mappedLocation,
                            ),
                        )
                    } catch (_: Exception) {
                    }
                }
            }
        }
    } catch (e: Exception) {
        e.printStackTrace()
        throw e
    }

    if (errors.isNotEmpty() && ktLintArguments.failOnError) {
        throw KtLintFailed(errors)
    }
    return KtLintResults(errors)
}
