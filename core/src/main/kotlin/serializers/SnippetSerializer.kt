package edu.illinois.cs.cs125.jeed.core.serializers

import edu.illinois.cs.cs125.jeed.core.Location
import edu.illinois.cs.cs125.jeed.core.Snippet
import edu.illinois.cs.cs125.jeed.core.SnippetProperties
import edu.illinois.cs.cs125.jeed.core.Source
import edu.illinois.cs.cs125.jeed.core.SourceRange
import edu.illinois.cs.cs125.jeed.core.Sources
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

@Serializable
@SerialName("Snippet")
data class SnippetJson(
    val sources: Map<String, String>,
    val originalSource: String,
    val rewrittenSource: String,
    val snippetRange: SourceRange,
    val wrappedClassName: String,
    val looseCodeMethodName: String,
    val fileType: String,
    val snippetProperties: SnippetProperties,
)

object SnippetSerializer : KSerializer<Snippet> {
    override val descriptor: SerialDescriptor = SnippetJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Snippet) {
        val snippetJson = SnippetJson(
            sources = value.sources,
            originalSource = value.originalSource,
            rewrittenSource = value.rewrittenSource,
            snippetRange = value.snippetRange,
            wrappedClassName = value.wrappedClassName,
            looseCodeMethodName = value.looseCodeMethodName,
            fileType = value.fileType.name,
            snippetProperties = value.snippetProperties,
        )
        encoder.encodeSerializableValue(SnippetJson.serializer(), snippetJson)
    }

    override fun deserialize(decoder: Decoder): Snippet {
        val json = decoder.decodeSerializableValue(SnippetJson.serializer())
        return Snippet(
            Sources(json.sources),
            json.originalSource,
            json.rewrittenSource,
            json.snippetRange,
            json.wrappedClassName,
            json.looseCodeMethodName,
            Source.FileType.valueOf(json.fileType),
            json.snippetProperties,
        )
    }
}
