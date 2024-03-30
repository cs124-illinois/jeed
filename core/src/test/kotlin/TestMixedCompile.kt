package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec

class TestMixedKompile : StringSpec({
    "should compile simple mixed sources" {
        val javaSource = Source(
            mapOf(
                "First.java" to """public class First {}"""",
            )
        )
        val kotlinSource = Source(
            mapOf(
                "Second.kt" to """class Second"""",
            )
        )
        val (classLoader, messageCollector) = kompileToFileManager(KompilationArguments(), kotlinSource, javaSource = javaSource)
    }
})
