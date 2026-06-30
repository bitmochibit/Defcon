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

package me.mochibit.defcon.commands

import com.mojang.brigadier.arguments.ArgumentType
import com.mojang.brigadier.arguments.StringArgumentType
import com.mojang.brigadier.arguments.IntegerArgumentType
import com.mojang.brigadier.builder.ArgumentBuilder
import com.mojang.brigadier.builder.RequiredArgumentBuilder
import com.mojang.brigadier.context.CommandContext
import com.mojang.brigadier.suggestion.SuggestionProvider
import io.papermc.paper.command.brigadier.CommandSourceStack
import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.command.brigadier.argument.ArgumentTypes

/**
 * Utility class for building command arguments with enhanced validation and suggestions
 */
object CommandBuilder {

    /**
     * Creates a string argument with validation and suggestions
     */
    fun stringArgument(
        name: String,
        suggestions: List<String> = emptyList(),
        validator: ((String) -> Boolean)? = null
    ): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument(name, StringArgumentType.word()).apply {
            if (suggestions.isNotEmpty()) {
                suggests { _, builder ->
                    val prefix = builder.remainingLowerCase
                    suggestions
                        .filter { it.lowercase().startsWith(prefix) }
                        .forEach(builder::suggest)
                    builder.buildFuture()
                }
            }
        }
    }

    /**
     * Creates an integer argument with optional bounds
     */
    fun intArgument(
        name: String,
        min: Int = Int.MIN_VALUE,
        max: Int = Int.MAX_VALUE
    ): RequiredArgumentBuilder<CommandSourceStack, Int> {
        return Commands.argument(name, IntegerArgumentType.integer(min, max))
    }

    /**
     * Creates a player name argument with online player suggestions
     */
    fun playerArgument(name: String): RequiredArgumentBuilder<CommandSourceStack, String> {
        return Commands.argument(name, StringArgumentType.word())
            .suggests { _, builder ->
                val prefix = builder.remainingLowerCase
                org.bukkit.Bukkit.getServer().onlinePlayers
                    .map { it.name }
                    .filter { it.lowercase().startsWith(prefix) }
                    .forEach(builder::suggest)
                builder.buildFuture()
            }
    }

    /**
     * Creates a position argument
     */
    fun positionArgument(name: String): RequiredArgumentBuilder<CommandSourceStack, *> {
        return Commands.argument(name, ArgumentTypes.blockPosition())
    }

    /**
     * Creates an enumeration argument from a list of valid values
     */
    fun <T : Enum<T>> enumArgument(
        name: String,
        enumClass: Class<T>
    ): RequiredArgumentBuilder<CommandSourceStack, String> {
        val values = enumClass.enumConstants.map { it.name.lowercase() }
        return stringArgument(name, values) { input ->
            values.contains(input.lowercase())
        }
    }
}

/**
 * Command validation utilities
 */
object CommandValidation {

    /**
     * Validates that a player exists and is online
     */
    fun validatePlayer(ctx: CommandContext<CommandSourceStack>, playerName: String): Boolean {
        val player = org.bukkit.Bukkit.getPlayer(playerName)
        return player != null && player.isOnline
    }

    /**
     * Validates that a number is within a specified range
     */
    fun validateRange(value: Int, min: Int, max: Int): Boolean {
        return value in min..max
    }

    /**
     * Validates that a string matches one of the allowed values
     */
    fun validateEnum(value: String, allowedValues: List<String>): Boolean {
        return allowedValues.contains(value.lowercase())
    }
}
