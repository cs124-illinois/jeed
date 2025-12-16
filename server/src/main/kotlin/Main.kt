@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.server

import com.beyondgrader.resourceagent.Agent
import com.beyondgrader.resourceagent.StaticFailureDetection
import edu.illinois.cs.cs125.jeed.core.VERSION
import edu.illinois.cs.cs125.jeed.core.checkDockerEnabled
import edu.illinois.cs.cs125.jeed.core.getStackTraceAsString
import edu.illinois.cs.cs125.jeed.core.serializers.JeedJson
import edu.illinois.cs.cs125.jeed.core.warm
import io.github.nhubbard.konf.source.json.toJson
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import mu.KotlinLogging
import sun.misc.Signal
import java.lang.management.ManagementFactory
import java.time.Instant
import java.util.concurrent.atomic.AtomicLong
import kotlin.system.exitProcess

@Suppress("UNUSED")
val logger = KotlinLogging.logger {}

private val statusLogIntervalMs = System.getenv("STATUS_LOG_INTERVAL_MS")?.toLongOrNull() ?: 300000L // 5 minutes
private val requestCounter = AtomicLong(0)
private val startTime = Instant.now()

val currentStatus = Status()

private fun getMemoryInfo(): String {
    val runtime = Runtime.getRuntime()
    val memoryBean = ManagementFactory.getMemoryMXBean()
    val heapUsage = memoryBean.heapMemoryUsage
    val nonHeapUsage = memoryBean.nonHeapMemoryUsage
    val mb = 1024 * 1024

    return buildString {
        append("heap=${heapUsage.used / mb}MB/${heapUsage.max / mb}MB")
        append(", nonHeap=${nonHeapUsage.used / mb}MB")
        append(", runtime.total=${runtime.totalMemory() / mb}MB")
        append(", runtime.free=${runtime.freeMemory() / mb}MB")
    }
}

private fun getThreadInfo(): String {
    val threadBean = ManagementFactory.getThreadMXBean()
    return "threads=${threadBean.threadCount}, peak=${threadBean.peakThreadCount}, daemon=${threadBean.daemonThreadCount}"
}

private fun getClassLoaderInfo(): String {
    val classBean = ManagementFactory.getClassLoadingMXBean()
    return "loadedClasses=${classBean.loadedClassCount}, totalLoaded=${classBean.totalLoadedClassCount}, unloaded=${classBean.unloadedClassCount}"
}

private fun getGCInfo(): String {
    val gcBeans = ManagementFactory.getGarbageCollectorMXBeans()
    return gcBeans.joinToString(", ") { gc ->
        "${gc.name}: count=${gc.collectionCount}, time=${gc.collectionTime}ms"
    }
}

private fun logDetailedStatus(reason: String) {
    val uptimeSeconds = java.time.Duration.between(startTime, Instant.now()).seconds
    logger.info {
        "STATUS [$reason] uptime=${uptimeSeconds}s, requests=${requestCounter.get()}, " +
            getMemoryInfo() + ", " + getThreadInfo() + ", " + getClassLoaderInfo()
    }
    logger.info { "GC: ${getGCInfo()}" }
}

private fun setupSignalHandlers() {
    val handler: (Signal) -> Unit = { signal ->
        logger.error { "SIGNAL RECEIVED: ${signal.name} (${signal.number})" }
        logDetailedStatus("SIGNAL-${signal.name}")

        // Log thread dump on termination signal
        logger.error { "Thread dump on ${signal.name}:" }
        Thread.getAllStackTraces().forEach { (thread, stackTrace) ->
            if (stackTrace.isNotEmpty()) {
                logger.error { "  ${thread.name} (${thread.state}): ${stackTrace.firstOrNull()}" }
            }
        }
    }

    try {
        Signal.handle(Signal("TERM"), handler)
        Signal.handle(Signal("INT"), handler)
        logger.info { "Signal handlers registered for SIGTERM and SIGINT" }
    } catch (e: Exception) {
        logger.warn { "Could not register signal handlers: ${e.message}" }
    }
}

private fun setupShutdownHook() {
    Runtime.getRuntime().addShutdownHook(
        Thread {
            logger.error { "SHUTDOWN HOOK TRIGGERED" }
            logDetailedStatus("SHUTDOWN")
        },
    )
    logger.info { "Shutdown hook registered" }
}


@Suppress("ComplexMethod", "LongMethod")
fun Application.jeed() {
    install(ContentNegotiation) {
        json(JeedJson)
    }
    // Start status logger in the application's coroutine scope
    launch {
        while (true) {
            delay(statusLogIntervalMs)
            logDetailedStatus("PERIODIC")
        }
    }
    routing {
        get("/") {
            call.respond(currentStatus.update())
        }
        get("/version") {
            call.respond(VERSION)
        }
        post("/") {
            val requestId = requestCounter.incrementAndGet()
            val requestStart = System.currentTimeMillis()
            logger.info { "REQUEST $requestId: started" }

            val parseStart = System.currentTimeMillis()
            val job = try {
                call.receive<Request>().check()
            } catch (e: Exception) {
                logger.warn { "REQUEST $requestId: parse failed after ${System.currentTimeMillis() - parseStart}ms" }
                logger.warn(e.getStackTraceAsString())
                call.respondText(e.message ?: e.toString(), status = HttpStatusCode.BadRequest)
                return@post
            }
            val parseTime = System.currentTimeMillis() - parseStart

            try {
                val runStart = System.currentTimeMillis()
                val result = job.run()
                val runTime = System.currentTimeMillis() - runStart
                currentStatus.lastRequest = Instant.now()

                val respondStart = System.currentTimeMillis()
                call.respond(result)
                val respondTime = System.currentTimeMillis() - respondStart

                val totalTime = System.currentTimeMillis() - requestStart
                logger.info {
                    "REQUEST $requestId: completed in ${totalTime}ms " +
                        "(parse=${parseTime}ms, run=${runTime}ms, respond=${respondTime}ms)"
                }

                val staticInitFailures = StaticFailureDetection.pollStaticInitializationFailures()
                if (staticInitFailures.isNotEmpty()) {
                    val failedClasses = staticInitFailures.map { it.clazz }
                    logger.warn("Execution detected failed static initializations: $failedClasses")
                    if (System.getenv("SHUTDOWN_ON_CACHE_POISONING") != null) {
                        logger.error("Terminating due to cache poisoning")
                        exitProcess(-1)
                    }
                }
            } catch (e: Exception) {
                val totalTime = System.currentTimeMillis() - requestStart
                logger.warn { "REQUEST $requestId: failed after ${totalTime}ms" }
                logger.warn(e.getStackTraceAsString())
                call.respondText(e.message ?: e.toString(), status = HttpStatusCode.BadRequest)
            }
        }
    }
}

fun main(@Suppress("unused") unused: Array<String>) {
    logger.info { "Jeed server starting..." }
    logger.info { "JVM: ${System.getProperty("java.version")} (${System.getProperty("java.vendor")})" }
    logger.info { "Max heap: ${Runtime.getRuntime().maxMemory() / 1024 / 1024}MB" }
    logger.info { Status().toJson() }
    logger.info(configuration.toJson.toText())

    setupSignalHandlers()
    setupShutdownHook()

    Agent.activate(countLines = false, redirectFiles = false)
    StaticFailureDetection.recordingFailedClasses = true

    runBlocking {
        try {
            warm(2, failLint = false)
        } catch (e: Exception) {
            logger.error("Warm failed, restarting: $e")
            exitProcess(-1)
        }
    }

    val dockerEnabled = try {
        checkDockerEnabled(true)
    } catch (e: Exception) {
        logger.warn("Docker check failed: $e")
        false
    }

    logger.info(
        "Docker " + if (dockerEnabled) {
            "enabled"
        } else {
            "disabled"
        },
    )

    logDetailedStatus("STARTUP-COMPLETE")
    logger.info { "Periodic status logging every ${statusLogIntervalMs / 1000}s (set STATUS_LOG_INTERVAL_MS to change)" }

    embeddedServer(Netty, port = configuration[TopLevel.port], module = Application::jeed).start(true)
}
