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
import me.mochibit.defcon.commands.CommandInfo
import me.mochibit.defcon.commands.GenericCommand
import me.mochibit.defcon.registers.ItemRegister
import org.bukkit.Bukkit
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Implementation of the 'give' command for providing plugin items to players
 */
@CommandInfo(
    name = "give",
    permission = "defcon.admin",
    requiresPlayer = false,
    description =
        "This command gives a specified <bold> Defcon </bold> item to a player. " +
        "If no player is specified, the item is given to the command executor."
)
class GiveCommand : GenericCommand() {

    override fun getArguments(): ArgumentBuilder<CommandSourceStack, *> {
        return Commands
            .argument("item", StringArgumentType.word())
            .suggests(::suggestItems)
            .then(
                Commands.argument("player", StringArgumentType.word())
                    .suggests(::suggestPlayers)
                    .executes(::handleFullCommand)
            )
            .executes(::handleExecutorAsTarget) // Allow execution without player argument
    }

    /**
     * Provides item suggestions based on the registered items
     */
    private fun suggestItems(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val prefix = builder.remainingLowerCase
        ItemRegister.registeredItems.keys
            .filterNotNull()
            .filter { it.lowercase().startsWith(prefix) }
            .forEach(builder::suggest)

        return builder.buildFuture()
    }

    /**
     * Provides player suggestions based on online players
     */
    private fun suggestPlayers(
        context: CommandContext<CommandSourceStack>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val prefix = builder.remainingLowerCase
        Bukkit.getServer().onlinePlayers
            .map { it.name }
            .filter { it.lowercase().startsWith(prefix) }
            .forEach(builder::suggest)

        return builder.buildFuture()
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
                itemId
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

        // Find target player
        val targetPlayer = Bukkit.getServer().getPlayer(playerName)

        if (targetPlayer == null || !targetPlayer.isOnline) {
            sendMessage(sender, "Player '$playerName' not found or not online", isError = true)
            return Command.SINGLE_SUCCESS
        }

        return giveItemToPlayer(sender, targetPlayer, itemId)
    }

    /**
     * Helper method to give an item to a player
     * @return Command execution result code
     */
    private fun giveItemToPlayer(sender: org.bukkit.command.CommandSender, targetPlayer: Player, itemId: String): Int {
        // Get the item from the register
        val item = ItemRegister.registeredItems[itemId]

        if (item == null) {
            sendMessage(sender, "Item with ID '$itemId' not found in the registry", isError = true)
            return Command.SINGLE_SUCCESS
        }

        // Give the item to the player
        try {
            val result = targetPlayer.inventory.addItem(item.itemStack)

            // Check if item was successfully added (empty result means success)
            if (result.isEmpty()) {
                val isSelf = sender == targetPlayer

                if (isSelf) {
                    sendMessage(sender, "You have been given a ${item.displayName}")
                } else {
                    sendMessage(sender, "Gave ${targetPlayer.name} a ${item.displayName}")
                    sendMessage(targetPlayer, "You received a ${item.displayName}")
                }
            } else {
                // Inventory was full
                sendMessage(sender, "Could not give item - player inventory is full", isError = true)
            }
        } catch (e: Exception) {
            sendMessage(sender, "Failed to give item: ${e.message}", isError = true)
            e.printStackTrace()
        }

        return Command.SINGLE_SUCCESS
    }
}