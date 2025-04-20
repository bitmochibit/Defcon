package me.mochibit.defcon.biomes

import me.mochibit.defcon.utils.BlockChanger
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.entity.Player
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
        biome: Biome,
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
            biome.key,
            minX, maxX, minY, maxY, minZ, maxZ
        )

        clientSideBiome[playerId] = boundary
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
    fun setCustomBiomeRange(
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


    /**
     * Checks if a player is within their assigned client-side biome boundary.
     */
    fun isPlayerInBiomeBoundary(player: Player): Boolean {
        val boundary = getBiomeAreaBoundaries(player.uniqueId) ?: return false
        val loc = player.location
        return boundary.isInBounds(loc.blockX, loc.blockY, loc.blockZ)
    }
}


