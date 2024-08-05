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

import com.mochibit.defcon.registers.StructureRegister
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player

@CommandInfo(name = "ncstructuresearch", permission = "defcon.admin", requiresPlayer = true)
class SearchStructure: GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val world = player.world
        val x: Int
        val y: Int
        val z: Int
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.size < 3) {
            player.sendMessage(ChatColor.RED.toString() + "You must specify the coordinates")
            return
        }
        try {
            x = args[0].toInt()
            y = args[1].toInt()
            z = args[2].toInt()
        } catch (ex: NumberFormatException) {
            player.sendMessage(ChatColor.RED.toString() + "The parameters must be integers")
            return
        }
        val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())

        val structureQuery = StructureRegister().searchByBlock(location)

        if (structureQuery.structures.isEmpty()) {
            player.sendMessage(ChatColor.RED.toString() + "No structure found")
            return
        }

        for (structure in structureQuery.structures) {
            player.sendMessage(ChatColor.GREEN.toString() + "Structure found: " + structure.id)
        }

    }

}