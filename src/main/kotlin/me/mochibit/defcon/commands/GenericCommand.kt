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
import com.mojang.brigadier.tree.LiteralCommandNode
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

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
     * Checks if the executor has permission to run this command
     * @param context The command source context
     * @return true if the executor can run the command, false otherwise
     */
    open fun checkPermissions(context: CommandSourceStack): Boolean {
        val executor = context.executor

        // Check if command requires player
        if (commandInfo.requiresPlayer && executor !is Player) {
            return false
        }

        // Check permission if specified
        if (commandInfo.permission.isNotEmpty()) {
            return executor?.hasPermission(commandInfo.permission) ?: false
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
            ctx.source.sender.sendRichMessage("<red>An error occurred while executing this command")
            Command.SINGLE_SUCCESS
        }
    }

    /**
     * Main command execution logic to be implemented by subclasses. By default, it shows the description.
     * @param ctx The command context
     * @return Command result code
     */
    open fun commandLogic(ctx: CommandContext<CommandSourceStack>): Int {
        sendMessage(ctx.source.sender, commandInfo.description)
        return Command.SINGLE_SUCCESS
    }

    /**
     * Helper method to send a message to a command sender
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

    companion object {
        const val COMMAND_ROOT = "defcon"
    }
}

