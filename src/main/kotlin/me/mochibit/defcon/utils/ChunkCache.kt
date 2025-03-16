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

package me.mochibit.defcon.utils

import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

class ChunkCache private constructor(
    private val world: World,
    private val maxCacheSize: Int = 500,
    private val expirationTimeMs: Long = 60000 // 60 seconds expiration time
) {
    companion object {
        private val instanceCache = ConcurrentHashMap<World, ChunkCache>()
        private val cleanupExecutor = Executors.newSingleThreadScheduledExecutor { r ->
            val thread = Thread(r, "ChunkCache-Cleanup")
            thread.isDaemon = true
            thread
        }

        // Track overall memory usage
        private val totalSnapshotsLoaded = AtomicInteger(0)
        private val MAX_TOTAL_SNAPSHOTS = 1000 // Reduced from 2000

        init {
            // Schedule periodic cleanup - less frequent (60 seconds instead of 30)
            cleanupExecutor.scheduleAtFixedRate({
                try {
                    val currentCount = totalSnapshotsLoaded.get()
                    if (currentCount > MAX_TOTAL_SNAPSHOTS * 0.8) { // 80% threshold
                        // Only clean if we're approaching the limit
                        instanceCache.values.forEach { it.cleanupCache() }
                    }
                } catch (e: Exception) {
                    Bukkit.getLogger().warning("Error in ChunkCache cleanup: ${e.message}")
                }
            }, 60, 60, TimeUnit.SECONDS)
        }

        fun getInstance(world: World, maxCacheSize: Int = 500): ChunkCache {
            return instanceCache.computeIfAbsent(world) { ChunkCache(world, maxCacheSize) }
        }

        // Cleanup all caches - avoid forced GC
        fun cleanupAllCaches() {
            instanceCache.values.forEach { it.cleanupCache() }
            totalSnapshotsLoaded.set(instanceCache.values.sumOf { it.localCache.size })
        }
    }

    private data class ChunkEntry(
        val snapshot: ChunkSnapshot,
        var lastAccess: Long = System.currentTimeMillis(),
        var accessCount: Int = 0
    )

    // Use a simple chunk key instead of Pair
    private data class ChunkKey(val x: Int, val z: Int)

    // Primary cache with expiration tracking
    private val localCache = ConcurrentHashMap<ChunkKey, ChunkEntry>()

    // Get cached snapshot or create a new one
    private fun getChunkSnapshot(chunkX: Int, chunkZ: Int): ChunkSnapshot {
        val key = ChunkKey(chunkX, chunkZ)

        // Try to get from cache first
        val entry = localCache[key]
        if (entry != null) {
            entry.lastAccess = System.currentTimeMillis()
            entry.accessCount++
            return entry.snapshot
        }

        // Check if we're over the memory limit
        if (localCache.size >= maxCacheSize || totalSnapshotsLoaded.get() >= MAX_TOTAL_SNAPSHOTS) {
            cleanupCache()
        }

        // Get new snapshot
        val snapshot = world.getChunkAt(chunkX, chunkZ).chunkSnapshot
        localCache[key] = ChunkEntry(snapshot)
        totalSnapshotsLoaded.incrementAndGet()

        return snapshot
    }

    // Clean up least recently used chunks
    fun cleanupCache() {
        val now = System.currentTimeMillis()

        // Sort by last access time and remove oldest entries
        val sortedEntries = localCache.entries.sortedBy { it.value.lastAccess }
        val removeCount = (sortedEntries.size * 0.2).toInt() // Remove 20% of oldest entries

        sortedEntries.take(removeCount).forEach { (key, _) ->
            localCache.remove(key)
            totalSnapshotsLoaded.decrementAndGet()
        }

        // Also remove expired entries
        val iterator = localCache.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (now - entry.value.lastAccess > expirationTimeMs) {
                iterator.remove()
                totalSnapshotsLoaded.decrementAndGet()
            }
        }
    }

    // Optimized cuboid region processing - uses the cache properly
    fun processCuboidRegion(
        minX: Int, minY: Int, minZ: Int,
        maxX: Int, maxY: Int, maxZ: Int,
        processor: (x: Int, y: Int, z: Int, material: Material) -> Unit
    ) {
        // Find all chunks that intersect with the region
        val minChunkX = minX shr 4
        val maxChunkX = maxX shr 4
        val minChunkZ = minZ shr 4
        val maxChunkZ = maxZ shr 4

        // Process by chunks to maximize cache usage
        for (chunkX in minChunkX..maxChunkX) {
            for (chunkZ in minChunkZ..maxChunkZ) {
                val snapshot = getChunkSnapshot(chunkX, chunkZ)

                // Calculate block bounds within this chunk
                val chunkMinX = chunkX shl 4
                val chunkMaxX = chunkMinX + 15
                val chunkMinZ = chunkZ shl 4
                val chunkMaxZ = chunkMinZ + 15

                val blockMinX = maxOf(minX, chunkMinX)
                val blockMaxX = minOf(maxX, chunkMaxX)
                val blockMinZ = maxOf(minZ, chunkMinZ)
                val blockMaxZ = minOf(maxZ, chunkMaxZ)

                // Process blocks in this chunk
                for (x in blockMinX..blockMaxX) {
                    for (z in blockMinZ..blockMaxZ) {
                        for (y in minY..maxY) {
                            if (y < 0 || y >= world.maxHeight) continue
                            val material = snapshot.getBlockType(x and 15, y, z and 15)
                            processor(x, y, z, material)
                        }
                    }
                }
            }

            // After processing a row of chunks, do a quick cleanup if needed
            if (localCache.size > maxCacheSize) {
                cleanupCache()
            }
        }
    }

    fun highestBlockYAt(x: Int, z: Int): Int {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        return getChunkSnapshot(chunkX, chunkZ).getHighestBlockYAt(x and 15, z and 15)
    }

    fun getBlockMaterial(x: Int, y: Int, z: Int): Material {
        if (y < 0 || y >= world.maxHeight) return Material.AIR
        val chunkX = x shr 4
        val chunkZ = z shr 4
        return getChunkSnapshot(chunkX, chunkZ).getBlockType(x and 15, y, z and 15)
    }

    fun getBlockData(x: Int, y: Int, z: Int): BlockData {
        if (y < 0 || y >= world.maxHeight) return Bukkit.createBlockData(Material.AIR)
        val chunkX = x shr 4
        val chunkZ = z shr 4
        return getChunkSnapshot(chunkX, chunkZ).getBlockData(x and 15, y, z and 15)
    }
}