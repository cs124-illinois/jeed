package edu.illinois.cs.cs125.jeed.core.serializers

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.security.Permission

@Serializable
@SerialName("Permission")
data class PermissionJson(val klass: String, val name: String, val actions: String? = null)

object PermissionSerializer : KSerializer<Permission> {
    override val descriptor: SerialDescriptor = PermissionJson.serializer().descriptor

    override fun serialize(encoder: Encoder, value: Permission) {
        val json = PermissionJson(
            klass = value.javaClass.name,
            name = value.name ?: "",
            actions = value.actions,
        )
        encoder.encodeSerializableValue(PermissionJson.serializer(), json)
    }

    override fun deserialize(decoder: Decoder): Permission {
        val json = decoder.decodeSerializableValue(PermissionJson.serializer())
        val permissionClass = Class.forName(json.klass)

        return try {
            if (json.actions != null) {
                val constructor = permissionClass.getConstructor(String::class.java, String::class.java)
                constructor.newInstance(json.name, json.actions) as Permission
            } else {
                val constructor = permissionClass.getConstructor(String::class.java)
                constructor.newInstance(json.name) as Permission
            }
        } catch (e: Exception) {
            error("Failed to deserialize Permission: ${json.klass}(${json.name}, ${json.actions}): ${e.message}")
        }
    }
}
