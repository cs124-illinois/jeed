package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class TestMompile :
    StringSpec({
        "should compile simple mixed sources" {
            Source(
                mapOf(
                    "First.java" to """public class First {}""",
                    "Second.kt" to """class Second : First()""",
                    "Third.kt" to """open class Third""",
                    "Fourth.java" to """class Fourth extends Third {}""",
                ),
            ).mompile().also { compiledSource ->
                compiledSource.fileManager.classFiles.keys shouldBe setOf("First.class", "Second.class", "Third.class", "Fourth.class")
            }
        }
        "should compile mixed sources with paths" {
            Source(
                mapOf(
                    "one/First.java" to """package one;
                    |public class First {}
                    """.trimMargin(),
                    "one/Second.kt" to """package one
                    |class Second : First()
                    """.trimMargin(),
                    "two/Third.kt" to """package two
                    |open class Third
                    """.trimMargin(),
                    "two/Fourth.java" to """package two;
                    |class Fourth extends Third {}
                    """.trimMargin(),
                ),
            ).mompile().also { compiledSource ->
                compiledSource.fileManager.classFiles.keys shouldBe setOf("one/First.class", "one/Second.class", "two/Third.class", "two/Fourth.class")
            }
        }
        "should report Kotlin warnings from mixed sources against the Kotlin file" {
            Source(
                mapOf(
                    "First.java" to """public class First {}""",
                    "Second.kt" to "class Second : First() {\n  fun f() {\n    val unused = 1\n  }\n}",
                ),
            ).mompile().also { compiledSource ->
                compiledSource.messages shouldHaveSize 1
                compiledSource.messages[0].location?.source shouldBe "Second.kt"
                compiledSource.messages[0].location?.line shouldBe 3
            }
        }
        "should report Kotlin errors from mixed sources against the Kotlin file" {
            val failed = shouldThrow<CompilationFailed> {
                Source(
                    mapOf(
                        "First.java" to """public class First {}""",
                        "Second.kt" to "class Second : First() {\n  val bad: Int = \"nope\"\n}",
                    ),
                ).mompile()
            }

            failed.errors shouldHaveSize 1
            failed should haveCompilationErrorAt(source = "Second.kt", line = 2, column = 16)
        }
    })
