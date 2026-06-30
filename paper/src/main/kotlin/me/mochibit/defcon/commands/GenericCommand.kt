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

package me.mochibit.defcon.commands

import com.mojang.brigadier.Command
import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.LiteralArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.Suggestions
import com.mojang.brigadier.suggestion.SuggestionsBuilder
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.Bukkit
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.concurrent.CompletableFuture

/**
 * Abstract base class for all plugin commands
 * Provides common functionality and enforces a consistent command structure
 */
abstract class GenericCommand {
    // Retrieve command information from annotation or throw a meaningful exception
    val commandInfo: CommandInfo = this::class.annotations
        .filterIsInstance<CommandInfo>()
        .firstOrNull()
        ?: throw IllegalStateException("Command class ${this::class.java.simpleName} is missing required @CommandInfo annotation")

    /**
     * Builds and returns the command node for Brigadier registration
     * @return The built command node
     */
    fun getCommand(): LiteralArgumentBuilder<CommandSourceStack> {
        val commandName = commandInfo.name.lowercase()

        return Commands.literal(commandName)
            .requires(::checkPermissions)
            .executes(::safeCommandExecution)
            .let { commandNode ->
                val arguments = getArguments()
                if (arguments != null) {
                    commandNode.then(arguments)
                }
                commandNode
            }
    }

    /**
     * Defines command arguments and structure
     * @return The argument builder for this command, or null if no arguments
     */
    open fun getArguments(): ArgumentBuilder<CommandSourceStack, *>? {
        return null
    }

    /**
     * Enhanced permission checking with admin override and better logging
     * @param context The command source context
     * @return true if the executor can run the command, false otherwise
     */
    open fun checkPermissions(context: CommandSourceStack): Boolean {
        val executor = context.executor
        val sender = context.sender

        // Check if command requires player
        if (commandInfo.requiresPlayer && executor !is Player) {
            return false
        }

        // Admin-only commands check
        if (commandInfo.adminOnly) {
            val hasAdminPerm = executor?.hasPermission("defcon.admin") ?: false
            val isOp = executor?.isOp ?: false
            if (!hasAdminPerm && !isOp) {
                return false
            }
        }

        // Check specific permission if specified
        if (commandInfo.permission.isNotEmpty()) {
            val hasPermission = executor?.hasPermission(commandInfo.permission) ?: false
            if (!hasPermission) {
                // Send helpful error message
                if (executor is Player) {
                    sendMessage(executor, "You don't have permission to use this command. Required: ${commandInfo.permission}", true)
                }
                return false
            }
        }

        return true
    }

    /**
     * Wraps command execution in a try-catch to prevent unhandled exceptions
     * @param ctx The command context
     * @return Command result code
     */
    private fun safeCommandExecution(ctx: CommandContext<CommandSourceStack>): Int {
        return try {
            commandLogic(ctx)
        } catch (e: Exception) {
            val sender = ctx.source.sender
            sendMessage(sender, "An error occurred while executing this command: ${e.message}", true)

            // Log the full stack trace for debugging
            e.printStackTrace()
            Command.SINGLE_SUCCESS
        }
    }

    /**
     * Main command execution logic to be implemented by subclasses. By default, it shows the description.
     * @param ctx The command context
     * @return Command result code
     */
    open fun commandLogic(ctx: CommandContext<CommandSourceStack>): Int {
        val description = commandInfo.description.ifEmpty { "No description available for this command." }
        sendMessage(ctx.source.sender, description)

        // Show usage if available
        if (commandInfo.usage.isNotEmpty()) {
            sendMessage(ctx.source.sender, "<gray>Usage: ${commandInfo.usage}")
        }

        return Command.SINGLE_SUCCESS
    }

    /**
     * Helper method to send a message to a command sender with consistent formatting
     * @param sender The command sender to message
     * @param message The message to send
     * @param isError Whether this is an error message
     */
    protected fun sendMessage(sender: CommandSender, message: String, isError: Boolean = false) {
        val prefix = if (isError)
            "<gradient:#ED213A:#93291E> <bold> DEFCON ☢</bold> </gradient>"
        else
            "<gradient:#a8ff78:#78ffd6> <bold> DEFCON ☢</bold> </gradient>"
        sender.sendRichMessage("$prefix $message")
    }

    /**
     * Helper method for player name suggestions
     */
    protected fun suggestPlayers(
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
     * Helper method for creating simple string suggestions
     */
    protected fun suggestFromList(
        suggestions: List<String>,
        builder: SuggestionsBuilder
    ): CompletableFuture<Suggestions> {
        val prefix = builder.remainingLowerCase
        suggestions
            .filter { it.lowercase().startsWith(prefix) }
            .forEach(builder::suggest)
        return builder.buildFuture()
    }

    /**
     * Validates that the command executor is a player
     */
    protected fun requirePlayer(ctx: CommandContext<CommandSourceStack>): Player? {
        val executor = ctx.source.executor
        if (executor !is Player) {
            sendMessage(ctx.source.sender, "This command can only be executed by a player.", true)
            return null
        }
        return executor
    }

    /**
     * Gets a player by name with error handling
     */
    protected fun getPlayerByName(ctx: CommandContext<CommandSourceStack>, playerName: String): Player? {
        val targetPlayer = Bukkit.getServer().getPlayer(playerName)
        if (targetPlayer == null || !targetPlayer.isOnline) {
            sendMessage(ctx.source.sender, "Player '$playerName' not found or not online", true)
            return null
        }
        return targetPlayer
    }

    /**
     * Sends a success message with consistent formatting
     */
    protected fun sendSuccess(sender: CommandSender, message: String) {
        sendMessage(sender, "<green>✓</green> $message")
    }

    /**
     * Sends a warning message with consistent formatting
     */
    protected fun sendWarning(sender: CommandSender, message: String) {
        sendMessage(sender, "<yellow>⚠</yellow> $message")
    }

    companion object {
        const val COMMAND_ROOT = "defcon"
    }
}
