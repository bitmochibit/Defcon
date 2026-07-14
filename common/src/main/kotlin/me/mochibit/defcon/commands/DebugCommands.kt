package me.mochibit.defcon.commands

import com.mojang.brigadier.CommandDispatcher
import me.mochibit.defcon.commands.DefconCommands.registerCommand
import me.mochibit.defcon.explosions.NuclearExplosion
import me.mochibit.defcon.foundation.info
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.levelgen.Heightmap


sealed interface CommandEntry {
    fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    )

    companion object {
        fun registerAll(
            dispatcher: CommandDispatcher<CommandSourceStack>,
            env: Commands.CommandSelection,
            context: CommandBuildContext,
        ) {
            "Registering mod commands..".info()
            CommandEntry::class.sealedSubclasses.filter { CommandEntry::class.java.isAssignableFrom(it.java) }
                .map { it.objectInstance as CommandEntry }.forEach { _ -> registerCommand(dispatcher, env, context) }
        }
    }
}

object DefconCommands : CommandEntry {
    override fun registerCommand(
        dispatcher: CommandDispatcher<CommandSourceStack>,
        env: Commands.CommandSelection,
        context: CommandBuildContext,
    ) {
        dispatcher.register(
            Commands.literal("defcon").requires { it.hasPermission(2) }
                .then(
                Commands.literal("nuke")
                    .executes { ctx ->
                    val source = ctx.source
                    val pos = BlockPos.containing(source.position)

                    NuclearExplosion(source.level, pos).trigger()
                    source.sendSuccess({ Component.literal("Triggered nuclear explosion at $pos") }, true)
                    1
                }).then(
                    Commands.literal("heightMapValues").executes { ctx ->
                        val source = ctx.source
                        val pos = BlockPos.containing(source.position)
                        val level = source.level

                        val generator = level.chunkSource.generator
                        val randomState = level.chunkSource.randomState()

                        val message = Component.literal(
                            "Height map values at: ${pos.x}, ${pos.z}\n" +
                                    "WORLD SURFACE: ${level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.x, pos.z)}\n" +
                                    "WORLD SURFACE WG: ${level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, pos.x, pos.z)}\n" +
                                    "MOTION_BLOCKING: ${level.getHeight(Heightmap.Types.MOTION_BLOCKING, pos.x, pos.z)}\n" +
                                    "MOTION_BLOCKING_NO_LEAVES: ${level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, pos.x, pos.z)}\n" +
                                    "CHUNK GENERATOR: ${generator.getBaseHeight(pos.x, pos.z, Heightmap.Types.WORLD_SURFACE_WG, level, randomState)}\n" +
                                    "OCEAN_FLOOR: ${level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.x, pos.z)}"
                        )

                        source.sendSystemMessage(message)
                        1
                    }
                )
        )
    }
}