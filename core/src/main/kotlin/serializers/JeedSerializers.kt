package edu.illinois.cs.cs125.jeed.core.serializers

import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.contextual

val JeedSerializersModule = SerializersModule {
    // Only register serializers for third-party types we don't own
    contextual(InstantSerializer)
    contextual(PermissionSerializer)
}

val JeedJson = Json {
    serializersModule = JeedSerializersModule
    ignoreUnknownKeys = true
    encodeDefaults = true
    explicitNulls = false
}
