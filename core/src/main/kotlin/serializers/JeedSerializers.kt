package edu.illinois.cs.cs125.jeed.core.serializers

import edu.illinois.cs.cs125.jeed.core.CheckstyleError
import edu.illinois.cs.cs125.jeed.core.CheckstyleFailed
import edu.illinois.cs.cs125.jeed.core.CompilationFailed
import edu.illinois.cs.cs125.jeed.core.ComplexityFailed
import edu.illinois.cs.cs125.jeed.core.FeatureMap
import edu.illinois.cs.cs125.jeed.core.FeaturesFailed
import edu.illinois.cs.cs125.jeed.core.KtLintError
import edu.illinois.cs.cs125.jeed.core.KtLintFailed
import edu.illinois.cs.cs125.jeed.core.MutationsFailed
import edu.illinois.cs.cs125.jeed.core.Snippet
import edu.illinois.cs.cs125.jeed.core.SnippetTransformationFailed
import edu.illinois.cs.cs125.jeed.core.SourceError
import edu.illinois.cs.cs125.jeed.core.TemplatingFailed
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual
import kotlinx.serialization.modules.polymorphic
import kotlinx.serialization.modules.subclass
import java.security.Permission
import java.time.Instant

val JeedSerializersModule = SerializersModule {
    contextual(InstantSerializer)
    contextual(PermissionSerializer)
    contextual(SnippetSerializer)
    contextual(FeatureMapSerializer)
    contextual(SnippetTransformationFailedSerializer)
    contextual(TemplatingFailedSerializer)
    contextual(CompilationFailedSerializer)
    contextual(CheckstyleFailedSerializer)
    contextual(KtLintFailedSerializer)
    contextual(ComplexityFailedSerializer)
    contextual(FeaturesFailedSerializer)
    contextual(MutationsFailedSerializer)
    contextual(CheckstyleErrorSerializer)
    contextual(KtLintErrorSerializer)
    contextual(CompilationMessageSerializer)
}

val JeedJson = Json {
    serializersModule = JeedSerializersModule
    ignoreUnknownKeys = true
    encodeDefaults = true
}
