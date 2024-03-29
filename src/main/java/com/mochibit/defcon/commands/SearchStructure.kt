package com.mochibit.defcon.commands

import com.mochibit.defcon.abstracts.GenericCommand
import com.mochibit.defcon.annotations.CommandInfo
import com.mochibit.defcon.services.StructureRegister
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