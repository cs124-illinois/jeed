package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.Spec
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

@Suppress("LargeClass")
class TestKotlinFeatures : StringSpec() {
    override suspend fun beforeSpec(spec: Spec) {
        seenKotlinFeatures.clear()
        watchKotlinFeatures = true
    }

    override suspend fun afterSpec(spec: Spec) {
        val focused = spec.rootTests().any { it.name.focus }
        if (!focused) {
            seenKotlinFeatures shouldBe KOTLIN_FEATURES
        }
    }

    init {
        "should count variable declarations" {
            Source.fromKotlinSnippet(
                """
var i = 0
var j: Int? = null
val k: Int = 0
i = 1
i += 1
i++
--j
""".trim(),
            ).features().check {
                featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 3
                featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(1, 2, 3))

                featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 3
                featureList should haveFeatureAt(FeatureName.VARIABLE_ASSIGNMENTS, listOf(1, 2, 3))

                featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 4
                featureList should haveFeatureAt(FeatureName.VARIABLE_REASSIGNMENTS, (4..7).toList())

                featureMap[FeatureName.UNARY_OPERATORS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.UNARY_OPERATORS, listOf(6, 7))

                featureMap[FeatureName.TYPE_INFERENCE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.TYPE_INFERENCE, listOf(1))

                featureMap[FeatureName.EXPLICIT_TYPE] shouldBe 2
                featureList should haveFeatureAt(FeatureName.EXPLICIT_TYPE, listOf(2, 3))

                featureMap[FeatureName.FINAL_VARIABLE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FINAL_VARIABLE, listOf(3))
            }.check("") {
                featureMap[FeatureName.CLASS] shouldBe 0
                featureList should haveFeatureAt(FeatureName.CLASS, listOf())

                featureMap[FeatureName.METHOD] shouldBe 0
                featureList should haveFeatureAt(FeatureName.METHOD, listOf())

                featureMap[FeatureName.COMPANION_OBJECT] shouldBe 0
                featureList should haveFeatureAt(FeatureName.COMPANION_OBJECT, listOf())
            }
        }
        "should count for loops" {
            Source.fromKotlinSnippet(
                """
for (i in 0 until 10) {
    println(i)
}
val first = arrayOf(1, 2, 4)
val second = intArrayOf(2, 4, 8)
val third = Array<Int>(8) { 0 }
val test = "arrayOf"
for (value in first) {
  println(value)
}
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOPS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(1, 8))

                featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 4
                featureList should haveFeatureAt(FeatureName.VARIABLE_ASSIGNMENTS, (4..7).toList())

                featureMap[FeatureName.ARRAYS] shouldBe 3
                featureList should haveFeatureAt(FeatureName.ARRAYS, (4..6).toList())

                featureMap[FeatureName.ARRAY_LITERAL] shouldBe 3
                featureList should haveFeatureAt(FeatureName.ARRAY_LITERAL, (4..6).toList())
            }
        }
        "should count nested for loops" {
            Source.fromKotlinSnippet(
                """
for (i in 0 until 10) {
    for (i in 0 until 10) {
        println(i + j)
    }
}
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOPS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(1, 2))

                featureMap[FeatureName.NESTED_FOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_FOR, listOf(2))
            }
        }
        "should not count nested for loops under if" {
            Source.fromKotlinSnippet(
                """
if (true) {
    for (i in 0 until 10) {
        println(i + j)
    }
}
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOPS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(2))

                featureMap[FeatureName.NESTED_FOR] shouldBe 0
                featureList should haveFeatureAt(FeatureName.NESTED_FOR, listOf())
            }
        }
        "should count nested for loops under if under loop" {
            Source.fromKotlinSnippet(
                """
for (i in 0 until 10) {
    if (true) {
        for (i in 0 until 10) {
            println(i + j)
        }
    }
}
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOPS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.FOR_LOOPS, listOf(1, 3))

                featureMap[FeatureName.NESTED_FOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_FOR, listOf(3))

                featureMap[FeatureName.NESTED_LOOP] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_LOOP, listOf(3))
            }
        }
        "should count while loops" {
            Source.fromKotlinSnippet(
                """
var i = 0
while (i < 32) {
  while (i < 16) {
    i++
  }
  i++
}
""",
            ).features().check {
                featureMap[FeatureName.WHILE_LOOPS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.WHILE_LOOPS, listOf(2, 3))

                featureMap[FeatureName.NESTED_WHILE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_WHILE, listOf(3))

                featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 0
                featureList should haveFeatureAt(FeatureName.DO_WHILE_LOOPS, listOf())

                featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.VARIABLE_REASSIGNMENTS, listOf(4, 6))
            }
        }
        "should count do-while loops" {
            Source.fromKotlinSnippet(
                """
var i = 0
do {
    println(i)
    i++
    var j = 0
    do {
        j++
    } while (j < 10)
} while (i < 10)
""",
            ).features().check {
                featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.DO_WHILE_LOOPS, listOf(2, 6))

                featureMap[FeatureName.NESTED_DO_WHILE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_DO_WHILE, listOf(6))

                featureMap[FeatureName.WHILE_LOOPS] shouldBe 0
                featureList should haveFeatureAt(FeatureName.WHILE_LOOPS, listOf())

                featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(1, 5))

                featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.VARIABLE_REASSIGNMENTS, listOf(4, 7))
            }
        }
        "should count simple if-else statements" {
            Source.fromKotlinSnippet(
                """
var i = 0
if (i < 5) {
    i++
} else {
    i--
}
""",
            ).features().check {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2))

                featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(4))
            }
        }
        "should count a chain of if-else statements" {
            Source.fromKotlinSnippet(
                """
var i = 0
if (i < 5) {
    i++
} else if (i < 10) {
    i--
} else if (i < 15) {
    i++
} else {
    i--
}
""",
            ).features().check {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2))

                featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(8))

                featureMap[FeatureName.ELSE_IF] shouldBe 2
                featureList should haveFeatureAt(FeatureName.ELSE_IF, listOf(4, 6))
            }
        }
        "should count nested if statements in snippets" {
            Source.fromKotlinSnippet(
                """
var i = 0
if (i < 15) {
    if (i < 10) {
        i--
        if (i < 5) {
            i++
        }
    } else {
        if (i > 10) {
            i--
        }
    }
}
""",
            ).features().check {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 4
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(2, 3, 5, 9))

                featureMap[FeatureName.NESTED_IF] shouldBe 3
                featureList should haveFeatureAt(FeatureName.NESTED_IF, listOf(3, 5, 9))
            }
        }
        "should not fail on nested methods" {
            Source.fromKotlinSnippet(
                """
fun test() {
  var i = 0
  if (i > 0) {
    fun another() {
      var j = 0
      if (j > 0) {
        println("Here")
      }
    }
  }
}
""",
            ).features().check("test()") {
                featureMap[FeatureName.NESTED_METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NESTED_METHOD, listOf(4))

                featureMap[FeatureName.IF_STATEMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(3, 6))

                featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(2, 5))

                featureMap[FeatureName.NESTED_IF] shouldBe 0
                featureList should haveFeatureAt(FeatureName.NESTED_IF, listOf())

                featureMap[FeatureName.METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(4))
            }.check("") {
                featureMap[FeatureName.METHOD] shouldBe 2
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(1, 4))

                featureMap[FeatureName.CLASS] shouldBe 0
                featureList should haveFeatureAt(FeatureName.CLASS, listOf())
            }
        }
        "should identify and record dotted method calls and property access" {
            Source.fromKotlinSnippet(
                """
val array = arrayOf(1, 2, 4)
println(array.size)
array.sort()
val sorted = array.sorted()
array.test.me().whatever.think()
""",
            ).features().check {
                featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 4
                featureList should haveFeatureAt(FeatureName.DOTTED_METHOD_CALL, listOf(3, 4, 5, 5))

                featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 3
                featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf(2, 5, 5))

                dottedMethodList shouldContainExactly setOf("sort", "sorted", "me", "think")
            }
        }
        "should count operators in snippets" {
            Source.fromKotlinSnippet(
                """
var i = 0
var j = 0
if (i < 5) {
    i += 5
    j = i - 1
} else if (i < 10) {
    i++
    j = j and i
} else if (i < 15) {
    i--
    j = j % i
} else {
    i -= 5
}
j = j shl 2
""",
            ).features().check {
                featureMap[FeatureName.UNARY_OPERATORS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.UNARY_OPERATORS, listOf(7, 10))

                featureMap[FeatureName.ARITHMETIC_OPERATORS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.ARITHMETIC_OPERATORS, listOf(5, 11))

                featureMap[FeatureName.BITWISE_OPERATORS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.BITWISE_OPERATORS, listOf(8, 15))

                featureMap[FeatureName.ASSIGNMENT_OPERATORS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.ASSIGNMENT_OPERATORS, listOf(4, 13))
            }
        }
        "should count print statements" {
            Source.fromKotlinSnippet(
                """
println("Hello, world")
println("test".length())
print("Another")
System.out.println("Hello, again")
System.out.println("world".length)
System.err.print("Whoa")
""",
            ).features().check {
                featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 1
                featureList should haveFeatureAt(FeatureName.DOTTED_METHOD_CALL, listOf(2))

                featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.DOTTED_VARIABLE_ACCESS, listOf(5))

                featureMap[FeatureName.DOT_NOTATION] shouldBe 2
                featureList should haveFeatureAt(FeatureName.DOT_NOTATION, listOf(2, 5))

                featureMap[FeatureName.PRINT_STATEMENTS] shouldBe 6
                featureList should haveFeatureAt(FeatureName.PRINT_STATEMENTS, (1..6).toList())

                featureMap[FeatureName.JAVA_PRINT_STATEMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.JAVA_PRINT_STATEMENTS, listOf(4, 5))
            }
        }
        "should count assert require and check statements" {
            Source.fromKotlinSnippet(
                """
assert(false)
assert(it == true) { "Test me" }
require(false)
check(true) { "Here" }
""",
            ).features().check {
                featureMap[FeatureName.ASSERT] shouldBe 2
                featureList should haveFeatureAt(FeatureName.ASSERT, listOf(1, 2))

                featureMap[FeatureName.REQUIRE_OR_CHECK] shouldBe 2
                featureList should haveFeatureAt(FeatureName.REQUIRE_OR_CHECK, listOf(3, 4))
            }
        }
        "should count conditional expressions and complex conditionals" {
            Source.fromKotlinSnippet(
                """
val i = 0
if (i < 5 || i > 15) {
    if (i < 0) {
        i--
    }
} else if (!(i > 5 && i < 15)) {
    i++
} else {
    i--
}
""",
            ).features().check {
                featureMap[FeatureName.COMPARISON_OPERATORS] shouldBe 5
                featureList should haveFeatureAt(FeatureName.COMPARISON_OPERATORS, listOf(2, 2, 3, 6, 6))

                featureMap[FeatureName.LOGICAL_OPERATORS] shouldBe 3
                featureList should haveFeatureAt(FeatureName.LOGICAL_OPERATORS, listOf(2, 6, 6))
            }
        }
        "should count and enumerate import statements" {
            Source.fromKotlinSnippet(
                """
import java.util.List
import java.util.Map

fun test() {
  println("Hello, world!")
}
""",
            ).features().check("") {
                featureMap[FeatureName.IMPORT] shouldBe 2
                featureList should haveFeatureAt(FeatureName.IMPORT, listOf(1, 2))

                importList shouldContainExactly setOf("java.util.List", "java.util.Map")
            }
        }
        "should lookup in top-level methods" {
            Source.fromKotlin(
                """
fun test(): Int {
  val test = 0
  return test
}
""",
            ).features().check("", "Main.kt") {
                featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.VARIABLE_ASSIGNMENTS, listOf(2))
            }
        }
        "should count primitive and non-primitive casts" {
            Source.fromKotlinSnippet(
                """
val i = 0 as Int
val j = 0.0.toDouble()
val m = "test" as String
""",
            ).features().check {
                featureMap[FeatureName.PRIMITIVE_CASTING] shouldBe 2
                featureList should haveFeatureAt(FeatureName.PRIMITIVE_CASTING, listOf(1, 2))

                featureMap[FeatureName.CASTING] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CASTING, listOf(3))
            }
        }
        "should count type checks" {
            Source.fromKotlinSnippet(
                """
println("test" is String)
println("test" is Int)
if (1 is Int) {
  println("Here")
}
""",
            ).features().check {
                featureMap[FeatureName.INSTANCEOF] shouldBe 3
                featureList should haveFeatureAt(FeatureName.INSTANCEOF, listOf(1, 2, 3))
            }
        }
        "should count if in init" {
            Source.fromKotlinSnippet(
                """
class Test {
  init {
    if (test < 10) {
      println("Here")
    }
  }
}
""",
            ).features().check("") {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(3))
            }.check("Test.init") {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(3))
            }
        }
        "should handle multiple init blocks" {
            Source.fromKotlinSnippet(
                """
class Test {
  init {
    if (test < 10) {
      println("Here")
    }
  }
  init {
    val test = 20
  }
}
""",
            ).features().check("Test.init") {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(3))

                featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.LOCAL_VARIABLE_DECLARATIONS, listOf(8))
            }
        }
        "should handle secondary constructors" {
            Source.fromKotlinSnippet(
                """
class Test {
  private val map = mutableMapOf<String, Int>()
  constructor(list: List<String>) {
    require(!list.isEmpty())
    for (place in list) {
      map[place] = 0
    }
  }
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.SECONDARY_CONSTRUCTOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.SECONDARY_CONSTRUCTOR, listOf(3))

                featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf(2))
            }
        }
        "should detect for loop step" {
            Source.fromKotlinSnippet(
                """
for (i in 0 until array.size step 2) {
  println(i)
}
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOP_STEP] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FOR_LOOP_STEP, listOf(1))
            }
        }
        "should detect for loop range" {
            Source.fromKotlinSnippet(
                """
for (i in 0..2) {
  println(i)
}
val t = 0..4
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOP_RANGE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FOR_LOOP_RANGE, listOf(1))
            }
        }
        "should detect for loop range until" {
            Source.fromKotlinSnippet(
                """
for (i in 0..<2) {
  println(i)
}
val t = 0..<4
""",
            ).features().check {
                featureMap[FeatureName.FOR_LOOP_RANGE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FOR_LOOP_RANGE, listOf(1))
            }
        }
        "should detect Elvis operator" {
            Source.fromKotlinSnippet(
                """
val t = s ?: 0
""",
            ).features().check {
                featureMap[FeatureName.ELVIS_OPERATOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ELVIS_OPERATOR, listOf(1))
            }
        }
        "should count class fields" {
            Source.fromKotlinSnippet(
                """
class Test {
  var first = 0
  val second: String = "another"
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.CLASS_FIELD] shouldBe 2
                featureList should haveFeatureAt(FeatureName.CLASS_FIELD, listOf(2, 3))

                featureMap[FeatureName.FINAL_FIELD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FINAL_FIELD, listOf(3))
            }
        }
        "should count constructors" {
            Source.fromKotlinSnippet(
                """
class Test(val first: Int) {
  var second: String = "another"
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.CONSTRUCTOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CONSTRUCTOR, listOf(1))
                featureMap[FeatureName.CLASS_FIELD] shouldBe 2
                featureList should haveFeatureAt(FeatureName.CLASS_FIELD, listOf(1, 2))
            }
        }
        "should count secondary constructors" {
            Source.fromKotlinSnippet(
                """
class Test(val first: Int) {
  constructor() : this(0)
  
  var second: String = "another"
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.CONSTRUCTOR] shouldBe 2
                featureList should haveFeatureAt(FeatureName.CONSTRUCTOR, listOf(1, 2))

                featureMap[FeatureName.SECONDARY_CONSTRUCTOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.SECONDARY_CONSTRUCTOR, listOf(2))
            }
        }
        "should count equals and Java equals" {
            Source.fromKotlinSnippet(
                """
val first = "test"
val second = "another"
println(first == second)
println(first === second)
println(second != first)
println(second !== first)
println(second.equals(first))
println(!first.equals(second))
""",
            ).features().check {
                featureMap[FeatureName.EQUALITY] shouldBe 4
                featureList should haveFeatureAt(FeatureName.EQUALITY, listOf(3, 5, 7, 8))

                featureMap[FeatureName.REFERENCE_EQUALITY] shouldBe 2
                featureList should haveFeatureAt(FeatureName.REFERENCE_EQUALITY, listOf(4, 6))

                featureMap[FeatureName.JAVA_EQUALITY] shouldBe 2
                featureList should haveFeatureAt(FeatureName.JAVA_EQUALITY, listOf(7, 8))
            }
        }
        "should count companion objects" {
            Source.fromKotlinSnippet(
                """
class Test {
  companion object {
    fun one() = 1
  }
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.COMPANION_OBJECT] shouldBe 1
                featureList should haveFeatureAt(FeatureName.COMPANION_OBJECT, listOf(2))
            }.check("") {
                featureMap[FeatureName.HAS_COMPANION_OBJECT] shouldBe 1
                featureList should haveFeatureAt(FeatureName.HAS_COMPANION_OBJECT, listOf(1))
            }
        }
        "should correctly count break and continue in snippets" {
            Source.fromKotlinSnippet(
                """
for (i in 0 until 10) {
    if (i < 7) {
        continue
    } else {
        break@for
    }
}
""",
            ).features().check {
                featureMap[FeatureName.BREAK] shouldBe 1
                featureList should haveFeatureAt(FeatureName.BREAK, listOf(5))

                featureMap[FeatureName.CONTINUE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CONTINUE, listOf(3))
            }
        }
        "should count methods and classes" {
            Source.fromKotlinSnippet(
                """
fun test(): Int {
  return 0
}
class Test { }
println("Hello, world!")
""",
            ).features().check("") {
                featureMap[FeatureName.METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(1))

                featureMap[FeatureName.RETURN] shouldBe 1
                featureList should haveFeatureAt(FeatureName.RETURN, listOf(2))

                featureMap[FeatureName.CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CLASS, listOf(4))

                featureList should haveFeatureAt(FeatureName.BLOCK_START, listOf(1, 4))
                featureList should haveFeatureAt(FeatureName.BLOCK_END, listOf(3, 4))
            }
        }
        "should count strings, streams, and null in snippets" {
            Source.fromKotlinSnippet(
                """
var first = "Hello, world!"
var second: String? = null
var third = String("test")
var another = String()
""",
            ).features().check {
                featureMap[FeatureName.STRING] shouldBe 5
                featureList should haveFeatureAt(FeatureName.STRING, listOf(1, 2, 3, 3, 4))

                featureMap[FeatureName.NULL] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NULL, listOf(2))

                featureMap[FeatureName.NULLABLE_TYPE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.NULLABLE_TYPE, listOf(2))
            }
        }
        "should count when" {
            Source.fromKotlinSnippet(
                """
when (thing) {
  10 -> true
  else -> false
}
when {
  true -> "0"
  false -> "1"
}
""",
            ).features().check {
                featureMap[FeatureName.WHEN_STATEMENT] shouldBe 2
                featureList should haveFeatureAt(FeatureName.WHEN_STATEMENT, listOf(1, 5))

                featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(3))

                featureMap[FeatureName.WHEN_ENTRY] shouldBe 4
                featureMap[FeatureName.LAST_WHEN_ENTRY] shouldBe 2
                featureList should haveFeatureAt(FeatureName.WHEN_ENTRY, listOf(2, 3, 6, 7))
                featureList should haveFeatureAt(FeatureName.LAST_WHEN_ENTRY, listOf(3, 7))
            }
        }
        "should count enum classes" {
            Source(
                mapOf(
                    "Test.kt" to """
enum class Test {
    FIRST,
    SECOND,
    THIRD
}
""".trimStart(),
                ),
            ).features().check("", "Test.kt") {
                featureMap[FeatureName.ENUM] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ENUM, listOf(1))
            }
        }
        "should count data classes" {
            Source.fromKotlinSnippet(
                """
data class Test(val first: Int)
""",
            ).features().check("") {
                featureMap[FeatureName.DATA_CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.DATA_CLASS, listOf(1))
            }
        }
        "should count interfaces and classes that implement interfaces" {
            Source.fromKotlinSnippet(
                """
interface Test {
    fun add(x: Int, y: Int): Int
    fun subtract(x: Int, y: Int): Int
}
class Calculator: Test {
    fun add(x: Int, y: Int): Int {
        return x + y
    }
    fun subtract(x: Int, y: Int): Int = x - y
}
""",
            ).features().check("") {
                featureMap[FeatureName.INTERFACE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.INTERFACE, listOf(1))
            }.check("Test") {
                featureMap[FeatureName.METHOD] shouldBe 2
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(2, 3))
            }.check("Calculator") {
                featureMap[FeatureName.IMPLEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IMPLEMENTS, listOf(5))
            }
        }
        "should count override annotation and import statements" {
            Source.fromKotlinSnippet(
                """
import java.util.Random

class Test(var number: Int) {
    override fun toString(): String = "String"
}
""",
            ).features().check("Test") {
                featureMap[FeatureName.OVERRIDE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.OVERRIDE, listOf(4))
            }.check("") {
                featureMap[FeatureName.IMPORT] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IMPORT, listOf(1))
            }
        }
        "should count getters and setters" {
            Source.fromKotlinSnippet(
                """
class Adder() {
  var value: Int = 0
    get() {
      return field
    }
    private set
  var another: Double = 0.0
    set(value) {
      field = value * 2.0
    }
}
""",
            ).features().check("Adder") {
                featureMap[FeatureName.SETTER] shouldBe 2
                featureList should haveFeatureAt(FeatureName.SETTER, listOf(6, 8))

                featureMap[FeatureName.GETTER] shouldBe 1
                featureList should haveFeatureAt(FeatureName.GETTER, listOf(3))

                featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf(6))
            }
        }
        "should count inheritance and open classes" {
            Source.fromKotlinSnippet(
                """
open class Test {
  open fun test() = 2
}
class Another : Test()
""",
            ).features().check("") {
                featureMap[FeatureName.OPEN_CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.OPEN_CLASS, listOf(1))

                featureMap[FeatureName.OPEN_METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.OPEN_METHOD, listOf(2))

                featureMap[FeatureName.EXTENDS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.EXTENDS, listOf(4))
            }
        }
        "should count super and this" {
            Source.fromKotlinSnippet(
                """
open class Test {
  fun value() = 2
}
class Another : Test() {
  val mine = 2
  fun value() = super.value() + this.mine
}
""",
            ).features().check("Another") {
                featureMap[FeatureName.SUPER] shouldBe 1
                featureList should haveFeatureAt(FeatureName.SUPER, listOf(6))

                featureMap[FeatureName.THIS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.THIS, listOf(6))
            }
        }
        "should count generic classes and type parameters" {
            Source.fromKotlinSnippet(
                """
class Test<T>
class Another<T,V>
val mine = mutableListOf<String>()
""",
            ).features().check("") {
                featureMap[FeatureName.CLASS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.CLASS, listOf(1, 2))

                featureMap[FeatureName.GENERIC_CLASS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.GENERIC_CLASS, listOf(1, 2))

                featureMap[FeatureName.TYPE_PARAMETERS] shouldBe 4
                featureList should haveFeatureAt(FeatureName.TYPE_PARAMETERS, listOf(1, 2, 2, 3))
            }
        }
        "should correctly count Comparable" {
            Source.fromKotlinSnippet(
                """
class Test: Comparable<Test> {
    override fun compareTo(other: Test): Int {
        return 0
    }
}""",
            ).features().check("Test") {
                featureMap[FeatureName.COMPARABLE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.COMPARABLE, listOf(1))

                featureMap[FeatureName.OVERRIDE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.OVERRIDE, listOf(2))
            }
        }
        "should count constructors, methods, visibility modifiers, and nested classes" {
            Source.fromKotlinSnippet(
                """
class Test(private var number: Int) {
    internal fun addToNumber() {
      number += 1
    }
    class InnerClass
}
""",
            ).features().check("") {
                featureMap[FeatureName.CLASS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.CLASS, listOf(1, 5))
            }.check("Test") {
                featureMap[FeatureName.CONSTRUCTOR] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CONSTRUCTOR, listOf(1))

                featureMap[FeatureName.METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(2))

                featureMap[FeatureName.VISIBILITY_MODIFIERS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.VISIBILITY_MODIFIERS, listOf(1, 2))

                featureMap[FeatureName.NESTED_CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.CLASS, listOf(5))
            }
        }
        "should count throwing exceptions" {
            Source.fromKotlinSnippet(
                """
fun container(size: Int) {
    if (setSize <= 0) {
      throw IllegalArgumentException("Container size must be positive")
    }
    values = new int[setSize];
}
""",
            ).features().check("") {
                featureMap[FeatureName.THROW] shouldBe 1
                featureList should haveFeatureAt(FeatureName.THROW, listOf(3))
            }
        }
        "should count try blocks and finally blocks" {
            Source.fromKotlinSnippet(
                """
var i = 0
try {
    assert(i > -1)
} catch (e: Exception) {
    println("Oops")
} finally { }
""",
            ).features().check {
                featureMap[FeatureName.TRY_BLOCK] shouldBe 1
                featureList should haveFeatureAt(FeatureName.TRY_BLOCK, listOf(2))

                featureMap[FeatureName.FINALLY] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FINALLY, listOf(6))
            }
        }
        "should count abstract classes and methods" {
            Source.fromKotlinSnippet(
                """
abstract class Test {
  abstract fun test(): Int
}
class Another : Test() {
  fun test() = 2
}
""",
            ).features().check("") {
                featureMap[FeatureName.ABSTRACT_CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ABSTRACT_CLASS, listOf(1))

                featureMap[FeatureName.ABSTRACT_METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ABSTRACT_METHOD, listOf(2))
            }
        }
        "should count static methods and fields" {
            Source.fromKotlinSnippet(
                """
class Test {
  companion object {
    fun test() = 2
    val another = 4
  }
}
""",
            ).features().check("") {
                featureMap[FeatureName.COMPANION_OBJECT] shouldBe 1
                featureList should haveFeatureAt(FeatureName.COMPANION_OBJECT, listOf(2))

                featureMap[FeatureName.STATIC_METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.STATIC_METHOD, listOf(3))

                featureMap[FeatureName.STATIC_FIELD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.STATIC_FIELD, listOf(4))
            }
        }
        "should count abstract classes and fields" {
            Source.fromKotlinSnippet(
                """
abstract class Test {
  abstract var test: Int
}
""",
            ).features().check("") {
                featureMap[FeatureName.ABSTRACT_CLASS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ABSTRACT_CLASS, listOf(1))

                featureMap[FeatureName.ABSTRACT_FIELD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ABSTRACT_FIELD, listOf(2))
            }
        }
        "should count anonymous objects and singletons" {
            Source.fromKotlinSnippet(
                """
object DoIt {
  fun it() = 8
}
interface Test {
  fun test(): Int
}
val test = object : Test {
  override fun test() = 2
}
""",
            ).features().check("") {
                featureMap[FeatureName.SINGLETON] shouldBe 1
                featureList should haveFeatureAt(FeatureName.SINGLETON, listOf(1))

                featureMap[FeatureName.ANONYMOUS_CLASSES] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ANONYMOUS_CLASSES, listOf(7))

                featureMap[FeatureName.METHOD] shouldBe 3
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(2, 5, 8))
            }.check("DoIt") {
                featureMap[FeatureName.METHOD] shouldBe 1
                featureList should haveFeatureAt(FeatureName.METHOD, listOf(2))
            }
        }
        "should count lambda expressions, anonymous methods, and functional interfaces" {
            Source.fromKotlinSnippet(
                """
fun interface Test {
  fun test(): Int
}
val test = Test { 0 }
val another = fun (x: Int): Int = x
""",
            ).features().check {
                featureMap[FeatureName.LAMBDA_EXPRESSIONS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.LAMBDA_EXPRESSIONS, listOf(4))

                featureMap[FeatureName.ANONYMOUS_FUNCTION] shouldBe 1
                featureList should haveFeatureAt(FeatureName.ANONYMOUS_FUNCTION, listOf(5))
            }.check("") {
                featureMap[FeatureName.FUNCTIONAL_INTERFACE] shouldBe 1
                featureList should haveFeatureAt(FeatureName.FUNCTIONAL_INTERFACE, listOf(1))
            }
        }
        "should count collection indexing and multi-level indexing" {
            Source.fromKotlinSnippet(
                """
val first = listOf("test", "me")
println(first[0])
val second = mapOf("test" to 1, "me" to 2)
println(second["test"])
val third = mapOf("test" to mapOf("test" to "me"))
println(third["test"]!!["test"])
""",
            ).features().check {
                featureMap[FeatureName.COLLECTION_INDEXING] shouldBe 3
                featureList should haveFeatureAt(FeatureName.COLLECTION_INDEXING, listOf(2, 4, 6))

                featureMap[FeatureName.MULTILEVEL_COLLECTION_INDEXING] shouldBe 1
                featureList should haveFeatureAt(FeatureName.MULTILEVEL_COLLECTION_INDEXING, listOf(6))
            }
        }
        "should count if expressions" {
            Source.fromKotlinSnippet(
                """
val test = if (another) {
  1
} else {
  2
}
if (test > 1) {
  println("Here")
}
""",
            ).features().check {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(1, 6))

                featureMap[FeatureName.IF_EXPRESSIONS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.IF_EXPRESSIONS, listOf(1))
            }
        }
        "should count try expressions" {
            Source.fromKotlinSnippet(
                """
val test = try {
  1
} catch (e: Exception) {
  2
}
try {
  check(true)
} catch (e: Exception) {
  println("Here")
}
""",
            ).features().check {
                featureMap[FeatureName.TRY_BLOCK] shouldBe 2
                featureList should haveFeatureAt(FeatureName.TRY_BLOCK, listOf(1, 6))

                featureMap[FeatureName.TRY_EXPRESSIONS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.TRY_EXPRESSIONS, listOf(1))
            }
        }
        "should count when expressions" {
            Source.fromKotlinSnippet(
                """
val test = when {
  true -> 1
  false -> 0
}
when {
  true -> println("Here")
  false -> println("Oops")
}
""",
            ).features().check {
                featureMap[FeatureName.WHEN_STATEMENT] shouldBe 2
                featureList should haveFeatureAt(FeatureName.WHEN_STATEMENT, listOf(1, 5))

                featureMap[FeatureName.WHEN_EXPRESSIONS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.WHEN_EXPRESSIONS, listOf(1))
            }
        }
        "should handle if in setter or getter" {
            Source.fromKotlin(
                """
class CountRepeats {
  var count = 0
    private set

  var value = 0
    set(value) {
      if (value == field) {
        count++
      }
      field = value
    }
    get() {
      if (field != 0) {
        return field * 2
      } else {
        return field
      }
    }
}
""",
            ).features().check("", filename = "Main.kt") {
                featureMap[FeatureName.IF_STATEMENTS] shouldBe 2
                featureList should haveFeatureAt(FeatureName.IF_STATEMENTS, listOf(7, 13))
            }
        }
        "should count various dots" {
            Source.fromKotlinSnippet(
                """
val test = "test"
var another: String? = null
println(test.length)
println(another?.length)
another = "test"
println(another!!.length)
println(another?.length!!.toString())
""",
            ).features().check {
                featureMap[FeatureName.DOT_NOTATION] shouldBe 1
                featureList should haveFeatureAt(FeatureName.DOT_NOTATION, listOf(3))

                featureMap[FeatureName.SAFE_CALL_OPERATOR] shouldBe 2
                featureList should haveFeatureAt(FeatureName.SAFE_CALL_OPERATOR, listOf(4, 7))

                featureMap[FeatureName.UNSAFE_CALL_OPERATOR] shouldBe 2
                featureList should haveFeatureAt(FeatureName.UNSAFE_CALL_OPERATOR, listOf(6, 7))
            }
        }
        "should separate statements and blocks" {
            val first = Source.fromKotlinSnippet(
                """
if (first) {
  println("Here")
  var i = 0
}
""",
            ).features().lookup(".").features

            val second = Source.fromKotlinSnippet(
                """
if (first) {
  println("Here")
}
var i = 0
""",
            ).features().lookup(".").features

            first.featureMap shouldBe second.featureMap

            first.featureList.map { it.feature } shouldNotBe second.featureList.map { it.feature }
        }
        "should count templates properly" {
            Source.fromTemplates(
                mapOf(
                    "Test.kt" to """println("Hello, world!")""",
                ),
                mapOf(
                    "Test.kt.hbs" to """
class Question {
    companion object {
        fun main() {
            {{{ contents }}}
        }
    }
}""".trim(),
                ),
            ).features().check("", "Test.kt") {
                featureMap[FeatureName.CLASS] shouldBe 0
                featureList should haveFeatureAt(FeatureName.CLASS, listOf())

                featureMap[FeatureName.METHOD] shouldBe 0
                featureList should haveFeatureAt(FeatureName.METHOD, listOf())

                featureMap[FeatureName.PRINT_STATEMENTS] shouldBe 1
                featureList should haveFeatureAt(FeatureName.PRINT_STATEMENTS, listOf(1))
            }
        }
    }
}
