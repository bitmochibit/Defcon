package me.mochibit.defcon.foundation.eventbus

import net.minecraft.server.MinecraftServer
import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Entity

object ServerEvents {
    data class ServerStartedEvent(
        val server: MinecraftServer,
    ) : ServerProxyEvent {
        override val side: LogicalSide = LogicalSide.SERVER
    }

    data class ServerStoppedEvent(
        val server: MinecraftServer,
    ) : ServerProxyEvent {
        override val side: LogicalSide = LogicalSide.SERVER
    }

    data class PlayerStartTrackingEntity(
        val player: ServerPlayer,
        val entity: Entity,
    ) : ServerProxyEvent {
        override val side: LogicalSide = LogicalSide.SERVER
    }

    data class PlayerStopTrackingEntity(
        val player: ServerPlayer,
        val entity: Entity,
    ) : ServerProxyEvent {
        override val side: LogicalSide = LogicalSide.SERVER
    }
}