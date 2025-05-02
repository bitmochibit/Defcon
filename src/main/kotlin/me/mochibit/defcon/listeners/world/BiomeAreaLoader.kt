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

package me.mochibit.defcon.listeners.world

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.save.savedata.BiomeAreaSave
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent
import org.bukkit.event.world.WorldUnloadEvent
import java.util.concurrent.ConcurrentHashMap

/**
 * Handles loading of custom biomes when chunks are loaded.
 * Only loads biomes that intersect with the loaded chunks.
 */
class BiomeChunkLoader : Listener {
    // Track which chunks have been checked for biomes in each world
    private val processedChunks = ConcurrentHashMap<String, MutableSet<ChunkCoord>>()

    // Track which biomes are active in which chunks
    private val chunkBiomeMap = ConcurrentHashMap<ChunkCoord, MutableSet<CustomBiomeHandler.CustomBiomeBoundary>>()

    data class ChunkCoord(val x: Int, val z: Int, val world: String)

    @EventHandler
    fun onChunkLoad(event: ChunkLoadEvent) {
        val chunk = event.chunk
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val worldName = chunk.world.name
        val chunkCoord = ChunkCoord(chunkX, chunkZ, worldName)

        // Skip if this chunk has already been processed
        if (processedChunks.computeIfAbsent(worldName) { mutableSetOf() }.contains(chunkCoord)) {
            return
        }

        // Mark as processed
        processedChunks[worldName]?.add(chunkCoord)

        // Check if there are any biomes intersecting this chunk
        Defcon.instance.launch(Dispatchers.Default) {
            // Make sure biome data is available for this world
            ensureWorldBiomesLoaded(worldName)

            // Find biomes that intersect with this chunk
            val intersectingBiomes = CustomBiomeHandler.getAllActiveBiomes().filter { biome ->
                biome.worldName == worldName && biome.intersectsChunk(chunkX, chunkZ)
            }.toSet()

            if (intersectingBiomes.isNotEmpty()) {
                chunkBiomeMap[chunkCoord] = intersectingBiomes.toMutableSet()

                // Update any players in or near this chunk to see these biomes
                updatePlayersAroundChunk(chunk.world, chunkX, chunkZ, intersectingBiomes)
            }
        }
    }

    @EventHandler
    fun onChunkUnload(event: ChunkUnloadEvent) {
        val chunk = event.chunk
        val chunkX = chunk.x
        val chunkZ = chunk.z
        val worldName = chunk.world.name
        val chunkCoord = ChunkCoord(chunkX, chunkZ, worldName)

        // Remove this chunk from our tracking
        chunkBiomeMap.remove(chunkCoord)
        processedChunks[worldName]?.remove(chunkCoord)
    }

    @EventHandler
    fun onWorldUnload(event: WorldUnloadEvent) {
        // Clean up all data for this world
        val worldName = event.world.name
        processedChunks.remove(worldName)

        // Remove all chunk data for this world
        val chunksToRemove = chunkBiomeMap.keys.filter { it.world == worldName }
        chunksToRemove.forEach { chunkBiomeMap.remove(it) }

        // Unload the biomes from the handler
        CustomBiomeHandler.unloadBiomesForWorld(worldName)
    }

    /**
     * Ensures the biome data for a world is loaded
     */
    private suspend fun ensureWorldBiomesLoaded(worldName: String) {
        // Only load if not already loaded
        if (!CustomBiomeHandler.isWorldLoaded(worldName)) {
            Logger.info("Loading biomes for world: $worldName")
            val biomeSave = BiomeAreaSave.getSave(worldName)

            try {
                val savedBiomes = biomeSave.getAll()

                for (biome in savedBiomes) {
                    CustomBiomeHandler.activateBiome(biome)
                }

                CustomBiomeHandler.markWorldAsLoaded(worldName)
            } catch (e: Exception) {
                Logger.warn("Failed to load biomes for world $worldName: ${e.message}")
                e.printStackTrace()
            }
        }
    }

    /**
     * Updates players in the vicinity of a chunk to see the biomes
     */
    private fun updatePlayersAroundChunk(world: org.bukkit.World, chunkX: Int, chunkZ: Int, biomes: Set<CustomBiomeHandler.CustomBiomeBoundary>) {
        // Get players within a reasonable distance of this chunk
        val potentialPlayers = world.players.filter { player ->
            val playerChunkX = player.location.blockX shr 4
            val playerChunkZ = player.location.blockZ shr 4
            val viewDistance = player.viewDistance.coerceAtMost(10)

            val deltaX = playerChunkX - chunkX
            val deltaZ = playerChunkZ - chunkZ
            val distanceSquared = deltaX * deltaX + deltaZ * deltaZ

            // Check if the chunk is within view distance of the player
            distanceSquared <= viewDistance * viewDistance
        }

        // For each player, make these biomes visible
        for (player in potentialPlayers) {
            for (biome in biomes) {
                CustomBiomeHandler.makeBiomeVisibleToPlayer(player.uniqueId, biome.uuid)
            }
        }
    }
}
