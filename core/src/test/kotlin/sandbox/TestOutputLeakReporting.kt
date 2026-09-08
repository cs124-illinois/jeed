package edu.illinois.cs.cs125.jeed.core.sandbox

import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.execute
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.haveStdout
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.should
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import java.io.ByteArrayOutputStream
import java.io.PrintStream

/**
 * JEED_DEBUG_OUTPUT_LEAKS is what identified the ktor/jansi leak, and it is meant to stay usable for the next one.
 * It is also the one part of the sandbox that only ever runs when a human turns it on, so nothing else would notice
 * if it stopped reporting.
 */
class TestOutputLeakReporting :
    StringSpec({
        "should report writes that reach the host while it is watching for them" {
            // Reports go to the streams captured at start(), so the capture has to be installed before starting
            Sandbox.stop()
            val hostStdout = System.out
            val hostStderr = System.err
            val reports = ByteArrayOutputStream()
            val originalDebugOutputLeaks = Sandbox.debugOutputLeaks
            Sandbox.debugOutputLeaks = "leakedFromTheHost"
            System.setOut(PrintStream(reports, true))
            System.setErr(PrintStream(reports, true))
            try {
                Sandbox.start()
                // Not a confined thread, so this write is routed to the host and matches the filter. A plain
                // thread rather than this one keeps the reported stack, which is written to the real stderr
                // by design, down to a few frames.
                Thread { System.out.println("leakedFromTheHost") }.apply { start() }.join()
            } finally {
                Sandbox.stop()
                Sandbox.debugOutputLeaks = originalDebugOutputLeaks
                System.setOut(hostStdout)
                System.setErr(hostStderr)
            }

            val reported = reports.toString()
            // The banner, so that which build is running is unambiguous
            reported shouldContain "JEED_DEBUG_OUTPUT_LEAKS=leakedFromTheHost: sandbox starting in pid"
            reported shouldContain "Sandbox loaded by"
            // The report itself, tagged with each route it was written by
            reported shouldContain "[host stdout] JEED_DEBUG_OUTPUT_LEAKS: stdout write \"leakedFromTheHost\" reached"
            reported shouldContain "[host stderr] JEED_DEBUG_OUTPUT_LEAKS: stdout write \"leakedFromTheHost\" reached"
            reported shouldContain "reached the host from thread"

            // Off again, and tasks still run
            Source.fromSnippet("""System.out.println("Here");""").compile().execute() should haveStdout("Here")
        }
        "should stay quiet when it is not watching" {
            Sandbox.stop()
            val hostStdout = System.out
            val quiet = ByteArrayOutputStream()
            System.setOut(PrintStream(quiet, true))
            try {
                Sandbox.start()
                System.out.println("leakedFromTheHost")
            } finally {
                Sandbox.stop()
                System.setOut(hostStdout)
            }
            quiet.toString() shouldContain "leakedFromTheHost"
            quiet.toString().lines().none { it.contains("JEED_DEBUG_OUTPUT_LEAKS") } shouldBe true
        }
    })
