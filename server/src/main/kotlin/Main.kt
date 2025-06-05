@file:Suppress("MatchingDeclarationName")

package edu.illinois.cs.cs125.jeed.server

import com.beyondgrader.resourceagent.Agent
import com.beyondgrader.resourceagent.StaticFailureDetection
import com.ryanharter.ktor.moshi.moshi
import com.squareup.moshi.Moshi
import edu.illinois.cs.cs125.jeed.core.VERSION
import edu.illinois.cs.cs125.jeed.core.checkDockerEnabled
import edu.illinois.cs.cs125.jeed.core.getStackTraceAsString
import edu.illinois.cs.cs125.jeed.core.warm
import edu.illinois.cs.cs125.jeed.server.moshi.Adapters
import io.github.nhubbard.konf.source.json.toJson
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.response.respondText
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import mu.KotlinLogging
import java.time.Instant
import kotlin.system.exitProcess
import edu.illinois.cs.cs125.jeed.core.moshi.Adapters as JeedAdapters

@Suppress("UNUSED")
val logger = KotlinLogging.logger {}
val moshi: Moshi = Moshi.Builder().let { builder ->
    Adapters.forEach { builder.add(it) }
    JeedAdapters.forEach { builder.add(it) }
    builder.build()
}

val currentStatus = Status()

@Suppress("ComplexMethod", "LongMethod")
fun Application.jeed() {
    install(CORS) {
        anyHost()
        allowNonSimpleContentTypes = true
    }
    install(ContentNegotiation) {
        moshi {
            JeedAdapters.forEach { this.add(it) }
            Adapters.forEach { this.add(it) }
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
            withContext(Dispatchers.IO) {
                val job = try {
                    call.receive<Request>().check()
                } catch (e: Exception) {
                    logger.warn(e.getStackTraceAsString())
                    call.respondText(e.message ?: e.toString(), status = HttpStatusCode.BadRequest)
                    return@withContext
                }

                try {
                    val result = job.run()
                    currentStatus.lastRequest = Instant.now()
                    call.respond(result)

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
                    logger.warn(e.getStackTraceAsString())
                    call.respondText(e.message ?: e.toString(), status = HttpStatusCode.BadRequest)
                }
            }
        }
    }
}

fun main(@Suppress("unused") unused: Array<String>): Unit = runBlocking {
    logger.info { Status().toJson() }
    logger.info(configuration.toJson.toText())

    Agent.activate(countLines = false, redirectFiles = false)
    StaticFailureDetection.recordingFailedClasses = true

    try {
        warm(2, failLint = false)
    } catch (e: Exception) {
        logger.error("Warm failed, restarting: $e")
        exitProcess(-1)
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

    embeddedServer(Netty, port = configuration[TopLevel.port], module = Application::jeed).start(true)
}
