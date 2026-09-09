package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.collections.shouldContainExactlyInAnyOrder
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import org.objectweb.asm.ClassReader
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.tree.InvokeDynamicInsnNode
import java.security.Permission

class TestKompile :
    StringSpec({
        "should compile simple sources" {
            Source(
                mapOf(
                    "Test.kt" to """val test = "string"""",
                ),
            ).kompile().also { compiledSource ->
                compiledSource should haveDefinedExactlyTheseClasses(setOf("TestKt"))
                compiledSource should haveProvidedThisManyClasses(0)
            }
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
        "should identify warnings in Kotlin sources" {
            // Also the guard that the compiler arguments reach the compiler at all: unused-variable
            // reporting only happens because setupCommonArguments turns extraWarnings into
            // languageVersionSettings, so if that call is ever dropped this goes quiet.
            val compiledSource = Source(
                mapOf("Test.kt" to "fun main() {\n  val unused = 5\n}"),
            ).kompile()

            compiledSource.messages shouldHaveSize 1
            compiledSource.messages[0].message.lowercase() shouldContain "unused"
            compiledSource should haveCompilationMessageAt(source = "Test.kt", line = 2)
        }
        "should report each Kotlin warning exactly once" {
            // Diagnostics are collected by the FIR pipeline and drained into the message collector
            // afterwards; draining more than once would report every warning twice over.
            val compiledSource = Source(
                mapOf("Test.kt" to "fun main() {\n  val first = 1\n  val second = 2\n}"),
            ).kompile()

            compiledSource.messages shouldHaveSize 2
            compiledSource.messages.map { it.location?.line } shouldContainExactlyInAnyOrder listOf(2, 3)
        }
        "should not treat Kotlin warnings as errors by default" {
            Source(
                mapOf("Test.kt" to "fun main() {\n  val unused = 5\n}"),
            ).kompile().messages shouldHaveSize 1
        }
        "should fail when Kotlin warnings are treated as errors" {
            val failed = shouldThrow<CompilationFailed> {
                Source(
                    mapOf("Test.kt" to "fun main() {\n  val unused = 5\n}"),
                ).kompile(KompilationArguments(allWarningsAsErrors = true))
            }

            failed should haveCompilationErrorAt(source = "Test.kt", line = 2)
        }
        "should report the location of a Kotlin type error" {
            val failed = shouldThrow<CompilationFailed> {
                Source(
                    mapOf("Test.kt" to "fun main() {\n  val x: Int = \"nope\"\n}"),
                ).kompile()
            }

            failed.errors shouldHaveSize 1
            failed should haveCompilationErrorAt(source = "Test.kt", line = 2, column = 14)
        }
        "should honor the requested JVM target" {
            // Class file major versions: 55 is Java 11, 61 is Java 17, 65 is Java 21.
            mapOf("11" to 55, "17" to 61, "21" to 65).forEach { (target, expectedMajor) ->
                val compiledSource = Source(mapOf("Test.kt" to "val test = 1"))
                    .kompile(KompilationArguments(jvmTarget = target))
                val bytecode = compiledSource.fileManager.classFiles.values.first()
                    .openInputStream().readAllBytes()
                val major = (bytecode[6].toInt() and 0xff) shl 8 or (bytecode[7].toInt() and 0xff)
                major shouldBe expectedMajor
            }
        }
        "should distinguish kompilation arguments by parent file manager contents" {
            // These arguments are part of the compilation cache key, so two different parents must
            // not collide.
            val first = Source(mapOf("A.kt" to "val a = 1")).kompile()
            val second = Source(mapOf("B.kt" to "val b = 2")).kompile()

            KompilationArguments(parentFileManager = first.fileManager).hashCode() shouldBe
                KompilationArguments(parentFileManager = first.fileManager).hashCode()
            KompilationArguments(parentFileManager = first.fileManager).hashCode() shouldNotBe
                KompilationArguments(parentFileManager = second.fileManager).hashCode()
            KompilationArguments().hashCode() shouldNotBe
                KompilationArguments(parentFileManager = first.fileManager).hashCode()
        }
        "should nest in-memory classpath paths under their root" {
            // The compiler keys its library path filter on the root's path and then matches each
            // file by prefix, so a file that does not sit under its root is attributed to no
            // module and its Kotlin metadata is silently discarded.
            val compiledSource = Source(
                mapOf("Test.kt" to "package com.example\n\nclass Test"),
            ).kompile()
            val root = compiledSource.fileManager.toVirtualFile() as SimpleVirtualFile

            fun leaves(file: SimpleVirtualFile): List<SimpleVirtualFile> = when {
                !file.isDirectory -> listOf(file)
                else -> file.children.flatMap { leaves(it as SimpleVirtualFile) }
            }

            val found = leaves(root)
            found.map { it.path } shouldContainExactlyInAnyOrder listOf(
                "${root.path}/com/example/Test.class",
                "${root.path}/META-INF/main.kotlin_module",
            )
            found.forEach { leaf ->
                leaf.toNioPath().startsWith(root.toNioPath()) shouldBe true
                leaf.path shouldBe leaf.toNioPath().toString()
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
        "should stop at a Kotlin syntax error" {
            // Nothing stops the light tree from handing a half-parsed file to the resolver, so the
            // stop between parsing and resolution is Jeed's own and this is what guards it: the
            // unresolved reference on line 3 must never be reported alongside the syntax error.
            val failed = shouldThrow<CompilationFailed> {
                Source(
                    mapOf("Test.kt" to "fun main() {\n  val x = = 5\n  println(nonexistent)\n}"),
                ).kompile()
            }

            failed should haveCompilationErrorAt(source = "Test.kt", line = 2)
            failed.errors.map { it.location?.line }.distinct() shouldBe listOf(2)
            failed.errors.none { it.message.lowercase().contains("unresolved") } shouldBe true
        }
        "should report the location of a Kotlin warning in a source with Windows line endings" {
            // The light tree normalizes CR/LF when it maps offsets to lines, and so does the
            // reporter that turns those offsets back into positions. If the two ever disagree the
            // column drifts by the number of carriage returns before it.
            val compiledSource = Source(
                mapOf(
                    "Test.kt" to "fun main() {\n  println(\"one\")\n  val unused = 5\n}".replace("\n", "\r\n"),
                ),
            ).kompile()

            compiledSource.messages shouldHaveSize 1
            compiledSource.messages[0].location?.line shouldBe 3
            compiledSource.messages[0].location?.column shouldBe 7
        }
        "should not write to the filesystem while compiling" {
            // The property the whole in-memory pipeline exists to preserve. Warm up first: the
            // first tryCache call initializes Cache.kt, and the disk cache creates its directory
            // under java.io.tmpdir as it is constructed.
            Source(mapOf("Warm.kt" to "val warm = 1")).kompile(KompilationArguments(useCache = false))
            Source(
                mapOf("WarmJava.java" to "public class WarmJava {}", "WarmMixed.kt" to "class WarmMixed"),
            ).mompile()

            val source = Source(
                mapOf(
                    "com/example/Person.kt" to "package com.example\n\ndata class Person(val name: String)",
                    "com/example/Main.kt" to
                        "package com.example\n\nfun main() {\n  println(Person(\"test\").name)\n}",
                ),
            )
            recordingFilesystemWrites {
                source.kompile(KompilationArguments(useCache = false))
            }.shouldBeEmpty()

            recordingFilesystemWrites {
                Source(
                    mapOf(
                        "Greeter.java" to "public class Greeter { public String greet() { return \"hi\"; } }",
                        "Main.kt" to "fun main() {\n  println(Greeter().greet())\n}",
                    ),
                ).mompile()
            }.shouldBeEmpty()
        }
        "should name file facade classes from the last segment of the source name" {
            // These are the names kotlinc itself produces for these two files, on 2.4.10 and on
            // 2.4.20 alike. Jeed used to produce com.example.Com_example_UtilKt for the first,
            // because it handed the compiler a file name with a directory in it, which a file on
            // disk never has. Callers that read generated facade names -- byClass coverage keys
            // among them -- see the corrected ones, so pin them rather than rediscover them.
            Source(
                mapOf(
                    "com/example/Util.kt" to "package com.example\n\nfun helper() = 1",
                    "Top.kt" to "fun top() = 2",
                ),
            ).kompile().fileManager.classFiles.keys shouldContainExactlyInAnyOrder
                listOf("TopKt.class", "com/example/UtilKt.class")

            // The corollary: two sources sharing a last segment, with no package to separate them,
            // now collide where the mangled names used to keep them apart.
            shouldThrow<CompilationFailed> {
                Source(
                    mapOf("a/Main.kt" to "fun a() = 1", "b/Main.kt" to "fun b() = 2"),
                ).kompile()
            }.errors.first().message shouldContain "Duplicate JVM class name"
        }
        "should compile and run a type-checking when expression" {
            // Kotlin 2.4.20 would generate this as an invokedynamic to
            // java.lang.runtime.SwitchBootstraps plus a tableswitch, since the JVM target is 21.
            // Kompile.kt pins the older chain-of-type-checks shape instead, because the
            // invokedynamic form moves the whole dispatch onto the line of the when subject and
            // costs a line of student coverage feedback. Assert the shape, not just the output, so
            // that dropping the pin fails here rather than quietly in a coverage adjustment.
            // The hierarchy is open rather than sealed on purpose: a sealed class carries a
            // PermittedSubclasses attribute, which the rewriter's ASM8 visitors reject. That is a
            // separate, pre-existing limitation and has nothing to do with the type switch.
            val compiledSource = Source(
                mapOf(
                    "Main.kt" to """
open class Shape
class Circle(val radius: Int) : Shape()
class Square(val side: Int) : Shape()

fun describe(shape: Shape) = when (shape) {
  is Circle -> "circle " + shape.radius
  is Square -> "square " + shape.side
  else -> "shape"
}

fun main() {
  println(describe(Circle(2)))
  println(describe(Square(3)))
  println(describe(Shape()))
}
""".trim(),
                ),
            ).kompile()

            val bootstraps = compiledSource.fileManager.classFiles.values.flatMap { classFile ->
                ClassNode(Opcodes.ASM9).also { node ->
                    ClassReader(classFile.openInputStream().readAllBytes()).accept(node, 0)
                }.methods.flatMap { method -> method.instructions.toList() }
                    .filterIsInstance<InvokeDynamicInsnNode>()
                    .map { it.bsm.owner }
            }
            bootstraps shouldNotContain "java/lang/runtime/SwitchBootstraps"

            compiledSource.execute().also {
                it should haveCompleted()
                it should haveOutput("circle 2\nsquare 3\nshape")
            }
        }
    })

/**
 * Records every filesystem write and delete made by the calling thread while [block] runs.
 *
 * A SecurityManager is the only mechanism on JDK 21 that sees both `java.io` and NIO writes
 * synchronously, and the test JVM already runs with `-Djava.security.manager=allow`. Kotest runs
 * specs sequentially, so no sandboxed task is confined while the manager is swapped, and the
 * sandbox's own manager delegates to the manager it captured at class-initialization time for
 * threads that are not confined, so installing this one is permitted. Only the calling thread is
 * recorded, which keeps Caffeine maintenance, coroutine dispatchers and the resource agent out.
 * Each recorded entry carries a stack trace so a hit is diagnosable.
 */
private fun <T> recordingFilesystemWrites(block: () -> T): List<String> {
    // Force Sandbox's initializer to run first. It captures System.getSecurityManager() once and
    // delegates to whatever it found forever after, so it must not capture the recorder.
    check(Sandbox.systemSecurityManager !is RecordingSecurityManager)

    val previous = System.getSecurityManager()
    val recorder = RecordingSecurityManager(Thread.currentThread())
    System.setSecurityManager(recorder)
    try {
        block()
    } finally {
        System.setSecurityManager(previous)
    }
    return recorder.writes
}

private class RecordingSecurityManager(private val watched: Thread) : SecurityManager() {
    val writes = mutableListOf<String>()

    override fun checkPermission(perm: Permission) {}
    override fun checkPermission(perm: Permission, context: Any?) {}
    override fun checkWrite(file: String) = record("write $file")
    override fun checkDelete(file: String) = record("delete $file")

    private fun record(what: String) {
        if (Thread.currentThread() !== watched) {
            return
        }
        val trace = Throwable()
        // Files.isWritable only asks whether a path could be written, and routes through checkWrite
        // because that is the permission it would need. javac calls it on the ct.sym archive it
        // opens read-only for --release, so it fires on the mixed-source path. Nothing is created,
        // modified or removed by it.
        if (trace.stackTrace.any { it.className == "java.nio.file.Files" && it.methodName == "isWritable" }) {
            return
        }
        writes += "$what\n" + trace.stackTraceToString()
    }
}
