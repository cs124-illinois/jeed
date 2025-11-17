package edu.illinois.cs.cs125.jeed.server.serializers

import edu.illinois.cs.cs125.jeed.core.server.FlatSource
import edu.illinois.cs.cs125.jeed.core.server.Task
import edu.illinois.cs.cs125.jeed.core.server.TaskArguments
import edu.illinois.cs.cs125.jeed.core.server.toFlatSources
import edu.illinois.cs.cs125.jeed.core.server.toSource
import edu.illinois.cs.cs125.jeed.server.Request
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@SerialName("Request")
data class RequestJson(
    val sources: List<FlatSource>? = null,
    val templates: List<FlatSource>? = null,
    val snippet: String? = null,
    val tasks: Set<Task>,
    val arguments: TaskArguments? = null,
    val label: String,
    val checkForSnippet: Boolean = false,
)

object RequestSerializer : KSerializer<Request> {
    override val descriptor: SerialDescriptor = RequestJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Request) {
        // Serialize using the original passed values, not the transformed ones
        val json = RequestJson(
            sources = value.source?.toFlatSources(),
            templates = value.templates?.toFlatSources(),
            snippet = value.snippet,
            tasks = value.tasks,
            arguments = value.arguments,
            label = value.label,
            checkForSnippet = value.checkForSnippet,
        )
        encoder.encodeSerializableValue(RequestJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): Request {
        val json = decoder.decodeSerializableValue(RequestJson.serializer())
        // Call the Request constructor - this will run the init block and do all the transformations
        return Request(
            passedSource = json.sources?.toSource(),
            templates = json.templates?.toSource(),
            passedSnippet = json.snippet,
            passedTasks = json.tasks,
            passedArguments = json.arguments,
            label = json.label,
            checkForSnippet = json.checkForSnippet,
        )
    }
}
