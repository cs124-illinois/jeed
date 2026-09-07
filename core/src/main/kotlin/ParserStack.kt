package edu.illinois.cs.cs125.jeed.core

import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/*
 * ANTLR's recursive-descent parsers recurse several times per level of nesting in the source, so how deeply nested a
 * program can be before the parser overflows depends on the thread stack size the host chose and, because interpreted
 * and C1-compiled frames are larger than C2 frames, on what the JIT has done so far. The server runs with -Xss256k,
 * where a few dozen chained else-ifs are enough under load. Running the parsers on threads with a fixed, generous stack
 * makes the limit a property of Jeed rather than of the host and of timing.
 */
private const val PARSER_STACK_BYTES = 16L * 1024 * 1024

private val onParserThread: ThreadLocal<Boolean> = ThreadLocal.withInitial { false }
private val parserThreadCount = AtomicInteger(0)
private val parserThreads = Executors.newCachedThreadPool { runnable ->
    Thread(null, runnable, "Jeed Parser ${parserThreadCount.getAndIncrement()}", PARSER_STACK_BYTES).apply {
        isDaemon = true
    }
}

/**
 * Runs [block] on a thread with a fixed, large stack and returns its result, rethrowing whatever it throws. Already
 * being on such a thread, or being unable to create one, runs [block] in place.
 */
@Suppress("TooGenericExceptionCaught")
internal fun <T> onParserStack(block: () -> T): T {
    if (onParserThread.get()) {
        return block()
    }
    val future = try {
        parserThreads.submit<T> {
            onParserThread.set(true)
            try {
                block()
            } finally {
                onParserThread.set(false)
            }
        }
    } catch (_: SecurityException) {
        // A confined task may not be allowed another thread. Its own stack is what it has.
        return block()
    }
    try {
        return future.get()
    } catch (e: ExecutionException) {
        throw e.cause ?: e
    }
}
