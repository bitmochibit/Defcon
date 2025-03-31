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

import me.mochibit.defcon.explosions.NuclearComponent
import me.mochibit.defcon.explosions.types.NuclearExplosion
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player

@CommandInfo(name = "nctestexpl", permission = "defcon.admin", requiresPlayer = true)
class TestExplosion : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val world = player.world
        val x: Int
        val y: Int
        val z: Int
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.size == 3) {
            try {
                x = args[0].toInt()
                y = args[1].toInt()
                z = args[2].toInt()
            } catch (ex: NumberFormatException) {
                player.sendMessage(ChatColor.RED.toString() + "The parameters must be integers")
                return
            }
        } else {
            x = player.location.blockX
            y = player.location.blockY
            z = player.location.blockZ
        }


        val location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        val nuclearComponent = NuclearComponent(blastPower = 1f, radiationPower = 1f, thermalPower = 1f, empPower = 1f);
        NuclearExplosion(location, nuclearComponent).explode()

//        val packet = PacketPlayOutWorldParticles(
//            Particles.aa, true,
//            (player.location.x.toFloat()).toDouble(),
//            (player.location.y.toFloat()).toDouble(),
//            (player.location.z.toFloat()).toDouble(),
//            0f, 0f, 0f, 15f, 15
//        )
//        val craftPlayer = player as CraftPlayer
//        craftPlayer.handle.b.a(packet)

    }
}
