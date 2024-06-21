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
import io.kotest.matchers.shouldNot

class TestTimeout : StringSpec({
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

        executionResult.cpuTime shouldBeLessThan 20 * 1000L * 1000L
    }
})
