package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe

class TestSnippet : StringSpec({
    "should parse snippets" {
        Source.fromJavaSnippet(
            """
import java.util.List;

class Test {
    int me = 0;
    int anotherTest() {
        return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
class AnotherTest { }
int i = 0;
i++;
""",
        ).snippetProperties.apply {
            importCount shouldBe 1
            looseCount shouldBe 2
            methodCount shouldBe 1
            classCount shouldBe 2
        }
    }
    "should parse Kotlin snippets" {
        Source.fromKotlinSnippet(
            """
import java.util.List

class Test(var me: Int = 0) {
  fun anotherTest() = 8
}
fun testing(): Int {
    var j = 0
    return 10
}
class AnotherTest
var i = 0
i++
""",
        ).snippetProperties.apply {
            importCount shouldBe 1
            looseCount shouldBe 2
            methodCount shouldBe 1
            classCount shouldBe 2
        }
    }
    "should not fail with new switch statements in snippets" {
        Source.fromJavaSnippet(
            """
public boolean shouldMakeCoffee(String situation) {
        return switch (situation) {
          case "Morning", "Cramming" -> true;
          case "Midnight" -> true;
          default -> false;
        };
}

// Testing
""",
        ).snippetProperties.apply {
            importCount shouldBe 0
            looseCount shouldBe 0
            methodCount shouldBe 1
            classCount shouldBe 0
        }
    }
    "should identify a parse errors in a broken snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
class Test {
    int me = 0;
    int anotherTest() {
      return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
int i = 0;
i++""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(12)
    }
    "should not allow top-level return in snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
class Test {
    int me = 0;
    int anotherTest() {
      return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
return;
""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(11)
    }
    "should not allow package declarations in snippets" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
package test.me;
int i = 0;
System.out.println(i);
""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(1)
    }
    "should identify multiple parse errors in a broken snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
class;
class Test {
    int me = 0;
    int anotherTest() {
      return 8;
    }
}
int testing() {
    int j = 0;
    return 10;
}
int i = 0;
i++
""",
            )
        }
        exception.errors shouldHaveSize 3
        exception should haveParseErrorOnLine(1)
        exception should haveParseErrorOnLine(13)
    }
    "should be able to reconstruct original sources using entry map" {
        val snippet =
            """
int i = 0;
i++;
public class Test {}
int adder(int first, int second) {
    return first + second;
}""".trim()
        val source = Source.fromJavaSnippet(snippet)

        source.originalSource shouldBe (snippet)
        source.rewrittenSource shouldNotBe (snippet)
        source.originalSourceFromMap() shouldBe (snippet)
    }
    "should be able to reconstruct original Kotlin sources using entry map" {
        val snippet =
            """
fun first() = Test(3)
data class Test(val first: Int)
fun second(): Test {
  return first()
}
println(second())""".trim()
        val source = Source.fromKotlinSnippet(snippet)

        source.originalSource shouldBe (snippet)
        source.rewrittenSource shouldNotBe (snippet)
        source.originalSourceFromMap() shouldBe (snippet)
    }
    "should not allow return statements in loose code" {
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet("""return;""")
        }
    }
    "should not allow return statements in loose code even under if statements" {
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
int i = 0;
if (i > 2) {
    return;
}""",
            )
        }
    }
    "should add static to methods that lack static" {
        Source.fromJavaSnippet(
            """
void test0() {
  System.out.println("Hello, world!");
}
public void test1() {
  System.out.println("Hello, world!");
}
private void test2() {
  System.out.println("Hello, world!");
}
protected void test3() {
  System.out.println("Hello, world!");
}
  public void test4() {
  System.out.println("Hello, world!");
}""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 0
                methodCount shouldBe 5
                classCount shouldBe 0
            }
        }.compile()
    }
    "should parse Java 13 constructs in snippets" {
        val executionResult = Source.fromJavaSnippet(
            """
static String test(int arg) {
  return switch (arg) {
      case 0 -> "test";
      case 1 -> {
        yield "interesting";
      }
      default -> "whatever";
  };
}
System.out.println(test(0));
        """.trim(),
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 1
                methodCount shouldBe 1
                classCount shouldBe 0
            }
        }.compile().execute()
        executionResult should haveCompleted()
        executionResult should haveOutput("test")
    }
    "should reject imports not at top of snippet" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
public class Foo { }
System.out.println("Hello, world!");
import java.util.List;
        """,
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(3)
    }
    "should allow class declarations in blocks" {
        Source.fromJavaSnippet(
            """
boolean value = true;
if (value) {
  class Foo { }
}
System.out.println("Hello, world!");""",
        )
            .also {
                it.snippetProperties.apply {
                    importCount shouldBe 0
                    looseCount shouldBe 5
                    methodCount shouldBe 0
                    classCount shouldBe 0
                }
            }.compile()
    }
    "should allow top-level lambda expression returns" {
        Source.fromJavaSnippet(
            """
interface Test {
  int test();
}
void tester(Test test) {
  System.out.println(test.test());
}
tester(() -> {
  return 0;
});""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 3
                methodCount shouldBe 1
                classCount shouldBe 1
            }
        }.compile()
    }
    "should allow method declarations in anonymous classes in snippets" {
        Source.fromJavaSnippet(
            """
interface IncludeValue {
  boolean include(int value);
}
int countArray(int[] values, IncludeValue includeValue) {
  int count = 0;
  for (int value : values) {
    if (includeValue.include(value)) {
      count++;
    }
  }
  return count;
}

int[] array = {1, 2, 5, -1};
System.out.println(countArray(array, new IncludeValue() {
  @Override
  public boolean include(int value) {
    return value < 5 && value > 10;
  }
}));""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 7
                methodCount shouldBe 1
                classCount shouldBe 1
            }
        }.compile()
    }
    "should handle warnings from outside the snippet" {
        Source.fromJavaSnippet(
            """
import net.bytebuddy.agent.ByteBuddyAgent;
ByteBuddyAgent.install(ByteBuddyAgent.AttachmentProvider.ForEmulatedAttachment.INSTANCE);
        """,
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 1
                looseCount shouldBe 1
                methodCount shouldBe 0
                classCount shouldBe 0
            }
        }.compile()
    }
    "should parse kotlin snippets" {
        Source.fromKotlinSnippet(
            """
import java.util.List

data class Person(val name: String)
fun test() {
  println("Here")
}
val i = 0
println(i)
test()
""",
        ).snippetProperties.apply {
            importCount shouldBe 1
            looseCount shouldBe 3
            methodCount shouldBe 1
            classCount shouldBe 1
        }
    }
    "should parse kotlin snippets containing only comments" {
        Source.fromKotlinSnippet(
            """
// Test me
""",
        ).snippetProperties.apply {
            importCount shouldBe 0
            looseCount shouldBe 0
            methodCount shouldBe 0
            classCount shouldBe 0
        }
    }
    "should identify parse errors in broken kotlin snippets" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet(
                """
import kotlinx.coroutines.*

data class Person(val name: String)
fun test() {
  println("Here")
}}
val i = 0
println(i)
test()
""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(6)
    }
    "should be able to reconstruct original kotlin sources using entry map" {
        val snippet =
            """
import kotlinx.coroutines.*

data class Person(val name: String)
fun test() {
  println("Here")
}
i = 0
println(i)
test()
""".trim()
        val source = Source.fromKotlinSnippet(snippet)

        source.originalSource shouldBe (snippet)
        source.rewrittenSource shouldNotBe (snippet)
        source.originalSourceFromMap() shouldBe (snippet)
    }
    "should not allow return statements in loose kotlin code" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet("""return""")
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(1)
    }
    "should not allow return statements in loose kotlin code even under if statements" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet(
                """
val i = 0
if (i < 1) {
    return
}""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(3)
    }
    "should allow return statements in loose kotlin code in methods" {
        Source.fromKotlinSnippet(
            """
println("Here")
fun add(a: Int, b: Int): Int {
  return a + b
}
println(add(2, 3))
        """,
        ).snippetProperties.apply {
            importCount shouldBe 0
            looseCount shouldBe 2
            methodCount shouldBe 1
            classCount shouldBe 0
        }
    }
    "should not allow package declarations in kotlin snippets" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet(
                """
package test.me

println("Hello, world!")""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(1)
    }
    "should not allow a class named MainKt in kotlin snippets" {
        val exception = shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet(
                """
class MainKt() { }

println("Hello, world!")""",
            )
        }
        exception.errors shouldHaveSize 1
        exception should haveParseErrorOnLine(1)
    }
    "should remap errors properly in kotlin snippets" {
        val exception = shouldThrow<CompilationFailed> {
            Source.fromKotlinSnippet(
                """
data class Person(name: String)
println("Hello, world!")""",
            ).kompile()
        }
        exception.errors shouldHaveSize 1
        exception.errors[0].location?.line shouldBe 1
    }
    "should parse instanceof pattern matching properly" {
        Source.fromJavaSnippet(
            """
Object o = new String("");
if (o instanceof String s) {
  System.out.println(s.length());
}""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 4
                methodCount shouldBe 0
                classCount shouldBe 0
            }
        }.compile()
    }
    "should parse records properly" {
        Source.fromJavaSnippet(
            """
record Range(int lo, int hi) {
    public Range {
        if (lo > hi) {
            throw new IllegalArgumentException(String.format("(%d,%d)", lo, hi));
        }
    }
}""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 0
                methodCount shouldBe 0
                classCount shouldBe 1
            }
        }.compile()
    }
    "should parse text blocks properly" {
        val input = "String data = \"\"\"\nHere\n\"\"\";\n" + "System.out.println(data);".trim()
        Source.fromJavaSnippet(input).compile().execute().also {
            it should haveOutput("Here")
        }
    }
    "should parse text blocks properly in Kotlin snippets" {
        val input = "val data = \"\"\"Here\nMe\"\"\"\n" + "println(data)".trim()
        Source.fromKotlinSnippet(input).kompile().execute().also {
            it should haveExactOutput("Here\nMe")
        }
    }
    "should allow interfaces in snippets" {
        Source.fromJavaSnippet(
            """
interface Test {
  void test();
}
class Tester implements Test {
  public void test() { }
}""",
        ).also {
            it.snippetProperties.apply {
                importCount shouldBe 0
                looseCount shouldBe 0
                methodCount shouldBe 0
                classCount shouldBe 2
            }
        }.compile()
    }
    "should allow generic methods in Java snippets" {
        Source.fromJavaSnippet(
            """
<T> T max(T[] array) {
  return null;
}
            """.trim(),
        ).compile()
    }
    "should allow generic methods in Kotlin snippets" {
        Source.fromKotlinSnippet(
            """
class Test<T>
fun <T> Test<T>.requireIndexIsNotNegative(index: Int): Unit = require(index >= 0)
            """.trim(),
        ).kompile()
    }
    "should allow anonymous classes in snippets" {
        Source.fromJavaSnippet(
            """
interface Adder {
  int addTo(int value);
}
Adder addOne = new Adder() {
  @Override
  public int addTo(int value) {
    return value + 1;
  }
};
            """.trim(),
        ).compile()
    }
    "should allow anonymous classes in Kotlin snippets" {
        Source.fromKotlinSnippet(
            """
open class Person {
  open fun getType(): String {
    return "Person"
  }
}
val student = object : Person() {
  override fun getType(): String {
    return "Student"
  }
}
""".trim(),
        ).kompile()
    }
    "should hoist functions in Kotlin snippets" {
        Source.fromKotlinSnippet(
            """
fun first() = Test(3)
data class Test(val first: Int)
fun second(): Test {
    return first()
}
println(second())
            """.trim(),
            SnippetArguments(indent = 4),
        ).also {
            it.ktLint(KtLintArguments(failOnError = true))
        }.kompile().execute().also {
            it should haveOutput("Test(first=3)")
        }
    }
    "should rewrite stack traces for Kotlin snippets" {
        val source = Source.fromKotlinSnippet(
            """
fun first(test: Int) {
  require(test % 2 == 0) { "Test is not even" }
}
fun second(test: Int) {
  return first(test)
}
second(3)
            """.trim(),
        )
        source.kompile().execute().also { results ->
            results.threw!!.getStackTraceForSource(source).lines().also {
                it shouldHaveSize 4
                it[2].trim() shouldBe "at second(:5)"
                it[3].trim() shouldBe "at main(:7)"
            }
        }
    }
    "should rewrite compilation errors for Kotlin snippets" {
        val exception = shouldThrow<CompilationFailed> {
            Source.fromKotlinSnippet(
                """
fun reversePrint(values: CharArray): Int {
  var tempsize = values.size
  var temp: CharArray = values.reverse()
  for (i in 0..tempsize) {
    println(temp[i])
  }
  return temp.size
}
            """.trim(),
            ).kompile()
        }
        exception.errors.first().location!!.line shouldBe 3
    }
    "should parse Kotlin property getters and setters" {
        Source.fromKotlinSnippet(
            """
class Dog(val name: String?) {
  var age: Double
    set(value) {
      field = value
    }
    get() = field
}
            """.trim(),
        )
        Source.fromKotlinSnippet(
            """
class Dog(val name: String?) {
  var age: Double
    get() = field
    set(value) {
      field = value
    }
}
            """.trim(),
        )
    }
    "should parse Kotlin secondary constructors" {
        Source.fromKotlinSnippet(
            """
class Person(val name: String, var age: Double) {
  constructor(name: String) : this(name, 0.0)
  init {
    require(age >= 0.0) { "People can't have negative ages" }
  }
}
            """.trim(),
        )
    }
    "should parse Kotlin functional interfaces" {
        Source.fromKotlinSnippet(
            """
fun interface It {
  fun it(value: Int): Boolean
}
val first = It { value -> value % 2 == 0 }
            """.trim(),
        )
    }
    "should parse Java 15 case syntax".config(enabled = systemCompilerVersion >= 15) {
        Source.fromJavaSnippet(
            """
int foo = 3;
boolean boo = switch (foo) {
  case 1, 2, 3 -> true;
  default -> false;
};
System.out.println(boo);
        """.trim(),
        )
    }
    "should parse another Java 15 case syntax".config(enabled = systemCompilerVersion >= 15) {
        Source.fromJavaSnippet(
            """
int foo = 3;
boolean boo = switch (foo) {
  case 1:
  case 2:
  case 3:
    yield false;
  default:
    yield true;
};
        """.trim(),
        )
    }
    "!should parse Java 21 patterns".config(enabled = systemCompilerVersion >= 21) {
        @Suppress("SpellCheckingInspection")
        Source.fromJavaSnippet("""
            record Point(int x, int y) {}
            Object obj = new Point(1, 10);
            if (obj instanceof Point(int a, int b)) {
                System.out.println("got the point!");
            }
        """.trimIndent())
    }
    "!should parse Java 21 switch".config(enabled = systemCompilerVersion >= 21) {
        Source.fromJavaSnippet("""
            Object obj = 5;
            String str = switch (obj) {
                Integer i when i > 0 -> "a positive number: " + i;
                Integer i -> "some other number: " + i;
                default -> "not a number";
            };
        """.trimIndent())
    }
    "should use Example.main when no loose code is provided" {
        Source.fromJavaSnippet(
            """
class Another {}
public class Example {
  public static void main(String[] unused) {
    System.out.println("Here");
  }
}""".trim(),
        ).compile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("Here")
        }
        Source.fromJavaSnippet(
            """
public class Example {
  public static void main() {
    int[] array = new int[] {1, 2, 4};
    System.out.println("ran");
  }
}""".trim(),
        ).compile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("ran")
        }
    }
    "should use Example.main even with comments" {
        Source.fromJavaSnippet(
            """
// Test
public class Example {
  public static void main(String[] unused) {
    System.out.println("Here");
  }
}""".trim(),
        ).compile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("Here")
        }
    }
    "should not use Example.main when a top-level method exists" {
        Source.fromJavaSnippet(
            """
int another() {
 return 0;
}
public class Example {
  public static void main(String[] unused) {
    System.out.println("Here");
  }
}""".trim(),
        ).compile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("")
        }
    }
    "should not use Example.main when Example is not public" {
        Source.fromJavaSnippet(
            """
class Example {
  public static void main(String[] unused) {
    System.out.println("Here");
  }
}""".trim(),
        ).compile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("")
        }
    }
    "should parse kotlin snippets without empty main when requested" {
        Source.fromKotlinSnippet(
            """
fun test() {
  i = 0
}
""".trim(),
        ).also {
            it.rewrittenSource.lines() shouldHaveSize 9
            val compilerError = shouldThrow<CompilationFailed> {
                it.kompile()
            }
            compilerError.errors shouldHaveSize 1
            compilerError.errors[0].location?.line shouldBe 2
        }
        Source.fromKotlinSnippet(
            """
fun test() {
  i = 0
}
""".trim(),
            SnippetArguments(noEmptyMain = true),
        ).also {
            it.rewrittenSource.lines() shouldHaveSize 7
            val compilerError = shouldThrow<CompilationFailed> {
                it.kompile()
            }
            compilerError.errors shouldHaveSize 1
            compilerError.errors[0].location?.line shouldBe 2
        }
    }
    "should snippet Kotlin code with main method" {
        Source.fromKotlinSnippet(
            """
fun main() {
  println("Here")
}
                """.trim(),
            SnippetArguments(noEmptyMain = true),
        ).kompile()
    }
    "should not include snippet errors past the end" {
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
System.out.println("He
                """.trim(),
            )
        }.also {
            it.errors.size shouldBe 1
        }
    }
    "should clean nasty ANTLR4 messages" {
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """if (true) {
  System.out.println("Here");

                """.trim(),
            )
        }.also {
            it.errors.size shouldBe 1
            it.errors.first().message shouldBe "reached end of file while parsing"
        }
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """if (true) {
  System.out.println("Here");
}}
                """.trim(),
            )
        }.also {
            it.errors.size shouldBe 1
            it.errors.first().message shouldBe "extraneous input '}'"
        }
        shouldThrow<SnippetTransformationFailed> {
            Source.fromJavaSnippet(
                """
/*
comment
                """.trim(),
            )
        }.also {
            it.errors.size shouldBe 1
            it.errors.first().message shouldBe "mismatched input '/'"
        }
        shouldThrow<SnippetTransformationFailed> {
            Source.fromKotlinSnippet(
                """
/*
comment
                """.trim(),
            )
        }.also {
            it.errors.size shouldBe 1
            it.errors.first().message shouldBe "mismatched input '/'"
        }
    }
    "should allow Kotlin interfaces to work" {
        Source.fromKotlinSnippet(
            """
fun interface Modify {
  fun modify(value: Int): Int
}
val first = Modify { value -> value + 1 }
val second = Modify { value -> value - 10 }

println(first.modify(10))
println(second.modify(3))
            """.trim(),
        ).kompile().execute().also { executionResult ->
            executionResult should haveCompleted()
            executionResult should haveOutput("11\n-7")
        }
    }
    "should parse bangs" {
        Source.fromKotlinSnippet(
            """
println(third["test"]!!["test"])
            """.trim(),
        )
    }
})

fun haveParseErrorOnLine(line: Int) = object : Matcher<SnippetTransformationFailed> {
    override fun test(value: SnippetTransformationFailed): MatcherResult {
        return MatcherResult(
            value.errors.any { it.location.line == line },
            { "should have parse error on line $line" },
            { "should not have parse error on line $line" },
        )
    }
}
