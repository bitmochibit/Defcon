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

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.radiation.RadiationAreaFactory
import org.bukkit.Bukkit
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3i

@CommandInfo(name = "ncirradiatezone", permission = "defcon.admin", requiresPlayer = true)
class IrradiateZone : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val x: Int
        val y: Int
        val z: Int
        val radius: Int
        val radLevel: Double
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.size < 4) {
            player.sendMessage(ChatColor.RED.toString() + "You must specify the coordinates and the radius")
            return
        }
        try {
            x = args[0].toInt()
            y = args[1].toInt()
            z = args[2].toInt()
            radius = args[3].toInt()
            radLevel = args[4].toDouble()
        } catch (ex: NumberFormatException) {
            player.sendMessage(ChatColor.RED.toString() + "The parameters must be integers")
            return
        }
        if (radius < 1) {
            player.sendMessage(ChatColor.RED.toString() + "The radius must be greater than 0")
            return
        }

        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            RadiationAreaFactory.fromCenter(
                Vector3i(x, y, z),
                player.world,
                radLevel = radLevel,
                radius
            ).join();
        })


    }
}
