package edu.illinois.cs.cs125.jeed.server

import com.beyondgrader.resourceagent.jeed.MemoryLimit
import com.beyondgrader.resourceagent.jeed.MemoryLimitArguments
import edu.illinois.cs.cs125.jeed.core.ConfiguredSandboxPlugin
import edu.illinois.cs.cs125.jeed.core.LineTrace
import edu.illinois.cs.cs125.jeed.core.LineTraceArguments
import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.compile
import edu.illinois.cs.cs125.jeed.core.findClassMethod
import edu.illinois.cs.cs125.jeed.core.fromSnippet
import edu.illinois.cs.cs125.jeed.core.serializers.JeedJson
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldNotContain
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.application.Application
import io.ktor.server.application.call
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.net.HttpURLConnection
import java.net.URI

private const val HELLO_REQUEST = """
{
"label": "test",
"snippet": "System.out.println(\"Hello, world!\");",
"tasks": [ "compile", "execute" ]
}"""

private const val REDIRECTING_STREAM = "edu.illinois.cs.cs125.jeed.core.Sandbox\$RedirectingPrintStream"

/**
 * Runs [block] with capturing streams installed underneath the sandbox, so that anything sandboxed code prints that
 * the sandbox fails to capture, or that the server lets through some other way, ends up in the returned string.
 *
 * The sandbox must already have been initialized once, so that stopping it here is a restart rather than the first
 * start, which the server's request path performs with the resource agent's help.
 */
private suspend fun captureHostStreams(block: suspend () -> Unit): String {
    Sandbox.stop()
    val hostStdout = System.out
    val hostStderr = System.err
    val captured = ByteArrayOutputStream()
    PrintStream(captured, true).also {
        System.setOut(it)
        System.setErr(it)
    }
    try {
        block()
    } finally {
        Sandbox.stop()
        System.setOut(hostStdout)
        System.setErr(hostStderr)
    }
    return captured.toString()
}

private suspend fun postHelloThroughTestEngine(times: Int) {
    testApplication {
        application {
            jeed()
        }
        repeat(times) {
            client.post("/") {
                header("content-type", "application/json")
                setBody(HELLO_REQUEST.trim())
            }.also { response ->
                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldContain "Hello, world!"
            }
        }
    }
}

/**
 * The shape questioner drives jeed with: one sandboxed execution that, under [Sandbox.redirectOutput] with a listener,
 * repeatedly invokes both a reference solution loaded by a plain (non-sandboxed) class loader and the sandboxed
 * submission, with the server's plugins attached, and the results serialized as JSON.
 */
private class QuestionerShapedRun {
    private val solutionMain = Source.fromSnippet("""System.out.println("Hello, world!");""").compile()
        .classLoader.findClassMethod()
    private val submission = Source.fromSnippet("""System.out.println("Hello, world!");""").compile()

    private val listener = object : Sandbox.OutputListener {
        override fun stdout(int: Int) {}
        override fun stderr(int: Int) {}
    }

    private fun plugins() = listOf(
        ConfiguredSandboxPlugin(LineTrace, LineTraceArguments(recordedLineLimit = 0, runLineLimit = null)),
        ConfiguredSandboxPlugin(
            MemoryLimit,
            MemoryLimitArguments(
                maxTotalAllocation = null,
                maxIndividualAllocation = null,
                stopSingleThreadTasksByThrow = false,
            ),
        ),
    )

    suspend fun execute(): List<String> {
        val outputs = mutableListOf<String>()
        val results = Sandbox.execute(
            submission.classLoader,
            Sandbox.ExecutionArguments(timeout = 5000L),
            plugins(),
        ) { (classLoader) ->
            repeat(6) {
                val captured = Sandbox.redirectOutput(listener, redirectingOutputLimit = 100, squashNormalOutput = true) {
                    solutionMain.invoke(null)
                    classLoader.findClassMethod().invoke(null)
                }
                outputs.add(captured.stdout)
            }
        }
        check(results.threw == null) { "run threw ${results.threw}" }
        return outputs
    }

    val module: Application.() -> Unit = {
        install(ContentNegotiation) {
            json(JeedJson)
        }
        routing {
            post("/q") {
                call.respond(execute())
            }
        }
    }

    suspend fun post(times: Int) {
        testApplication {
            application(module)
            repeat(times) {
                client.post("/q").also { response ->
                    response.status shouldBe HttpStatusCode.OK
                    response.bodyAsText() shouldContain "Hello, world!"
                }
            }
        }
    }
}

// Executions after the first in a JVM are the ones that have been seen to leak, so every test here performs a first
// execution through the server before installing the capture.
class TestOutputLeaks :
    StringSpec({
        "should not leak sandboxed output to the host through later test engine requests" {
            postHelloThroughTestEngine(1)
            val leaked = captureHostStreams {
                postHelloThroughTestEngine(3)
                // Nothing in the server or ktor should have replaced the sandbox's streams.
                System.out.javaClass.name shouldBe REDIRECTING_STREAM
                System.err.javaClass.name shouldBe REDIRECTING_STREAM
            }
            leaked shouldNotContain "Hello, world!"
        }
        "should not leak sandboxed output to the host through later Netty requests" {
            postHelloThroughTestEngine(1)
            val leaked = captureHostStreams {
                // The engine the deployed server runs on, rather than the test host.
                val server = embeddedServer(Netty, port = 0, module = Application::jeed).start(wait = false)
                try {
                    val port = server.engine.resolvedConnectors().first().port
                    repeat(3) {
                        // java.net.http.HttpClient checks permissions against an access control context, which
                        // the sandbox's security manager does not handle; HttpURLConnection does not.
                        val connection = URI.create("http://127.0.0.1:$port/").toURL().openConnection() as HttpURLConnection
                        connection.requestMethod = "POST"
                        connection.setRequestProperty("content-type", "application/json")
                        connection.doOutput = true
                        connection.outputStream.use { it.write(HELLO_REQUEST.trim().toByteArray()) }
                        connection.responseCode shouldBe HttpStatusCode.OK.value
                        connection.inputStream.bufferedReader().use { it.readText() } shouldContain "Hello, world!"
                    }
                    System.out.javaClass.name shouldBe REDIRECTING_STREAM
                    System.err.javaClass.name shouldBe REDIRECTING_STREAM
                } finally {
                    server.stop(100, 1000)
                }
            }
            leaked shouldNotContain "Hello, world!"
        }
        "should not leak sandboxed output from a questioner-shaped handler after earlier executions" {
            postHelloThroughTestEngine(1)
            val run = QuestionerShapedRun()
            // A direct execution first, then one through HTTP, before capturing.
            run.execute().forEach { it shouldContain "Hello, world!" }
            run.post(1)
            val leaked = captureHostStreams {
                run.post(3)
                System.out.javaClass.name shouldBe REDIRECTING_STREAM
            }
            leaked shouldNotContain "Hello, world!"
        }
    })
