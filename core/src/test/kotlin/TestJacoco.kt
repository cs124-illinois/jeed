package edu.illinois.cs.cs125.jeed.core

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.Matcher
import io.kotest.matchers.MatcherResult
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.collections.shouldHaveAtLeastSize
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import java.io.IOException
import java.lang.IllegalStateException

class TestJacoco :
    StringSpec({
        "it should calculate full coverage properly" {
            Source.fromJava(
                """
public class Test {
  private int value;
  public Test() {
    value = 10;
  }
  public Test(int setValue) {
    value = setValue;
  }
}
public class Main {
  public static void main() {
    Test test = new Test(10);
    Test test2 = new Test();
    System.out.println("Yay");
  }
}""",
            ).coverage().also { coverageMap ->
                coverageMap should haveFileMissedCount(1)
                coverageMap should haveClassMissedCount(0, klass = "Test")
                coverageMap should haveClassCoveredCount(6, klass = "Test")
            }
        }
        "it should detect uncovered code" {
            Source.fromJava(
                """
public class Test {
  private int value;
  public Test() {
    value = 10;
  }
  public Test(int setValue) {
    value = setValue;
  }
}
public class Main {
  public static void main() {
    Test test = new Test(10);
    System.out.println("Hmm");
  }
}""",
            ).coverage().also { coverageMap ->
                coverageMap should haveFileMissedCount(4)
                coverageMap should haveClassMissedCount(3, klass = "Test")
                coverageMap should haveClassCoveredCount(3, klass = "Test")
            }
        }
        "it should allow class enumeration in the sandbox" {
            val source = Source.fromJava(
                """
public class Main {
  public static void main() {
    Main main = new Main();
    System.out.println(main.getClass().getDeclaredMethods().length);
  }
}""",
            ).compile()
            source.execute().also {
                it should haveCompleted()
                it should haveOutput("1")
            }
            source.execute(SourceExecutionArguments().addPlugin(Jacoco)).also {
                it should haveCompleted()
                it should haveOutput("2")
            }
        }
        "it should not allow hijacking jacocoInit" {
            val source = Source.fromJava(
                """
import java.lang.invoke.MethodHandles;
public class Main {
  private static void ${"$"}jacocoInit(MethodHandles.Lookup lookup, String name, Class type) {
    // do something with the lookup?
  }
  public static void main() { }
}""",
            ).compile()
            shouldThrow<IOException> {
                // Jacoco refuses to re-instrument, which is good
                source.execute(SourceExecutionArguments().addPlugin(Jacoco))
            }
        }
        "should combine line tracing and branch tracing for a main method" {
            val result = Source.fromJava(
                """
public class Main {
  public static void main() {
    int i = 4;
    i += 1;
    System.out.println(i);
  }
}""",
            ).compile().execute(SourceExecutionArguments().addPlugin(Jacoco).addPlugin(LineTrace))
            result should haveCompleted()
            result should haveOutput("5")

            val trace = result.pluginResult(LineTrace)
            trace.steps shouldHaveAtLeastSize 3
            trace.steps[0] shouldBe LineTraceResult.LineStep("Main.java", 3, 0)

            val testCoverage = result.pluginResult(Jacoco).classes.find { it.name == "Main" }!!
            testCoverage.lineCounter.missedCount shouldBe 1
            testCoverage.lineCounter.coveredCount shouldBe 4
        }
        "should combine line tracing and branch tracing for a Kotlin when statement" {
            val compiledSource = Source(
                mapOf(
                    "Main.kt" to """
class PingPonger(setState: String) {
  private var state = when (setState) {
    "ping" -> true
    "pong" -> false
    else -> throw IllegalArgumentException()
  }
  fun ping(): Boolean {
    state = true
    return state
  }
  fun pong(): Boolean {
    state = false
    return state
  }
}
fun main() {
  val pingPonger = PingPonger("ping")
  pingPonger.pong()
  pingPonger.ping()

  val pongPonger = PingPonger("pong")
  pongPonger.ping()
  pongPonger.pong()

  try {
    val pongPonger = PingPonger("barg")
  } catch (e: Exception) {}
}""".trim(),
                ),
            ).kompile()

            compiledSource.execute(SourceExecutionArguments().addPlugin(Jacoco)).let { results ->
                results should haveCompleted()
                results.pluginResult(Jacoco).classes.find { it.name == "PingPonger" }!!.allMissedLines() should beEmpty()
            }
            // LineTrace after works
            compiledSource.execute(SourceExecutionArguments().addPlugin(Jacoco).addPlugin(LineTrace)).let { results ->
                results should haveCompleted()
                results.pluginResult(Jacoco).classes.find { it.name == "PingPonger" }!!.allMissedLines() should beEmpty()
            }
            // LineTrace before doesn't (because LineTrace interferes with Jacoco's avoidance of hash-collision branches)
            shouldThrow<IllegalStateException> {
                compiledSource.execute(SourceExecutionArguments().addPlugin(LineTrace).addPlugin(Jacoco))
            }
        }
        "should miss assert" {
            Source.fromJava(
                """
public class Test {
  public void test() {
    assert System.currentTimeMillis() != 0L;
  }
}
public class Main {
  public static void main() {
    Test test = new Test();
    test.test();
  }
}""",
            ).coverage().also { testCoverage ->
                testCoverage should haveFileCoverageAt(3, LineCoverage.PARTLY_COVERED)
            }
        }
        "checking file arguments" {
            val source = Source.fromJava(
                """
public class Test {
  public void test() {
    assert System.currentTimeMillis() != 0L;
  }
}
public class Main {
  public static void main() {
    Test test = new Test();
    test.test();
  }
}""",
            )
            source.coverage().also { coverageResult ->
                coverageResult should haveFileCoverageAt(3, LineCoverage.PARTLY_COVERED)
            }
        }
        "remap snippet lines correctly" {
            Source.fromSnippet("""assert System.currentTimeMillis() != 0L;""").coverage().also { coverageResult ->
                coverageResult should haveFileCoverageAt(1, LineCoverage.PARTLY_COVERED)
            }
        }
        "should remap template lines correctly with a single source" {
            Source.fromTemplates(
                mapOf(
                    "Main.java" to """
public static void main() {
  System.out.println("Here");
}""".trim(),
                ),
                mapOf(
                    "Main.java.hbs" to "public class Main { {{{ contents }}} }",
                ),
            ).coverage().also { coverageResult ->
                coverageResult should haveFileCoverageAt(2, LineCoverage.COVERED)
            }
        }
        "should fail template remapping with multiple sources" {
            val source = Source.fromTemplates(
                mapOf(
                    "Main.java" to """
public static void main() {
  System.out.println("Here");
}""".trim(),
                    "Test.java" to """
public class Test {}
                    """.trimIndent(),
                ),
                mapOf(
                    "Main.java.hbs" to "public class Main { {{{ contents }}} }",
                ),
            )
            source.compile().execute(SourceExecutionArguments().addPlugin(Jacoco)).also { taskResults ->
                taskResults.completed shouldBe true
                taskResults.permissionDenied shouldBe false
            }.let { taskResult ->
                shouldThrow<IllegalStateException> {
                    source.processCoverage(taskResult.pluginResult(Jacoco))
                }
            }
        }
    })

suspend fun Source.coverage(): CoverageResult = when (type) {
    Source.SourceType.JAVA -> compile()
    Source.SourceType.KOTLIN -> kompile()
    else -> error("Can't compute coverage for mixed sources")
}.execute(SourceExecutionArguments().addPlugin(Jacoco)).also { taskResults ->
    taskResults.completed shouldBe true
    taskResults.permissionDenied shouldBe false
}.let { taskResult -> processCoverage(taskResult.pluginResult(Jacoco)) }

fun haveFileCoverageAt(line: Int, expectedCoverage: LineCoverage, filename: String? = null) = object : Matcher<CoverageResult> {
    override fun test(value: CoverageResult): MatcherResult {
        val toRetrieve = filename ?: value.byFile.keys.let {
            check(it.size == 1) { "Must specify a key to retrieve multi-file coverage result" }
            it.first()
        }
        val actualCoverage = value.byFile[toRetrieve]!![line]!!

        return MatcherResult(
            expectedCoverage == actualCoverage,
            { "Expected coverage $expectedCoverage at line $line but found $actualCoverage" },
            { "Expected coverage $expectedCoverage at line $line but found $actualCoverage" },
        )
    }
}

fun haveFileMissedCount(expectedCount: Int, filename: String? = null) = object : Matcher<CoverageResult> {
    override fun test(value: CoverageResult): MatcherResult {
        val toRetrieve = filename ?: value.byFile.keys.let {
            check(it.size == 1) { "Must specify a key to retrieve multi-file coverage result: ${it.joinToString(",")}" }
            it.first()
        }
        val actualCount =
            value.byFile[toRetrieve]!!.values.count { it == LineCoverage.PARTLY_COVERED || it == LineCoverage.NOT_COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}

fun haveMissedCount(expectedCount: Int) = object : Matcher<FileCoverage> {
    override fun test(value: FileCoverage): MatcherResult {
        val actualCount =
            value.values.count { it == LineCoverage.PARTLY_COVERED || it == LineCoverage.NOT_COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}

fun haveFileCoveredCount(expectedCount: Int, filename: String? = null) = object : Matcher<CoverageResult> {
    override fun test(value: CoverageResult): MatcherResult {
        val toRetrieve = filename ?: value.byFile.keys.let {
            check(it.size == 1) { "Must specify a key to retrieve multi-file coverage result" }
            it.first()
        }
        val actualCount = value.byFile[toRetrieve]!!.values.count { it == LineCoverage.COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}

fun haveCoveredCount(expectedCount: Int) = object : Matcher<FileCoverage> {
    override fun test(value: FileCoverage): MatcherResult {
        val actualCount = value.values.count { it == LineCoverage.COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}

fun haveClassMissedCount(expectedCount: Int, klass: String? = null) = object : Matcher<CoverageResult> {
    override fun test(value: CoverageResult): MatcherResult {
        val toRetrieve = klass ?: value.byClass.keys.let {
            check(it.size == 1) { "Must specify a key to retrieve multi-file coverage result" }
            it.first()
        }
        val actualCount =
            value.byClass[toRetrieve]!!.values.count { it == LineCoverage.PARTLY_COVERED || it == LineCoverage.NOT_COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}

fun haveClassCoveredCount(expectedCount: Int, klass: String? = null) = object : Matcher<CoverageResult> {
    override fun test(value: CoverageResult): MatcherResult {
        val toRetrieve = klass ?: value.byClass.keys.let {
            check(it.size == 1) { "Must specify a key to retrieve multi-file coverage result" }
            it.first()
        }
        val actualCount = value.byClass[toRetrieve]!!.values.count { it == LineCoverage.COVERED }

        return MatcherResult(
            expectedCount == actualCount,
            { "Expected $expectedCount missed lines but found $actualCount" },
            { "Expected $expectedCount missed lines but found $actualCount" },
        )
    }
}
