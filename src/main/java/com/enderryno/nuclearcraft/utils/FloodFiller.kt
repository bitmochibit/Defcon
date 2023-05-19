package com.enderryno.nuclearcraft.utils

import org.bukkit.Location
import java.util.*

object FloodFiller {
    fun getFloodFill(startLoc: Location, maxRange: Int): List<Location> {
        val visited: MutableSet<Location> = HashSet()
        val queue: Queue<Location> = LinkedList()
        queue.add(startLoc)
        visited.add(startLoc)
        val locations: MutableList<Location> = ArrayList()
        var range = 0
        while (!queue.isEmpty() && range <= maxRange) {
            val levelSize = queue.size
            for (i in 0 until levelSize) {
                val loc = queue.poll()
                val block = loc.block
                if (!block.isSolid) {
                    locations.add(loc)
                    addNeighbors(loc, queue, visited)
                }
            }
            range++
        }
        return locations
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
