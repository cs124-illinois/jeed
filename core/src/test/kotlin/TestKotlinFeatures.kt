package edu.illinois.cs.cs125.jeed.core

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

@Suppress("LargeClass")
class TestKotlinFeatures : StringSpec({
    "should count variable declarations in snippets" {
        Source.fromKotlinSnippet(
            """
var i = 0
var j: Int? = null
val k: Int = 0
i = 1
i += 1
i++
--j
""".trim()
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
        }.check("") {
            featureMap[FeatureName.CLASS] shouldBe 0
            featureList should haveFeatureAt(FeatureName.CLASS, listOf())

            featureMap[FeatureName.METHOD] shouldBe 0
            featureList should haveFeatureAt(FeatureName.METHOD, listOf())

            featureMap[FeatureName.COMPANION_OBJECT] shouldBe 0
            featureList should haveFeatureAt(FeatureName.COMPANION_OBJECT, listOf())
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
"""
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
            """.trimIndent()
        ).features().check {
            featureMap[FeatureName.DOTTED_METHOD_CALL] shouldBe 1
            featureMap[FeatureName.DOTTED_VARIABLE_ACCESS] shouldBe 1
            featureMap[FeatureName.DOT_NOTATION] shouldBe 2
            featureMap[FeatureName.PRINT_STATEMENTS] shouldBe 6
            featureMap[FeatureName.JAVA_PRINT_STATEMENTS] shouldBe 2
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
} else if (!(i > 5 && i < 15)) {
    i++
} else {
    i--
}
""".trim()
        ).features().check {
            featureMap[FeatureName.COMPARISON_OPERATORS] shouldBe 5
            featureList should haveFeatureAt(FeatureName.COMPARISON_OPERATORS, listOf(2, 2, 3, 6, 6))

            featureMap[FeatureName.LOGICAL_OPERATORS] shouldBe 3
            featureList should haveFeatureAt(FeatureName.LOGICAL_OPERATORS, listOf(2, 6, 6))
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
"""
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
"""
        ).features().check("Test") {
            featureMap[FeatureName.COMPANION_OBJECT] shouldBe 1
            featureList should haveFeatureAt(FeatureName.COMPANION_OBJECT, listOf(2))
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
"""
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
class Test
println("Hello, world!")
"""
        ).features().check("") {
            featureMap[FeatureName.METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.METHOD, listOf(1))

            featureMap[FeatureName.RETURN] shouldBe 1
            featureList should haveFeatureAt(FeatureName.RETURN, listOf(2))

            featureMap[FeatureName.CLASS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.CLASS, listOf(4))
        }
    }
    "should count strings, streams, and null in snippets" {
        Source.fromKotlinSnippet(
            """
var first = "Hello, world!"
var second: String? = null
var third = String("test")
var another = String()
"""
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
"""
        ).features().check {
            featureMap[FeatureName.WHEN] shouldBe 2
            featureList should haveFeatureAt(FeatureName.WHEN, listOf(1, 5))

            featureMap[FeatureName.ELSE_STATEMENTS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ELSE_STATEMENTS, listOf(3))
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
                """.trim()
            )
        ).features().check("", "Test.kt") {
            featureMap[FeatureName.ENUM] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ENUM, listOf(1))
        }
    }
    "should count data classes" {
        Source.fromKotlinSnippet(
            """
data class Test(val first: Int)
"""
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
"""
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
"""
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
"""
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
"""
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
"""
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
"""
        ).features().check("") {
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
}"""
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
"""
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
"""
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
"""
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
"""
        ).features().check("") {
            featureMap[FeatureName.ABSTRACT_CLASS] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ABSTRACT_CLASS, listOf(1))

            featureMap[FeatureName.ABSTRACT_METHOD] shouldBe 1
            featureList should haveFeatureAt(FeatureName.ABSTRACT_METHOD, listOf(2))
        }
    }
})

fun FeaturesResults.check(path: String = ".", filename: String = "", block: Features.() -> Any): FeaturesResults {
    with(lookup(path, filename).features, block)
    return this
}
