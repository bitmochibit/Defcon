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

package me.mochibit.defcon.commands

import org.bukkit.ChatColor
import org.bukkit.command.Command
import org.bukkit.command.CommandExecutor
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import java.util.*

abstract class GenericCommand : CommandExecutor {
    val commandInfo: CommandInfo = javaClass.getDeclaredAnnotation(CommandInfo::class.java)

    init {
        Objects.requireNonNull(commandInfo, "Commands must have a CommandInfo annotation")
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<String>): Boolean {
        if (commandInfo.permission.isNotEmpty()) {
            if (!sender.hasPermission(commandInfo.permission)) {
                sender.sendMessage(ChatColor.RED.toString() + "You can't execute this command.")
                return true
            }
        }
        if (commandInfo.requiresPlayer) {
            if (sender !is Player) {
                sender.sendMessage(ChatColor.RED.toString() + "This command can be only executed from a player.")
                return true
            }
            execute(sender, args)
            return true
        }
        execute(sender, args)
        return true
    }

    open fun execute(player: Player, args: Array<String>) {}
    private fun execute(sender: CommandSender?, args: Array<String>?) {}
}
