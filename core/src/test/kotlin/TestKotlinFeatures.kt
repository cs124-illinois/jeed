package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe

@Suppress("LargeClass")
class TestKotlinFeatures : StringSpec({
    "should count variable declarations in snippets" {
        Source.fromKotlinSnippet(
            """
var i = 0
var j: Int? = null
i = 1
i += 1
i++
--j
""".trim()
        ).features().check {
            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
            featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 2
            featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 4
        }
    }
    "should count for loops in snippets" {
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
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 4
            featureMap[FeatureName.ARRAYS] shouldBe 3
        }
    }
    "should count nested for loops in snippets" {
        Source.fromKotlinSnippet(
            """
for (i in 0 until 10) {
    for (i in 0 until 10) {
        println(i + j)
    }
}
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            featureMap[FeatureName.NESTED_FOR] shouldBe 1
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
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 1
            featureMap[FeatureName.NESTED_FOR] shouldBe 0
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
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOPS] shouldBe 2
            featureMap[FeatureName.NESTED_FOR] shouldBe 1
            featureMap[FeatureName.NESTED_LOOP] shouldBe 1
        }
    }
    "should count while loops in snippets" {
        Source.fromKotlinSnippet(
            """
var i = 0
while (i < 32) {
  while (i < 16) {
    i++
  }
  i++
}
""".trim()
        ).features().check {
            featureMap[FeatureName.WHILE_LOOPS] shouldBe 2
            featureMap[FeatureName.NESTED_WHILE] shouldBe 1
            featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 0
            featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 2
        }
    }
    "should count do-while loops in snippets" {
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
""".trim()
        ).features().check {
            featureMap[FeatureName.DO_WHILE_LOOPS] shouldBe 2
            featureMap[FeatureName.NESTED_DO_WHILE] shouldBe 1
            featureMap[FeatureName.WHILE_LOOPS] shouldBe 0
            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
            featureMap[FeatureName.VARIABLE_REASSIGNMENTS] shouldBe 2
        }
    }
    "should count simple if-else statements in snippets" {
        Source.fromKotlinSnippet(
            """
var i = 0
if (i < 5) {
    i++
} else {
    i--
}
""".trim()
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
        }
    }
    "should count a chain of if-else statements in snippets" {
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
""".trim()
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
            featureMap[FeatureName.ELSE_IF] shouldBe 2
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
""".trim()
        ).features().check {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 4
            featureMap[FeatureName.NESTED_IF] shouldBe 3
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
            """.trim()
        ).features().check("test()") {
            featureMap[FeatureName.NESTED_METHOD] shouldBe 1
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 2
            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 2
            featureMap[FeatureName.NESTED_IF] shouldBe 0
            featureMap[FeatureName.METHOD] shouldBe 1
        }.check("") {
            featureMap[FeatureName.METHOD] shouldBe 2
            featureMap[FeatureName.CLASS] shouldBe 0
        }.check {
            featureMap[FeatureName.METHOD] shouldBe 0
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
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 4
            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 3
            dottedMethodList shouldContainExactly setOf("sort", "sorted", "me", "think")
        }
    }
    "should count print statements" {
        Source.fromKotlinSnippet(
            """
println("Hello, world")
print("Another")
System.out.println("Hello, again")
System.err.print("Whoa")
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 0
            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 0
            featureMap[FeatureName.DOT_NOTATION] shouldBe 0
            featureMap[FeatureName.PRINT_STATEMENTS] shouldBe 4
            featureMap[FeatureName.JAVA_PRINT_STATEMENTS] shouldBe 1
        }
    }
    "should count assert require and check statements" {
        Source.fromKotlinSnippet(
            """
assert(false)
assert(it == true) { "Test me" }
require(false)
check(true) { "Here" }
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.ASSERT] shouldBe 2
            featureMap[FeatureName.REQUIRE_OR_CHECK] shouldBe 2
        }
    }
    "should count conditional expressions and complex conditionals in snippets" {
        Source.fromKotlinSnippet(
            """
val i = 0
if (i < 5 || i > 15) {
    if (i < 0) {
        i--
    }
} else if (i > 5 && i < 15) {
    i++
} else {
    i--
}
""".trim()
        ).features().check {
            featureMap[FeatureName.COMPARISON_OPERATORS] shouldBe 5
            featureMap[FeatureName.LOGICAL_OPERATORS] shouldBe 2
        }
    }
    "should count and enumerate import statements" {
        Source.fromKotlin(
            """
import java.util.List
import java.util.Map

fun test() {
  println("Hello, world!")
}
""".trim()
        ).features().check("", "Main.kt") {
            featureMap[FeatureName.IMPORT] shouldBe 2
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
""".trim()
        ).features().check("", "Main.kt") {
            featureMap[FeatureName.VARIABLE_ASSIGNMENTS] shouldBe 1
        }
    }
    "should count primitive and non-primitive casts" {
        Source.fromKotlinSnippet(
            """
val i = 0 as Int
val j = 0.0.toDouble()
val m = "test" as String
""".trim()
        ).features().check {
            featureMap[FeatureName.PRIMITIVE_CASTING] shouldBe 2
            featureMap[FeatureName.CASTING] shouldBe 1
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
""".trim()
        ).features().check {
            featureMap[FeatureName.INSTANCEOF] shouldBe 3
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
""".trim()
        ).features().check("") {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
        }.check("Test.init") {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
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
""".trim()
        ).features().check("Test.init") {
            featureMap[FeatureName.IF_STATEMENTS] shouldBe 1
            featureMap[FeatureName.LOCAL_VARIABLE_DECLARATIONS] shouldBe 1
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
""".trim()
        ).features()
    }
    "should detect for loop step" {
        Source.fromKotlinSnippet(
            """
for (i in 0 until array.size step 2) {
  println(i)
}
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOP_STEP] shouldBe 1
        }
    }
    "should detect for loop range" {
        Source.fromKotlinSnippet(
            """
for (i in 0..2) {
  println(i)
}
val t = 0..4
""".trim()
        ).features().check {
            featureMap[FeatureName.FOR_LOOP_RANGE] shouldBe 1
        }
    }
    "should detect Elvis operator" {
        Source.fromKotlinSnippet(
            """
val t = s ?: 0
""".trim()
        ).features().check {
            featureMap[FeatureName.ELVIS_OPERATOR] shouldBe 1
        }
    }
    "should count class fields" {
        Source.fromKotlinSnippet(
            """
class Test {
  var first = 0
  var second: String = "another"
}
""".trim()
        ).features().check("Test") {
            featureMap[FeatureName.CLASS_FIELD] shouldBe 2
        }
    }
    "should count constructors" {
        Source.fromKotlinSnippet(
            """
class Test(val first: Int) {
  var second: String = "another"
}
""".trim()
        ).features().check("Test") {
            featureMap[FeatureName.CONSTRUCTOR] shouldBe 1
        }
    }
    "should count secondary constructors" {
        Source.fromKotlinSnippet(
            """
class Test(val first: Int) {
  constructor() : this(0)
  
  var second: String = "another"
}
""".trim()
        ).features().check("Test") {
            featureMap[FeatureName.CONSTRUCTOR] shouldBe 2
            featureMap[FeatureName.SECONDARY_CONSTRUCTOR] shouldBe 1
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
""".trim()
        ).features().check {
            featureMap[FeatureName.EQUALITY] shouldBe 4
            featureMap[FeatureName.REFERENCE_EQUALITY] shouldBe 2
            featureMap[FeatureName.JAVA_EQUALITY] shouldBe 2
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
""".trim()
        ).features().check("Test") {
            featureMap[FeatureName.COMPANION_OBJECT] shouldBe 1
        }
    }
})

fun FeaturesResults.check(path: String = ".", filename: String = "", block: Features.() -> Any): FeaturesResults {
    with(lookup(path, filename).features, block)
    return this
}
