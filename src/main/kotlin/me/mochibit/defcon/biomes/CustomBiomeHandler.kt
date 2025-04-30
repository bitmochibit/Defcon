package me.mochibit.defcon.biomes

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.BlockChanger
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.block.Biome
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles custom biome creation and manipulation for both client-side and server-side biomes.
 */
object CustomBiomeHandler {
    /**
     * Represents the boundary of a client-side biome area for a specific player.
     */
    data class ClientSideBiomeBoundary(
        val biome: NamespacedKey,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int
    ) {
        /**
         * Checks if a specific location is within this biome boundary.
         */
        fun isInBounds(x: Int, y: Int, z: Int): Boolean {
            return x in minX..maxX && y in minY..maxY && z in minZ..maxZ
        }

        /**
         * Checks if a chunk intersects with this biome boundary.
         */
        fun intersectsChunk(chunkX: Int, chunkZ: Int): Boolean {
            val chunkMinX = chunkX * 16
            val chunkMaxX = chunkMinX + 15
            val chunkMinZ = chunkZ * 16
            val chunkMaxZ = chunkMinZ + 15

            return !(chunkMaxX < minX || chunkMinX > maxX ||
                    chunkMaxZ < minZ || chunkMinZ > maxZ)
        }
    }

    // Thread-safe map to store player-specific biome boundaries
    private val clientSideBiome = ConcurrentHashMap<UUID, ClientSideBiomeBoundary>()

    /**
     * Gets the biome area boundaries for a specific player.
     *
     * @param playerId UUID of the player
     * @return ClientSideBiomeBoundary or null if none exists
     */
    fun getBiomeAreaBoundaries(playerId: UUID): ClientSideBiomeBoundary? {
        return clientSideBiome[playerId]
    }

    /**
     * Sets a client-side biome for a specific player.
     * This only affects what the player sees, not the actual world biome.
     */
    fun setBiomeClientSide(
        playerId: UUID,
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int,
        lengthNegativeY: Int,
        lengthPositiveX: Int,
        lengthNegativeX: Int,
        lengthPositiveZ: Int,
        lengthNegativeZ: Int,
    ) {
        // Validate input parameters
        require(lengthPositiveY >= 0) { "Positive Y length must be non-negative" }
        require(lengthNegativeY >= 0) { "Negative Y length must be non-negative" }
        require(lengthPositiveX >= 0) { "Positive X length must be non-negative" }
        require(lengthNegativeX >= 0) { "Negative X length must be non-negative" }
        require(lengthPositiveZ >= 0) { "Positive Z length must be non-negative" }
        require(lengthNegativeZ >= 0) { "Negative Z length must be non-negative" }

        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ

        // Get the world's min and max height
        val worldMinY = center.world.minHeight
        val worldMaxY = center.world.maxHeight

        // Calculate the bounds
        val minX = centerX - lengthNegativeX
        val maxX = centerX + lengthPositiveX
        val minZ = centerZ - lengthNegativeZ
        val maxZ = centerZ + lengthPositiveZ
        val minY = (centerY - lengthNegativeY).coerceAtLeast(worldMinY)
        val maxY = (centerY + lengthPositiveY).coerceAtMost(worldMaxY)

        // Create and store the biome boundary
        val boundary = ClientSideBiomeBoundary(
            biome.asBukkitBiome.key,
            minX, maxX, minY, maxY, minZ, maxZ
        )

        clientSideBiome[playerId] = boundary
        updateClientSideBiomeChunks(playerId)
    }


    /**
     * Updates chunks to reflect client-side biome changes.
     * Optimized to refresh only affected chunks in an efficient manner.
     */
    fun updateClientSideBiomeChunks(playerId: UUID) {
        val boundary = clientSideBiome[playerId] ?: return
        val player = Defcon.instance.server.getPlayer(playerId) ?: return
        val world = player.world

        // Calculate which chunks are affected by the biome change
        val minChunkX = boundary.minX shr 4
        val maxChunkX = boundary.maxX shr 4
        val minChunkZ = boundary.minZ shr 4
        val maxChunkZ = boundary.maxZ shr 4

        // Get player's view distance, but ensure it's capped to reduce lag
        val viewDistance = player.viewDistance.coerceAtMost(10)
        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4

        // Collect chunks that need updating and are within view distance
        val chunksToUpdate = mutableListOf<Pair<Int, Int>>()

        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                // Check if chunk is within player's view distance
                val distanceSquared = (chunkX - playerChunkX) * (chunkX - playerChunkX) +
                        (chunkZ - playerChunkZ) * (chunkZ - playerChunkZ)

                if (distanceSquared <= viewDistance * viewDistance) {
                    // Check if chunk is loaded before adding
                    if (world.isChunkLoaded(chunkX, chunkZ)) {
                        chunksToUpdate.add(Pair(chunkX, chunkZ))
                    }
                }
            }
        }

        // Sort chunks by distance to player for better visual experience
        chunksToUpdate.sortBy { (x, z) ->
            (x - playerChunkX) * (x - playerChunkX) + (z - playerChunkZ) * (z - playerChunkZ)
        }

        var delay = 1L
        for ((chunkX, chunkZ) in chunksToUpdate) {
            Defcon.instance.launch {
                delay(delay)
                try {
                    world.refreshChunk(chunkX, chunkZ)
                } catch (e: Exception) {
                    // Log error but continue with other chunks
                    Logger.warn("Failed to refresh chunk at $chunkX, $chunkZ: ${e.message}")
                }
            }
            // Increase delay between chunk updates to prevent overwhelming the client
            delay += 2L
        }
    }

    /**
     * Optimized spiral coordinate generator.
     * This generates coordinates in a spiral pattern starting from center.
     * This is kept as a utility but no longer used in the main implementation.
     */
    private fun spiralCoords(radius: Int): List<Pair<Int, Int>> {
        val coords = mutableListOf<Pair<Int, Int>>()
        var x = 0
        var z = 0
        var dx = 0
        var dz = -1

        // Calculate exact number of points needed based on radius
        val maxI = (2 * radius + 1) * (2 * radius + 1)

        for (i in 0 until maxI) {
            if (-radius <= x && x <= radius && -radius <= z && z <= radius) {
                coords.add(Pair(x, z))
            }

            // Logic to generate spiral pattern
            if (x == z || (x < 0 && x == -z) || (x > 0 && x == 1 - z)) {
                val tmp = dx
                dx = -dz
                dz = tmp
            }

            x += dx
            z += dz
        }

        return coords
    }


    /**
     * Removes the client-side biome effect for a player.
     */
    fun removeBiomeClientSide(playerId: UUID) {
        clientSideBiome.remove(playerId)
    }

    /**
     * Sets a custom biome for a range of blocks in the world.
     * This changes the actual world biome for all players.
     */
    suspend fun setCustomBiomeRange(
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int,
        lengthNegativeY: Int,
        lengthPositiveX: Int,
        lengthNegativeX: Int,
        lengthPositiveZ: Int,
        lengthNegativeZ: Int,
    ) {
        // Validate input parameters
        require(lengthPositiveY >= 0) { "Positive Y length must be non-negative" }
        require(lengthNegativeY >= 0) { "Negative Y length must be non-negative" }
        require(lengthPositiveX >= 0) { "Positive X length must be non-negative" }
        require(lengthNegativeX >= 0) { "Negative X length must be non-negative" }
        require(lengthPositiveZ >= 0) { "Positive Z length must be non-negative" }
        require(lengthNegativeZ >= 0) { "Negative Z length must be non-negative" }

        val world = center.world
        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ

        // Get the Bukkit biome representation
        val bukkitBiome = biome.asBukkitBiome

        // Get block changer for this world
        val blockChanger = BlockChanger.getInstance(world)

        // Calculate the bounds
        val minX = centerX - lengthNegativeX
        val maxX = centerX + lengthPositiveX
        val minZ = centerZ - lengthNegativeZ
        val maxZ = centerZ + lengthPositiveZ

        // Make sure to respect world height limits
        val worldMinY = world.minHeight
        val worldMaxY = world.maxHeight
        val minY = (centerY - lengthNegativeY).coerceAtLeast(worldMinY)
        val maxY = (centerY + lengthPositiveY).coerceAtMost(worldMaxY)

        // Optimize by pre-calculating the total volume
        val totalBlocks = (maxX - minX + 1) * (maxY - minY + 1) * (maxZ - minZ + 1)

        // Add logging for large operations
        if (totalBlocks > 100000) {
            println("Setting biome for a large area with $totalBlocks blocks. This may take some time.")
        }

        // Change the biome for each block in the range
        for (y in minY..maxY) {
            for (x in minX..maxX) {
                for (z in minZ..maxZ) {
                    blockChanger.changeBiome(x, y, z, bukkitBiome)
                }
            }
        }
    }
}


