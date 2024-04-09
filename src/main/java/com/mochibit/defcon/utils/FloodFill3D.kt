package com.mochibit.defcon.utils

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.services.BlockRegister
import io.papermc.lib.PaperLib
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.collections.HashSet


object FloodFill3D {

    private enum class Direction(val vec: Vector3) {
        UP(Vector3(0.0, 1.0, 0.0)),
        DOWN(Vector3(0.0, -1.0, 0.0)),
        NORTH(Vector3(0.0, 0.0, -1.0)),
        SOUTH(Vector3(0.0, 0.0, 1.0)),
        EAST(Vector3(1.0, 0.0, 0.0)),
        WEST(Vector3(-1.0, 0.0, 0.0))
    }

    fun getFloodFill(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false
    ): List<Location> {
        val positions = HashSet<Location>()
        val queue: Queue<Location> = LinkedList()

        queue.add(startLoc)

        while (!queue.isEmpty() && positions.size < maxRange) {
            val currentPos = queue.poll()
            if (positions.contains(currentPos)) continue

            if (nonSolidOnly && currentPos.block.type.isSolid) continue
            if (customBlockOnly && !isCustomBlock(currentPos)) continue
            positions.add(currentPos)

            val pos = Vector3(currentPos.x, currentPos.y, currentPos.z)
            for (direction in Direction.entries) {
                val nextPos = (pos + direction.vec).toLocation(currentPos.world)

                if (nonSolidOnly && nextPos.block.type.isSolid) continue
                if (customBlockOnly && !isCustomBlock(nextPos)) continue

                queue.add(nextPos)
            }
        }

        return positions.toList();
    }

    fun getFloodFillAsync(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false
    ): CompletableFuture<List<Location>> {
        return CompletableFuture.supplyAsync {
            return@supplyAsync getFloodFill(startLoc, maxRange, nonSolidOnly, customBlockOnly)
        }
    }


    //TODO: To optimize
    private fun isCustomBlock(loc: Location): Boolean {
        return BlockRegister.getBlock(loc) != null
    }
}
