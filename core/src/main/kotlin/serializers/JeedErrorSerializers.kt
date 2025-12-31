package edu.illinois.cs.cs125.jeed.core.serializers

import edu.illinois.cs.cs125.jeed.core.CheckstyleError
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationError
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.CompilationMessage
import edu.illinois.cs.cs125.jeed.core.ComplexityFailed
import edu.illinois.cs.cs125.jeed.core.FailedCompilationData
import edu.illinois.cs.cs125.jeed.core.FeaturesFailed
import edu.illinois.cs.cs125.jeed.core.KtLintError
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.MutationsFailed
import edu.illinois.cs.cs125.jeed.core.SnippetTransformationError
import edu.illinois.cs.cs125.jeed.core.SnippetTransformationFailed
import edu.illinois.cs.cs125.jeed.core.SourceError
import edu.illinois.cs.cs125.jeed.core.SourceLocation
import edu.illinois.cs.cs125.jeed.core.TemplatingError
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Serializers for JeedError subclasses - serialize as objects with error lists
@Serializable
data class SnippetTransformationErrorJson(val line: Int, val column: Int, val message: String)

@Serializable
data class SnippetTransformationFailedJson(val errors: List<SnippetTransformationErrorJson>)

@Serializable
data class TemplatingFailedJson(val errors: List<SourceError>)

@Serializable
data class CompilationFailedJson(
    val errors: List<SourceError>,
    val compilationData: FailedCompilationData? = null,
)

@Serializable
data class CheckstyleFailedJson(val errors: List<SourceError>)

@Serializable
data class KtLintFailedJson(val errors: List<SourceError>)

@Serializable
data class ComplexityFailedJson(val errors: List<SourceError>)

@Serializable
data class FeaturesFailedJson(val errors: List<SourceError>)

@Serializable
data class MutationsFailedJson(val errors: List<SourceError>)

object SnippetTransformationFailedSerializer : KSerializer<SnippetTransformationFailed> {
    override val descriptor: SerialDescriptor = SnippetTransformationFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: SnippetTransformationFailed) {
        val json = SnippetTransformationFailedJson(
            value.errors.map { error ->
                SnippetTransformationErrorJson(error.location.line, error.location.column, error.message)
            },
        )
        encoder.encodeSerializableValue(SnippetTransformationFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): SnippetTransformationFailed {
        val json = decoder.decodeSerializableValue(SnippetTransformationFailedJson.serializer())
        return SnippetTransformationFailed(
            json.errors.map { error ->
                SnippetTransformationError(error.line, error.column, error.message)
            },
        )
    }
}

object TemplatingFailedSerializer : KSerializer<TemplatingFailed> {
    override val descriptor: SerialDescriptor = TemplatingFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: TemplatingFailed) {
        val json = TemplatingFailedJson(value.errors)
        encoder.encodeSerializableValue(TemplatingFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): TemplatingFailed {
        val json = decoder.decodeSerializableValue(TemplatingFailedJson.serializer())
        @Suppress("UNCHECKED_CAST")
        return TemplatingFailed(json.errors as List<TemplatingError>)
    }
}

object CompilationFailedSerializer : KSerializer<CompilationFailed> {
    override val descriptor: SerialDescriptor = CompilationFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CompilationFailed) {
        val json = CompilationFailedJson(value.errors, value.compilationData)
        encoder.encodeSerializableValue(CompilationFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): CompilationFailed {
        val json = decoder.decodeSerializableValue(CompilationFailedJson.serializer())
        @Suppress("UNCHECKED_CAST")
        return CompilationFailed(json.errors as List<CompilationError>, json.compilationData)
    }
}

object CheckstyleFailedSerializer : KSerializer<CheckstyleFailed> {
    override val descriptor: SerialDescriptor = CheckstyleFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CheckstyleFailed) {
        val json = CheckstyleFailedJson(value.errors)
        encoder.encodeSerializableValue(CheckstyleFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): CheckstyleFailed {
        val json = decoder.decodeSerializableValue(CheckstyleFailedJson.serializer())
        @Suppress("UNCHECKED_CAST")
        return CheckstyleFailed(json.errors as List<CheckstyleError>)
    }
}

object KtLintFailedSerializer : KSerializer<KtLintFailed> {
    override val descriptor: SerialDescriptor = KtLintFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: KtLintFailed) {
        val json = KtLintFailedJson(value.errors)
        encoder.encodeSerializableValue(KtLintFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): KtLintFailed {
        val json = decoder.decodeSerializableValue(KtLintFailedJson.serializer())
        @Suppress("UNCHECKED_CAST")
        return KtLintFailed(json.errors as List<KtLintError>)
    }
}

object ComplexityFailedSerializer : KSerializer<ComplexityFailed> {
    override val descriptor: SerialDescriptor = ComplexityFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: ComplexityFailed) {
        val json = ComplexityFailedJson(value.errors)
        encoder.encodeSerializableValue(ComplexityFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): ComplexityFailed {
        val json = decoder.decodeSerializableValue(ComplexityFailedJson.serializer())
        return ComplexityFailed(json.errors)
    }
}

object FeaturesFailedSerializer : KSerializer<FeaturesFailed> {
    override val descriptor: SerialDescriptor = FeaturesFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: FeaturesFailed) {
        val json = FeaturesFailedJson(value.errors)
        encoder.encodeSerializableValue(FeaturesFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): FeaturesFailed {
        val json = decoder.decodeSerializableValue(FeaturesFailedJson.serializer())
        return FeaturesFailed(json.errors)
    }
}

object MutationsFailedSerializer : KSerializer<MutationsFailed> {
    override val descriptor: SerialDescriptor = MutationsFailedJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: MutationsFailed) {
        val json = MutationsFailedJson(value.errors)
        encoder.encodeSerializableValue(MutationsFailedJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): MutationsFailed {
        val json = decoder.decodeSerializableValue(MutationsFailedJson.serializer())
        return MutationsFailed(json.errors)
    }
}

// Serializers for specific error types
@Serializable
data class CheckstyleErrorJson(
    val severity: String,
    val key: String?,
    val location: SourceLocation,
    val message: String,
    val sourceName: String = "",
)

object CheckstyleErrorSerializer : KSerializer<CheckstyleError> {
    override val descriptor: SerialDescriptor = CheckstyleErrorJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CheckstyleError) {
        val json = CheckstyleErrorJson(
            severity = value.severity,
            key = value.key,
            location = value.location,
            message = value.message,
            sourceName = value.sourceName,
        )
        encoder.encodeSerializableValue(CheckstyleErrorJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): CheckstyleError {
        val json = decoder.decodeSerializableValue(CheckstyleErrorJson.serializer())
        return CheckstyleError(
            severity = json.severity,
            key = json.key,
            location = json.location,
            message = json.message,
            sourceName = json.sourceName,
        )
    }
}

@Serializable
data class KtLintErrorJson(
    val ruleId: String,
    val detail: String,
    val location: SourceLocation,
)

object KtLintErrorSerializer : KSerializer<KtLintError> {
    override val descriptor: SerialDescriptor = KtLintErrorJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: KtLintError) {
        val json = KtLintErrorJson(
            ruleId = value.ruleId,
            detail = value.detail,
            location = value.location,
        )
        encoder.encodeSerializableValue(KtLintErrorJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): KtLintError {
        val json = decoder.decodeSerializableValue(KtLintErrorJson.serializer())
        return KtLintError(
            ruleId = json.ruleId,
            detail = json.detail,
            location = json.location,
        )
    }
}

@Serializable
data class CompilationMessageJson(
    val kind: String,
    val location: SourceLocation?,
    val message: String,
)

object CompilationMessageSerializer : KSerializer<CompilationMessage> {
    override val descriptor: SerialDescriptor = CompilationMessageJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: CompilationMessage) {
        val json = CompilationMessageJson(
            kind = value.kind,
            location = value.location,
            message = value.message,
        )
        encoder.encodeSerializableValue(CompilationMessageJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): CompilationMessage {
        val json = decoder.decodeSerializableValue(CompilationMessageJson.serializer())
        return CompilationMessage(
            kind = json.kind,
            location = json.location,
            message = json.message,
        )
    }
}
