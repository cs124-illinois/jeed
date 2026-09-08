package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveOutput
import edu.illinois.cs.cs125.jeed.core.sandbox
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldContain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.CountDownLatch

class TestStartStop :
    StringSpec({
        "should start and stop properly" {
            Sandbox.start()
            Sandbox.running shouldBe true
            Sandbox.stop()
            Sandbox.running shouldBe false
        }
        "should restore the host's standard input when it stops" {
            // Capture after a stop, since another spec may have left the sandbox running.
            Sandbox.stop()
            val hostStdin = System.`in`

            Sandbox.start()
            System.`in` shouldNotBe hostStdin

            Sandbox.stop()
            System.`in` shouldBe hostStdin
        }
        "should autostart properly" {
            val executeMainResult = Source.fromSnippet(
                """
int i = 0;
i++;
System.out.println(i);
            """.trim(),
            ).compile().execute()
            executeMainResult should haveCompleted()
            executeMainResult should haveOutput("1")
            Sandbox.running shouldBe true
            Sandbox.stop()
            Sandbox.running shouldBe false
        }
        "should refuse to execute when stopped and autostart is disabled" {
            Sandbox.stop()
            Sandbox.autoStart = false
            try {
                shouldThrow<IllegalArgumentException> {
                    Sandbox.execute { }
                }.message shouldContain "autoStart not enabled"
            } finally {
                Sandbox.autoStart = true
            }
        }
        "should refuse to confine one class loader in two tasks at once" {
            val sandboxedClassLoader = Source.fromSnippet(
                """
int i = 0;
            """.trim(),
            ).compile().classLoader.sandbox(Sandbox.ClassLoaderConfiguration())

            val taskStarted = CountDownLatch(1)
            val releaseTask = CountDownLatch(1)
            coroutineScope {
                val running = async(Dispatchers.IO) {
                    Sandbox.execute(sandboxedClassLoader, Sandbox.ExecutionArguments(timeout = 10000)) {
                        taskStarted.countDown()
                        releaseTask.await()
                    }
                }
                taskStarted.await()
                shouldThrow<IllegalArgumentException> {
                    Sandbox.execute(sandboxedClassLoader, Sandbox.ExecutionArguments()) { }
                }.message shouldContain "Duplicate class loader"
                releaseTask.countDown()
                running.await() should haveCompleted()
            }
        }
    })
