package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.ints.shouldBeLessThanOrEqual

class TestGoogleFormat : StringSpec({
    "should reformat code to remove long lines" {
        val long = Source.fromJava(
            """
// Testing a very very very very very very very very very very very very very very very very very very very very very very very very long line
        """,
        )
        long.googleFormat().contents.lines().forEach {
            it.length shouldBeLessThanOrEqual 100
        }
    }
})
