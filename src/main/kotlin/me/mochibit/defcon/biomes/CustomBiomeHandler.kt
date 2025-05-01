package me.mochibit.defcon.biomes

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

/**
 * Handles custom biome creation and manipulation for both client-side and server-side biomes.
 */
object CustomBiomeHandler {
    /**
     * Represents the boundary of a custom biome area.
     */
    data class CustomBiomeBoundary(
        val id: UUID, // Unique identifier for this biome boundary
        val biome: NamespacedKey,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int,
        val worldName: String // Store the world name to differentiate between worlds
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

    // Thread-safe map to store active biome boundaries by their unique IDs
    private val activeBiomes = ConcurrentHashMap<UUID, CustomBiomeBoundary>()

    // Thread-safe map to track which players can see which biomes (for client-side updates)
    private val playerVisibleBiomes = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    /**
     * Gets the biome boundary at a specific location.
     *
     * @param location The location to check
     * @return CustomBiomeBoundary or null if none exists at that location
     */
    fun getBiomeAtLocation(location: Location): CustomBiomeBoundary? {
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        val worldName = location.world.name

        return activeBiomes.values.find { boundary ->
            boundary.worldName == worldName && boundary.isInBounds(x, y, z)
        }
    }

    /**
     * Gets the biome boundaries visible to a specific player.
     *
     * @param playerId UUID of the player
     * @return Set of CustomBiomeBoundary objects or empty set if none exists
     */
    fun getPlayerVisibleBiomes(playerId: UUID): Set<CustomBiomeBoundary> {
        val biomeIds = playerVisibleBiomes[playerId] ?: return emptySet()
        return biomeIds.mapNotNull { activeBiomes[it] }.toSet()
    }

    /**
     * Gets all active biome boundaries in the system.
     *
     * @return Collection of all CustomBiomeBoundary objects
     */
    fun getAllActiveBiomes(): Collection<CustomBiomeBoundary> {
        return activeBiomes.values
    }

    /**
     * Creates a new custom biome area.
     *
     * @return UUID of the created biome area
     */
    fun createBiomeArea(
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int,
        lengthNegativeY: Int,
        lengthPositiveX: Int,
        lengthNegativeX: Int,
        lengthPositiveZ: Int,
        lengthNegativeZ: Int,
    ): UUID {
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

        // Create a unique ID for this biome area
        val biomeId = UUID.randomUUID()

        // Create and store the biome boundary
        val boundary = CustomBiomeBoundary(
            id = biomeId,
            biome = biome.asBukkitBiome.key,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            minZ = minZ,
            maxZ = maxZ,
            worldName = center.world.name
        )

        activeBiomes[biomeId] = boundary

        // Update nearby players who should see this biome
        updateNearbyPlayers(boundary)

        return biomeId
    }

    /**
     * Makes a specific biome visible to a player.
     */
    fun makeBiomeVisibleToPlayer(playerId: UUID, biomeId: UUID) {
        val playerBiomes = playerVisibleBiomes.computeIfAbsent(playerId) { mutableSetOf() }
        if (playerBiomes.add(biomeId)) {
            // Only refresh chunks if this is a new addition
            updateClientSideBiomeChunks(playerId)
        }
    }

    /**
     * Makes a biome no longer visible to a player.
     */
    fun removeBiomeVisibilityFromPlayer(playerId: UUID, biomeId: UUID) {
        playerVisibleBiomes[playerId]?.remove(biomeId)
        updateClientSideBiomeChunks(playerId)
    }

    /**
     * Updates which players should see a biome based on proximity.
     */
    private fun updateNearbyPlayers(boundary: CustomBiomeBoundary) {
        val world = Defcon.instance.server.getWorld(boundary.worldName) ?: return

        // Find players in the same world that should see this biome
        for (player in world.players) {
            val playerChunkX = player.location.blockX shr 4
            val playerChunkZ = player.location.blockZ shr 4
            val viewDistance = player.viewDistance.coerceAtMost(10)

            // Check if any chunk in view distance intersects with the biome boundary
            var shouldSee = false
            outerLoop@ for (dx in -viewDistance..viewDistance) {
                for (dz in -viewDistance..viewDistance) {
                    val chunkX = playerChunkX + dx
                    val chunkZ = playerChunkZ + dz
                    if (boundary.intersectsChunk(chunkX, chunkZ)) {
                        shouldSee = true
                        break@outerLoop
                    }
                }
            }

            if (shouldSee) {
                makeBiomeVisibleToPlayer(player.uniqueId, boundary.id)
            }
        }
    }

    /**
     * Removes a custom biome area.
     *
     * @param biomeId UUID of the biome area to remove
     * @return true if the biome was found and removed, false otherwise
     */
    fun removeBiomeArea(biomeId: UUID): Boolean {
        val biome = activeBiomes.remove(biomeId) ?: return false

        // Update all players who could see this biome
        for (playerId in playerVisibleBiomes.keys) {
            playerVisibleBiomes[playerId]?.remove(biomeId)
            updateClientSideBiomeChunks(playerId)
        }

        return true
    }

    /**
     * Updates a player's view of client-side biomes.
     * Should be called when a player moves to a new chunk or when biomes change.
     */
    fun updatePlayerBiomeView(player: Player) {
        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4
        val viewDistance = player.viewDistance.coerceAtMost(10)
        val worldName = player.world.name

        // Find which biomes should be visible based on chunk proximity
        val visibleBiomes = activeBiomes.filter { (_, boundary) ->
            if (boundary.worldName != worldName) return@filter false

            for (dx in -viewDistance..viewDistance) {
                for (dz in -viewDistance..viewDistance) {
                    val chunkX = playerChunkX + dx
                    val chunkZ = playerChunkZ + dz
                    if (boundary.intersectsChunk(chunkX, chunkZ)) {
                        return@filter true
                    }
                }
            }
            false
        }.keys.toSet()

        // Update player's visible biomes
        val currentVisible = playerVisibleBiomes.computeIfAbsent(player.uniqueId) { mutableSetOf() }
        if (currentVisible != visibleBiomes) {
            currentVisible.clear()
            currentVisible.addAll(visibleBiomes)
            updateClientSideBiomeChunks(player.uniqueId)
        }
    }

    /**
     * Updates chunks to reflect client-side biome changes.
     * Optimized to refresh only affected chunks in an efficient manner.
     */
    private fun updateClientSideBiomeChunks(playerId: UUID) {
        val player = Defcon.instance.server.getPlayer(playerId) ?: return
        val world = player.world

        // Get all biomes visible to this player
        val visibleBiomes = getPlayerVisibleBiomes(playerId)
        if (visibleBiomes.isEmpty()) return

        // Get player's view distance, but ensure it's capped to reduce lag
        val viewDistance = player.viewDistance.coerceAtMost(10)
        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4

        // Find all chunks affected by any biome that's visible to this player
        val chunksToUpdate = mutableSetOf<Pair<Int, Int>>()

        for (boundary in visibleBiomes) {
            if (boundary.worldName != world.name) continue

            val minChunkX = boundary.minX shr 4
            val maxChunkX = boundary.maxX shr 4
            val minChunkZ = boundary.minZ shr 4
            val maxChunkZ = boundary.maxZ shr 4

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
        }

        // Sort chunks by distance to player for better visual experience
        val sortedChunks = chunksToUpdate.sortedBy { (x, z) ->
            (x - playerChunkX) * (x - playerChunkX) + (z - playerChunkZ) * (z - playerChunkZ)
        }

        // Group chunks by their distance to the player
        val immediateChunks = mutableListOf<Pair<Int, Int>>()
        val nearChunks = mutableListOf<Pair<Int, Int>>()
        val farChunks = mutableListOf<Pair<Int, Int>>()

        for ((chunkX, chunkZ) in sortedChunks) {
            val distanceSquared = (chunkX - playerChunkX) * (chunkX - playerChunkX) +
                    (chunkZ - playerChunkZ) * (chunkZ - playerChunkZ)

            when {
                distanceSquared <= 4 -> immediateChunks.add(Pair(chunkX, chunkZ))  // Within 2 chunks
                distanceSquared <= 25 -> nearChunks.add(Pair(chunkX, chunkZ))      // Within 5 chunks
                else -> farChunks.add(Pair(chunkX, chunkZ))
            }
        }
        Defcon.instance.launch(Dispatchers.Default) {

            // Update immediate chunks with minimal delay
            for ((chunkX, chunkZ) in immediateChunks) {
                try {
                    world.refreshChunk(chunkX, chunkZ)
                } catch (e: Exception) {
                    Logger.warn("Failed to refresh immediate chunk at $chunkX, $chunkZ: ${e.message}")
                }
            }

            // Short delay before updating near chunks
            delay(0.1.seconds)

            // Update near chunks with small delays
            for ((chunkX, chunkZ) in nearChunks) {
                try {
                    world.refreshChunk(chunkX, chunkZ)
                    // Small delay between near chunks
                    delay(0.2.seconds)
                } catch (e: Exception) {
                    Logger.warn("Failed to refresh near chunk at $chunkX, $chunkZ: ${e.message}")
                }
            }

            // Update far chunks with longer delays
            for ((chunkX, chunkZ) in farChunks) {
                try {
                    world.refreshChunk(chunkX, chunkZ)
                    // Longer delay for far chunks
                    delay(0.5.seconds)
                } catch (e: Exception) {
                    Logger.warn("Failed to refresh far chunk at $chunkX, $chunkZ: ${e.message}")
                }
            }
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
}