package com.mochibit.nuclearcraft.commands

import com.mochibit.nuclearcraft.abstracts.GenericCommand
import com.mochibit.nuclearcraft.annotations.CommandInfo
import com.mochibit.nuclearcraft.classes.structures.NuclearWarhead
import com.mochibit.nuclearcraft.explosives.NuclearComponent
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player

@CommandInfo(name = "nctestexpl", permission = "nuclearcraft.admin", requiresPlayer = true)
class TestExplosion : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val world = player.world
        val x: Int
        val y: Int
        val z: Int
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.size < 3) {
            player.sendMessage(ChatColor.RED.toString() + "You must specify the coordinates of the center of the explosion")
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

        val nuclearWarhead = NuclearWarhead();
        val nuclearComponent = NuclearComponent(blastPower = 1f, radiationPower = 1f, thermalPower = 1f, empPower = 1f);
        nuclearWarhead.explode(location, nuclearComponent);
    }
}
