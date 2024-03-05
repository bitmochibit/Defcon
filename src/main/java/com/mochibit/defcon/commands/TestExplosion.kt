package com.mochibit.defcon.commands

import com.mochibit.defcon.abstracts.GenericCommand
import com.mochibit.defcon.annotations.CommandInfo
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.explosions.NuclearExplosion
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
    }
}
