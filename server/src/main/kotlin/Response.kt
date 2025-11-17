package edu.illinois.cs.cs125.jeed.server

import edu.illinois.cs.cs125.jeed.core.Interval
import edu.illinois.cs.cs125.jeed.core.serializers.JeedJson
import edu.illinois.cs.cs125.jeed.core.server.CompletedTasks
import edu.illinois.cs.cs125.jeed.core.server.FailedTasks
import edu.illinois.cs.cs125.jeed.core.server.Task
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable

@Serializable
class Response(@Contextual val request: Request) {
    val email = request.email
    val audience = request.audience

    val status = currentStatus

    val completedTasks: MutableSet<Task> = mutableSetOf()
    val completed: CompletedTasks = CompletedTasks()

    val failedTasks: MutableSet<Task> = mutableSetOf()
    val failed: FailedTasks = FailedTasks()

    @Contextual
    lateinit var interval: Interval

    @Suppress("unused")
    val json: String
        get() = JeedJson.encodeToString(this)

    companion object {
        fun from(response: String?): Response {
            check(response != null) { "can't deserialize null string" }
            return JeedJson.decodeFromString<Response>(response)
        }
    }
}
