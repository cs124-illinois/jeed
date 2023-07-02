package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlin.time.ExperimentalTime
import kotlin.time.measureTime

@OptIn(ExperimentalTime::class)
class TestParser : StringSpec({
    "it should parse kotlin code" {
        Source(
            mapOf(
                "Main.kt" to """
class Person {
  val name: String = ""
  val age: Double
  init {
    age = 0.0
  }
}
""".trim(),
            ),
        ).getParsed("Main.kt").tree
    }
    "it should distinguish between different kinds of sources" {
        val javaSnippet =
            """System.out.println("Hello, world!");"""
        val javaSource =
            """
public class Example {}
            """.trimIndent()
        val kotlinSnippet =
            """println("Hello, world!")"""
        val kotlinSource =
            """
fun main() {
  println("Hello, world!")
}
            """.trimIndent()
        javaSnippet.distinguish("java") shouldBe SourceType.JAVA_SNIPPET
        javaSource.distinguish("java") shouldBe SourceType.JAVA_SOURCE
        kotlinSnippet.distinguish("kotlin") shouldBe SourceType.KOTLIN_SNIPPET
        kotlinSource.distinguish("kotlin") shouldBe SourceType.KOTLIN_SOURCE
    }
    "it should identify a snippet" {
        """
class Flip {
  boolean state;
  Flip(boolean start) {
    state = start;
  }
  boolean flop() {
    state = !state;
    return state;
  }
}
Flip f = new Flip(true);
System.out.println(f.flop());
System.out.println(f.flop());""".trim().distinguish("java") shouldBe SourceType.JAVA_SNIPPET
    }
    "it should identify a source with records" {
        """
record State(int value) {
  State {
    if (value < 0) {
      throw new IllegalArgumentException("Bad");
    }
  }
}
""".trim().distinguish("java") shouldBe SourceType.JAVA_SOURCE
    }
    "it should parse long if statements" {
        val source = """fun mystery(a: Int): Int {
    if (a == -1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == -2147483648) {
      return 2
    } else if (a == 889510) {
      return 2
    } else if (a == 598806) {
      return 2
    } else if (a == 974889) {
      return 2
    } else if (a == 485818) {
      return 3
    } else if (a == 858845) {
      return 3
    } else if (a == 887182) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    } else if (a == 668881) {
      return 3
    }
    return 1
}
"""
        println(measureTime {
            Source.fromKotlin(source).getParsed("Main.kt").tree
        })
        repeat(8) {
            println(measureTime {
                Source.fromKotlin(source).getParsed("Main.kt").tree
            })
        }
    }
})
