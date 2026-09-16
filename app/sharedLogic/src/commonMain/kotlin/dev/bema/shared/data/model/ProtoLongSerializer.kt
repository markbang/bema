package dev.bema.shared.data.model

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerializationException
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.longOrNull

object ProtoLongSerializer : KSerializer<Long> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ProtoLong", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Long) {
        encoder.encodeString(value.toString())
    }

    override fun deserialize(decoder: Decoder): Long {
        if (decoder is JsonDecoder) {
            val primitive = decoder.decodeJsonElement() as? JsonPrimitive
                ?: throw SerializationException("Expected a protojson int64 value")
            return primitive.longOrNull
                ?: primitive.content.toLongOrNull()
                ?: throw SerializationException("Invalid protojson int64 value: ${primitive.content}")
        }
        return decoder.decodeString().toLongOrNull()
            ?: throw SerializationException("Invalid proto int64 value")
    }
}
