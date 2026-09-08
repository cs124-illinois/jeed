package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.JEED_LOGGER_NAME
import edu.illinois.cs.cs125.jeed.core.JeedOutputCapture
import edu.illinois.cs.cs125.jeed.core.OutputHardLimitExceeded
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.SnippetArguments
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceExecutionArguments
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.findClassMethod
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveOutput
import edu.illinois.cs.cs125.jeed.core.haveStderr
import edu.illinois.cs.cs125.jeed.core.haveStdout
import edu.illinois.cs.cs125.jeed.core.haveTimedOut
import edu.illinois.cs.cs125.jeed.core.kompile
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.assertions.withClue
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.collections.shouldNotContain
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.ints.shouldBeLessThanOrEqual
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.kotest.matchers.types.beInstanceOf
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import org.fusesource.jansi.AnsiPrintStreamStub
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

class TestOutputCapture :
    StringSpec({
        "should capture stdout" {
            val executionResult = Source.fromSnippet(
                """
System.out.println("Here");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Here")
            executionResult should haveStderr("")
        }
        "should capture stderr" {
            val executionResult = Source.fromSnippet(
                """
System.err.println("Here");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("")
            executionResult should haveStderr("Here")
        }
        "should capture stderr and stdout" {
            val executionResult = Source.fromSnippet(
                """
System.out.println("Here");
System.err.println("There");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Here")
            executionResult should haveStderr("There")
            executionResult should haveOutput("Here\nThere")
        }
        "should capture incomplete stderr and stdout lines" {
            val executionResult = Source.fromSnippet(
                """
System.out.print("Here");
System.err.print("There");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Here")
            executionResult should haveStderr("There")
            executionResult should haveOutput("Here\nThere")
        }
        "should not intermingle unrelated thread output" {
            // Install the combined stream underneath the sandbox rather than over it, so that the sandbox adopts it
            // as the host stream and unrelated output reaches it through the redirect.
            Sandbox.stop()
            val combinedOutputStream = ByteArrayOutputStream()
            val combinedPrintStream = PrintStream(combinedOutputStream)
            val originalStdout = System.out
            val originalStderr = System.err
            System.setOut(combinedPrintStream)
            System.setErr(combinedPrintStream)

            (0..8).toList().map {
                if (it % 2 == 0) {
                    async {
                        Source.fromSnippet(
                            """
for (int i = 0; i < 32; i++) {
    for (long j = 0; j < 1024 * 1024 * 1024; j++);
}
                        """.trim(),
                        ).compile().execute(SourceExecutionArguments(timeout = 1000L))
                    }
                } else {
                    async {
                        repeat(512) {
                            println("Bad")
                            System.err.println("Bad")
                            delay(1L)
                        }
                    }
                }
            }.map { it.await() }.filterIsInstance<Sandbox.TaskResults<out Any?>>().forEach { executionResult ->
                executionResult should haveTimedOut()
                executionResult.outputLines.map { it.line } shouldNotContain "Bad"
                executionResult.stderrLines.map { it.line } shouldNotContain "Bad"
            }
            Sandbox.stop()
            System.setOut(originalStdout)
            System.setErr(originalStderr)

            val unrelatedOutput = combinedOutputStream.toString()
            unrelatedOutput.lines().filter { it == "Bad" }.size shouldBe (4 * 2 * 512)
        }
        "should redirect output to trusted task properly" {
            val compiledSource = Source.fromSnippet(
                """
System.out.println("Here");
System.out.println("There");
System.err.println("There");
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(compiledSource.classLoader) { (classLoader, redirectOutput) ->
                redirectOutput {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout == "Here\nThere\n")
                    assert(it.stderr == "There\n")
                }
            }
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Here\nThere")
            executionResult should haveStderr("There")
        }
        "should limit redirected output to trusted task properly" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; i < 1024; i++) {
  System.out.println("Here");
  System.err.println("There");
}
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(compiledSource.classLoader) { (classLoader) ->
                Sandbox.redirectOutput(redirectingOutputLimit = 32) {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout.trim().lines().size == 16)
                    assert(it.truncatedLines > 0)
                    assert(it.stderr.trim().lines().size == 16)
                }
            }
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult.outputLines.size shouldBeGreaterThan 0
        }
        "should squash normal output when requested" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; i < 128; i++) {
  System.out.println(i + " Here");
  System.err.println(i + " There");
}
            """.trim(),
            ).compile()
            val squashedResult = Sandbox.execute(compiledSource.classLoader) { (classLoader) ->
                Sandbox.redirectOutput(squashNormalOutput = true) {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout.trim().lines().size == 128)
                    assert(it.truncatedLines == 0)
                    assert(it.stderr.trim().lines().size == 128)
                }
            }
            squashedResult should haveCompleted()
            squashedResult shouldNot haveTimedOut()
            squashedResult.outputLines shouldHaveSize 0
            squashedResult.stderrLines shouldHaveSize 0

            val unsquashedResult = Sandbox.execute(compiledSource.classLoader) { (classLoader) ->
                Sandbox.redirectOutput {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout.trim().lines().size == 128)
                    assert(it.truncatedLines == 0)
                    assert(it.stderr.trim().lines().size == 128)
                }
            }
            unsquashedResult should haveCompleted()
            unsquashedResult shouldNot haveTimedOut()
            unsquashedResult.truncatedLines shouldBe 0
            unsquashedResult.stdoutLines shouldHaveSize 128
            unsquashedResult.stderrLines shouldHaveSize 128
        }
        "should redirect output to trusted task properly with print" {
            val compiledSource = Source.fromSnippet(
                """
System.out.println("Here");
System.out.print("There");
System.err.print("There");
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(compiledSource.classLoader) { (classLoader, redirectOutput) ->
                redirectOutput {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout == "Here\nThere")
                    assert(it.stderr == "There")
                }
                redirectOutput {
                    classLoader.findClassMethod().invoke(null)
                }.also {
                    assert(it.stdout == "Here\nThere")
                    assert(it.stderr == "There")
                }
            }
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Here\nThereHere\nThere")
            executionResult should haveStderr("ThereThere")
        }
        "should hard limit output when requested" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; i < 1024; i++) {
  System.out.println("Here");
}
            """.trim(),
            ).compile()

            compiledSource.execute().also { executionResult ->
                executionResult should haveCompleted()
                executionResult shouldNot haveTimedOut()
            }
            Sandbox.execute(compiledSource.classLoader) { (classLoader) ->
                Sandbox.hardLimitOutput(1024) {
                    classLoader.findClassMethod().invoke(null)
                }
            }.also { executionResult ->
                executionResult shouldNot haveCompleted()
                executionResult.threw?.cause should beInstanceOf<OutputHardLimitExceeded>()
            }
        }
        "should handle null print arguments" {
            val executionResult = Source.fromSnippet(
                """
int[] output = null;
System.out.println(output);
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("null")
            executionResult should haveStderr("")
        }
        "should handle print without newline" {
            val executionResult = Source.fromSnippet(
                """
System.out.print("Hello");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult should haveStdout("Hello")
        }
        "should handle print with multiple newlines" {
            val executionResult = Source.fromSnippet(
                """
System.out.println("\nHello\n");
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult.stdout shouldBe "\nHello\n"
        }
        "should warn once when System.out is replaced after the sandbox starts" {
            // Make sure the sandbox is running with the current streams, then wrap System.out the way a host that
            // captures its own output might. The wrapper forwards to the sandbox's stream, so capture keeps working,
            // but every sandboxed print now also passes through the wrapper.
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            val warnings = mutableListOf<String>()
            val handler = object : Handler() {
                override fun publish(record: LogRecord) {
                    if (record.level.intValue() >= Level.WARNING.intValue()) {
                        warnings.add(record.message)
                    }
                }

                override fun flush() {}
                override fun close() {}
            }
            val jeedLogger = Logger.getLogger(JEED_LOGGER_NAME).also { it.addHandler(handler) }
            val originalStdout = System.out
            val originalFailOnReplacedStreams = Sandbox.failOnReplacedStreams
            Sandbox.failOnReplacedStreams = false
            System.setOut(PrintStream(originalStdout, true))
            try {
                repeat(2) {
                    Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
                }
            } finally {
                System.setOut(originalStdout)
                Sandbox.failOnReplacedStreams = originalFailOnReplacedStreams
                jeedLogger.removeHandler(handler)
            }
            warnings.filter { it.contains("System.out is now java.io.PrintStream") } shouldHaveSize 1
        }
        "should fail when System.out is replaced by a stream that does not forward to the sandbox" {
            // What jansi's AnsiConsole.systemInstall does: the new stream writes elsewhere, so nothing sandboxed
            // code prints can be captured. That fails regardless of failOnReplacedStreams.
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            val originalStdout = System.out
            val originalFailOnReplacedStreams = Sandbox.failOnReplacedStreams
            Sandbox.failOnReplacedStreams = false
            val elsewhere = ByteArrayOutputStream()
            System.setOut(PrintStream(elsewhere, true))
            try {
                shouldThrow<Sandbox.SandboxOutputStreamsReplaced> {
                    Source.fromSnippet("""System.out.println("Here");""").compile().execute()
                }.message shouldContain "does not forward"
            } finally {
                System.setOut(originalStdout)
                Sandbox.failOnReplacedStreams = originalFailOnReplacedStreams
            }
            elsewhere.toString() shouldNotContain "Here"
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
        }
        "should fail when System.out is replaced after the sandbox starts and asked to" {
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            val originalStdout = System.out
            val originalFailOnReplacedStreams = Sandbox.failOnReplacedStreams
            Sandbox.failOnReplacedStreams = true
            System.setOut(PrintStream(originalStdout, true))
            try {
                shouldThrow<Sandbox.SandboxOutputStreamsReplaced> {
                    Source.fromSnippet("""System.out.println("Here");""").compile().execute()
                }
            } finally {
                System.setOut(originalStdout)
                Sandbox.failOnReplacedStreams = originalFailOnReplacedStreams
            }
            // Restored, so tasks run again
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
        }
        "should never leak sandboxed output to the real stdout or stderr" {
            // Whatever the sandbox fails to capture lands on the streams System.out and System.err held when
            // the sandbox started. Install capturing streams underneath it and check that nothing printed by
            // sandboxed code ever reaches them, across the ways code can end up printing.
            Sandbox.stop()
            val hostStdout = System.out
            val hostStderr = System.err
            val leaked = ByteArrayOutputStream()
            PrintStream(leaked, true).also {
                System.setOut(it)
                System.setErr(it)
            }
            try {
                fun arguments() = SourceExecutionArguments(timeout = 5000L, maxExtraThreads = 16, waitForShutdown = true)
                fun assertNothingLeaked(scenario: String) = withClue(scenario) {
                    leaked.toString() shouldNotContain "Hello, world!"
                }

                mapOf(
                    "stdout" to """System.out.println("Hello, world!");""",
                    "stderr" to """System.err.println("Hello, world!");""",
                    "student thread" to """
Thread t = new Thread(() -> System.out.println("Hello, world!"));
t.start();
t.join();""",
                    "static initializer" to """
class Noisy {
  static { System.out.println("Hello, world!"); }
  static void touch() { }
}
Noisy.touch();""",
                ).forEach { (scenario, snippet) ->
                    val result = Source.fromSnippet(snippet.trim()).compile().execute(arguments())
                    withClue(scenario) { result.output shouldContain "Hello, world!" }
                    assertNothingLeaked(scenario)
                }

                // The common pool's workers live outside any confined thread group. Create some here, as a
                // host process would have, before sandboxed code tries to use them.
                (0 until 2048).toList().parallelStream().forEach { Thread.sleep(0, 1000) }
                Source.fromSnippet(
                    """
import java.util.stream.IntStream;
IntStream.range(0, 8).parallel().forEach(i -> System.out.println("Hello, world!"));
                    """.trim(),
                ).compile().execute(arguments())
                assertNothingLeaked("parallel stream")

                mapOf(
                    "kotlin" to """println("Hello, world!")""",
                    "kotlin coroutine" to """
import kotlinx.coroutines.*
GlobalScope.launch {
  delay(1)
  println("Hello, world!")
}""",
                ).forEach { (scenario, snippet) ->
                    val result = Source.fromSnippet(snippet.trim(), SnippetArguments(fileType = Source.FileType.KOTLIN))
                        .kompile()
                        .execute(arguments())
                    withClue(scenario) { result.output shouldContain "Hello, world!" }
                    assertNothingLeaked(scenario)
                }

                val echo = Source.fromSnippet(
                    """
import java.util.Scanner;
Scanner scanner = new Scanner(System.in);
System.out.println(scanner.nextLine());
                    """.trim(),
                ).compile()
                echo.execute(
                    SourceExecutionArguments(
                        timeout = 5000L,
                        systemInStream = ByteArrayInputStream("Hello, world!\n".toByteArray()),
                    ),
                ).output shouldContain "Hello, world!"
                assertNothingLeaked("stdin echo")

                val hello = Source.fromSnippet("""System.out.println("Hello, world!");""").compile()
                Sandbox.execute(hello.classLoader, arguments()) { (classLoader) ->
                    Sandbox.redirectOutput(redirectingOutputLimit = 100, squashNormalOutput = true) {
                        classLoader.findClassMethod().invoke(null)
                    }.stdout shouldContain "Hello, world!"
                }
                assertNothingLeaked("redirected to trusted task")

                coroutineScope {
                    (0 until 8).map { async { hello.execute(arguments()) } }.awaitAll()
                }.forEach { it.output shouldContain "Hello, world!" }
                assertNothingLeaked("concurrent tasks")

                Source.fromSnippet(
                    """
while (true) {
  try { System.out.println("Hello, world!"); } catch (Throwable t) { }
}
                    """.trim(),
                ).compile().execute(SourceExecutionArguments(timeout = 200L)) should haveTimedOut()
                assertNothingLeaked("killed while printing")
            } finally {
                Sandbox.stop()
                System.setOut(hostStdout)
                System.setErr(hostStderr)
            }
        }
        "should refuse to nest calls to redirectOutput" {
            val executionResult = Sandbox.execute { (_, redirectOutput) ->
                redirectOutput {
                    redirectOutput { }
                }
            }
            // The outer redirect records what the block threw rather than letting it escape the task
            executionResult should haveCompleted()
            val capture = executionResult.returned as JeedOutputCapture
            capture.threw should beInstanceOf<IllegalStateException>()
            capture.threw?.message shouldContain "can't nest calls to redirectOutput"
        }
        "should refuse to nest calls to hardLimitOutput" {
            val executionResult = Sandbox.execute {
                Sandbox.hardLimitOutput(1024) {
                    Sandbox.hardLimitOutput(1024) { }
                }
            }
            executionResult shouldNot haveCompleted()
            executionResult.threw should beInstanceOf<IllegalStateException>()
            executionResult.threw?.message shouldContain "can't nest calls to hardLimitOutput"
        }
        "should limit the combined input and output it records" {
            val maxIOBytes = 64
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; i < 128; i++) {
  System.out.println("0123456789");
}
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(
                compiledSource.classLoader,
                Sandbox.ExecutionArguments(maxIOBytes = maxIOBytes),
            ) { (classLoader) ->
                classLoader.findClassMethod().invoke(null)
            }
            executionResult should haveCompleted()
            // The line-based capture is limited separately, by maxOutputLines, so it keeps everything here
            executionResult.output.length shouldBeGreaterThan maxIOBytes
            executionResult.combinedInputOutput.length shouldBeLessThanOrEqual maxIOBytes + 1
        }
        "should capture every way a task can print to a stream" {
            // RedirectingPrintStream has to override the whole PrintStream surface: anything it leaves out is
            // inherited from PrintStream itself and writes to the null stream it was constructed with, silently
            // losing that output. Exercise every override from sandboxed code and check nothing goes missing.
            val executionResult = Source.fromSnippet(
                """
System.out.print(true);
System.out.print('c');
System.out.print(new char[] {'d', 'e'});
System.out.print(1.5d);
System.out.print(2.5f);
System.out.print(3);
System.out.print(4L);
System.out.print((Object) "obj");
System.out.print("str");
System.out.println();
System.out.println(true);
System.out.println('c');
System.out.println(new char[] {'f', 'g'});
System.out.println(1.5d);
System.out.println(2.5f);
System.out.println(5);
System.out.println(6L);
System.out.println((Object) "obj2");
System.out.println("str2");
System.out.append('h');
System.out.append("seq");
System.out.append("subseq", 0, 3);
System.out.format("%d", 7);
System.out.format(java.util.Locale.US, "%d", 8);
System.out.printf("%d", 9);
System.out.printf(java.util.Locale.US, "%d", 10);
System.out.write(65);
try {
  System.out.write(new byte[] {66});
} catch (java.io.IOException e) { }
System.out.flush();
System.out.println();
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult.stdout shouldBe listOf(
                "truecde1.52.534objstr",
                "true",
                "c",
                "fg",
                "1.5",
                "2.5",
                "5",
                "6",
                "obj2",
                "str2",
                "hseqsub78910AB",
            ).joinToString("\n")
        }
        "should let a task close its own stream" {
            // close() reaches only the task's own PrintStream, so it cannot affect anything else
            val executionResult = Source.fromSnippet(
                """
System.out.println("Here");
System.out.close();
            """.trim(),
            ).compile().execute()
            executionResult should haveCompleted()
            executionResult should haveStdout("Here")
            Source.fromSnippet("""System.out.println("There");""").compile().execute() should haveStdout("There")
        }
        "should warn once when System.err is replaced after the sandbox starts" {
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            val warnings = mutableListOf<String>()
            val handler = warningCollector(warnings)
            val jeedLogger = Logger.getLogger(JEED_LOGGER_NAME).also { it.addHandler(handler) }
            val originalStderr = System.err
            val originalFailOnReplacedStreams = Sandbox.failOnReplacedStreams
            Sandbox.failOnReplacedStreams = false
            // A distinct class, so that this replacement is not deduplicated against another test's
            System.setErr(ForwardingPrintStream(originalStderr))
            try {
                repeat(2) {
                    Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
                }
            } finally {
                System.setErr(originalStderr)
                Sandbox.failOnReplacedStreams = originalFailOnReplacedStreams
                jeedLogger.removeHandler(handler)
            }
            warnings.filter {
                it.contains("System.err is now ${ForwardingPrintStream::class.java.name}")
            } shouldHaveSize 1
        }
        "should report both streams when both are replaced after the sandbox starts" {
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            val warnings = mutableListOf<String>()
            val handler = warningCollector(warnings)
            val jeedLogger = Logger.getLogger(JEED_LOGGER_NAME).also { it.addHandler(handler) }
            val originalStdout = System.out
            val originalStderr = System.err
            val originalFailOnReplacedStreams = Sandbox.failOnReplacedStreams
            Sandbox.failOnReplacedStreams = false
            System.setOut(ForwardingPrintStream(originalStdout))
            System.setErr(ForwardingPrintStream(originalStderr))
            try {
                Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
            } finally {
                System.setOut(originalStdout)
                System.setErr(originalStderr)
                Sandbox.failOnReplacedStreams = originalFailOnReplacedStreams
                jeedLogger.removeHandler(handler)
            }
            warnings.filter {
                it.contains("System.out is now") && it.contains("and System.err is now")
            } shouldHaveSize 1
        }
        "should warn when it starts with a jansi stream already installed" {
            // What ktor 3.5's CallLogging plugin leaves behind: jansi's streams write to the file descriptor
            // rather than to the stream they replaced, so output the sandbox routes to the host bypasses
            // anything capturing System.out.
            Sandbox.stop()
            val warnings = mutableListOf<String>()
            val handler = warningCollector(warnings)
            val jeedLogger = Logger.getLogger(JEED_LOGGER_NAME).also { it.addHandler(handler) }
            val hostStdout = System.out
            System.setOut(AnsiPrintStreamStub(hostStdout))
            try {
                Sandbox.start()
            } finally {
                Sandbox.stop()
                System.setOut(hostStdout)
                jeedLogger.removeHandler(handler)
            }
            warnings.filter {
                it.contains(AnsiPrintStreamStub::class.java.name) && it.contains("file descriptor")
            } shouldHaveSize 1
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
        }
    })

/** A forwarding wrapper of a kind the sandbox has not seen from another test, so its warning is not deduplicated. */
private class ForwardingPrintStream(target: PrintStream) : PrintStream(target, true)

private fun warningCollector(into: MutableList<String>) = object : Handler() {
    override fun publish(record: LogRecord) {
        if (record.level.intValue() >= Level.WARNING.intValue()) {
            into.add(record.message)
        }
    }

    override fun flush() {}
    override fun close() {}
}
