package me.mochibit.defcon.foundation.services


import me.mochibit.defcon.foundation.network.packet.ModPacket
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

interface NetworkService {
    fun sendToServer(packet: ModPacket)

    fun sendToPlayer(
        player: ServerPlayer,
        packet: ModPacket,
    )

    fun sendToTrackingEntity(
        packet: ModPacket,
        entity: Entity,
    )

    fun broadcast(packet: ModPacket)
}

val networkService: NetworkService by lazy {
    loadService<NetworkService>()
}