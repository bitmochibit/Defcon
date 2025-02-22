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

package me.mochibit.defcon.utils

import me.mochibit.defcon.registers.BlockRegister
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.joml.Vector3i
import java.util.*
import java.util.concurrent.CompletableFuture


object FloodFill3D {

    private enum class Direction(val vec: Vector3i) {
        UP(Vector3i(0, 1, 0)),
        DOWN(Vector3i(0, -1, 0)),
        NORTH(Vector3i(0, 0, -1)),
        SOUTH(Vector3i(0, 0, 1)),
        EAST(Vector3i(1, 0, 0)),
        WEST(Vector3i(-1, 0, 0))
    }


    fun getFloodFillBlock(
        startBlock: Block,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false,
        ignoreEmpty: Boolean = false,
        blockFilter: ((Block) -> Boolean)? = null
    ): EnumMap<Material, HashSet<Location>> {
        val positions: EnumMap<Material, HashSet<Location>> = EnumMap(Material::class.java)
        val queue: Queue<Block> = LinkedList()
        val visited = HashSet<Location>()

        queue.add(startBlock)
        var blockCount = 0

        while (queue.isNotEmpty() && blockCount < maxRange) {
            val currentBlock = queue.poll()
            if (visited.contains(currentBlock.location) || !isValidBlock(currentBlock, nonSolidOnly, customBlockOnly, ignoreEmpty, blockFilter)) continue
            visited.add(currentBlock.location)

            positions.getOrPut(currentBlock.type) { HashSet() }.add(currentBlock.location)
            blockCount++

            for (direction in Direction.entries) {
                val nextBlock = currentBlock.getRelative(
                    direction.vec.x.toInt(),
                    direction.vec.y.toInt(),
                    direction.vec.z.toInt()
                )
                queue.add(nextBlock)
            }
        }
        return positions
    }

    fun getFloodFill(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false,
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

            for (direction in Direction.entries) {
                val nextPos = Location(currentPos.world, currentPos.x + direction.vec.x, currentPos.y + direction.vec.y, currentPos.z + direction.vec.z)

                if (nonSolidOnly && nextPos.block.type.isSolid) continue
                if (customBlockOnly && !isCustomBlock(nextPos)) continue

                queue.add(nextPos)
            }
        }

        return positions.toList()
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

    private fun isValidBlock(
        block: Block,
        nonSolidOnly: Boolean,
        customBlockOnly: Boolean,
        ignoreEmpty: Boolean,
        blockFilter: ((Block) -> Boolean)?
    ): Boolean {
        return (!nonSolidOnly || !block.type.isSolid) &&
                (!customBlockOnly || isCustomBlock(block.location)) &&
                (!ignoreEmpty || !block.type.isAir) &&
                (blockFilter == null || blockFilter(block))
    }

    //TODO: To optimize
    private fun isCustomBlock(loc: Location): Boolean {
        return BlockRegister.getBlock(loc) != null
    }
}
