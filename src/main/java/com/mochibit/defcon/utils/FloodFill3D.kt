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

package com.mochibit.defcon.utils

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.registers.BlockRegister
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
