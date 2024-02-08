package com.enderryno.nuclearcraft.utils

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.services.BlockRegister
import org.bukkit.Location
import java.util.*

object FloodFiller {
    fun getFloodFill(startLoc: Location, maxRange: Int, nonSolidOnly: Boolean = false, customBlockOnly: Boolean = false): List<Location> {
        val visited: MutableSet<Location> = HashSet()
        val queue: Queue<Location> = LinkedList()
        val locations: MutableList<Location> = ArrayList()
        if (customBlockOnly && !isCustomBlock(startLoc)) {
            return locations
        }
        queue.add(startLoc)
        visited.add(startLoc)
        var range = 0
        while (!queue.isEmpty() && range <= maxRange) {
            val levelSize = queue.size
            for (i in 0 until levelSize) {
                val loc = queue.poll()
                if (customBlockOnly && !isCustomBlock(loc)) {
                    continue
                }

                val block = loc.block
                if (nonSolidOnly && block.isSolid) {
                    continue
                }

                locations.add(loc)
                addNeighbors(loc, queue, visited)
            }
            range++
        }
        return locations
    }


    // To optimize
    private fun isCustomBlock(loc: Location): Boolean {
        return BlockRegister.getBlock(loc) != null
    }

    private fun addNeighbors(loc: Location, queue: Queue<Location>, visited: MutableSet<Location>) {
        val x = loc.blockX
        val y = loc.blockY
        val z = loc.blockZ
        var neighbor = Location(loc.world, (x + 1).toDouble(), y.toDouble(), z.toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
        neighbor = Location(loc.world, (x - 1).toDouble(), y.toDouble(), z.toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
        neighbor = Location(loc.world, x.toDouble(), (y + 1).toDouble(), z.toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
        neighbor = Location(loc.world, x.toDouble(), (y - 1).toDouble(), z.toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
        neighbor = Location(loc.world, x.toDouble(), y.toDouble(), (z + 1).toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
        neighbor = Location(loc.world, x.toDouble(), y.toDouble(), (z - 1).toDouble())
        if (!visited.contains(neighbor)) {
            queue.add(neighbor)
            visited.add(neighbor)
        }
    }
}
