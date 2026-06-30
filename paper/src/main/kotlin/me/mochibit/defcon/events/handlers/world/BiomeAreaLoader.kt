/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.events.handlers.world

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.Logger
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.events.AutoRegisterHandler
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Optimized chunk-based biome loader that reduces server lag and fixes cross-world issues.
 */
@AutoRegisterHandler
class BiomeChunkLoader : Listener {
    // Per-world tracking to prevent cross-world contamination
    private val processedChunks = ConcurrentHashMap<String, ConcurrentHashMap<ChunkCoord, Boolean>>()
    private val chunkBiomeMap =
        ConcurrentHashMap<String, ConcurrentHashMap<ChunkCoord, MutableSet<CustomBiomeHandler.CustomBiomeBoundary>>>()


    // Add a loading states tracker to prevent spam
    private val worldLoadingStates = ConcurrentHashMap<String, Boolean>()

    // Add cooldown for player updates
    private val playerUpdateCooldown = ConcurrentHashMap<UUID, Long>()
    private val PLAYER_UPDATE_COOLDOWN_MS = 1000L // 1 second cooldown

    data class ChunkCoord(val x: Int, val z: Int) {
        override fun hashCode(): Int = x * 31 + z
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is ChunkCoord) return false
            return x == other.x && z == other.z
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val worldName = chunk.world.name
        val chunkCoord = ChunkCoord(chunk.x, chunk.z)

        // Get or create world-specific maps
        val worldProcessedChunks = processedChunks.computeIfAbsent(worldName) { ConcurrentHashMap() }
        val worldChunkBiomes = chunkBiomeMap.computeIfAbsent(worldName) { ConcurrentHashMap() }

        // Skip if already processed
        if (worldProcessedChunks.containsKey(chunkCoord)) {
            return
        }

        worldProcessedChunks[chunkCoord] = true

        Defcon.launch(Dispatchers.IO) {
            processChunkBiomes(worldName, chunkCoord, worldChunkBiomes)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val worldName = event.chunk.world.name
        val chunkCoord = ChunkCoord(event.chunk.x, event.chunk.z)

        // Clean up chunk data for this specific world
        chunkBiomeMap[worldName]?.remove(chunkCoord)
        processedChunks[worldName]?.remove(chunkCoord)
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onWorldUnload(event: WorldUnloadEvent) {
        val worldName = event.world.name

        // Clean up all data for this world
        processedChunks.remove(worldName)
        chunkBiomeMap.remove(worldName)

        // Notify biome handler to unload world-specific data
        CustomBiomeHandler.unloadBiomesForWorld(worldName)

        Logger.debug("Cleaned up biome data for unloaded world: $worldName")
    }

    /**
     * Processes biome intersections for a specific chunk.
     * This method is called asynchronously to prevent main thread blocking.
     */
    private suspend fun processChunkBiomes(
        worldName: String,
        chunkCoord: ChunkCoord,
        worldChunkBiomes: ConcurrentHashMap<ChunkCoord, MutableSet<CustomBiomeHandler.CustomBiomeBoundary>>
    ) {
        try {
            // Ensure world biomes are loaded
            ensureWorldBiomesLoaded(worldName)

            // Get biomes for this specific world only
            val worldBiomes = CustomBiomeHandler.getAllActiveBiomesInWorld(worldName)
            if (worldBiomes.isEmpty()) {
                return
            }

            // Find biomes that intersect with this chunk
            val intersectingBiomes = worldBiomes.filter { biome ->
                biome.intersectsChunk(chunkCoord.x, chunkCoord.z)
            }.toMutableSet()

            if (intersectingBiomes.isNotEmpty()) {
                worldChunkBiomes[chunkCoord] = intersectingBiomes

                // Update players in this world only
                updatePlayersInWorld(worldName, chunkCoord, intersectingBiomes)
            }
        } catch (e: Exception) {
            Logger.warn("Failed to process biomes for chunk ${chunkCoord.x},${chunkCoord.z} in world $worldName: ${e.message}")
        }
    }

    /**
     * Ensures biome data for a world is loaded, with improved error handling.
     */
    private suspend fun ensureWorldBiomesLoaded(worldName: String) {
        if (worldLoadingStates.putIfAbsent(worldName, true) != null) {
            return
        }

        try {
            if (CustomBiomeHandler.isWorldLoaded(worldName)) {
                worldLoadingStates[worldName] = false
                return
            }
            Logger.debug("Loading biomes for world: $worldName")
            val biomeSave = BiomeAreaSave.getSave(worldName)
            val savedBiomes = biomeSave.getAll()

            // Batch activate biomes to reduce individual calls
            if (savedBiomes.isNotEmpty()) {
                CustomBiomeHandler.activateBiomes(worldName, savedBiomes)
                Logger.debug("Loaded ${savedBiomes.size} biomes for world: $worldName")
            }

            CustomBiomeHandler.markWorldAsLoaded(worldName)
        } catch (e: Exception) {
            Logger.err("Failed to load biomes for world $worldName")
            e.printStackTrace()
        } finally {
            worldLoadingStates.remove(worldName)
        }
    }

    /**
     * Updates players in a specific world only, preventing cross-world contamination.
     */
    private fun updatePlayersInWorld(
        worldName: String,
        chunkCoord: ChunkCoord,
        biomes: Set<CustomBiomeHandler.CustomBiomeBoundary>
    ) {
        val world = Defcon.server.getWorld(worldName) ?: return

        // Only get players from this specific world
        val worldPlayers = world.players
        if (worldPlayers.isEmpty()) return

        // Filter players based on view distance more efficiently
        val now = System.currentTimeMillis()
        val nearbyPlayers = worldPlayers.filter { player ->
            // Check cooldown first
            val lastUpdate = playerUpdateCooldown[player.uniqueId] ?: 0
            if (now - lastUpdate < PLAYER_UPDATE_COOLDOWN_MS) {
                return@filter false
            }

            val playerChunkX = player.location.blockX shr 4
            val playerChunkZ = player.location.blockZ shr 4
            val viewDistance = minOf(player.viewDistance, 8) // Reduced from 10

            val deltaX = playerChunkX - chunkCoord.x
            val deltaZ = playerChunkZ - chunkCoord.z
            val distanceSquared = deltaX * deltaX + deltaZ * deltaZ

            distanceSquared <= viewDistance * viewDistance
        }

        if (nearbyPlayers.isNotEmpty()) {
            for (player in nearbyPlayers) {
                playerUpdateCooldown[player.uniqueId] = now
                for (biome in biomes) {
                    CustomBiomeHandler.addBiomeVisibilityForPlayer(player.uniqueId, biome.uuid, worldName)
                }
            }
        }
    }

    /**
     * Gets biomes active in a specific chunk of a specific world.
     */
    fun getChunkBiomes(worldName: String, chunkX: Int, chunkZ: Int): Set<CustomBiomeHandler.CustomBiomeBoundary>? {
        return chunkBiomeMap[worldName]?.get(ChunkCoord(chunkX, chunkZ))?.toSet()
    }

    /**
     * Gets all processed chunks for a world (for debugging/monitoring).
     */
    fun getProcessedChunksForWorld(worldName: String): Set<ChunkCoord> {
        return processedChunks[worldName]?.keys?.toSet() ?: emptySet()
    }

    /**
     * Clears processed chunks for a world (useful for reloading).
     */
    fun clearProcessedChunksForWorld(worldName: String) {
        processedChunks[worldName]?.clear()
        chunkBiomeMap[worldName]?.clear()
    }
}