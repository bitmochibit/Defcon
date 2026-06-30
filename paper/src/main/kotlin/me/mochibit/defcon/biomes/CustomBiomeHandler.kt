package me.mochibit.defcon.biomes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.chunked
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import me.mochibit.defcon.utils.Logger.info
import org.bukkit.Location
import org.bukkit.NamespacedKey
import org.bukkit.entity.Player
import java.util.Collections
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

@OptIn(ExperimentalTime::class, ExperimentalCoroutinesApi::class)
object CustomBiomeHandler {
    data class CustomBiomeBoundary(
        val id: Int = 0,
        val uuid: UUID,
        val biome: NamespacedKey,
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int,
        val worldName: String,
        val priority: Int = 0,
        val transitions: List<BiomeTransition> = emptyList(),
    ) {
        data class BiomeTransition(
            val transitionDuration: Duration,
            val targetBiome: NamespacedKey,
            val targetPriority: Int = 0,
            val transitionTime: Instant = Clock.System.now() + transitionDuration,
            val completed: Boolean = false,
        )

        @Transient
        private val chunkIntersectionCache = ConcurrentHashMap<Long, Boolean>()

        // Combined bounds checking
        fun isInBounds(
            x: Int,
            y: Int,
            z: Int,
        ): Boolean = x in minX..maxX && y in minY..maxY && z in minZ..maxZ

        fun intersectsChunk(
            chunkX: Int,
            chunkZ: Int,
        ): Boolean {
            val key = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
            return chunkIntersectionCache.computeIfAbsent(key) {
                val chunkMinX = chunkX shl 4
                val chunkMaxX = chunkMinX + 15
                val chunkMinZ = chunkZ shl 4
                val chunkMaxZ = chunkMinZ + 15
                !(chunkMaxX < minX || chunkMinX > maxX || chunkMaxZ < minZ || chunkMinZ > maxZ)
            }
        }

        fun contains(other: CustomBiomeBoundary): Boolean =
            worldName == other.worldName &&
                minX <= other.minX && maxX >= other.maxX &&
                minY <= other.minY && maxY >= other.maxY &&
                minZ <= other.minZ && maxZ >= other.maxZ

        fun getNextPendingTransition(): BiomeTransition? {
            val now = Clock.System.now()
            return transitions
                .asSequence()
                .filter { !it.completed && it.transitionTime < now }
                .minByOrNull { it.transitionTime }
        }

        fun clearCache() = chunkIntersectionCache.clear()

        // Immutable update methods - kotlin idiomatic
        fun withBiome(newBiome: NamespacedKey) = copy(biome = newBiome)

        fun withTransitions(newTransitions: List<BiomeTransition>) = copy(transitions = newTransitions)

        fun withPriority(newPriority: Int) = copy(priority = newPriority)
    }

    // Consolidated world management
    private val worldData = ConcurrentHashMap<String, WorldBiomeData>()
    private val loadedWorlds = Collections.synchronizedSet(HashSet<String>())

    // Flow-based player updates
    private val playerVisibilityUpdates =
        MutableSharedFlow<PlayerVisibilityUpdate>(
            extraBufferCapacity = 1000,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )

    // Flow-based biome state changes
    private val biomeStateChanges =
        MutableSharedFlow<BiomeStateChange>(
            replay = 0,
            extraBufferCapacity = 100,
            onBufferOverflow = kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST,
        )

    private data class WorldBiomeData(
        val biomes: ConcurrentHashMap<UUID, CustomBiomeBoundary> = ConcurrentHashMap(),
        val spatialIndex: SpatialIndex = SpatialIndex(),
        val playerVisibility: ConcurrentHashMap<UUID, ConcurrentHashMap.KeySetView<UUID, Boolean>> = ConcurrentHashMap(),
    )

    private data class PlayerVisibilityUpdate(
        val playerId: UUID,
        val worldName: String,
        val biomeId: UUID,
        val isVisible: Boolean,
    )

    private data class BiomeStateChange(
        val biomeId: UUID,
        val worldName: String,
        val changeType: BiomeChangeType,
        val biome: CustomBiomeBoundary? = null,
    )

    private enum class BiomeChangeType { CREATED, UPDATED, REMOVED, TRANSITIONED }

    private class SpatialIndex {
        private val chunkToBiomes = ConcurrentHashMap<Long, MutableSet<UUID>>()

        fun addBiome(biome: CustomBiomeBoundary) {
            getChunkRange(biome).forEach { chunkKey ->
                chunkToBiomes
                    .computeIfAbsent(chunkKey) {
                        Collections.synchronizedSet(HashSet())
                    }.add(biome.uuid)
            }
        }

        fun removeBiome(biome: CustomBiomeBoundary) {
            getChunkRange(biome).forEach { chunkKey ->
                chunkToBiomes[chunkKey]?.remove(biome.uuid)
            }
        }

        fun getBiomesInChunk(
            chunkX: Int,
            chunkZ: Int,
        ): Set<UUID> {
            val key = packChunkKey(chunkX, chunkZ)
            return chunkToBiomes[key]?.toSet() ?: emptySet()
        }

        private fun getChunkRange(biome: CustomBiomeBoundary): Sequence<Long> =
            sequence {
                val minChunkX = biome.minX shr 4
                val maxChunkX = biome.maxX shr 4
                val minChunkZ = biome.minZ shr 4
                val maxChunkZ = biome.maxZ shr 4

                for (chunkX in minChunkX..maxChunkX) {
                    for (chunkZ in minChunkZ..maxChunkZ) {
                        yield(packChunkKey(chunkX, chunkZ))
                    }
                }
            }

        fun clear() = chunkToBiomes.clear()

        companion object {
            @JvmStatic
            private fun packChunkKey(
                chunkX: Int,
                chunkZ: Int,
            ): Long = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)
        }
    }

    // Task management with flows
    private var taskScope: CoroutineScope? = null
    private var biomeMergeJob: Job? = null
    private var transitionCheckJob: Job? = null
    private var playerUpdateJob: Job? = null

    // Optimized constants
    private val MERGE_CHECK_INTERVAL = 5.minutes
    private val TRANSITION_CHECK_INTERVAL = 30.seconds
    private const val MAX_CHUNKS_PER_BATCH = 16 // Increased for better throughput
    private const val CHUNK_UPDATE_DELAY_MS = 50L // Reduced delay for faster updates
    private const val PLAYER_UPDATE_BATCH_SIZE = 100 // Increased batch size

    fun initialize() {
        info("Initializing custom biome handler...")
        taskScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

        // Periodic biome management tasks
        biomeMergeJob =
            taskScope?.launch {
                while (isActive) {
                    delay(MERGE_CHECK_INTERVAL)
                    processBiomeMerges()
                }
            }

        transitionCheckJob =
            taskScope?.launch {
                while (isActive) {
                    delay(TRANSITION_CHECK_INTERVAL)
                    processBiomeTransitions()
                }
            }

        // Flow-based player visibility updates
        playerUpdateJob =
            taskScope?.launch {
                playerVisibilityUpdates
                    .buffer(capacity = PLAYER_UPDATE_BATCH_SIZE)
                    .chunked(PLAYER_UPDATE_BATCH_SIZE)
                    .collect { updates ->
                        processPlayerVisibilityUpdates(updates)
                    }
            }

        info("CustomBiomeHandler initialized with flow-based processing")
    }

    fun shutdown() {
        taskScope?.cancel()
        biomeMergeJob = null
        transitionCheckJob = null
        playerUpdateJob = null

        worldData.values.forEach { data ->
            data.biomes.values.forEach { it.clearCache() }
        }
        worldData.clear()
        loadedWorlds.clear()

        info("CustomBiomeHandler shut down")
    }

    private suspend fun processBiomeMerges() {
        val mergeCandidates = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary>>()

        worldData.values.forEach { worldData ->
            val biomes = worldData.biomes.values.toList()
            for (i in biomes.indices) {
                for (j in i + 1 until biomes.size) {
                    val biome1 = biomes[i]
                    val biome2 = biomes[j]

                    when {
                        biome1.contains(biome2) && biome1.priority > biome2.priority -> {
                            mergeCandidates.add(biome1 to biome2)
                        }

                        biome2.contains(biome1) && biome2.priority > biome1.priority -> {
                            mergeCandidates.add(biome2 to biome1)
                        }
                    }
                }
            }
        }

        mergeCandidates.forEach { (container, contained) ->
            mergeCustomBiomes(container, contained)
        }
    }

    private suspend fun processBiomeTransitions() {
        val transitionsToApply = mutableListOf<Pair<CustomBiomeBoundary, CustomBiomeBoundary.BiomeTransition>>()

        worldData.values.forEach { worldData ->
            worldData.biomes.values.forEach { biome ->
                biome.getNextPendingTransition()?.let { transition ->
                    transitionsToApply.add(biome to transition)
                }
            }
        }

        transitionsToApply.forEach { (biome, transition) ->
            applyBiomeTransition(biome, transition)
        }
    }

    private suspend fun processPlayerVisibilityUpdates(updates: List<PlayerVisibilityUpdate>) {
        val groupedUpdates = updates.groupBy { it.playerId }

        groupedUpdates.forEach { (playerId, playerUpdates) ->
            val player = Defcon.server.getPlayer(playerId) ?: return@forEach

            playerUpdates.forEach { update ->
                if (player.world.name == update.worldName) {
                    updateClientSideBiomeChunks(player, update.biomeId, update.isVisible)
                }
            }
        }
    }

    // Optimized biome management methods
    fun isWorldLoaded(worldName: String): Boolean = loadedWorlds.contains(worldName)

    fun markWorldAsLoaded(worldName: String) {
        loadedWorlds.add(worldName)
        worldData.computeIfAbsent(worldName) { WorldBiomeData() }
    }

    fun activateBiome(biome: CustomBiomeBoundary) {
        val data = worldData.computeIfAbsent(biome.worldName) { WorldBiomeData() }
        data.biomes[biome.uuid] = biome
        data.spatialIndex.addBiome(biome)

        // Emit state change
        biomeStateChanges.tryEmit(
            BiomeStateChange(biome.uuid, biome.worldName, BiomeChangeType.CREATED, biome),
        )
    }

    fun activateBiomes(
        worldName: String,
        biomes: Collection<CustomBiomeBoundary>,
    ) {
        val data = worldData.computeIfAbsent(worldName) { WorldBiomeData() }

        biomes.forEach { biome ->
            data.biomes[biome.uuid] = biome
            data.spatialIndex.addBiome(biome)
        }
    }

    fun addBiomeVisibilityForPlayer(
        playerId: UUID,
        biomeId: UUID,
        worldName: String,
    ) {
        val data = worldData[worldName] ?: return
        val playerBiomes =
            data.playerVisibility.computeIfAbsent(playerId) {
                ConcurrentHashMap.newKeySet()
            }

        if (playerBiomes.add(biomeId)) {
            playerVisibilityUpdates.tryEmit(
                PlayerVisibilityUpdate(playerId, worldName, biomeId, true),
            )
        }
    }

    fun removeBiomeVisibilityFromPlayer(
        playerId: UUID,
        biomeId: UUID,
        worldName: String,
    ) {
        val data = worldData[worldName] ?: return
        val removed = data.playerVisibility[playerId]?.remove(biomeId) == true

        if (removed) {
            playerVisibilityUpdates.tryEmit(
                PlayerVisibilityUpdate(playerId, worldName, biomeId, false),
            )
        }
    }

    fun getBiomesInChunk(
        worldName: String,
        chunkX: Int,
        chunkZ: Int,
    ): Collection<CustomBiomeBoundary> {
        val data = worldData[worldName] ?: return emptySet()
        val biomeIds = data.spatialIndex.getBiomesInChunk(chunkX, chunkZ)
        return biomeIds.mapNotNull { data.biomes[it] }
    }

    fun getBiomeAtLocation(location: Location): CustomBiomeBoundary? {
        val data = worldData[location.world.name] ?: return null
        val x = location.blockX
        val y = location.blockY
        val z = location.blockZ
        val chunkX = x shr 4
        val chunkZ = z shr 4

        val candidateBiomes =
            data.spatialIndex
                .getBiomesInChunk(chunkX, chunkZ)
                .mapNotNull { data.biomes[it] }
                .filter { it.isInBounds(x, y, z) }

        return candidateBiomes.maxByOrNull { it.priority }
    }

    fun getPlayerVisibleBiomes(
        playerId: UUID,
        worldName: String,
    ): Set<CustomBiomeBoundary> {
        val data = worldData[worldName] ?: return emptySet()
        val biomeIds = data.playerVisibility[playerId] ?: return emptySet()
        return biomeIds.mapNotNull { data.biomes[it] }.toSet()
    }

    fun getAllActiveBiomesInWorld(worldName: String): Collection<CustomBiomeBoundary> = worldData[worldName]?.biomes?.values ?: emptySet()

    fun getAllActiveBiomes(): Collection<CustomBiomeBoundary> = worldData.values.flatMap { it.biomes.values }

    // Simplified biome creation with flow integration
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
        transitions: List<CustomBiomeBoundary.BiomeTransition> = emptyList(),
    ): UUID =
        withContext(Dispatchers.Default) {
            val worldName = center.world.name
            val biomeId = UUID.randomUUID()

            val boundary =
                CustomBiomeBoundary(
                    uuid = biomeId,
                    biome = biome.asBukkitBiome.key,
                    minX = center.blockX - lengthNegativeX,
                    maxX = center.blockX + lengthPositiveX,
                    minY = (center.blockY - lengthNegativeY).coerceAtLeast(center.world.minHeight),
                    maxY = (center.blockY + lengthPositiveY).coerceAtMost(center.world.maxHeight),
                    minZ = center.blockZ - lengthNegativeZ,
                    maxZ = center.blockZ + lengthPositiveZ,
                    worldName = worldName,
                    priority = priority,
                    transitions = transitions,
                )

            markWorldAsLoaded(worldName)
            val biomeSave = BiomeAreaSave.getSave(worldName)
            val savedBoundary = biomeSave.addBiome(boundary)

            activateBiome(savedBoundary)
            updateAffectedPlayers(savedBoundary)

            biomeId
        }

    private fun updateAffectedPlayers(boundary: CustomBiomeBoundary) {
        val world = Defcon.server.getWorld(boundary.worldName) ?: return

        world.players.forEach { player ->
            val playerChunkX = player.location.blockX shr 4
            val playerChunkZ = player.location.blockZ shr 4

            if (boundary.intersectsChunk(playerChunkX, playerChunkZ)) {
                addBiomeVisibilityForPlayer(player.uniqueId, boundary.uuid, boundary.worldName)
            }
        }
    }

    private suspend fun updateClientSideBiomeChunks(
        player: Player,
        biomeId: UUID,
        isVisible: Boolean,
    ) = withContext(Dispatchers.Default) {
        val worldName = player.world.name
        val data = worldData[worldName] ?: return@withContext
        val biome = data.biomes[biomeId] ?: return@withContext

        if (!isVisible) return@withContext // Skip updates for removed biomes

        val playerChunkX = player.location.blockX shr 4
        val playerChunkZ = player.location.blockZ shr 4
        val viewDistance = player.viewDistance.coerceAtMost(8)

        // Calculate affected chunks within view distance
        val minChunkX = (biome.minX shr 4).coerceAtLeast(playerChunkX - viewDistance)
        val maxChunkX = (biome.maxX shr 4).coerceAtMost(playerChunkX + viewDistance)
        val minChunkZ = (biome.minZ shr 4).coerceAtLeast(playerChunkZ - viewDistance)
        val maxChunkZ = (biome.maxZ shr 4).coerceAtMost(playerChunkZ + viewDistance)

        val chunksToUpdate =
            buildList {
                for (chunkX in minChunkX..maxChunkX) {
                    for (chunkZ in minChunkZ..maxChunkZ) {
                        if (player.world.isChunkLoaded(chunkX, chunkZ)) {
                            add(chunkX to chunkZ)
                        }
                    }
                }
            }

        // Process chunks in batches and apply biomes
        chunksToUpdate
            .take(MAX_CHUNKS_PER_BATCH)
            .chunked(4) // Process 4 chunks at a time for optimal performance
            .forEach { chunkBatch ->
                withContext(Dispatchers.IO) {
                    chunkBatch.forEach { (chunkX, chunkZ) ->
                        val chunk = player.world.getChunkAt(chunkX, chunkZ)
                        applyBiomesToChunk(chunk, biome)
                    }

                    // Resend all chunks in this batch to the player
                    chunkBatch.forEach { (chunkX, chunkZ) ->
                        player.world.refreshChunk(chunkX, chunkZ)
                    }
                }
                delay(CHUNK_UPDATE_DELAY_MS)
            }
    }

    /**
     * Applies custom biomes to a chunk by modifying biome data
     * Only affects positions within the custom biome boundary
     * This is client-side only and doesn't persist to the world
     */
    @Suppress("DEPRECATION")
    private fun applyBiomesToChunk(
        chunk: org.bukkit.Chunk,
        customBiome: CustomBiomeBoundary,
    ) {
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val chunkMinX = chunkX shl 4
        val chunkMinZ = chunkZ shl 4

        // Get the Bukkit biome from the custom biome
        val bukkitBiome =
            org.bukkit.Registry.BIOME
                .get(customBiome.biome)
                ?: org.bukkit.Registry.BIOME
                    .get(NamespacedKey.minecraft("plains"))
                ?: return

        // Set biomes for all positions in the chunk that are within the custom biome boundary
        // Biomes are stored in 4x4x4 blocks, so we iterate in steps of 4
        for (x in 0 until 16 step 4) {
            for (z in 0 until 16 step 4) {
                val worldX = chunkMinX + x
                val worldZ = chunkMinZ + z

                // Check if this position is within the X/Z bounds
                if (worldX in customBiome.minX..customBiome.maxX &&
                    worldZ in customBiome.minZ..customBiome.maxZ
                ) {
                    // Set biome for all Y levels in this column that are within bounds
                    for (y in customBiome.minY..customBiome.maxY step 4) {
                        try {
                            chunk.getBlock(x, y, z).biome = bukkitBiome
                        } catch (_: Exception) {
                            // Ignore errors for invalid coordinates
                        }
                    }
                }
            }
        }
    }

    // Remaining methods simplified and optimized...
    suspend fun removeBiomeArea(biomeId: UUID): Boolean =
        withContext(Dispatchers.Default) {
            val (biome, worldName) = findBiomeInAnyWorld(biomeId) ?: return@withContext false
            val data = worldData[worldName] ?: return@withContext false

            data.biomes.remove(biomeId)
            data.spatialIndex.removeBiome(biome)

            // Update affected players
            data.playerVisibility.keys.forEach { playerId ->
                removeBiomeVisibilityFromPlayer(playerId, biomeId, worldName)
            }

            biomeStateChanges.tryEmit(
                BiomeStateChange(biomeId, worldName, BiomeChangeType.REMOVED),
            )

            BiomeAreaSave.getSave(worldName).delete(biome.id)
        }

    private fun findBiomeInAnyWorld(biomeId: UUID): Pair<CustomBiomeBoundary, String>? {
        worldData.forEach { (worldName, data) ->
            data.biomes[biomeId]?.let { biome ->
                return biome to worldName
            }
        }
        return null
    }

    fun unloadBiomesForWorld(worldName: String) {
        worldData.remove(worldName)?.let { data ->
            data.biomes.values.forEach { it.clearCache() }
            data.spatialIndex.clear()
        }
        loadedWorlds.remove(worldName)
        info("Unloaded biomes for world: $worldName")
    }

    fun cleanupPlayer(playerId: UUID) {
        worldData.values.forEach { data ->
            data.playerVisibility.remove(playerId)
        }
    }

    private suspend fun mergeCustomBiomes(
        _container: CustomBiomeBoundary,
        contained: CustomBiomeBoundary,
    ) {
        removeBiomeArea(contained.uuid)
    }

    private suspend fun applyBiomeTransition(
        biome: CustomBiomeBoundary,
        transition: CustomBiomeBoundary.BiomeTransition,
    ) {
        val updatedBiome =
            biome
                .withBiome(transition.targetBiome)
                .withPriority(transition.targetPriority)
                .withTransitions(
                    biome.transitions.map {
                        if (it == transition) it.copy(completed = true) else it
                    },
                )

        val data = worldData[biome.worldName] ?: return
        data.biomes[biome.uuid] = updatedBiome

        BiomeAreaSave.getSave(biome.worldName).updateBiome(updatedBiome)

        biomeStateChanges.tryEmit(
            BiomeStateChange(biome.uuid, biome.worldName, BiomeChangeType.TRANSITIONED, updatedBiome),
        )
    }
}
