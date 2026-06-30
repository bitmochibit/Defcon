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
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import me.mochibit.defcon.commands.CommandInfo
import me.mochibit.defcon.commands.GenericCommand
import me.mochibit.defcon.registry.ItemRegistry
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

/**
 * Implementation of the 'give' command for providing plugin items to players
 */
@CommandInfo(
    name = "give",
    aliases = ["item"],
    permission = "defcon.admin",
    adminOnly = true,
    requiresPlayer = false,
    description = "Give a specified Defcon item to a player. If no player is specified, the item is given to the command executor.",
    usage = "/defcon give <item> [player]",
)
class GiveCommand : GenericCommand() {
    override fun getArguments(): ArgumentBuilder<CommandSourceStack, *> {
        return Commands
            .argument("item", StringArgumentType.word())
            .suggests { _, builder ->
                suggestFromList(
                    ItemRegistry.registeredIds.toList(),
                    builder,
                )
            }.then(
                Commands
                    .argument("player", StringArgumentType.word())
                    .suggests(::suggestPlayers)
                    .executes(::handleFullCommand),
            ).executes(::handleExecutorAsTarget) // Allow execution without player argument
    }

    /**
     * Handles the case when the command is executed without specifying a player
     * Uses the command executor as the target if they're a player
     */
    private fun handleExecutorAsTarget(ctx: CommandContext<CommandSourceStack>): Int {
        val itemId = StringArgumentType.getString(ctx, "item")
        val sender = ctx.source.sender

        // If executed by a player, give item to that player
        if (ctx.source.executor is Player) {
            return giveItemToPlayer(
                sender,
                ctx.source.executor as Player,
                itemId,
            )
        }

        // Console must specify a player
        sendMessage(sender, "You must specify a player name when executing from console", isError = true)
        return Command.SINGLE_SUCCESS
    }

    private fun handleFullCommand(ctx: CommandContext<CommandSourceStack>): Int {
        val itemId = StringArgumentType.getString(ctx, "item")
        val playerName = StringArgumentType.getString(ctx, "player")
        val sender = ctx.source.sender

        // Find target player using helper method
        val targetPlayer = getPlayerByName(ctx, playerName) ?: return Command.SINGLE_SUCCESS

        return giveItemToPlayer(sender, targetPlayer, itemId)
    }

    /**
     * Helper method to give an item to a player
     * @return Command execution result code
     */
    private fun giveItemToPlayer(
        sender: CommandSender,
        targetPlayer: Player,
        itemId: String,
    ): Int {
        // Get the item from the register
        val item = ItemRegistry.get(itemId)

        if (item == null) {
            sendMessage(
                sender,
                "Item with ID '$itemId' not found in the registry. Available items: ${ItemRegistry.registeredIds.joinToString()}",
                isError = true,
            )
            return Command.SINGLE_SUCCESS
        }

        // Give the item to the player
        try {
            val result = targetPlayer.inventory.addItem(item.itemStack)

            // Check if item was successfully added (empty result means success)
            if (result.isEmpty()) {
                val isSelf = sender == targetPlayer

                if (isSelf) {
                    sendSuccess(sender, "You have been given a ${item.properties.displayName}")
                } else {
                    sendSuccess(sender, "Gave ${targetPlayer.name} a ${item.properties.displayName}")
                    sendSuccess(targetPlayer, "You received a ${item.properties.displayName}")
                }
            } else {
                // Inventory was full
                sendWarning(sender, "Could not give item - player inventory is full")
            }
        } catch (e: Exception) {
            sendMessage(sender, "Failed to give item: ${e.message}", isError = true)
            e.printStackTrace()
        }

        return Command.SINGLE_SUCCESS
    }
}
