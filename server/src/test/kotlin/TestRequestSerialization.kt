package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.jeed.core.serializers.JeedJson
import edu.illinois.cs.cs125.jeed.core.server.Task
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.serialization.decodeFromString

class TestRequestSerialization : StringSpec() {
    init {
        "should deserialize simple snippet request" {
            val json = """
                {
                    "label": "test",
                    "snippet": "System.out.println(\"Here\");",
                    "tasks": ["compile", "execute"]
                }
            """.trimIndent()

            val request = JeedJson.decodeFromString<Request>(json)

            request.label shouldBe "test"
            request.snippet shouldBe "System.out.println(\"Here\");"
            request.tasks.contains(Task.compile) shouldBe true
            request.tasks.contains(Task.execute) shouldBe true
        }
    }
}
