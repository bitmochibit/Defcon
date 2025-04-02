/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

@file:Suppress("UnstableApiUsage")

package me.mochibit.defcon.commands.definitions

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import io.papermc.paper.command.brigadier.argument.resolvers.BlockPositionResolver
import io.papermc.paper.math.BlockPosition
import me.mochibit.defcon.commands.CommandInfo
import me.mochibit.defcon.commands.GenericCommand
import me.mochibit.defcon.explosions.types.Explosion
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture
import kotlin.reflect.KClass
import kotlin.reflect.KParameter
import kotlin.reflect.full.primaryConstructor

@CommandInfo("explode", "defcon.admin", false)
class ExplodeCommand : GenericCommand() {
    companion object {
        // Cache explosion classes on load
        val explosionClasses: List<KClass<out Explosion>> by lazy {
            Explosion::class.sealedSubclasses.filter { subclass ->
                Explosion::class.java.isAssignableFrom(subclass.java)
            }.map { it }
        }

        // Maximum raycast distance
        private const val MAX_RAYCAST_DISTANCE = 100.0
    }

    // Simplified suggestion provider
    private val explosionNamesSuggestion: (CommandContext<CommandSourceStack>, SuggestionsBuilder) -> CompletableFuture<Suggestions> = { _, builder ->
        explosionClasses.forEach { explosionClass ->
            explosionClass.simpleName?.let { builder.suggest(it) }
        }
        builder.buildFuture()
    }

    override fun getArguments(): ArgumentBuilder<CommandSourceStack, *> {
        return Commands
            .argument("explosionName", StringArgumentType.word())
            .suggests(explosionNamesSuggestion)
            .then(
                Commands.literal("pos")
                    .then(
                        Commands.argument("coordinate", ArgumentTypes.blockPosition())
                            .executes(::handleWithCoordinate)
                    )
            )
            .then(
                Commands.literal("raycast")
                    .executes(::handleWithRaycast)
            )
    }

    // We don't need this method since all our executions are handled by specific methods
    override fun commandLogic(ctx: CommandContext<CommandSourceStack>): Int {
        return Command.SINGLE_SUCCESS
    }

    private fun handleWithRaycast(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender

        if (sender !is Player) {
            sendMessage(sender, "This command can only be executed by a player.", true)
            return Command.SINGLE_SUCCESS
        }

        // Get the block the player is looking at
        val raycastResult = sender.world.rayTraceBlocks(
            sender.eyeLocation,
            sender.location.direction,
            MAX_RAYCAST_DISTANCE,
            FluidCollisionMode.SOURCE_ONLY,
            false
        )

        val hitBlock = raycastResult?.hitBlock
        if (hitBlock == null) {
            sendMessage(sender, "No block found within range. Try looking at a block.", true)
            return Command.SINGLE_SUCCESS
        }

        val explosionName = StringArgumentType.getString(ctx, "explosionName")

        return createExplosion(sender, explosionName, hitBlock.location)
    }

    private fun handleWithCoordinate(ctx: CommandContext<CommandSourceStack>): Int {
        val sender = ctx.source.sender
        val explosionName = StringArgumentType.getString(ctx, "explosionName")
        val blockPositionResolver = ctx.getArgument("coordinate", BlockPositionResolver::class.java)
        val coordinate = blockPositionResolver.resolve(ctx.source)
        val world = ctx.source.executor?.world ?: run {
            sendMessage(sender, "Could not determine world for explosion.", true)
            return Command.SINGLE_SUCCESS
        }

        return createExplosion(sender, explosionName, coordinate.toLocation(world))
    }

    private fun createExplosion(sender: CommandSender, explosionName: String, location: Location): Int {
        // Find the requested explosion type
        val explosionClass = explosionClasses.find { it.simpleName == explosionName }

        if (explosionClass == null) {
            sendMessage(sender, "Unknown explosion type: $explosionName. Available types: ${explosionClasses.mapNotNull { it.simpleName }.joinToString()}", true)
            return Command.SINGLE_SUCCESS
        }

        // Get default explosion component

        try {
            // Create and trigger the explosion


            val centerParam = explosionClass.primaryConstructor?.parameters?.find { it.name == "center" }
                ?: throw IllegalStateException("No center parameter found for explosion class $explosionName")

            val params = mapOf(
                centerParam to location
            )

            val explosionInstance = explosionClass.primaryConstructor?.callBy(params)
                ?: throw IllegalStateException("Could not instantiate explosion of type $explosionName")

            explosionInstance.explode()

            sendMessage(sender, "Created explosion of type $explosionName at ${location.blockX}, ${location.blockY}, ${location.blockZ}", false)

            return Command.SINGLE_SUCCESS
        } catch (e: Exception) {
            sendMessage(sender, "Failed to create explosion: ${e.message}", true)
            e.printStackTrace()
            return 0
        }
    }
}