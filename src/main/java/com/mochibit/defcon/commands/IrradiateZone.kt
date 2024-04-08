package com.mochibit.defcon.commands

import com.mochibit.defcon.abstracts.GenericCommand
import com.mochibit.defcon.annotations.CommandInfo
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.radiation.RadiationArea
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.entity.Player

@CommandInfo(name = "ncirradiatezone", permission = "defcon.admin", requiresPlayer = true)
class IrradiateZone : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val x: Int
        val y: Int
        val z: Int
        val radius: Int
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
        } catch (ex: NumberFormatException) {
            player.sendMessage(ChatColor.RED.toString() + "The parameters must be integers")
            return
        }
        if (radius < 1) {
            player.sendMessage(ChatColor.RED.toString() + "The radius must be greater than 0")
            return
        }

        RadiationArea.fromCenter(Location(player.world, x.toDouble(), y.toDouble(), z.toDouble()), radius)

    }
}
