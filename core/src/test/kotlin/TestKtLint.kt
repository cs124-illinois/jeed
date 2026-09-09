@file:Suppress("MagicNumber")

package edu.illinois.cs.cs125.jeed.core

import com.pinterest.ktlint.rule.engine.core.api.KtlintKotlinCompiler
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain

class TestKtLint :
    StringSpec({
        // core/src/main/kotlin/KtlintKotlinCompiler.kt is a patched copy of a class that also ships inside
        // ktlint-rule-engine-core-1.8.0.jar, and it only takes effect because Jeed's own classes precede
        // dependency jars on the classpath. Nothing else would notice if that ordering changed: the jar's
        // copy would simply load and every ktlint call would fail on Kotlin 2.4.20. Pin the ordering here
        // so that the shadow losing is a named failure rather than twelve confusing ones.
        "it should load Jeed's copy of the ktlint compiler bootstrap" {
            val location = KtlintKotlinCompiler::class.java.protectionDomain.codeSource.location.toString()
            location shouldNotContain "ktlint-rule-engine"
        }
        "it should check simple kotlin sources" {
            Source.fromKotlinSnippet(
                """println("Hello, world!")""",
            ).ktLint(KtLintArguments(failOnError = true))
        }
        "it should check kotlin sources with too long lines" {
            @Suppress("MaxLineLength")
            shouldThrow<KtLintFailed> {
                Source.fromKotlinSnippet(
                    """val test = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"""",
                ).ktLint(KtLintArguments(failOnError = true))
            }.shouldHaveError("max-line-length")
        }
        "it should ignore errors when filtered" {
            @Suppress("MaxLineLength")
            shouldThrow<KtLintFailed> {
                Source.fromKotlinSnippet(
                    """val test = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"""",
                ).ktLint(KtLintArguments(failOnError = true))
            }.shouldHaveError("max-line-length")

            Source.fromKotlinSnippet(
                """val test = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"""",
            ).ktLint(
                KtLintArguments(failOnError = true, filterErrors = { error ->
                    error.location.line != 1
                }),
            )

            Source.fromKotlinSnippet(
                """val test = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"""",
            ).ktLint(
                KtLintArguments(failOnError = true, filterErrors = { error ->
                    error.ruleId != "standard:max-line-length"
                }),
            )
        }
        "it should fail when everything is on one line" {
            shouldThrow<KtLintFailed> {
                Source.fromKotlin(
                    """class Course(var number: String) { fun changeNumber(newNumber: String) { number = newNumber } }""",
                ).ktLint(KtLintArguments(failOnError = true))
            }.shouldHaveOnlyError("statement-wrapping")
        }
        "it should fail when everything is on one line per documentation" {
            shouldThrow<KtLintFailed> {
                Source.fromKotlin(
                    """fun foo1() { if (true) {
  val i = 0
}
}""",
                ).ktLint(KtLintArguments(failOnError = true))
            }.shouldHaveOnlyError("statement-wrapping")
        }
        "it should allow empty single-line classes" {
            Source.fromKotlin(
                """class Test""",
            ).ktLint(KtLintArguments(failOnError = true))
        }
        "it should check simple kotlin sources with indentation errors" {
            shouldThrow<KtLintFailed> {
                Source.fromSnippet(
                    """println("Hello, world!")""".trim(),
                    SnippetArguments(fileType = Source.FileType.KOTLIN, indent = 3),
                ).ktLint(KtLintArguments(failOnError = true))
            }.shouldHaveError("indent")
        }
        "it should adjust indent for indentation errors" {
            shouldThrow<KtLintFailed> {
                Source.fromKotlinSnippet(
                    """ println("Hello, world!")""",
                    trim = false,
                ).ktLint(KtLintArguments(failOnError = true))
            }.apply {
                shouldHaveError("indent")
                errors.first().let {
                    it.message shouldContain "Unexpected indentation (1)"
                    it.message shouldContain "(should be 0)"
                }
            }
        }
        "it should check kotlin snippets and get the line numbers right" {
            shouldThrow<KtLintFailed> {
                Source.fromKotlinSnippet(
                    """ println("Hello, world!")""",
                    trim = false,
                ).ktLint(KtLintArguments(failOnError = true))
            }.apply {
                shouldHaveError("indent")
                errors.first().location.line shouldBe 1
            }
        }
        "it should reformat Kotlin sources" {
            Source.fromKotlin(
                """fun main() {
                |println("Hello, world!");
                |}
                """.trimMargin(),
            ).ktFormat().also {
                it.contents shouldBe """fun main() {
                |  println("Hello, world!")
                |}
                """.trimMargin()
            }
        }
        "it should check kotlin scripts" {
            repeat(8) {
                val results = Source.fromKotlin(
                    """println("Hello, world!")""",
                ).ktLint(KtLintArguments(script = true))
                results.errors.isEmpty() shouldBe true
            }
        }
        "it should reformat kotlin scripts" {
            repeat(8) {
                Source.fromKotlin(
                    """if (true) {
                |println("Hello, world!");
                |}
                    """.trimMargin(),
                ).ktFormat(KtLintArguments(script = true)).also {
                    it.contents shouldBe """if (true) {
                |  println("Hello, world!")
                |}
                    """.trimMargin()
                }
            }
        }
    })

fun KtLintFailed.shouldHaveError(ruleId: String, prefix: String = "standard") = errors.filterIsInstance<KtLintError>().also { ktlintErrors ->
    ktlintErrors shouldHaveSize 1
    ktlintErrors.filter { it.ruleId == "$prefix:$ruleId" } shouldHaveSize 1
}

fun KtLintFailed.shouldHaveOnlyError(ruleId: String, prefix: String = "standard") = errors.filterIsInstance<KtLintError>().also { ktlintErrors ->
    ktlintErrors.all { it.ruleId == "$prefix:$ruleId" } shouldBe true
}
