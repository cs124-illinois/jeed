package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.jeed.core.Sandbox
import edu.illinois.cs.cs125.jeed.core.serializers.PermissionJson
import io.github.nhubbard.konf.Config
import io.github.nhubbard.konf.source.yaml.yaml
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.longs.shouldBeExactly
import io.kotest.matchers.shouldBe
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication

class TestConfig :
    StringSpec({
        "should load defaults correctly" {
            configuration[Limits.Execution.timeout] shouldBeExactly Sandbox.ExecutionArguments.DEFAULT_TIMEOUT
        }
        "should load simple configuration from a file" {
            val config = Config { addSpec(Limits) }.from.yaml.string(
                """
limits:
  execution:
    timeout: 10000
        """.trim(),
            )
            config[Limits.Execution.timeout] shouldBeExactly 10000L
        }
        "should load complex configuration from a file" {
            val config = Config { addSpec(Limits) }.from.yaml.string(
                """
limits:
  execution:
    permissions:
      - klass: java.lang.RuntimePermission
        name: createClassLoader
    timeout: 10000
        """.trim(),
            )

            config[Limits.Execution.timeout] shouldBeExactly 10000L
            config[Limits.Execution.permissions] shouldHaveSize 1
            config[Limits.Execution.permissions][0] shouldBe PermissionJson(
                "java.lang.RuntimePermission",
                "createClassLoader",
                null,
            )
        }
        "should reject snippet request with too long timeout" {
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
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "timeout": "${configuration[Limits.Execution.timeout] + 1}"
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }

                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
  "label": "test",
  "snippet": "System.out.println(\"Here\");",
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "timeout": "${configuration[Limits.Execution.timeout] + 1}"
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject snippet request with too many extra threads" {
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
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "maxExtraThreads": "${configuration[Limits.Execution.maxExtraThreads] + 1}"
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }

                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
  "label": "test",
  "snippet": "System.out.println(\"Here\");",
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "maxExtraThreads": "${configuration[Limits.Execution.maxExtraThreads] + 1}"
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject snippet request with too many permissions" {
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
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "permissions": [{
        "klass": "java.lang.RuntimePermission",
        "name": "accessDeclaredMembers"
      }, {
        "klass": "java.lang.reflect.ReflectPermission",
        "name": "suppressAccessChecks"
      }]
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK
                }

                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
  "label": "test",
  "snippet": "System.out.println(\"Here\");",
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "permissions": [{
        "klass": "java.lang.RuntimePermission",
        "name": "createClassLoader"
      }, {
        "klass": "java.lang.reflect.ReflectPermission",
        "name": "suppressAccessChecks"
      }]
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject snippet request attempting to remove blacklisted classes" {
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
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "classLoaderConfiguration": {
        "blacklistedClasses": [
          "java.lang.reflect."
        ]
      }
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.OK
                }

                client.post("/") {
                    header("content-type", "application/json")
                    setBody(
                        """
{
  "label": "test",
  "snippet": "System.out.println(\"Here\");",
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "classLoaderConfiguration": {
        "blacklistedClasses": []
      }
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
        "should reject snippet request attempting to remove forbidden methods" {
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
  "tasks": [ "compile", "execute" ],
  "arguments": {
    "execution": {
      "classLoaderConfiguration": {
        "blacklistedMethods": [
          {
            "ownerClassPrefix": "java.lang.Class",
            "methodPrefix": "forName",
            "allowInReload": false
          }
        ]
      }
    }
  }
}""".trim(),
                    )
                }.also { response ->
                    response.status shouldBe HttpStatusCode.BadRequest
                }
            }
        }
    })
