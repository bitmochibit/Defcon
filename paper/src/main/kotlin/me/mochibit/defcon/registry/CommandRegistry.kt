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

package me.mochibit.defcon.registry

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.commands.CommandInfo
import me.mochibit.defcon.commands.GenericCommand
import me.mochibit.defcon.utils.Logger
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.ConcurrentHashMap

object CommandRegistry {
    private val packageName: String = Defcon.javaClass.getPackage().name
    private val registeredCommands = ConcurrentHashMap<String, GenericCommand>()
    private var isInitialized = false

    /**
     * Registers all plugin commands with enhanced error handling and validation
     */
    fun registerCommands() {
        if (isInitialized) {
            Logger.warn("Command registry already initialized. Skipping duplicate registration.")
            return
        }

        Logger.info("Registering plugin commands")

        val commandBuilderLeafs = mutableListOf<GenericCommand>()
        var successCount = 0
        var failureCount = 0

        // Discover and instantiate commands with better error handling
        for (commandClass in Reflections("$packageName.commands").getSubTypesOf(GenericCommand::class.java)) {
            try {
                // Validate command class before instantiation
                val commandInfo = validateCommandClass(commandClass)
                if (commandInfo == null) {
                    Logger.warn("Skipping command class ${commandClass.simpleName}: Missing or invalid @CommandInfo annotation")
                    failureCount++
                    continue
                }

                // Check for duplicate command names
                if (isCommandNameTaken(commandInfo)) {
                    Logger.warn("Skipping command '${commandInfo.name}': Name already registered")
                    failureCount++
                    continue
                }

                val genericCommand = commandClass.getDeclaredConstructor().newInstance()
                commandBuilderLeafs.add(genericCommand)

                // Cache the command
                registeredCommands[commandInfo.name] = genericCommand
                commandInfo.aliases.forEach { alias ->
                    registeredCommands[alias] = genericCommand
                }

                Logger.info("✓ Registered command: '${commandInfo.name}'" +
                    if (commandInfo.aliases.isNotEmpty()) " (aliases: ${commandInfo.aliases.joinToString()})" else "")
                successCount++

            } catch (e: InstantiationException) {
                Logger.err("Failed to instantiate command ${commandClass.simpleName}: ${e.message}")
                failureCount++
            } catch (e: IllegalAccessException) {
                Logger.err("Access denied for command ${commandClass.simpleName}: ${e.message}")
                failureCount++
            } catch (e: InvocationTargetException) {
                Logger.err("Error invoking constructor for command ${commandClass.simpleName}: ${e.targetException?.message}")
                failureCount++
            } catch (e: NoSuchMethodException) {
                Logger.err("No default constructor found for command ${commandClass.simpleName}")
                failureCount++
            } catch (e: Exception) {
                Logger.err("Unexpected error registering command ${commandClass.simpleName}: ${e.message}")
                failureCount++
            }
        }

        if (commandBuilderLeafs.isEmpty()) {
            Logger.warn("No valid commands found to register!")
            return
        }

        // Build the command tree with improved structure
        val commandTree = Commands
            .literal(GenericCommand.COMMAND_ROOT)
            .executes { ctx ->
                // Show help when root command is executed
                showHelpMessage(ctx.source.sender)
                1
            }

        // Add all commands and their aliases to the tree
        for (command in commandBuilderLeafs) {
            val commandNode = command.getCommand()
            commandTree.then(commandNode)

            // Register aliases
            command.commandInfo.aliases.forEach { alias ->
                commandTree.then(
                    Commands.literal(alias.lowercase())
                        .requires(command::checkPermissions)
                        .redirect(commandNode.build())
                )
            }
        }

        // Register with Paper's lifecycle system
        try {
            Defcon.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
                commands.registrar().register(commandTree.build())
            }

            isInitialized = true
            Logger.info("Command registration completed: $successCount successful, $failureCount failed")
        } catch (e: Exception) {
            Logger.err("Failed to register command tree with Paper: ${e.message}")
        }
    }

    /**
     * Validates a command class has proper annotation
     */
    private fun validateCommandClass(commandClass: Class<out GenericCommand>): CommandInfo? {
        return try {
            commandClass.annotations
                .filterIsInstance<CommandInfo>()
                .firstOrNull()
                ?.takeIf { it.name.isNotBlank() }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Checks if a command name or any of its aliases are already taken
     */
    private fun isCommandNameTaken(commandInfo: CommandInfo): Boolean {
        if (registeredCommands.containsKey(commandInfo.name)) return true
        return commandInfo.aliases.any { registeredCommands.containsKey(it) }
    }

    /**
     * Shows help message for the root command
     */
    private fun showHelpMessage(sender: org.bukkit.command.CommandSender) {
        sender.sendRichMessage("<gradient:#a8ff78:#78ffd6> <bold> DEFCON ☢</bold> </gradient> Available commands:")

        registeredCommands.values.distinctBy { it.commandInfo.name }.forEach { command ->
            val info = command.commandInfo
            val aliases = if (info.aliases.isNotEmpty()) " (${info.aliases.joinToString()})" else ""
            val permission = if (info.permission.isNotEmpty()) " [${info.permission}]" else ""

            sender.sendRichMessage("<gray>  /${GenericCommand.COMMAND_ROOT} ${info.name}$aliases$permission")
            if (info.description.isNotEmpty()) {
                sender.sendRichMessage("<dark_gray>    ${info.description}")
            }
        }
    }

    /**
     * Gets a registered command by name or alias
     */
    fun getCommand(name: String): GenericCommand? = registeredCommands[name.lowercase()]

    /**
     * Gets all registered commands
     */
    fun getAllCommands(): Map<String, GenericCommand> = registeredCommands.toMap()

    /**
     * Checks if the registry has been initialized
     */
    fun isInitialized(): Boolean = isInitialized

    /**
     * Gets command count statistics
     */
    fun getCommandStats(): Pair<Int, Int> {
        val uniqueCommands = registeredCommands.values.distinctBy { it.commandInfo.name }.size
        val totalAliases = registeredCommands.size - uniqueCommands
        return Pair(uniqueCommands, totalAliases)
    }
}