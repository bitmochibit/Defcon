package me.mochibit.defcon.biomes

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import me.mochibit.defcon.threading.scheduling.intervalAsync
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.io.Closeable
import java.time.Instant
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.minutes
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
        val worldName: String, // Store the world name to differentiate between worlds
        val priority: Int = 0, // Priority flag for resolving overlaps (higher values take precedence)
        val transitions: List<BiomeTransition> = emptyList() // List of scheduled transitions
    ) {
        data class BiomeTransition(
            val transitionTime: Instant,
            val targetBiome: NamespacedKey,
            val targetPriority: Int = 0,
            val completed: Boolean = false,
        )

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

        /**
         * Checks if this biome boundary completely contains another.
         */
        fun contains(other: CustomBiomeBoundary): Boolean {
            // Must be in the same world
            if (worldName != other.worldName) return false

            // Check if this boundary completely contains the other boundary
            return minX <= other.minX && maxX >= other.maxX &&
                    minY <= other.minY && maxY >= other.maxY &&
                    minZ <= other.minZ && maxZ >= other.maxZ
        }

        /**
         * Creates a copy of this boundary with a new biome.
         */
        fun withBiome(newBiome: NamespacedKey): CustomBiomeBoundary {
            return copy(biome = newBiome)
        }

        /**
         * Creates a copy of this boundary with updated transitions.
         */
        fun withTransitions(newTransitions: List<BiomeTransition>): CustomBiomeBoundary {
            return copy(transitions = newTransitions)
        }

        /**
         * Creates a copy with a new priority value.
         */
        fun withPriority(newPriority: Int): CustomBiomeBoundary {
            return copy(priority = newPriority)
        }

        /**
         * Gets the next pending transition, if any.
         */
        fun getNextPendingTransition(): BiomeTransition? {
            val now = Instant.now()
            return transitions
                .filter { !it.completed && it.transitionTime.isBefore(now) }
                .minByOrNull { it.transitionTime }
        }
    }

    // Thread-safe map to store active biome boundaries by their unique IDs
    private val activeBiomes = ConcurrentHashMap<UUID, CustomBiomeBoundary>()

    // Thread-safe map to track which players can see which biomes (for client-side updates)
    private val playerVisibleBiomes = ConcurrentHashMap<UUID, MutableSet<UUID>>()

    // Track which worlds have had their biomes loaded
    private val loadedWorlds = Collections.synchronizedSet(HashSet<String>())

    // Tasks for periodic checks
    private var biomeMergeTask: Closeable? = null
    private var transitionCheckTask: Closeable? = null

    // Configuration for workers
    private val MERGE_CHECK_INTERVAL = 5.minutes
    private val TRANSITION_CHECK_INTERVAL = 30.seconds

    /**
     * Initializes the biome handler workers.
     * Should be called during plugin startup.
     */
    fun initialize() {
        biomeMergeTask = intervalAsync(MERGE_CHECK_INTERVAL) {
            checkAndMergeBiomes()
        }


        transitionCheckTask = intervalAsync(TRANSITION_CHECK_INTERVAL) {
            checkBiomeTransitions()
        }
    }

    /**
     * Shuts down the biome handler workers.
     * Should be called during plugin shutdown.
     */
    fun shutdown() {
        biomeMergeTask?.close()
        transitionCheckTask?.close()
        biomeMergeTask = null
        transitionCheckTask = null
        Logger.info("Shut down CustomBiomeHandler workers")
    }

    /**
     * Worker task that checks for biome boundaries that contain each other
     * and merges them based on priority.
     */
    private fun checkAndMergeBiomes() {
        try {
            val biomesToMerge = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary>>()

            // Group biomes by world for more efficient checking
            val biomesByWorld = activeBiomes.values.groupBy { it.worldName }

            // For each world
            for ((worldName, worldBiomes) in biomesByWorld) {
                // Check each pair of biomes
                for (containerCandidate in worldBiomes) {
                    for (containedCandidate in worldBiomes) {
                        // Skip self-comparison
                        if (containerCandidate.uuid == containedCandidate.uuid) continue

                        // If one contains the other and has higher priority, mark for merge
                        if (containerCandidate.contains(containedCandidate) &&
                            containerCandidate.priority > containedCandidate.priority
                        ) {
                            biomesToMerge.add(containerCandidate to containedCandidate)
                        }
                    }
                }
            }

            // Process the merges
            if (biomesToMerge.isNotEmpty()) {
                Defcon.instance.launch(Dispatchers.Default) {
                    for ((container, contained) in biomesToMerge) {
                        mergeCustomBiomes(container, contained)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.err("Error in biome merge worker: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Worker task that checks for biome transitions that need to be applied.
     */
    private fun checkBiomeTransitions() {
        try {
            val biomesToTransition = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary.BiomeTransition>>()

            // Find biomes with pending transitions
            for (biome in activeBiomes.values) {
                val nextTransition = biome.getNextPendingTransition()
                if (nextTransition != null) {
                    biomesToTransition.add(biome to nextTransition)
                }
            }

            // Process the transitions
            if (biomesToTransition.isNotEmpty()) {
                Defcon.instance.launch(Dispatchers.Default) {
                    for ((biome, transition) in biomesToTransition) {
                        applyBiomeTransition(biome, transition)
                    }
                }
            }
        } catch (e: Exception) {
            Logger.err("Error in biome transition worker: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Merges one biome area into another.
     * The contained biome will be removed from the system.
     */
    private suspend fun mergeCustomBiomes(container: CustomBiomeBoundary, contained: CustomBiomeBoundary) {
        try {

            // Remove the contained biome (it will be replaced by the container)
            removeBiomeArea(contained.uuid)

            // Update all players to reflect the changes
            val world = Defcon.instance.server.getWorld(container.worldName)
            if (world != null) {
                for (player in world.players) {
                    updatePlayerBiomeView(player)
                }
            }

        } catch (e: Exception) {
            Logger.err("Failed to merge biomes: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Applies a biome transition by updating the biome type and marking the transition as completed.
     */
    private suspend fun applyBiomeTransition(
        biome: CustomBiomeBoundary,
        transition: CustomBiomeBoundary.BiomeTransition
    ) {
        try {

            // Create updated biome with new biome type
            val updatedBiome = biome.withBiome(transition.targetBiome)

            // Mark this transition as completed
            val updatedTransitions = biome.transitions.map {
                if (it == transition) it.copy(completed = true) else it
            }
            val finalBiome = updatedBiome.withTransitions(updatedTransitions).withPriority(transition.targetPriority)

            // Update in storage
            val biomeSave = BiomeAreaSave.getSave(biome.worldName)
            biomeSave.updateBiome(finalBiome)

            // Update in memory
            activeBiomes[biome.uuid] = finalBiome

            // Update all players who can see this biome
            for (playerId in playerVisibleBiomes.keys) {
                val playerBiomes = playerVisibleBiomes[playerId] ?: continue
                if (biome.uuid in playerBiomes) {
                    updateClientSideBiomeChunks(playerId)
                }
            }

        } catch (e: Exception) {
            Logger.err("Failed to apply biome transition: ${e.message}")
            e.printStackTrace()
        }
    }

    /**
     * Schedules a biome transition to occur at a specific time.
     */
    suspend fun scheduleBiomeTransition(
        biomeId: UUID,
        targetBiome: NamespacedKey,
        transitionTime: Instant,
        targetPriority: Int = 0
    ): Boolean = withContext(Dispatchers.Default) {
        val biome = activeBiomes[biomeId] ?: return@withContext false

        // Create a new transition
        val newTransition = CustomBiomeBoundary.BiomeTransition(transitionTime, targetBiome, targetPriority)

        // Add to existing transitions
        val updatedTransitions = biome.transitions + newTransition
        val updatedBiome = biome.withTransitions(updatedTransitions)

        // Update in storage and memory
        val biomeSave = BiomeAreaSave.getSave(biome.worldName)
        biomeSave.updateBiome(updatedBiome)
        activeBiomes[biomeId] = updatedBiome

        return@withContext true
    }

    /**
     * Updates the priority of a biome boundary.
     */
    suspend fun updateBiomePriority(biomeId: UUID, priority: Int): Boolean = withContext(Dispatchers.Default) {
        val biome = activeBiomes[biomeId] ?: return@withContext false

        // Create updated biome with new priority
        val updatedBiome = biome.withPriority(priority)

        // Update in storage and memory
        val biomeSave = BiomeAreaSave.getSave(biome.worldName)
        biomeSave.updateBiome(updatedBiome)
        activeBiomes[biomeId] = updatedBiome

        return@withContext true
    }

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

        // Find all biomes that contain this location
        val matchingBiomes = activeBiomes.values.filter { boundary ->
            boundary.worldName == worldName && boundary.isInBounds(x, y, z)
        }

        // If multiple biomes match, return the one with highest priority
        return matchingBiomes.maxByOrNull { it.priority }
    }

    /**
     * Gets the biome boundaries visible to a specific player.
     *
     * @param playerId UUID of the player
     * @return Set of CustomBiomeBoundary objects or empty set if none exists
     */
    fun getPlayerVisibleBiomes(playerId: UUID): Set<CustomBiomeBoundary> {
        val biomeIds = playerVisibleBiomes[playerId] ?: return emptySet()
        // Create a synchronized copy of the set to prevent concurrent modification during iteration
        val biomeIdsCopy = synchronized(biomeIds) { biomeIds.toSet() }

        // Now safely map over the copy
        return biomeIdsCopy.mapNotNull { activeBiomes[it] }.toSet()
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
        priority: Int = 0,
        transitions: List<CustomBiomeBoundary.BiomeTransition> = emptyList()
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
            worldName = worldName,
            priority = priority,
            transitions = transitions
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
    }
}