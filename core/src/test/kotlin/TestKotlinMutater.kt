package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldMatch
import io.kotest.matchers.string.shouldNotContain
import kotlin.random.Random
import kotlin.time.measureTime

class TestKotlinMutater :
    StringSpec({
        "it should mutate equals" {
            Source.fromKotlin(
                """
fun example() {
  if (true == true) return
  if (false === true) return
}
""".trim(),
            ).checkMutations<ChangeEquals> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "==", "===")
                mutations[1].check(contents, "===", "==")
            }
        }
        "it should find boolean literals to mutate" {
            Source.fromKotlin(
                """
class Example() {
  fun example() {
    val first: Boolean = true
    val second: Boolean = false
  }
}""",
            ).checkMutations<BooleanLiteral> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "true", "false")
                mutations[1].check(contents, "false", "true")
            }
        }
        "it should find char literals to mutate" {
            Source.fromKotlin(
                """
class Example {
  fun example() {
    val first: Char = 'a'
    val second: Char = '!'
    val third: Char = '\n'
  }
}""",
            ).checkMutations<CharLiteral> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations[0].check(contents, "'a'")
                mutations[1].check(contents, "'!'")
                mutations[2].check(contents, "'${"\\"}n'")
            }
        }
        "it should find string literals to mutate" {
            Source.fromKotlin(
                """
fun example() {
    println("Hello, world!")
    val l = listOf()
    val s: String = ""
    val t = ${"\"\"\""}Test Me${"\"\"\""}
    val u = "front ${"$"}{l.size} middle ${"$"}{l.size} back"
    val v = ${"\"\"\""}front ${"$"}{l.size} middle ${"$"}{l.size} back${"\"\"\""}
}
""".trim(),
            ).checkMutations<StringLiteral> { mutations, contents ->
                mutations shouldHaveSize 9
                mutations[0].check(contents, "Hello, world!").also {
                    it shouldMatch ".*println\\(\".*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
                mutations[1].check(contents, "\"\"")
                mutations[2].check(contents, "Test Me")
            }
        }
        "it should find string literals lookalikes to mutate" {
            Source.fromKotlin(
                """
fun example() {
    println("Hello, world")
    val l = listOf()
    val s: String = ""
    val t = ${"\"\"\""}Test Me${"\"\"\""}
    val u = "front ${"$"}{l.size} middle ${"$"}{l.size} back"
    val v = ${"\"\"\""}front ${"$"}{l.size} middle ${"$"}{l.size} back${"\"\"\""}
}
""".trim(),
            ).checkMutations<StringLiteralLookalike> { mutations, contents ->
                mutations shouldHaveSize 5
                mutations[0].check(contents, "Hello, world").also {
                    it shouldMatch ".*0.*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
            }
        }
        "it should find string literals to trim" {
            Source.fromKotlin(
                """
fun example() {
    println("Hello, world!")
    val l = listOf()
    val s: String = ""
    val t = ${"\"\"\""}Test Me${"\"\"\""}
    val u = "front ${"$"}{l.size} middle ${"$"}{l.size} back"
    val v = ${"\"\"\""}front ${"$"}{l.size} middle ${"$"}{l.size} back${"\"\"\""}
}
""".trim(),
            ).checkMutations<StringLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 8
                mutations[0].check(contents, "Hello, world!")
                mutations[1].check(contents, "Test Me")
            }
        }
        "it should not mutate expressions in strings" {
            Source.fromKotlin(
                """
fun example() {
    val list = listOf(1, 2, 4)
    println("${"$"}{list.size}")
}
""".trim(),
            ).checkMutations<StringLiteral> { mutations, _ ->
                mutations shouldHaveSize 0
            }
        }
        "it should find number literals to mutate" {
            Source.fromKotlin(
                """
fun example() {
    println(1234)
    val f: Float = 1.01f
    var hex: Int = 0x0F
    val bin = 0b1001011
}
""".trim(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "1234")
                mutations[1].check(contents, "1.01f")
                mutations[2].check(contents, "0x0F")
                mutations[3].check(contents, "0b1001011")
            }
        }
        "it should not trim to zeroes" {
            Source.fromKotlin(
                """
fun example() {
    val first = 1000
    val second = 1
    val third = 0
    val fourth = -10
}
""".trim(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].shouldNotBe(contents, "1000", "000")
                mutations[0].estimatedCount shouldBe 1
                mutations[1].shouldNotBe(contents, "10", "0")
                mutations[1].estimatedCount shouldBe 1
            }
        }
        "it should not mutate negatives to zeroes" {
            Source.fromKotlin(
                """
fun example() {
    val first = -1
}
""".trim(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].shouldNotBe(contents, "1", "0")
                mutations[0].estimatedCount shouldBe 1
            }
        }
        "it should find number literals to trim" {
            Source.fromKotlin(
                """
fun example() {
    println(1234)
    val f: Float = 1.01f
    val l: Long = 10L
    var hex: Int = 0x0F
    val bin = 0b1001011
}
""".trim(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 5
                mutations[0].check(contents, "1234")
                mutations[1].check(contents, "1.01f")
                mutations[2].check(contents, "10L")
                mutations[3].check(contents, "0x0F")
                mutations[4].check(contents, "0b1001011")
            }
        }
        "it should find increments and decrements to mutate" {
            // what if in text
            Source.fromKotlin(
                """
fun example() {
  var i = 0
  var j = 1
  i++
  --j
}
""".trim(),
            ).checkMutations<IncrementDecrement> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "++", "--")
                mutations[1].check(contents, "--", "++")
            }
        }
        "it should find negatives to invert" {
            Source.fromKotlin(
                """
fun example() {
  val i = 0
  val j = -1
  val k = -j
}
""".trim(),
            ).checkMutations<InvertNegation> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "-", "")
                mutations[1].check(contents, "-", "")
            }
        }
        "it should find math to mutate" {
            Source.fromKotlin(
                """
fun example() {
  val i = 0
  val j = 1
  var k = i + j
  k = i - j
  k = i * j
  k = i / j
  var l = i % 10
}
""".trim(),
            ).checkMutations<MutateMath> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "-", "+")
                mutations[1].check(contents, "*", "/")
                mutations[2].check(contents, "/", "*")
                mutations[3].check(contents, "%", "*")
            }
        }
        "it should mutate plus separately" {
            Source.fromKotlin(
                """
fun example() {
  val i = 0
  val j = 1
  val k = i + j
}
""".trim(),
            ).checkMutations<PlusToMinus> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "+", "-")
            }
        }
        "it should find conditional boundaries to mutate" {
            Source.fromKotlin(
                """
fun example() {
  val i = 0
  if (i < 10) {
    println("Here")
  } else if (i >= 20) {
    println("There")
  }
}
""".trim(),
            ).checkMutations<ConditionalBoundary> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "<", "<=")
                mutations[1].check(contents, ">=", ">")
            }
        }
        "it should find conditionals to negate" {
            Source.fromKotlin(
                """
fun example() {
  val i = 0
  if (i < 10) {
    println("Here")
  } else if (i >= 20) {
    println("There")
  } else if (i == 10) {
    println("Again")
  }
}
""".trim(),
            ).checkMutations<NegateConditional> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations[0].check(contents, "<", ">=")
                mutations[1].check(contents, ">=", "<")
                mutations[2].check(contents, "==", "!=")
            }
        }
        "it should find primitive returns to mutate" {
            Source.fromKotlin(
                """
fun first() {}
fun second(): Int {
  return 1
}
fun third(): Char {
  return 'A'
}
fun fourth(): Boolean {
  return true
}
fun fifth(): Int {
  return 0
}
fun sixth(): Long {
  return 0L
}
fun seventh(): Double {
  return 0.0
}
fun eighth(): Double {
  return 0.0f
}
""".trim(),
            ).checkMutations<PrimitiveReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1", "0")
                mutations[1].check(contents, "'A'", "0")
            }
        }
        "it should find true returns to mutate" {
            Source.fromKotlin(
                """
fun first() {}
fun second(): Boolean {
  val it = false
  return it
}
fun third(): Boolean {
  return false
}
fun fourth(): Boolean {
  return true
}
""".trim(),
            ).checkMutations<TrueReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "it", "true")
                mutations[1].check(contents, "false", "true")
            }
        }
        "it should find false returns to mutate" {
            Source.fromKotlin(
                """
fun first() {}
fun second(): Boolean {
  it = false
  return it
}
fun third(): Boolean {
  return false
}
fun fourth(): Boolean {
  return true
}
""".trim(),
            ).checkMutations<FalseReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "it", "false")
                mutations[1].check(contents, "true", "false")
            }
        }
        "it should find null returns to mutate" {
            Source.fromKotlin(
                """
fun first() {}
fun second(): Boolean {
  it = false
  return it
}
fun third(): Boolean {
  return false
}
fun fourth(): Object {
  return Object()
}
fun fifth(): IntArray {
  return IntArray(5)
}
""".trim(),
            ).checkMutations<NullReturn> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "Object()", "null")
                mutations[1].check(contents, "IntArray(5)", "null")
            }
        }
        "it should find asserts, requires, and checks to mutate" {
            Source.fromKotlin(
                """
fun test(first: Int, second: Int) {
  assert(first > 0)
  require(first > 10)
  check(first >= 100)
  assert(second >= 0) {"Bad second value"}
}
""".trim(),
            ).checkMutations<RemoveRuntimeCheck> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "assert(first > 0)", "")
                mutations[1].check(contents, "require(first > 10)", "")
                mutations[2].check(contents, "check(first >= 100)", "")
                mutations[3].check(contents, """assert(second >= 0) {"Bad second value"}""", "")
            }
        }
        "it should remove entire methods" {
            Source.fromKotlin(
                """
fun test(first: Int, second: Int): Int {
  if (first > second) {
    return first
  } else {
    return second
  }
}
fun test(first: Int, second: Int): LongArray {
  return longArrayOf(1L, 2L, 4L)
}
""".trim(),
            ).checkMutations<RemoveMethod> { mutations, _ ->
                mutations shouldHaveSize 2
            }
        }
        "it should not remove entire methods if they are already blank" {
            Source.fromKotlin(
                """
fun test(first: Int, second: Int) {
}
fun test2(first: Int, second: Int) { }
fun test3(first: Int, second: Int) {


}
fun test4(first: Int, second: Int) { return }
fun test4(first: Int, second: Int) {
  return
}
""".trim(),
            ).checkMutations<RemoveMethod> { mutations, _ ->
                mutations shouldHaveSize 0
            }
        }
        "it should negate if statements" {
            Source.fromKotlin(
                """
fun test(first: Int, second: Int): Int {
  if (first > second) {
    return first
  } else {
    return second
  }
}
""".trim(),
            ).checkMutations<NegateIf> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "first > second", "!(first > second)")
            }
        }
        "it should negate while statements" {
            Source.fromKotlin(
                """
fun test(first: Int): Int {
  var i = 0
  while (i < first) {
    i++
  }
  do {
    i++
  } while (i > first)
}
""".trim(),
            ).checkMutations<NegateWhile> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "i < first", "!(i < first)")
                mutations[1].check(contents, "i > first", "!(i > first)")
            }
        }
        "it should remove if statements" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  if (first > 0) {
    println(1)
  }
  if (first > 0) {
    println(2)
  } else {
    println(3)
  }
  if (first > 0) {
    println(4)
  } else if (first < 0) {
    println(5)
  } else if (first == 0) {
    println(6)
  } else {
    if (first < 0) {
      println(7)
    }
    println(7)
  }
}
""".trim(),
            ).checkMutations<RemoveIf> { mutations, contents ->
                mutations shouldHaveSize 8
                mutations[0].check(
                    contents,
                    """if (first > 0) {
    println(1)
  }""",
                    "",
                )
            }
        }
        "it should swap and and or" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  if (first > 0 && first < 0) {
    println(1)
  }
}
""".trim(),
            ).checkMutations<SwapAndOr> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations[0].check(contents, "&&", "||")
            }
        }
        "it should swap break and continue" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..10) {
    if (i < 5) {
      continue
    }
    if (i > 7) {
      break
    }
  }
}
""".trim(),
            ).checkMutations<SwapBreakContinue> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "continue", "break")
            }
        }
        "it should remove plus and minus 1" {
            Source.fromKotlin(
                """
fun test() {
  var i = 0
  var j = 0
  var i = i + 1
  var j = j - 1
}""",
            ).checkMutations<PlusOrMinusOneToZero> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "1", "0")
                mutations[1].check(contents, "1", "0")
            }
        }
        "it should remove loops correctly" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) { }
  while (true) { }
  for (item: Int in intArrayOf(1, 2, 4)) { }
  do {} while (true)
}
""".trim(),
            ).checkMutations<RemoveLoop> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "for (i in 0..first) { }", "")
                mutations[1].check(contents, "while (true) { }", "")
                mutations[2].check(contents, "for (item: Int in intArrayOf(1, 2, 4)) { }", "")
                mutations[3].check(contents, "do {} while (true)", "")
            }
        }
        "it should add breaks to for loops correctly" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) { 
    i += 1
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}
""".trim(),
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations.forEach {
                    it.check(contents, "}", "break }")
                }
            }
        }
        "it should add breaks to for loops with nesting correctly" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) { 
    if (i > 2) {
      i += 2
    }
    i += 1
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations.forEach {
                    it.check(contents, "}", "break }")
                }
            }
        }
        "it should add breaks to for loops with nesting correctly and avoid double break" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) {
    if (i > 2) {
        i += 2
        break
    }
    if (i < 1) {
        i += 0
    }
    i += 1
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}""",
            ).checkMutations<AddBreak> { mutations, contents ->
                mutations shouldHaveSize 3
                mutations.forEach {
                    it.check(contents, "}", "break }")
                }
            }
        }
        "it should add continue to for loops with nesting correctly and avoid double continue" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) {
    if (i > 2) {
        i += 2
        continue
    }
    if (i < 1) {
        i += 0
    }
    i += 1
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue }")
                }
            }
        }
        "it should not add continue in last statement in for loop" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) {
    if (i > 2) {
        i += 2
        continue
    }
    if (i < 1) {
        i += 0
    }
    if (i > 3) {
        i += 1
    }
    continue
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue }")
                }
            }
        }
        "it should not add continue in last try statement in for loop" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  for (i in 0..first) {
    if (i > 2) {
        i += 2
        continue
    }
    if (i > 3) {
        i += 1
    }
    try {
      i = 0
    } catch (e: Exception) {}
    continue
  }
  for (item: Int in intArrayOf(1, 2, 4)) { }
}""",
            ).checkMutations<AddContinue> { mutations, contents ->
                mutations shouldHaveSize 1
                mutations.forEach {
                    it.check(contents, "}", "continue }")
                }
            }
        }
        "it should remove and-ors in while loops" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  while (true && false) { }
  while (false || true) { }
}
""".trim(),
            ).checkMutations<RemoveAndOr> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "true && ", "")
                mutations[1].check(contents, " && false", "")
                mutations[2].check(contents, "false || ", "")
                mutations[3].check(contents, " || true", "")
            }
        }
        "it should remove and-ors correctly" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  if (true && false) { }
  if (false || true) { }
}
""".trim(),
            ).checkMutations<RemoveAndOr> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "true && ", "")
                mutations[1].check(contents, " && false", "")
                mutations[2].check(contents, "false || ", "")
                mutations[3].check(contents, " || true", "")
            }
        }
        "it should remove try correctly" {
            Source.fromKotlin(
                """
fun test(first: Int) {
  try {
    val value = 0
  } catch (e: Exception) { }
}
""".trim(),
            ).checkMutations<RemoveTry> { mutations, _ ->
                mutations shouldHaveSize 1
            }
        }
        "it should remove statements correctly" {
            val source = Source.fromKotlin(
                """
fun test() {
  var i = 0
  i = 1
  i++
  if (i > 0) {
    i++
  } else if (i == 0) {
    i--
  } else {
    i++
  }
  var t = when {
    i > 0 -> false
    else -> true
  }
}
""".trim(),
            )
            source.checkMutations<RemoveStatement> { mutations, _ ->
                mutations shouldHaveSize 5
                mutations.forEach {
                    it.apply(source.contents).ktLint()
                }
            }
        }
        "it should remove plus correctly" {
            Source.fromKotlin(
                """
fun test() {
  var i = 1 + 2
  i = 3 + 4
}
""".trim(),
            ).checkMutations<RemovePlus> { mutations, contents ->
                mutations shouldHaveSize 4
                mutations[0].check(contents, "1 + ", "")
                mutations[1].check(contents, " + 2", "")
                mutations[2].check(contents, "3 + ", "")
                mutations[3].check(contents, " + 4", "")
            }
        }
        "it should change length and size" {
            Source.fromKotlin(
                """
fun test() {
  var array = arrayOf(1, 2, 4)
  for (i in 0 until array.size) {
    println(i)
  }
  var s = "test"
  for (i in 0 until s.length) {
    println(i)
  }
}
""".trim(),
            ).checkMutations<ChangeLengthAndSize> { mutations, contents ->
                mutations shouldHaveSize 2
                mutations[0].check(contents, "size")
                mutations[1].check(contents, "length")
            }
        }
        "it should mutate array literals" {
            Source.fromKotlin(
                """
fun test() {
  val ignore = arrayOf(8)
  val values = arrayOf(1, 2, 4)
  val second = arrayOf(arrayOf(1, 2), arrayOf(4, 5))
  val list = listOf("test,me", "again")
}
""".trim(),
            ).checkMutations<ModifyArrayLiteral> { mutations, contents ->
                mutations shouldHaveSize 5
                mutations[0].check(contents, "1, 2, 4")
                mutations[1].check(contents, "arrayOf(1, 2), arrayOf(4, 5)").also {
                    it shouldMatch ".*arrayOf\\(1, 2\\).*".toRegex(RegexOption.DOT_MATCHES_ALL)
                }
                mutations[4].check(contents, """"test,me", "again"""")
            }
        }
        "it should remove blank lines correctly" {
            val source = Source.fromKotlin(
                """
fun test(first: Int, second: Int) {
  assert(first > 0)
  assert(second >= 0) {"Bad second value"}
}
""".trim(),
            )
            source.allMutations(types = setOf(Mutation.Type.REMOVE_RUNTIME_CHECK)).also { mutations ->
                mutations shouldHaveSize 2
                mutations[0].contents.lines() shouldHaveSize 3
                mutations[0].contents.lines().filter { it.isBlank() } shouldHaveSize 0
                mutations[1].contents.lines() shouldHaveSize 3
                mutations[1].contents.lines().filter { it.isBlank() } shouldHaveSize 0
            }
        }
        "it should ignore suppressed mutations" {
            Source.fromKotlin(
                """
fun fourth(): Object {
  if (true) {
    println("Here")
  }
    return Object() // mutate-disable
  }
""".trim(),
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 7
                mutations[0].cleaned().also {
                    it["Main.kt"] shouldNotContain "mutate-disable"
                }
            }
        }
        "it should ignore specific suppressed mutations" {
            Source.fromKotlin(
                """
fun example(first: Int, second: Int): Int {
  if (first > second) { // mutate-disable-conditional-boundary
    return first
  } else {
    return second
  }
}
""".trim(),
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 9
                mutations[0].cleaned().also {
                    it["Main.kt"] shouldNotContain "mutate-disable-conditional-boundary"
                }
            }
        }
        "it should apply multiple mutations" {
            Source.fromKotlin(
                """
fun greeting() {
  val i = 0
  println("Hello, world!")
}
""".trim(),
            ).also { source ->
                source.mutater(seed = 124)
                    .also { mutater ->
                        mutater.appliedMutations shouldHaveSize 0
                        val modifiedSource = mutater.apply().contents
                        source.contents shouldNotBe modifiedSource
                        mutater.appliedMutations shouldHaveSize 1
                        mutater.size shouldBe 1
                        val anotherModifiedSource = mutater.apply().contents
                        setOf(source.contents, modifiedSource, anotherModifiedSource) shouldHaveSize 3
                        mutater.size shouldBe 0
                    }
                source.mutate().also { mutatedSource ->
                    source.contents shouldNotBe mutatedSource.contents
                    mutatedSource.mutations shouldHaveSize 1
                }
                source.mutate(limit = Int.MAX_VALUE).also { mutatedSource ->
                    source.contents shouldNotBe mutatedSource.contents
                    mutatedSource.unappliedMutations shouldBe 0
                }
                source.allMutations(random = Random(124)).also { mutatedSources ->
                    mutatedSources shouldHaveSize 7
                    mutatedSources.map { it.contents }.toSet() shouldHaveSize 7
                }
            }
        }
        "it should handle overlapping mutations" {
            Source.fromKotlin(
                """
fun testing(): Int {
  return 10
}
""".trim(),
            ).also { source ->
                source.mutater(types = ALL - setOf(Mutation.Type.REMOVE_METHOD, Mutation.Type.REMOVE_STATEMENT))
                    .also { mutater ->
                        mutater.size shouldBe 3
                        mutater.apply()
                        mutater.size shouldBe 0
                    }
            }
        }
        "it should shift mutations correctly" {
            Source.fromKotlin(
                """
fun testing(): Int {
  val it = true
  return 10
}
""".trim(),
            ).also { source ->
                source.mutater(
                    shuffle = false,
                    types = ALL - setOf(Mutation.Type.REMOVE_METHOD, Mutation.Type.REMOVE_STATEMENT),
                )
                    .also { mutater ->
                        mutater.size shouldBe 4
                        mutater.apply()
                        mutater.size shouldBe 3
                        mutater.apply()
                        mutater.size shouldBe 0
                    }
                source.allMutations(
                    types = ALL - setOf(Mutation.Type.REMOVE_METHOD, Mutation.Type.REMOVE_STATEMENT),
                    random = Random(124),
                )
                    .also { mutations ->
                        mutations shouldHaveSize 4
                        mutations.map { it.contents }.toSet() shouldHaveSize 3
                    }
            }
        }
        "it should return predictable mutations" {
            Source.fromKotlin(
                """
fun testing(): Int {
  val it = true
  return 10
}
""".trim(),
            ).also { source ->
                val first = source.allMutations(random = Random(seed = 10))
                val second = source.allMutations(random = Random(seed = 10))
                first.size shouldBe second.size
                first.zip(second).forEach { (first, second) ->
                    first.contents shouldBe second.contents
                }
            }
        }
        "it should apply mutations correctly with Strings" {
            Source.fromKotlin(
                """
fun reformatName(input: String?): String? {
  if (input == null) {
    return null
  }
  val parts = input.split(",")
  return parts[1].trim() + " " + parts[0].trim()
}
""".trim(),
            ).also { source ->
                source.allMutations()
            }
        }
        "it should mutate functions with equals block" {
            Source.fromKotlin(
                """
fun isOdd(arg: Int) = arg % 2 != 0
""".trim(),
            ).also { source ->
                source.allMutations().also {
                    it shouldHaveAtLeastSize 1
                }
            }
        }
        "it should apply stream mutations" {
            Source.fromKotlin(
                """
fun testStream(): String {
  val test = "foobarfoobarfoobarfoobar"
  return test
}
""".trim(),
            ).also { source ->
                source.mutationStream().take(512).toList().size shouldBe 512
            }
        }
        "it should apply all fixed mutations" {
            Source.fromKotlin(
                """
fun testStream(): String {
  val test = "foobarfoobarfoobarfoobar"
  if (test.length > 4) {
    return "blah"
  }
  return test
}
""".trim(),
            ).allFixedMutations(random = Random(124)).also { mutations ->
                mutations shouldHaveSize 31
            }
        }
        "it should end stream mutations when out of things to mutate" {
            Source.fromKotlin(
                """
fun testStream(): Int {
  var i = 0
  i++
  return i
}
""".trim(),
            ).also { source ->
                source.mutationStream().take(1024).toList().size shouldBe 6
            }
        }
        "it should not mutate annotations" {
            Source.fromKotlin(
                """
@Suppress("unused")
fun reformatName(input: String) {
}
""".trim(),
            ).also { source ->
                source.allMutations() shouldHaveSize 0
            }
        }
        "it should mark mutations cleanly" {
            Source.fromKotlin(
                """
fun reformatName(input: String?) {
  if (input == null) {
    return
  }
  println("Hello, " + input)
}
""".trim(),
            ).allMutations().also { mutations ->
                mutations shouldHaveSize 14
                mutations.forEach { mutatedSource ->
                    mutatedSource.marked().ktLint(KtLintArguments(failOnError = true))
                }
            }
        }
        "it should handle double marks" {
            Source.fromKotlin(
                """
fun startWord(input: String, word: String): String {
    if (
        input.length > 4 &&
            word.length > 5 &&
            word.length > 4
        ) {
        println("Here")
    }
    if (input.length > 0 && input.substring(1).startsWith(word.substring(1))) {
        return input.substring(0, word.length)
    } else {
        return ""
    }
}
""".trim(),
            ).allMutations().onEach { mutatedSource ->
                mutatedSource.marked().ktLint().also { errors ->
                    errors.errors.filter {
                        it.ruleId != KTLINT_INDENTATION_RULE_NAME && it.ruleId != "standard:no-trailing-spaces" && it.ruleId != "standard:no-multi-spaces"
                    } shouldHaveSize 0
                }
            }
        }
        "it should handle double marks again" {
            Source.fromKotlin(
                """
class Question {
    fun gameOver(board: Array<CharArray>): Char {
        for (i in 0..3) {
            if (
                board[i][0] != ' ' &&
                board[i][0] == board[i][1] &&
                board[i][0] == board[i][2]
            ) {
                return board[i][0]
            }
        }
        for (i in 0..3) {
            if (
                board[0][i] != ' ' &&
                board[0][i] == board[1][i] &&
                board[0][i] == board[2][i]
            ) {
                return board[0][i]
            }
        }
        return ' '
    }
}
""",
            ).ktFormat().allMutations().map { it.formatted() }.onEach { mutatedSource ->
                mutatedSource.marked().also {
                    it.ktLint().errors shouldHaveSize 0
                }
            }
        }
        "it should handle getters and setters" {
            Source.fromKotlin(
                """
class Adder() {
  var value: Int = 0
    get() {
      return field
    }
  fun add(newNum: Int): Int {
    value += newNum
    return value
  }
}
""",
            ).allMutations()
        }
        "it should handle braceless loops" {
            Source.fromKotlin(
                """
fun listSum(list: List<Int>): Int {
  var sum = 0
  for (item in list) sum += item
  return sum
}
""",
            ).allMutations()
        }
        "it should handle nested loops" {
            Source.fromKotlin(
                """
fun test() {
  for (i in 0..10) {
    while (true) {
      println("Here")
    }
  }
}
            """,
            ).allMutations()
        }
        "it should not mutate modulo to zero with number literal" {
            Source.fromKotlin(
                """
fun test() {
  val test = value % 1
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromKotlin(
                """
fun test() {
  test %= 1
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "test %= 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromKotlin(
                """
fun test() {
  val test = value / 1
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value / 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromKotlin(
                """
fun test() {
  val test = value / (((1)))
}
                """.trimMargin(),
            ).checkMutations<NumberLiteral> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value / (((0)))"
                    }
                    mutations.first().reset()
                }
            }
        }
        "it should not mutate modulo to zero with number literal trim" {
            Source.fromKotlin(
                """
fun test() {
  val test = value % 10
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromKotlin(
                """
fun test() {
  val test = value / 10
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value / 0"
                    }
                    mutations.first().reset()
                }
            }
            Source.fromKotlin(
                """
fun test() {
  val test = value / ((10))
}
                """.trimMargin(),
            ).checkMutations<NumberLiteralTrim> { mutations, contents ->
                mutations shouldHaveSize 1
                repeat(32) {
                    mutations.first().apply(contents).also { mutated ->
                        mutated shouldNotContain "value % ((0))"
                    }
                    mutations.first().reset()
                }
            }
        }
        "!it should handle braceless statements" {
            // BROKEN WITH NEW GRAMMAR
            // Not a huge price to pay. (Why does this even work?)
            Source.fromKotlin(
                """
fun test() {
  if (whisper)
  else if (age >= 18) {
    println("Adult")
  } else if (age < 18) {
    println("Not Adult")
  }
}
""",
            ).allMutations()
        }
        "!it should generate a lot of mutations" {
            val originalSource = Source.fromKotlin(
                """
fun getPronunciation(input: String): String {
  var characters = input.lowercase().toCharArray()
  var i = 0
  var pronunciation = ""
  while (i < characters.size) {
    var current = characters[i]
    var next = ' '
    if (i < characters.size - 1) {
      next = characters[i + 1]
    }
    var previous = ' '
    if (i > 0) {
      previous = characters[i - 1]
    }
    if (current == 'w') {
      if (previous == 'i' || previous == 'e') {
        pronunciation += "v"
      } else {
        pronunciation += "w"
      }
    } else if (current == 'a') {
      if (next == 'i' || next == 'e') {
        pronunciation += "eye-"
        i++
      } else if (next == 'o' || next == 'u') {
        pronunciation += "ow-"
          i++
        } else {
          pronunciation += "ah-"
        }
      } else if (current == 'e') {
        if (next == 'i') {
          pronunciation += "ay-"
          i++
        } else if (next == 'u') {
          pronunciation += "eh-oo-"
          i++
        } else {
          pronunciation += "eh-"
        }
      } else if (current == 'i') {
        if (next == 'u') {

        }
      } else if (current == 'o') {
        if (next == 'i') {
          pronunciation += "oy-"
          i++
        } else if (next == 'u') {
          pronunciation += "ow-"
          i++
        } else {
          pronunciation += "oh-"
        }
      } else if (current == 'u') {
        if (next == 'i') {
          pronunciation += "ooey-"
          i++
        } else {
          pronunciation += "oo-"
        }
        //p,k,h,l,m,n
        } else if (current == 'p') {
          pronunciation += "p"
        } else if (current == 'k') {
          pronunciation += "k"
        } else if (current == 'h') {
          pronunciation += "h"
        } else if (current == 'l') {
          pronunciation += "l"
        } else if (current == 'm') {
          pronunciation += "m"
        } else if (current == 'n') {
          pronunciation += "n"
      } else {
       throw IllegalArgumentException()
      }
      i++
    }
    if (pronunciation.endsWith("-")) {
      return pronunciation.substring(0, pronunciation.length - 1)
  } else {
    return pronunciation
  }
}""",
            )
            val sourceTime = measureTime {
                originalSource.complexity()
            }
            println("Source: $sourceTime")

            originalSource.allFixedMutations().forEachIndexed { _, mutatedSource ->
                val mutationTime = measureTime {
                    Source.fromKotlin(mutatedSource.contents).complexity()
                }
                println(mutationTime)
            }
        }
    })
