package me.mochibit.defcon.foundation.network
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractEncoder
import kotlinx.serialization.encoding.CompositeEncoder
import net.minecraft.network.FriendlyByteBuf

@OptIn(ExperimentalSerializationApi::class)
class FriendlyByteBufEncoder(
    internal val buf: FriendlyByteBuf,
) : AbstractEncoder() {
    override val serializersModule = MinecraftSerializersModule

    override fun encodeBoolean(value: Boolean) {
        buf.writeBoolean(value)
    }

    override fun encodeByte(value: Byte) {
        buf.writeByte(value.toInt())
    }

    override fun encodeShort(value: Short) {
        buf.writeShort(value.toInt())
    }

    override fun encodeInt(value: Int) {
        buf.writeVarInt(value)
    }

    override fun encodeLong(value: Long) {
        buf.writeLong(value)
    }

    override fun encodeFloat(value: Float) {
        buf.writeFloat(value)
    }

    override fun encodeDouble(value: Double) {
        buf.writeDouble(value)
    }

    override fun encodeString(value: String) {
        buf.writeUtf(value)
    }

    override fun encodeEnum(
        enumDescriptor: SerialDescriptor,
        index: Int,
    ) {
        buf.writeVarInt(index)
    }

    override fun encodeNull() {
        buf.writeBoolean(false)
    }

    override fun encodeNotNullMark() {
        buf.writeBoolean(true)
    }

    override fun beginCollection(
        descriptor: SerialDescriptor,
        collectionSize: Int,
    ): CompositeEncoder {
        buf.writeVarInt(collectionSize)
        return this
    }

    override fun beginStructure(descriptor: SerialDescriptor): CompositeEncoder = FriendlyByteBufEncoder(buf)
}