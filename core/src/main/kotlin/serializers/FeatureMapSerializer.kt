package edu.illinois.cs.cs125.jeed.core.serializers

import edu.illinois.cs.cs125.jeed.core.FeatureMap
import edu.illinois.cs.cs125.jeed.core.FeatureName
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

object FeatureMapSerializer : KSerializer<FeatureMap> {
    private val delegateSerializer = MapSerializer(String.serializer(), Int.serializer())
    override val descriptor: SerialDescriptor = delegateSerializer.descriptor

    override fun serialize(encoder: Encoder, value: FeatureMap) {
        val stringMap = value.map.mapKeys { it.key.name }
        encoder.encodeSerializableValue(delegateSerializer, stringMap)
    }

    override fun deserialize(decoder: Decoder): FeatureMap {
        val stringMap = decoder.decodeSerializableValue(delegateSerializer)
        val featureMap = FeatureMap()
        stringMap.forEach { (key, value) ->
            featureMap[FeatureName.valueOf(key)] = value
        }
        return featureMap
    }
}
