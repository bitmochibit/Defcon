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

package com.mochibit.defcon.commands

import com.mochibit.defcon.exceptions.ItemNotRegisteredException
import com.mochibit.defcon.interfaces.PluginItem
import com.mochibit.defcon.registers.ItemRegister
import org.bukkit.ChatColor
import org.bukkit.entity.Player

@CommandInfo(name = "ncgive", permission = "defcon.admin", requiresPlayer = true)
class GiveCommand : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        if (args.isEmpty()) {
            player.sendMessage(ChatColor.RED.toString() + "The second parameter must be a Defcon ID")
            return
        }
        val itemId: String = try {
            args[0]
        } catch (ex: NumberFormatException) {
            player.sendMessage(ChatColor.RED.toString() + "The second parameter must be a Defcon ID")
            return
        }
        val item: PluginItem? = try {
            ItemRegister.registeredItems[itemId]
        } catch (e: ItemNotRegisteredException) {
            e.printStackTrace()
            return
        }
        if (item == null) {
            player.sendMessage(ChatColor.RED.toString() + "This item is invalid")
            return
        }
        try {
            player.inventory.addItem(item.itemStack)
        } catch (e: Exception) {
            player.sendMessage(ChatColor.RED.toString() + "Something went wrong, look for the console")
            e.printStackTrace()
            return
        }
        player.sendMessage(ChatColor.GREEN.toString() + "Gave the player a " + item.displayName)
    }
}
