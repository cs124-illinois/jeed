@file:Suppress("MagicNumber")

package edu.illinois.cs.cs125.jeed.server

import com.beyondgrader.resourceagent.jeed.MemoryLimit
import edu.illinois.cs.cs125.jeed.core.LineTrace
import edu.illinois.cs.cs125.jeed.core.VERSION
import edu.illinois.cs.cs125.jeed.core.checkDockerEnabled
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.beEmpty
import io.kotest.matchers.ints.shouldBeGreaterThan
import io.kotest.matchers.nulls.beNull
import io.kotest.matchers.shouldBe
import io.kotest.matchers.shouldNot
import io.kotest.matchers.shouldNotBe
import io.kotest.matchers.string.shouldEndWith
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject

@Suppress("LargeClass")
class TestHTTP : StringSpec() {
    init {
        "should accept good snippet request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "System.out.println(\"Here\");",
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "Main"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should timeout a snippet request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "
int i = 0;
while (true) {
  i++;
}",
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "Main"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                    jeedResponse.completed.execution?.timeout shouldBe true
                }
            }
        }
        "should timeout a snippet request with line count limit" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "
int i = 0;
while (true) {
  i++;
}",
"tasks": [ "compile", "execute" ],
"arguments": {
  "plugins": {
    "lineCountLimit": "1000"
  }
}
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "Main"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                    jeedResponse.completed.execution?.timeout shouldBe false
                    jeedResponse.completed.execution?.killReason shouldBe LineTrace.KILL_REASON
                }
            }
        }
        "should reject OOM snippet request properly" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "
int[] values = new int[1024 * 1024 * 1024];
values[0] = 0;
",
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK
                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "Main"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                    jeedResponse.completed.execution?.timeout shouldBe false
                    jeedResponse.completed.execution?.killReason shouldBe MemoryLimit.INDIVIDUAL_LIMIT_KILL_REASON
                }
            }
        }
        "should prevent counterfeiting initialization failures" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    throw new NoClassDefFoundError(\"Could not initialize class java.lang.invoke.MethodHandles\");
  }
}"
  }
],
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.failedTasks.size shouldBe 0
                    jeedResponse.completed.execution?.threw?.klass shouldEndWith "SecurityException"
                    jeedResponse.completed.execution?.permissionRequests?.find {
                        it.permission.name == "useForbiddenMethod"
                    } shouldNot beNull()
                }
            }
        }
        "should accept good snippet cexecution request".config(enabled = checkDockerEnabled()) {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "System.out.println(\"Here\");",
"tasks": [ "compile", "cexecute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.cexecution?.klass shouldBe "Main"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good kotlin snippet request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "println(\"Here\")",
"tasks": [ "kompile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "MainKt"
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    System.out.println(\"Here\");
  }
}"
  }
],
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good kotlin source request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Here\")
}"
  }
],
"tasks": [ "kompile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "MainKt"
                    jeedResponse.completed.execution?.outputLines?.joinToString(separator = "\n") {
                        it.line
                    }?.trim() shouldBe "Here"
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "kotlin coroutines should work by default" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
import kotlinx.coroutines.*
fun main() {
  val job = GlobalScope.launch {
    println(\"Here\")
  }
  runBlocking {
    job.join()
  }
}"
  }
],
"tasks": [ "kompile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completed.execution?.klass shouldBe "MainKt"
                    jeedResponse.completed.execution?.outputLines?.joinToString(separator = "\n") {
                        it.line
                    }?.trim() shouldBe "Here"
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source checkstyle request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
public static void main() {
    System.out.println(\"Here\");
}
}"
  }
],
"tasks": [ "checkstyle", "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source ktlint request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Hello, world!\")
}"
  }
],
"tasks": [ "ktlint", "kompile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should reject checkstyle request for non-Java sources" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Here\")
}"
  }
],
"tasks": [ "checkstyle", "kompile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should accept good templated source request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"templates": [
  {
    "path": "Main.java.hbs",
    "contents": "
public class Main {
public static void main() {
    {{{ contents }}}
}
}"
  }
],
"sources": [
  {
    "path": "Main.java",
    "contents": "System.out.println(\"Here\");"
  }
],
"tasks": [ "template", "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source complexity request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
    public static void main() {
        System.out.println(\"Here\");
    }
}"
  }
],
"tasks": [ "complexity" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good source features request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
    public static void main() {
        System.out.println(\"Here\");
    }
}"
  }
],
"tasks": [ "features" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should fail bad source features request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
    public static void main() {
        System.out.println(\"Here\")
    }
}"
  }
],
"tasks": [ "features" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 0
                    jeedResponse.failedTasks.size shouldBe 1
                }
            }
        }
        "should accept good source mutations request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
    public static void main() {
        System.out.println(\"Here\");
    }
}"
  }
],
"tasks": [ "mutations" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.completed.mutations shouldNot beNull()
                    jeedResponse.completed.mutations!!.mutatedSources shouldNot beEmpty()
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good Kotlin complexity request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
class Main {
    fun main() {
      println(\"Here\");
    }
}"
  }
],
"tasks": [ "complexity" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should handle snippet error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "System.out.println(\"Here\")",
"tasks": [ "snippet" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 0
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.snippet?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should serialize snippet errors with flat line and column fields" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "int i = 0\nprint(\"Hello\")",
"tasks": [ "snippet" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val responseText = response.bodyAsText()
                    val jeedResponse = Response.from(responseText)
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.snippet?.errors?.size ?: 0) shouldBeGreaterThan 0

                    // Verify JSON structure has flat line/column (not nested in location)
                    val parsed = Json.parseToJsonElement(responseText).jsonObject
                    val snippetErrors = parsed["failed"]!!.jsonObject["snippet"]!!.jsonObject["errors"]!!.jsonArray
                    snippetErrors.forEach { errorElement ->
                        val error = errorElement.jsonObject
                        error.containsKey("line") shouldBe true
                        error.containsKey("column") shouldBe true
                        error.containsKey("message") shouldBe true
                        error.containsKey("location") shouldBe false
                    }
                }
            }
        }
        "should handle template error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"templates": [
  {
    "path": "Main.java.hbs",
    "contents": "
public class Main {
public static void main() {
    {{ contents }}}
}
}"
  }
],
"sources": [
  {
    "path": "Main.java",
    "contents": "System.out.println(\"Here\");"
  }
],
"tasks": [ "template" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 0
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.template?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should handle compilation error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    System.out.println(\"Here\")
  }
}"
  }
],
"tasks": [ "compile" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 0
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.compilation?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should handle kompilation error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  printing(\"Here\")
}"
  }
],
"tasks": [ "kompile" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 0
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.kompilation?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should handle checkstyle error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"arguments": {
  "checkstyle": {
    "failOnError": true
  }
},
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
public static void main() {
System.out.println(\"Here\");
}
}"
  }
],
"tasks": [ "checkstyle", "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.checkstyle?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should serialize checkstyle errors with severity field" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"arguments": {
  "checkstyle": {
    "failOnError": true
  }
},
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
public static void main() {
System.out.println(\"Here\");
}
}"
  }
],
"tasks": [ "checkstyle" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val responseText = response.bodyAsText()
                    val jeedResponse = Response.from(responseText)
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.checkstyle?.errors?.size ?: 0) shouldBeGreaterThan 0

                    // Verify JSON structure has severity field
                    val parsed = Json.parseToJsonElement(responseText).jsonObject
                    val checkstyleErrors = parsed["failed"]!!.jsonObject["checkstyle"]!!.jsonObject["errors"]!!.jsonArray
                    checkstyleErrors.forEach { errorElement ->
                        val error = errorElement.jsonObject
                        error.containsKey("severity") shouldBe true
                        error.containsKey("location") shouldBe true
                        error.containsKey("message") shouldBe true
                    }
                }
            }
        }
        "should serialize ktlint errors with correct JSON key" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"arguments": {
  "ktLint": {
    "failOnError": true
  }
},
"sources": [
  {
    "path": "Main.kt",
    "contents": "fun main() {\nprintln(\"Here\")\n}"
  }
],
"tasks": [ "ktlint" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val responseText = response.bodyAsText()
                    val jeedResponse = Response.from(responseText)
                    jeedResponse.failedTasks.size shouldBe 1
                    (jeedResponse.failed.ktlint?.errors?.size ?: 0) shouldBeGreaterThan 0

                    // Verify JSON structure uses ktLint key (camelCase) and has correct fields
                    val parsed = Json.parseToJsonElement(responseText).jsonObject
                    parsed["failed"]!!.jsonObject.containsKey("ktLint") shouldBe true
                    val ktlintErrors = parsed["failed"]!!.jsonObject["ktLint"]!!.jsonObject["errors"]!!.jsonArray
                    ktlintErrors.forEach { errorElement ->
                        val error = errorElement.jsonObject
                        error.containsKey("ruleId") shouldBe true
                        error.containsKey("detail") shouldBe true
                        error.containsKey("location") shouldBe true
                    }
                }
            }
        }
        "should return checkstyle results when not configured to fail" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
public static void main() {
System.out.println(\"Here\");
}
}"
  }
],
"tasks": [ "checkstyle", "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0
                    (jeedResponse.completed.checkstyle?.errors?.size ?: 0) shouldBeGreaterThan 0
                }
            }
        }
        "should handle execution error" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    Object t = null;
    System.out.println(t.toString());
  }
}"
  }
],
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.failedTasks.size shouldBe 0
                    jeedResponse.completed.execution?.threw shouldNotBe ""
                }
            }
        }
        "should handle cexecution error".config(enabled = checkDockerEnabled()) {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  private static void min() {
    System.out.println(\"Here\");
  }
}"
  }
],
"tasks": [ "compile", "cexecute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 1
                    jeedResponse.failedTasks.size shouldBe 1
                }
            }
        }
        "should reject both source and snippet request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"snippet": "System.out.println(\"Hello, world!\");",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    Object t = null;
    System.out.println(t.toString());
  }
}"
  }
],
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should handle a java source that is actually a snippet" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
System.out.println(\"Here\");
"
  }
],
"tasks": [ "compile", "execute" ],
"checkForSnippet": true
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0

                    jeedResponse.completed.execution?.outputLines?.joinToString(separator = "\n") {
                        it.line
                    }?.trim() shouldBe "Here"
                }
            }
        }
        "should handle a kotlin source that is actually a snippet" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
println(\"Here\")
"
  }
],
"tasks": [ "kompile", "execute" ],
"checkForSnippet": true
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 3
                    jeedResponse.failedTasks.size shouldBe 0

                    jeedResponse.completed.execution?.outputLines?.joinToString(separator = "\n") {
                        it.line
                    }?.trim() shouldBe "Here"
                }
            }
        }
        "should reject neither source nor snippet request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject mapped source request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"source": {
  "Main.java": "
public class Main {
  public static void main() {
    System.out.println(\"Here\");
  }
}"
},
"tasks": [ "compile", "execute" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should accept good disassemble request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.java",
    "contents": "
public class Main {
  public static void main() {
    System.out.println(\"Hi\");
  }
}"
  }
],
"tasks": [ "compile", "disassemble" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.completed.disassemble!!.disassemblies.keys shouldBe setOf("Main")
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should accept good kotlin disassemble request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
"label": "test",
"sources": [
  {
    "path": "Main.kt",
    "contents": "
fun main() {
  println(\"Here\")
}"
  }
],
"tasks": [ "kompile", "disassemble" ]
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK

                    val jeedResponse = Response.from(response.bodyAsText())
                    jeedResponse.completedTasks.size shouldBe 2
                    jeedResponse.completed.disassemble!!.disassemblies.keys shouldBe setOf("MainKt")
                    jeedResponse.failedTasks.size shouldBe 0
                }
            }
        }
        "should reject unauthorized request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody("broken")
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject bad request" {
            testApplication {
                application {
                    jeed()
                }
                client.post("/") {
                    header("content-type", "application/json")
                    setBody("broken")
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should provide info in response to GET" {
            testApplication {
                application {
                    jeed()
                }
                client.get("/") {
                    header("content-type", "application/json")
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK
                    val status = Status.from(response.bodyAsText())
                    status.versions.jeed shouldBe VERSION
                }
            }
        }
    }
}
