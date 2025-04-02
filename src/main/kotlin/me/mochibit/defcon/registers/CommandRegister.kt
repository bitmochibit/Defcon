/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.registers

import io.papermc.paper.command.brigadier.Commands
import io.papermc.paper.plugin.lifecycle.event.LifecycleEvent
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Companion.Logger.info
import me.mochibit.defcon.commands.GenericCommand
import org.bukkit.plugin.java.JavaPlugin
import org.reflections.Reflections
import java.lang.reflect.InvocationTargetException

class CommandRegister {
    private val packageName: String = Defcon.instance.javaClass.getPackage().name

    fun registerCommands() {
        info("Registering commands from $packageName.commands")

        val commandBuilderLeafs = mutableListOf<GenericCommand>()

        for (commandClass in Reflections("$packageName.commands").getSubTypesOf(GenericCommand::class.java)) {
            try {
                val genericCommand = commandClass.getDeclaredConstructor().newInstance()
                commandBuilderLeafs.add(genericCommand)
                // debug info
                info("Registering command: ${genericCommand.commandInfo.name}")

            } catch (e: InstantiationException) {
                throw RuntimeException(e)
            } catch (e: IllegalAccessException) {
                throw RuntimeException(e)
            } catch (e: InvocationTargetException) {
                throw RuntimeException(e)
            } catch (e: NoSuchMethodException) {
                throw RuntimeException(e)
            }
        }

        val commandTree = Commands
            .literal(GenericCommand.COMMAND_ROOT)

        for (command in commandBuilderLeafs) {
            commandTree.then(command.getCommand())
        }

        Defcon.instance.lifecycleManager.registerEventHandler(LifecycleEvents.COMMANDS) { commands ->
            commands.registrar().register(commandTree.build())
        }
    }
}
