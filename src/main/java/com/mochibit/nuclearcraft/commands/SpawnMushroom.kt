package com.mochibit.nuclearcraft.commands

import com.mochibit.nuclearcraft.abstracts.GenericCommand
import com.mochibit.nuclearcraft.annotations.CommandInfo
import org.bukkit.ChatColor
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.entity.Player

@CommandInfo(name = "ncspawnmushroom", permission = "nuclearcraft.admin", requiresPlayer = true)
class SpawnMushroom : GenericCommand() {
    override fun execute(player: Player, args: Array<String>) {
        val world = player.world
        val x: Int
        val y: Int
        val z: Int
        // The first, second and third are the coordinates of the center;
        // The fourth is the radius;
        if (args.size < 3) {
            player.sendMessage(ChatColor.RED.toString() + "You must specify the coordinates and the radius")
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
        val particleCount = 10000
        val speed = 0.1f

        // generate the mushroom cloud particles
        world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, location, particleCount, 0.0, 0.0, 0.0, speed.toDouble())
        world.spawnParticle<Any>(Particle.REDSTONE, location, particleCount, 0.0, 0.0, 0.0, 0.0, null, true)
        world.spawnParticle(Particle.REDSTONE, location, particleCount, 0.0, 0.0, 0.0)

        // generate the stem particles
        val stemHeight = 20
        for (i in 0 until stemHeight) {
            val stemLocation = location.clone().subtract(0.0, i.toDouble(), 0.0) // set the location of the stem
            world.spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, stemLocation, 1, 0.0, 0.0, 0.0, 0.0) // spawn the stem particle
            world.spawnParticle(Particle.REDSTONE, stemLocation, 1, 0.0, 0.0, 0.0)
        }
    }
}
