package me.mochibit.defcon.foundation.eventbus


import com.mojang.brigadier.CommandDispatcher
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.world.entity.Entity
import net.minecraft.world.item.crafting.Recipe
import net.minecraft.world.item.crafting.RecipeHolder
import net.minecraft.world.item.crafting.RecipeInput
import net.minecraft.world.level.Level
import net.minecraft.world.level.LevelAccessor
import java.util.Optional
import java.util.function.Supplier

/**
 * This event type is used to register platform specific event handlers that are required even for the common layer
 */
sealed interface ProxyEvent : ModEvent {
    val side: LogicalSide
}

sealed interface ClientProxyEvent : ProxyEvent

sealed interface ServerProxyEvent : ProxyEvent

object CommonEvents {
    data class EntityJoinLevelEvent(
        val entity: Entity,
        val level: Level,
        override val side: LogicalSide,
    ) : ClientProxyEvent,
        ServerProxyEvent

    data class RegisterCommandsEvent(
        val dispatcher: CommandDispatcher<CommandSourceStack>,
        val env: Commands.CommandSelection,
        val context: CommandBuildContext,
        override val side: LogicalSide,
    ) : ClientProxyEvent,
        ServerProxyEvent

    data class LevelUnloadEvent(
        val levelAccess: LevelAccessor,
        override val side: LogicalSide,
    ) : ClientProxyEvent,
        ServerProxyEvent

    class GameShuttingDownEvent(
        override val side: LogicalSide,
    ) : ClientProxyEvent,
        ServerProxyEvent

}

object TickEvents {
    enum class Type {
        LEVEL,
        PLAYER,
        CLIENT,
        SERVER,
        RENDER,
    }

    enum class Phase {
        START,
        END,
    }

    data class ClientTickEvent(
        val type: Type,
        val phase: Phase,
    ) : ClientProxyEvent {
        override val side: LogicalSide = LogicalSide.CLIENT
    }
}