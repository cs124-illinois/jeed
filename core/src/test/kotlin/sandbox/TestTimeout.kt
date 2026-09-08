package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.findClassMethod
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveCompleted
import edu.illinois.cs.cs125.jeed.core.haveCpuTimedOut
import edu.illinois.cs.cs125.jeed.core.haveTimedOut
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.longs.shouldBeLessThan
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.types.beInstanceOf

class TestTimeout :
    StringSpec({
        "should timeout runaway task properly" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; ; i++);
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(
                compiledSource.classLoader,
                Sandbox.ExecutionArguments(timeout = 100, pollIntervalMS = 1),
            ) { (classLoader, _, sandboxControl) ->
                sandboxControl.setTimeoutMS(10)
                classLoader.findClassMethod().invoke(null)
            }
            executionResult shouldNot haveCompleted()
            executionResult should haveTimedOut()
            executionResult.executionInterval.length shouldBeLessThan 20
        }
        "should timeout runaway task properly with CPU timeout" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; ; i++);
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(
                compiledSource.classLoader,
                Sandbox.ExecutionArguments(timeout = 100, pollIntervalMS = 1),
            ) { (classLoader, _, sandboxControl) ->
                sandboxControl.setCPUTimeoutNS(10 * 1000L * 1000L)
                classLoader.findClassMethod().invoke(null)
            }
            executionResult shouldNot haveCompleted()
            executionResult should haveTimedOut()
            executionResult should haveCpuTimedOut()

            executionResult.cpuTime shouldBeLessThan 24 * 1000L * 1000L
        }
        "should let a task clear its own wall clock timeout" {
            val executionResult = Sandbox.execute(
                executionArguments = Sandbox.ExecutionArguments(timeout = 100, pollIntervalMS = 1),
            ) { (_, _, sandboxControl) ->
                sandboxControl.setTimeoutMS(10)
                sandboxControl.clearTimeout()
                Thread.sleep(50)
                "done"
            }
            executionResult should haveCompleted()
            executionResult.returned shouldBe "done"
        }
        "should let a task clear its own CPU timeout" {
            val executionResult = Sandbox.execute(
                executionArguments = Sandbox.ExecutionArguments(
                    timeout = 2000,
                    cpuTimeoutNS = 10 * 1000L * 1000L,
                    pollIntervalMS = 1,
                ),
            ) { (_, _, sandboxControl) ->
                sandboxControl.clearCPUTimeout()
                burnCPU(100)
                "done"
            }
            executionResult should haveCompleted()
            executionResult shouldNot haveCpuTimedOut()
            executionResult.returned shouldBe "done"
        }
        "should let a task set both of its timeouts at once" {
            val compiledSource = Source.fromSnippet(
                """
for (int i = 0; ; i++);
            """.trim(),
            ).compile()
            val executionResult = Sandbox.execute(
                compiledSource.classLoader,
                Sandbox.ExecutionArguments(timeout = 4000, pollIntervalMS = 1),
            ) { (classLoader, _, sandboxControl) ->
                sandboxControl.setTimeouts(20, 1000L * 1000L * 1000L)
                classLoader.findClassMethod().invoke(null)
            }
            executionResult shouldNot haveCompleted()
            executionResult should haveTimedOut()
            executionResult.executionInterval.length shouldBeLessThan 1000
        }
        "should let a task clear both of its timeouts at once" {
            val executionResult = Sandbox.execute(
                executionArguments = Sandbox.ExecutionArguments(
                    timeout = 50,
                    cpuTimeoutNS = 5 * 1000L * 1000L,
                    pollIntervalMS = 1,
                ),
            ) { (_, _, sandboxControl) ->
                sandboxControl.clearTimeouts()
                burnCPU(100)
                "done"
            }
            executionResult should haveCompleted()
            executionResult shouldNot haveTimedOut()
            executionResult shouldNot haveCpuTimedOut()
            executionResult.returned shouldBe "done"
        }
        "should refuse to change a timeout without a poll interval" {
            val executionResult = Sandbox.execute(
                executionArguments = Sandbox.ExecutionArguments(pollIntervalMS = 0),
            ) { (_, _, sandboxControl) ->
                sandboxControl.setTimeoutMS(10)
            }
            executionResult shouldNot haveCompleted()
            executionResult.threw should beInstanceOf<IllegalStateException>()
        }
    })

/** Spins for [durationMS] of this thread's own time, so that a CPU timeout has something to measure. */
private fun burnCPU(durationMS: Long) {
    val end = System.nanoTime() + durationMS * 1000L * 1000L
    @Suppress("ControlFlowWithEmptyBody")
    while (System.nanoTime() < end) {
    }
}
