@file:Suppress("MagicNumber")

package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class TestKtLint :
    StringSpec({
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

fun KtLintFailed.shouldHaveError(ruleId: String, prefix: String = "standard") =
    errors.filterIsInstance<KtLintError>().also { ktlintErrors ->
        ktlintErrors shouldHaveSize 1
        ktlintErrors.filter { it.ruleId == "$prefix:$ruleId" } shouldHaveSize 1
    }

fun KtLintFailed.shouldHaveOnlyError(ruleId: String, prefix: String = "standard") =
    errors.filterIsInstance<KtLintError>().also { ktlintErrors ->
        ktlintErrors.all { it.ruleId == "$prefix:$ruleId" } shouldBe true
    }
