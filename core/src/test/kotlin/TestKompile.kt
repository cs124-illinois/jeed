package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe

class TestKompile :
    StringSpec({
        "should compile simple sources" {
            val compiledSource = Source(
                mapOf(
                    "Test.kt" to """val test = "string"""",
                ),
            ).kompile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("TestKt"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
        "should compile simple classes" {
            val compiledSource = Source(
                mapOf(
                    "Test.kt" to """
data class Person(val name: String)
fun main() {
  println("Here")
}
""".trim(),
                ),
            ).kompile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("TestKt", "Person"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
        "should not fail on windows line endings" {
            val compiledSource = Source(
                mapOf(
                    "Test.kt" to """
data class Person(val name: String)
fun main() {
  println("Here")
}
""".trim().replace("\n", "\r\n"),
                ),
            ).kompile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("TestKt", "Person"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
        "should compile sources with dependencies" {
            val compiledSource = Source(
                mapOf(
                    "Test.kt" to "open class Test()",
                    "Me.kt" to "class Me() : Test()",
                ),
            ).kompile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
        "should compile sources with dependencies in wrong order" {
            val compiledSource = Source(
                mapOf(
                    "Me.kt" to "class Me() : Test()",
                    "Test.kt" to "open class Test()",
                ),
            ).kompile()

            compiledSource should haveDefinedExactlyTheseClasses(setOf("Test", "Me"))
            compiledSource should haveProvidedThisManyClasses(0)
        }
        "should identify sources that use coroutines" {
            val compiledSource = Source(
                mapOf(
                    "Me.kt" to """
import kotlinx.coroutines.*
fun main() {
  println("Hello, world!")
}
            """.trim(),
                    "Test.kt" to "open class Test()",
                ),
            ).kompile()

            compiledSource.source.parsed shouldBe false
            compiledSource.usesCoroutines() shouldBe true
            compiledSource.source.parsed shouldBe true
        }
        "should compile with predictable performance" {
            val source = Source(
                mapOf(
                    "Test.kt" to """
data class Person(val name: String)
fun main() {
  println("Here")
}
""".trim(),
                ),
            )
            val kompilationArguments = KompilationArguments(useCache = false)
            source.kompile(kompilationArguments)

            @Suppress("MagicNumber")
            repeat(8) {
                val kompilationResult = source.kompile(kompilationArguments)
                kompilationResult.interval.length shouldBeLessThan 800L
            }
        }
        "should load classes from a separate classloader" {
            val first = Source(
                mapOf(
                    "Test.java" to """
public class Test {
  public void print() {
    System.out.println("test");
  }
}
""".trim(),
                ),
            ).compile()

            val second = Source(
                mapOf(
                    "Main.kt" to """
fun main() {
  val test = Test()
  test.print()
}
""".trim(),
                ),
            ).kompile(
                kompilationArguments = KompilationArguments(
                    parentFileManager = first.fileManager,
                    parentClassLoader = first.classLoader,
                ),
            )
                .execute()

            second should haveCompleted()
            second should haveOutput("test")
        }
        "should load inner classes from a separate classloader" {
            val first = Source(
                mapOf(
                    "SimpleLinkedList.java" to """
public class SimpleLinkedList {
  protected class Item {
    public Object value;
    public Item next;

    Item(Object setValue, Item setNext) {
      value = setValue;
      next = setNext;
    }
  }
  protected Item start;
}
""".trim(),
                ),
            ).compile()

            Source(
                mapOf(
                    "CountLinkedList.kt" to """
class CountLinkedList : SimpleLinkedList() {
  fun count(value: Any): Int {
    var count = 0
    var current: SimpleLinkedList.Item? = start
    while (current != null) {
      if (current.value == value) {
        count++
      }
      current = current.next
    }
    return count
  }
}
""".trim(),
                ),
            ).kompile(
                kompilationArguments = KompilationArguments(
                    parentFileManager = first.fileManager,
                    parentClassLoader = first.classLoader,
                ),
            )
        }
        "should load classes from package in a separate classloader" {
            val first = Source(
                mapOf(
                    "test/Test.java" to """
package test;

public class Test {
  public void print() {
    System.out.println("test");
  }
}
""".trim(),
                ),
            ).compile()

            val second = Source(
                mapOf(
                    "Main.kt" to """
import test.Test

fun main() {
  val test = Test()
  test.print()
}
""".trim(),
                ),
            ).kompile(
                kompilationArguments = KompilationArguments(
                    parentFileManager = first.fileManager,
                    parentClassLoader = first.classLoader,
                ),
            )
                .execute()

            second should haveCompleted()
            second should haveOutput("test")
        }
        "should load classes from a separate classloader with null checks" {
            val first = Source(
                mapOf(
                    "Test.kt" to """
class Test {
  fun me(input: String) = "me"
}
""".trim(),
                ),
            ).kompile()

            shouldThrow<CompilationFailed> {
                Source(
                    mapOf(
                        "Main.kt" to """
fun main() {
  val test = Test()
  println(test.me(null))
}
""".trim(),
                    ),
                ).kompile(
                    kompilationArguments = KompilationArguments(
                        parentFileManager = first.fileManager,
                        parentClassLoader = first.classLoader,
                    ),
                )
            }
        }
        "should load top-level methods from a separate classloader" {
            Source(
                mapOf(
                    "Test.kt" to """
fun blah() = "me"
""".trim(),
                    "Main.kt" to """
fun main() {
  println(blah())
}
""".trim(),
                ),
            ).kompile().execute().also {
                it should haveCompleted()
                it should haveOutput("me")
            }

            val first = Source(
                mapOf(
                    "Test.kt" to """
fun blah() = "me"
""".trim(),
                ),
            ).kompile()

            Source(
                mapOf(
                    "Main.kt" to """
fun main() {
  println(blah())
}
""".trim(),
                ),
            ).kompile(
                kompilationArguments = KompilationArguments(
                    parentFileManager = first.fileManager,
                    parentClassLoader = first.classLoader,
                ),
            ).execute().also {
                it should haveCompleted()
                it should haveOutput("me")
            }
        }
        "should enumerate classes from multiple file managers" {
            val first = Source(
                mapOf(
                    "test/Test.java" to """
package test;

public class Test {
  public void print() {
    System.out.println("test");
  }
}
""".trim(),
                ),
            ).compile()

            val second = Source(
                mapOf(
                    "Main.kt" to """
package blah

import test.Test

data class AnotherTest(val name: String)

fun main() {
  val test = Test()
  test.print()
}
""".trim(),
                ),
            ).kompile(
                kompilationArguments = KompilationArguments(
                    parentFileManager = first.fileManager,
                    parentClassLoader = first.classLoader,
                ),
            )

            second.fileManager.allClassFiles.keys shouldContainExactlyInAnyOrder listOf(
                "blah/AnotherTest.class",
                "blah/MainKt.class",
                "test/Test.class",
            )
        }
        "should isolate classes correctly when requested" {
            val source = Source(
                mapOf(
                    "Test.kt" to """
package examples

class Test {
  companion object {
    @JvmStatic
    fun welcome() = "Jeed"
  }
}
            """.trim(),
                ),
            )
            source.kompile().also {
                // Incorrectly loads the class from the classpath when not isolated
                it.classLoader.loadClass("examples.Test")
                    .getDeclaredMethod("welcome").invoke(null) shouldBe "Classpath"
            }
            source.kompile(KompilationArguments(isolatedClassLoader = true, useCache = false)).also {
                it.cached shouldBe false
                it.classLoader.loadClass("examples.Test")
                    .getDeclaredMethod("welcome").invoke(null) shouldBe "Jeed"
            }
        }
        "should compile with parameter names when requested" {
            val source = Source(
                mapOf(
                    "Test.kt" to """
class Test {
  fun method(first: Int, second: Int) { }
}
            """.trim(),
                ),
            )
            source.kompile().also { compiledSource ->
                val klass = compiledSource.classLoader.loadClass("Test")
                klass.declaredMethods.find { it.name == "method" }?.parameters?.map { it.name }?.first() shouldBe "arg0"
            }
            source.kompile(KompilationArguments(parameters = true)).also { compiledSource ->
                val klass = compiledSource.classLoader.loadClass("Test")
                klass.declaredMethods.find { it.name == "method" }?.parameters?.map { it.name }
                    ?.first() shouldBe "first"
            }
        }
        "should catch recursion errors" {
            shouldThrow<CompilationFailed> {
                Source(
                    mapOf(
                        "Person.kt" to """
class Person(var name: String) {
  var name = name
    get() {
      return field
    }
    set(newName: String) {
      field = newName
      return field
    }
}

            """.trim(),
                    ),
                ).kompile()
            }
        }
        "should compute empty klass size" {
            getEmptyKotlinClassSize() shouldBeGreaterThan 0
        }
    })
