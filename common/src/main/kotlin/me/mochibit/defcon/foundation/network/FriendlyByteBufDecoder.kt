package me.mochibit.defcon.foundation.network

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.AbstractDecoder
import kotlinx.serialization.encoding.CompositeDecoder
import net.minecraft.network.FriendlyByteBuf

@OptIn(ExperimentalSerializationApi::class)
class FriendlyByteBufDecoder(
    internal val buf: FriendlyByteBuf,
) : AbstractDecoder() {
    private var elementIndex = 0
    private var collectionSize = -1
    override val serializersModule = MinecraftSerializersModule

    override fun decodeBoolean() = buf.readBoolean()

    override fun decodeByte() = buf.readByte()

    override fun decodeShort() = buf.readShort()

    override fun decodeInt() = buf.readVarInt()

    override fun decodeLong() = buf.readLong()

    override fun decodeFloat() = buf.readFloat()

    override fun decodeDouble() = buf.readDouble()

    override fun decodeString() = buf.readUtf()

    override fun decodeEnum(enumDescriptor: SerialDescriptor) = buf.readVarInt()

    override fun decodeNull(): Nothing? = null

    override fun decodeNotNullMark() = buf.readBoolean()

    override fun decodeSequentially() = true

    override fun decodeElementIndex(descriptor: SerialDescriptor): Int {
        val limit = if (collectionSize >= 0) collectionSize else descriptor.elementsCount
        if (elementIndex == limit) return CompositeDecoder.DECODE_DONE
        return elementIndex++
    }

    override fun decodeCollectionSize(descriptor: SerialDescriptor): Int {
        collectionSize = buf.readVarInt()
        return collectionSize
    }

    override fun beginStructure(descriptor: SerialDescriptor) = FriendlyByteBufDecoder(buf)
}