package me.mochibit.defcon.biomes

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.World
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
        val id: Int = 0, // Unique ID for this biome boundary
        val uuid: UUID, // Unique identifier for this biome boundary
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

    // Track which worlds have had their biomes loaded
    private val loadedWorlds = Collections.synchronizedSet(HashSet<String>())

    /**
     * Checks if a world's biomes have been loaded
     */
    fun isWorldLoaded(worldName: String): Boolean {
        return loadedWorlds.contains(worldName)
    }

    /**
     * Marks a world as having its biomes loaded
     */
    fun markWorldAsLoaded(worldName: String) {
        loadedWorlds.add(worldName)
    }

    /**
     * Activates a biome in memory
     */
    fun activateBiome(biome: CustomBiomeBoundary) {
        activeBiomes[biome.uuid] = biome
    }

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
     * Creates a new custom biome area and persists it to storage.
     *
     * @return UUID of the created biome area
     */
    suspend fun createBiomeArea(
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int,
        lengthNegativeY: Int,
        lengthPositiveX: Int,
        lengthNegativeX: Int,
        lengthPositiveZ: Int,
        lengthNegativeZ: Int,
    ): UUID = withContext(Dispatchers.Default) {
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
        val worldName = center.world.name

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

        // Create the biome boundary
        val boundary = CustomBiomeBoundary(
            uuid = biomeId,
            biome = biome.asBukkitBiome.key,
            minX = minX,
            maxX = maxX,
            minY = minY,
            maxY = maxY,
            minZ = minZ,
            maxZ = maxZ,
            worldName = worldName
        )

        // Make sure the world is marked as loaded
        markWorldAsLoaded(worldName)

        // Save to persistent storage
        val biomeSave = BiomeAreaSave.getSave(worldName)
        val savedBoundary = biomeSave.addBiome(boundary)

        // Update in-memory map with the saved ID
        activeBiomes[biomeId] = savedBoundary

        // Check chunks that might be affected by this new biome
        updateChunksForNewBiome(savedBoundary)

        return@withContext biomeId
    }

    /**
     * Updates chunks that might be affected by a newly created biome
     */
    private fun updateChunksForNewBiome(boundary: CustomBiomeBoundary) {
        val world = Defcon.instance.server.getWorld(boundary.worldName) ?: return

        // Calculate chunk range
        val minChunkX = boundary.minX shr 4
        val maxChunkX = boundary.maxX shr 4
        val minChunkZ = boundary.minZ shr 4
        val maxChunkZ = boundary.maxZ shr 4

        // For each potentially affected chunk that's loaded
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                if (world.isChunkLoaded(chunkX, chunkZ)) {
                    // Find players who should see this biome
                    for (player in world.players) {
                        val playerChunkX = player.location.blockX shr 4
                        val playerChunkZ = player.location.blockZ shr 4
                        val viewDistance = player.viewDistance.coerceAtMost(10)

                        val deltaX = playerChunkX - chunkX
                        val deltaZ = playerChunkZ - chunkZ
                        val distanceSquared = deltaX * deltaX + deltaZ * deltaZ

                        if (distanceSquared <= viewDistance * viewDistance) {
                            makeBiomeVisibleToPlayer(player.uniqueId, boundary.uuid)
                        }
                    }
                }
            }
        }
    }

    /**
     * Creates a new custom biome area synchronously (for use in Bukkit API methods that can't be suspended).
     * This is a convenience method that launches a coroutine but returns immediately.
     */
    fun createBiomeAreaSync(
        center: Location,
        biome: CustomBiome,
        lengthPositiveY: Int,
        lengthNegativeY: Int,
        lengthPositiveX: Int,
        lengthNegativeX: Int,
        lengthPositiveZ: Int,
        lengthNegativeZ: Int,
        callback: (UUID) -> Unit = {}
    ) {
        Defcon.instance.launch(Dispatchers.Default) {
            val biomeId = createBiomeArea(
                center, biome, lengthPositiveY, lengthNegativeY,
                lengthPositiveX, lengthNegativeX, lengthPositiveZ, lengthNegativeZ
            )
            callback(biomeId)
        }
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
     * Removes a custom biome area from both memory and persistent storage.
     *
     * @param biomeId UUID of the biome area to remove
     * @return true if the biome was found and removed, false otherwise
     */
    suspend fun removeBiomeArea(biomeId: UUID): Boolean = withContext(Dispatchers.Default) {
        val biome = activeBiomes.remove(biomeId) ?: return@withContext false

        // Update all players who could see this biome
        for (playerId in playerVisibleBiomes.keys) {
            playerVisibleBiomes[playerId]?.remove(biomeId)
            updateClientSideBiomeChunks(playerId)
        }

        // Remove from persistent storage
        val success = BiomeAreaSave.getSave(biome.worldName).delete(biome.id)
        return@withContext success
    }

    /**
     * Removes a custom biome area synchronously (for use in Bukkit API methods).
     * This is a convenience method that launches a coroutine but returns immediately.
     */
    fun removeBiomeAreaSync(biomeId: UUID, callback: (Boolean) -> Unit = {}) {
        Defcon.instance.launch(Dispatchers.Default) {
            val success = removeBiomeArea(biomeId)
            callback(success)
        }
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
     * Unloads biomes for a specific world from memory.
     * This should be called when a world is unloaded.
     */
    fun unloadBiomesForWorld(worldName: String) {
        Logger.info("Unloading biomes for world: $worldName")

        // Find all biomes in this world
        val biomesToRemove = activeBiomes.values
            .filter { it.worldName == worldName }
            .map { it.uuid }
            .toList()

        // Remove them from the active biomes map
        for (biomeId in biomesToRemove) {
            activeBiomes.remove(biomeId)
        }

        // Remove them from player visible biomes
        for (playerId in playerVisibleBiomes.keys) {
            val visible = playerVisibleBiomes[playerId] ?: continue
            visible.removeAll(biomesToRemove.toSet())
        }

        loadedWorlds.remove(worldName)
        Logger.info("Unloaded ${biomesToRemove.size} biomes for world: $worldName")
    }
}