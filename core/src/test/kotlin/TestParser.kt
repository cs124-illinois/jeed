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
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    } else if (a == 0) {
      return 0
    } else if (a == 1) {
      return 0
    }
    return 1
}
"""
        println(
            measureTime {
                Source.fromKotlin(source).getParsed("Main.kt").tree
            },
        )
        repeat(8) {
            println(
                measureTime {
                    Source.fromKotlin(source).getParsed("Main.kt").tree
                },
            )
        }
    }
    "it should parse an actual file" {
        val source = """
fun getPronunciation(word: String): String {
  var pron = ""
  var charArray = word.lowercase().toCharArray()
  var i = 0
  while (i < charArray.size) {
    var current = charArray[i]

    var previous = ' '
    if (i > 0) previous = charArray[i - 1]
    var next = ' '
    if (i < charArray.size - 1) next = charArray[i + 1]

    if (current == 'w') {
      if (previous == 'i' || previous == 'e') {
        pron += "v"
      } else pron += "w"
    } else if (current == 'a') {
      if (next == 'i' || next == 'e') {
        pron += "eye-"
        i++
      } else if (next == 'o' || next == 'u') {
        pron += "ow-"
        i++
      } else pron += "ah-"
    } else if (current == 'e') {
      if (next == 'i') {
        pron += "ay-"
        i++
      } else if (next == 'u') {
        pron += "eh-oo-"
        i++
      } else pron += "eh-"
    } else if (current == 'i') {
      if (next == 'u') {
        pron += "ew-"
        i++
      } else pron += "ee-"
    } else if (current == 'o') {
      if (next == 'i') {
        pron += "oy-"
        i++
      } else if (next == 'u') {
        pron += "ow-"
        i++
      } else pron += "oh-"
    } else if (current == 'u') {
      if (next == 'i') {
        pron += "ooey-"
        i++
      } else pron += "oo-"
    } else if (current == 'p') {
      pron += "p"
    } else if (current == 'h') {
      pron += "h"
    } else if (current == 'l') {
      pron += "l"
    } else if (current == 'k') {
      pron += "k"
    } else if (current == 'm') {
      pron += "m"
    } else if (current == 'n') {
      pron += "n"
    } else throw IllegalArgumentException()
    i++
  }
  if (pron.last() == '-') pron = pron.dropLast(1)
  return pron
}
"""
        println(
            measureTime {
                Source.fromKotlin(source).getParsed("Main.kt").tree
            },
        )
        repeat(8) {
            println(
                measureTime {
                    Source.fromKotlin(source).getParsed("Main.kt").tree
                },
            )
        }
    }
    "it should parse lambda assignment" {
        Source.fromKotlin(
            """
val t = {
  true
}
""",
        ).parse()
    }
})
