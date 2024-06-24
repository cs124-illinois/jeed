package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
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
    })
