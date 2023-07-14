@file:Suppress("MagicNumber")

package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

class TestKtLint : StringSpec({
    "it should check simple kotlin sources" {
        repeat(8) {
            val results = Source.fromKotlinSnippet(
                """println("Hello, world!")""",
            ).ktLint()
            results.errors.isEmpty() shouldBe true
        }
    }
    "it should check kotlin sources with too long lines" {
        @Suppress("MaxLineLength")
        val ktLintFailed = shouldThrow<KtLintFailed> {
            Source.fromKotlinSnippet(
                """val test = "ttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttttt"""",
            ).ktLint(KtLintArguments(failOnError = true))
        }

        ktLintFailed.errors.filterIsInstance<KtLintError>().filter { it.ruleId == "standard:max-line-length" } shouldHaveSize 1
    }
    "!it should fail when everything is on one line" {
        shouldThrow<KtLintFailed> {
            Source.fromKotlinSnippet(
                """class Course(var number: String) { fun changeNumber(newNumber: String) { number = newNumber } }""",
            ).ktLint(KtLintArguments(failOnError = true))
        }
    }
    "it should check simple kotlin sources with indentation errors" {
        val ktLintFailed = shouldThrow<KtLintFailed> {
            Source.fromSnippet(
                """println("Hello, world!")""".trim(),
                SnippetArguments(fileType = Source.FileType.KOTLIN, indent = 3),
            ).ktLint(KtLintArguments(failOnError = true))
        }

        ktLintFailed.errors shouldHaveSize 1
        println(ktLintFailed.errors)
        ktLintFailed.errors.filterIsInstance<KtLintError>().filter { it.ruleId == KTLINT_INDENTATION_RULE_NAME } shouldHaveSize 1
    }
    "it should adjust indent for indentation errors" {
        val ktLintFailed = shouldThrow<KtLintFailed> {
            Source.fromSnippet(
                """println("Hello, world!")""".trim(),
                SnippetArguments(fileType = Source.FileType.KOTLIN, indent = 3),
            ).ktLint(KtLintArguments(failOnError = true))
        }

        ktLintFailed.errors shouldHaveSize 1
        ktLintFailed.errors.filterIsInstance<KtLintError>().filter { it.ruleId == KTLINT_INDENTATION_RULE_NAME } shouldHaveSize 1
        ktLintFailed.errors.first().let {
            it.message shouldContain "Unexpected indentation (0)"
            it.message shouldContain "(should be 3)"
        }
    }
    "it should check kotlin snippets and get the line numbers right" {
        val ktLintFailed = shouldThrow<KtLintFailed> {
            Source.fromKotlinSnippet(
                """ println("Hello, world!")""",
                trim = false,
            ).ktLint(KtLintArguments(failOnError = true))
        }

        ktLintFailed.errors shouldHaveSize 1
        ktLintFailed.errors.filterIsInstance<KtLintError>().filter { it.ruleId == KTLINT_INDENTATION_RULE_NAME } shouldHaveSize 1
        ktLintFailed.errors.first().location.line shouldBe 1
    }
    "it should reformat Kotlin sources" {
        Source.fromKotlin(
            """fun main() {
                |println("Hello, world!");
                |}
            """.trimMargin(),
        ).ktFormat().also {
            it.contents shouldBe """fun main() {
                |    println("Hello, world!")
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
                |    println("Hello, world!")
                |}
                """.trimMargin()
            }
        }
    }
})
