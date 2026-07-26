package me.mochibit.defcon.commands

import com.mojang.brigadier.CommandDispatcher
import com.mojang.brigadier.arguments.IntegerArgumentType
import me.mochibit.defcon.commands.DefconCommands.registerCommand
import me.mochibit.defcon.content.explosion.NuclearExplosion
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext
import me.mochibit.defcon.foundation.info
import net.minecraft.commands.CommandBuildContext
import net.minecraft.commands.CommandSourceStack
import net.minecraft.commands.Commands
import net.minecraft.core.BlockPos
import net.minecraft.network.chat.Component
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.phys.BlockHitResult
import net.minecraft.world.phys.HitResult


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
                            val player = source.playerOrException
                            val level = source.level

                            val hitResult = player.pick(600.0, 0.0f, false)

                            val pos = if (hitResult.type == HitResult.Type.BLOCK) {
                                (hitResult as BlockHitResult).blockPos
                            } else {
                                BlockPos.containing(player.position())
                            }

                            NuclearExplosion(level, pos).trigger()
                            source.sendSuccess({ Component.literal("Triggered nuclear explosion at $pos") }, true)
                            1
                        }
                )
                .then(
                    Commands.literal("heightMapValues").executes { ctx ->
                        val source = ctx.source
                        val pos = BlockPos.containing(source.position)
                        val level = source.level

                        val generator = level.chunkSource.generator
                        val randomState = level.chunkSource.randomState()

                        val message = Component.literal(
                            "Height map values at: ${pos.x}, ${pos.z}\n" +
                                    "WORLD SURFACE: ${level.getHeight(Heightmap.Types.WORLD_SURFACE, pos.x, pos.z)}\n" +
                                    "WORLD SURFACE WG: ${
                                        level.getHeight(
                                            Heightmap.Types.WORLD_SURFACE_WG,
                                            pos.x,
                                            pos.z
                                        )
                                    }\n" +
                                    "MOTION_BLOCKING: ${
                                        level.getHeight(
                                            Heightmap.Types.MOTION_BLOCKING,
                                            pos.x,
                                            pos.z
                                        )
                                    }\n" +
                                    "MOTION_BLOCKING_NO_LEAVES: ${
                                        level.getHeight(
                                            Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                                            pos.x,
                                            pos.z
                                        )
                                    }\n" +
                                    "CHUNK GENERATOR: ${
                                        generator.getBaseHeight(
                                            pos.x,
                                            pos.z,
                                            Heightmap.Types.WORLD_SURFACE_WG,
                                            level,
                                            randomState
                                        )
                                    }\n" +
                                    "OCEAN_FLOOR: ${level.getHeight(Heightmap.Types.OCEAN_FLOOR, pos.x, pos.z)}"
                        )

                        source.sendSystemMessage(message)
                        1
                    }
                )
                .then(
                    Commands.literal("burnTree")
                        .then(
                            Commands.argument("radius", IntegerArgumentType.integer(1, 50))
                                .executes { ctx ->
                                    val source = ctx.source
                                    val player = source.playerOrException
                                    val level = source.level

                                    val radius = IntegerArgumentType.getInteger(ctx, "radius")
                                    val centerPos = BlockPos.containing(player.position())

                                    val contexts = mutableMapOf<net.minecraft.world.level.ChunkPos, WorldGenColumnCarveContext>()

                                    for (x in -radius..radius) {
                                        for (z in -radius..radius) {
                                            val absX = centerPos.x + x
                                            val absZ = centerPos.z + z

                                            var treeVerified: Boolean? = null
                                            var trunkTopY: Int? = null
                                            var trunkBaseY: Int? = null

                                            val height = level.getHeight(
                                                Heightmap.Types.WORLD_SURFACE,
                                                absX,
                                                absZ
                                            )

                                            val chunkPos = net.minecraft.world.level.ChunkPos(absX shr 4, absZ shr 4)
                                            val wgCarveContext = contexts.getOrPut(chunkPos) {
                                                WorldGenColumnCarveContext(
                                                    level,
                                                    chunk = level.getChunk(chunkPos.x, chunkPos.z),
                                                    center = centerPos
                                                )
                                            }

                                            for (currentY in height downTo 63) {
                                                val currentState = level.getBlockState(BlockPos(absX, currentY, absZ))

                                                if (wgCarveContext.isTreeBlock(absX, currentY, absZ)) {
                                                    if (treeVerified == null) {
                                                        treeVerified = wgCarveContext.isColumnTreeLike(absX, currentY, absZ)
                                                    }

                                                    if (treeVerified == true) {
                                                        if (trunkTopY == null && wgCarveContext.isLogOrWood(currentState)) {
                                                            trunkTopY = currentY
                                                            trunkBaseY = wgCarveContext.findLocalTrunkBase(absX, currentY, absZ)
                                                        }
                                                        wgCarveContext.burnTreeBlock(
                                                            absX,
                                                            currentY,
                                                            absZ,
                                                            0.4,
                                                            trunkTopY,
                                                            trunkBaseY
                                                        )
                                                        continue
                                                    }
                                                }
                                            }
                                        }
                                    }

                                    contexts.values.forEach { it.flushClientUpdates() }

                                    1
                                }
                        )
                )
        )
    }
}