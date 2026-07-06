package me.mochibit.defcon.foundation.network.packet

import kotlinx.serialization.serializer
import me.mochibit.defcon.foundation.network.FriendlyByteBufDecoder
import me.mochibit.defcon.foundation.network.FriendlyByteBufEncoder
import me.mochibit.defcon.foundation.network.NetDirection
import net.minecraft.network.FriendlyByteBuf
import net.minecraft.server.level.ServerPlayer

sealed interface ModPacket {
    fun handle(context: Context): Boolean

    data class Context(
        val sender: ServerPlayer?,
    )
}

inline fun <reified T : ModPacket> T.writeTo(buf: FriendlyByteBuf) {
    serializer<T>().serialize(FriendlyByteBufEncoder(buf), this)
}

inline fun <reified T : ModPacket> FriendlyByteBuf.readPacket(): T =
    serializer<T>().deserialize(
        FriendlyByteBufDecoder(
            this,
        ),
    )

interface HasNetDirection {
    val netDirection: NetDirection
}

interface C2SPacket : HasNetDirection {
    override val netDirection: NetDirection
        get() = NetDirection.C2S

    fun handleClient(context: ModPacket.Context): Boolean = false
}

interface S2CPacket : HasNetDirection {
    override val netDirection: NetDirection
        get() = NetDirection.S2C

    fun handleServer(context: ModPacket.Context): Boolean = false
}