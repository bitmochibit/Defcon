package me.mochibit.defcon.utils

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.defcon.registers.BlockRegister
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector3i
import java.util.*
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

    // Cache for custom blocks to avoid repeated lookups
    private val customBlockCache = ConcurrentHashMap<Vector3i, Boolean>()

    /**
     * Clear the custom block cache when no longer needed
     */
    fun clearCustomBlockCache() {
        customBlockCache.clear()
    }

    /**
     * Primary flood fill method that returns blocks grouped by material
     */
    fun getFloodFillAdvanced(
        startPos: Vector3i,
        world: World,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false,
        ignoreEmpty: Boolean = false,
        blockFilter: ((Vector3i) -> Boolean)? = null
    ): EnumMap<Material, MutableSet<Vector3i>> {
        val result = EnumMap<Material, MutableSet<Vector3i>>(Material::class.java)
        val queue = LinkedList<Vector3i>()
        val visited = HashSet<Vector3i>()

        queue.add(startPos)

        while (queue.isNotEmpty() && visited.size < maxRange) {
            val currentPos = queue.poll()

            // Skip if already visited
            if (currentPos in visited) continue
            visited.add(currentPos)

            // Get the actual block
            val type = world.getType(currentPos.x, currentPos.y, currentPos.z)

            // Check if the block is valid according to our filters
            if (!isValidBlock(type, currentPos, world, nonSolidOnly, customBlockOnly, ignoreEmpty, blockFilter)) {
                continue
            }

            // Add the location to the result map
            result.getOrPut(type) { HashSet() }
                .add(currentPos)

            // Add neighbor blocks to the queue
            for (direction in Direction.entries) {
                val (nx, ny, nz) = direction.getRelative(currentPos.x, currentPos.y, currentPos.z)
                val nextPos = Vector3i(nx, ny, nz)

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
        val startPos = Vector3i(startLoc.x.toInt(), startLoc.y.toInt(), startLoc.z.toInt())
        val positions = HashSet<Vector3i>()
        val queue = LinkedList<Vector3i>()

        queue.add(startPos)

        while (queue.isNotEmpty() && positions.size < maxRange) {
            val currentPos = queue.poll()

            // Skip if already visited
            if (currentPos in positions) continue

            // Get the block and check conditions
            val type = world.getType(currentPos.x, currentPos.y, currentPos.z)

            if (nonSolidOnly && type.isSolid) continue
            if (customBlockOnly && !isCustomBlock(currentPos, world)) continue

            positions.add(currentPos)

            // Add neighboring blocks
            for (direction in Direction.entries) {
                val (nx, ny, nz) = direction.getRelative(currentPos.x, currentPos.y, currentPos.z)
                val nextPos = Vector3i(nx, ny, nz)

                if (nextPos !in positions) {
                    queue.add(nextPos)
                }
            }
        }

        // Convert BlockPos back to Location for the result
        return positions.map { Location(world, it.x.toDouble(), it.y.toDouble(), it.z.toDouble()) }
    }

    /**
     * Async version of flood fill
     */
    suspend fun getFloodFillAsync(
        startLoc: Location,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        customBlockOnly: Boolean = false
    ): List<Location> = withContext(Dispatchers.IO) {
        getFloodFill(startLoc, maxRange, nonSolidOnly, customBlockOnly)
    }

    /**
     * Check if a block meets all filter criteria
     */
    private fun isValidBlock(
        blockType: Material,
        blockLocation: Vector3i,
        world: World,
        nonSolidOnly: Boolean,
        customBlockOnly: Boolean,
        ignoreEmpty: Boolean,
        blockFilter: ((Vector3i) -> Boolean)?
    ): Boolean {
        return (!nonSolidOnly || !blockType.isSolid) &&
                (!customBlockOnly || isCustomBlock(blockLocation, world)) &&
                (!ignoreEmpty || !blockType.isAir) &&
                (blockFilter == null || blockFilter(blockLocation))
    }

    /**
     * Optimized method to check if a location contains a custom block
     * Uses caching to avoid repeated registry lookups
     */
    private fun isCustomBlock(pos: Vector3i, world: World): Boolean {
        return customBlockCache.computeIfAbsent(pos) {
            BlockRegister.getBlock(pos, world) != null
        }
    }
}