package me.mochibit.defcon.utils

import me.mochibit.defcon.registers.BlockRegister
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.joml.Vector3i
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object FloodFill3D {
    // Direction vectors for 3D flood fill
    private enum class Direction(val x: Int, val y: Int, val z: Int) {
        UP(0, 1, 0),
        DOWN(0, -1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1),
        EAST(1, 0, 0),
        WEST(-1, 0, 0);

        fun getRelative(x: Int, y: Int, z: Int): Triple<Int, Int, Int> {
            return Triple(x + this.x, y + this.y, z + this.z)
        }
    }

    // Lightweight block position class to avoid creating many Location objects
    data class BlockPos(val x: Int, val y: Int, val z: Int) {
        fun toLocation(world: World): Location = Location(world, x.toDouble(), y.toDouble(), z.toDouble())
        fun getBlock(world: World): Block = world.getBlockAt(x, y, z)

        companion object {
            fun fromLocation(location: Location): BlockPos =
                BlockPos(location.blockX, location.blockY, location.blockZ)

            fun fromBlock(block: Block): BlockPos =
                BlockPos(block.x, block.y, block.z)
        }
    }

    // Cache for custom blocks to avoid repeated lookups
    private val customBlockCache = ConcurrentHashMap<BlockPos, Boolean>()

    /**
     * Clear the custom block cache when no longer needed
     */
    fun clearCustomBlockCache() {
        customBlockCache.clear()
    }

    /**
     * Primary flood fill method that returns blocks grouped by material
     */
    fun getFloodFillBlock(
        startBlock: Block,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false,
        ignoreEmpty: Boolean = false,
        blockFilter: ((Block) -> Boolean)? = null
    ): EnumMap<Material, MutableSet<Location>> {
        val world = startBlock.world
        val startPos = BlockPos.fromBlock(startBlock)
        val result = EnumMap<Material, MutableSet<Location>>(Material::class.java)
        val queue = LinkedList<BlockPos>()
        val visited = HashSet<BlockPos>()

        queue.add(startPos)

        while (queue.isNotEmpty() && visited.size < maxRange) {
            val currentPos = queue.poll()

            // Skip if already visited
            if (currentPos in visited) continue
            visited.add(currentPos)

            // Get the actual block
            val currentBlock = currentPos.getBlock(world)

            // Check if the block is valid according to our filters
            if (!isValidBlock(currentBlock, nonSolidOnly, customBlockOnly, ignoreEmpty, blockFilter)) {
                continue
            }

            // Add the location to the result map
            result.getOrPut(currentBlock.type) { HashSet() }
                 .add(currentPos.toLocation(world))

            // Add neighbor blocks to the queue
            for (direction in Direction.entries) {
                val (nx, ny, nz) = direction.getRelative(currentPos.x, currentPos.y, currentPos.z)
                val nextPos = BlockPos(nx, ny, nz)

                if (nextPos !in visited) {
                    queue.add(nextPos)
                }
            }
        }

        return result
    }

    /**
     * Simplified flood fill returning list of locations
     */
    fun getFloodFill(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false
    ): List<Location> {
        val world = startLoc.world ?: return emptyList()
        val startPos = BlockPos.fromLocation(startLoc)
        val positions = HashSet<BlockPos>()
        val queue = LinkedList<BlockPos>()

        queue.add(startPos)

        while (queue.isNotEmpty() && positions.size < maxRange) {
            val currentPos = queue.poll()

            // Skip if already visited
            if (currentPos in positions) continue

            // Get the block and check conditions
            val block = currentPos.getBlock(world)

            if (nonSolidOnly && block.type.isSolid) continue
            if (customBlockOnly && !isCustomBlock(currentPos, world)) continue

            positions.add(currentPos)

            // Add neighboring blocks
            for (direction in Direction.entries) {
                val (nx, ny, nz) = direction.getRelative(currentPos.x, currentPos.y, currentPos.z)
                val nextPos = BlockPos(nx, ny, nz)

                if (nextPos !in positions) {
                    queue.add(nextPos)
                }
            }
        }

        // Convert BlockPos back to Location for the result
        return positions.map { it.toLocation(world) }
    }

    /**
     * Async version of flood fill
     */
    fun getFloodFillAsync(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false
    ): CompletableFuture<List<Location>> {
        return CompletableFuture.supplyAsync {
            getFloodFill(startLoc, maxRange, nonSolidOnly, customBlockOnly)
        }
    }

    /**
     * Check if a block meets all filter criteria
     */
    private fun isValidBlock(
        block: Block,
        nonSolidOnly: Boolean,
        customBlockOnly: Boolean,
        ignoreEmpty: Boolean,
        blockFilter: ((Block) -> Boolean)?
    ): Boolean {
        return (!nonSolidOnly || !block.type.isSolid) &&
                (!customBlockOnly || isCustomBlock(BlockPos.fromBlock(block), block.world)) &&
                (!ignoreEmpty || !block.type.isAir) &&
                (blockFilter == null || blockFilter(block))
    }

    /**
     * Optimized method to check if a location contains a custom block
     * Uses caching to avoid repeated registry lookups
     */
    private fun isCustomBlock(pos: BlockPos, world: World): Boolean {
        return customBlockCache.computeIfAbsent(pos) { p ->
            BlockRegister.getBlock(p.toLocation(world)) != null
        }
    }
}