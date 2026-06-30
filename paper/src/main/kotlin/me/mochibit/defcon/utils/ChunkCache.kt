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

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.scope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.future.await
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.LinkedHashMap

class ChunkCache private constructor(
    private val world: World,
    private val maxAccessCount: Int = 20,
    private val useLocalCache: Boolean = true,
) {
    companion object {
        // Shared cache with memory-efficient eviction
        private val sharedChunkCache =
            object : LinkedHashMap<Long, SoftReference<SmartChunkSnapshot>>(16, 0.75f, true) {
                private val MAX_SHARED_CACHE_SIZE = 5000

                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SoftReference<SmartChunkSnapshot>>): Boolean =
                    size > MAX_SHARED_CACHE_SIZE
            }

        private val sharedCacheMutex = Mutex()
        private val instanceCache = ConcurrentHashMap<String, ChunkCache>()
        private val memoryStats = AtomicLong(0L)

        fun getInstance(
            world: World,
            maxAccessCount: Int = 20,
            useLocalCache: Boolean = true,
        ): ChunkCache {
            val key = "${world.name}_${maxAccessCount}_$useLocalCache"
            return instanceCache.computeIfAbsent(key) {
                ChunkCache(world, maxAccessCount, useLocalCache)
            }
        }

        suspend fun cleanupAllCaches() {
            sharedCacheMutex.withLock {
                sharedChunkCache.clear()
            }
            instanceCache.values.forEach { it.cleanupLocalCache() }
            memoryStats.set(0L)
        }

        fun getMemoryUsage(): Long = memoryStats.get()

        // Efficient chunk key packing
        fun packChunkKey(
            chunkX: Int,
            chunkZ: Int,
        ): Long = (chunkX.toLong() shl 32) or (chunkZ.toLong() and 0xFFFFFFFFL)

        fun unpackChunkX(key: Long): Int = (key shr 32).toInt()

        fun unpackChunkZ(key: Long): Int = key.toInt()
    }

    // Local cache - only used if useLocalCache is true
    private val localCache =
        if (useLocalCache) {
            object : LinkedHashMap<Long, SmartChunkSnapshot>(16, 0.75f, true) {
                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, SmartChunkSnapshot>): Boolean {
                    if (size > maxAccessCount) {
                        // Move to shared cache before eviction
                        Defcon.launch {
                            sharedCacheMutex.withLock {
                                sharedChunkCache[eldest.key] = SoftReference(eldest.value)
                            }
                        }
                        return true
                    }
                    return false
                }
            }
        } else {
            null
        }

    // Async chunk loading queue
    private val loadingQueue = Channel<ChunkLoadRequest>(capacity = Channel.UNLIMITED)
    private val loadingJobs = ConcurrentHashMap<Long, Deferred<SmartChunkSnapshot>>()
    private val localCacheMutex = Mutex()

    init {
        // Start async chunk loader
        Defcon.launch {
            for (request in loadingQueue) {
                processChunkLoadRequest(request)
            }
        }
    }

    private data class ChunkLoadRequest(
        val chunkKey: Long,
        val priority: Int = 0,
    )

    private suspend fun processChunkLoadRequest(request: ChunkLoadRequest) {
        val chunkX = unpackChunkX(request.chunkKey)
        val chunkZ = unpackChunkZ(request.chunkKey)

        try {
            withContext(Dispatchers.Default) {
                val chunk = world.getChunkAtAsyncUrgently(chunkX, chunkZ).await()
                val snapshot = SmartChunkSnapshot(chunk.chunkSnapshot)

                // Store in appropriate cache
                if (useLocalCache && localCache != null) {
                    localCacheMutex.withLock {
                        localCache[request.chunkKey] = snapshot
                    }
                } else {
                    sharedCacheMutex.withLock {
                        sharedChunkCache[request.chunkKey] = SoftReference(snapshot)
                    }
                }

                memoryStats.addAndGet(estimateSnapshotSize())
            }
        } finally {
            loadingJobs.remove(request.chunkKey)
        }
    }

    private fun estimateSnapshotSize(): Long {
        // Rough estimate: 16x16x384 blocks = ~100KB per chunk
        return 100_000L
    }

    suspend fun cleanupLocalCache() {
        if (localCache != null) {
            localCacheMutex.withLock {
                localCache.clear()
            }
        }
    }

    private suspend fun getChunkSnapshotAsync(
        x: Int,
        z: Int,
    ): SmartChunkSnapshot {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val chunkKey = packChunkKey(chunkX, chunkZ)

        // Check local cache first (if enabled)
        if (useLocalCache && localCache != null) {
            localCacheMutex.withLock {
                localCache[chunkKey]?.let { return it }
            }
        }

        // Check shared cache
        val sharedSnapshot =
            sharedCacheMutex.withLock {
                sharedChunkCache[chunkKey]?.get()
            }
        if (sharedSnapshot != null) {
            // Move to local cache if enabled
            if (useLocalCache && localCache != null) {
                localCacheMutex.withLock {
                    localCache[chunkKey] = sharedSnapshot
                }
            }
            return sharedSnapshot
        }

        // Check if already loading
        loadingJobs[chunkKey]?.let { return it.await() }

        // Start async loading
        val deferred =
            Defcon.scope.async {
                val chunk = world.getChunkAtAsync(chunkX, chunkZ).await()
                val snapshot = SmartChunkSnapshot(chunk.chunkSnapshot)

                // Store in appropriate cache
                if (useLocalCache && localCache != null) {
                    localCacheMutex.withLock {
                        localCache[chunkKey] = snapshot
                    }
                } else {
                    sharedCacheMutex.withLock {
                        sharedChunkCache[chunkKey] = SoftReference(snapshot)
                    }
                }

                memoryStats.addAndGet(estimateSnapshotSize())
                snapshot
            }

        loadingJobs[chunkKey] = deferred
        return deferred.await().also {
            loadingJobs.remove(chunkKey)
        }
    }

    // Bulk preloading with priority and batching
    suspend fun preloadChunksAsync(
        chunkKeys: Set<Long>,
        batchSize: Int = 10,
        priority: Int = 0,
    ) {
        chunkKeys.chunked(batchSize).forEach { batch ->
            val jobs =
                batch.map { key ->
                    Defcon.scope.async {
                        // Skip if already cached
                        if (useLocalCache && localCache != null) {
                            val cached = localCacheMutex.withLock { localCache.containsKey(key) }
                            if (cached) return@async
                        }

                        val sharedExists =
                            sharedCacheMutex.withLock {
                                sharedChunkCache[key]?.get() != null
                            }
                        if (sharedExists) return@async

                        // Queue for loading
                        loadingQueue.trySend(ChunkLoadRequest(key, priority))
                    }
                }
            jobs.awaitAll()

            // Small delay between batches to prevent server overload
            delay(5)
        }
    }

    // Convenience method for coordinate-based preloading
    suspend fun preloadChunksInRadius(
        centerX: Int,
        centerZ: Int,
        radius: Int,
        batchSize: Int = 10,
    ) {
        val chunkKeys = mutableSetOf<Long>()
        val centerChunkX = centerX shr 4
        val centerChunkZ = centerZ shr 4

        for (x in (centerChunkX - radius)..(centerChunkX + radius)) {
            for (z in (centerChunkZ - radius)..(centerChunkZ + radius)) {
                chunkKeys.add(packChunkKey(x, z))
            }
        }

        preloadChunksAsync(chunkKeys, batchSize)
    }

    // Async block access methods
    suspend fun highestBlockYAtAsync(
        x: Int,
        z: Int,
    ): Int = getChunkSnapshotAsync(x, z).getHighestBlockYAt(x and 15, z and 15)

    suspend fun getBlockMaterialAsync(
        x: Int,
        y: Int,
        z: Int,
    ): Material {
        if (y < world.minHeight || y > world.maxHeight) return Material.AIR
        return getChunkSnapshotAsync(x, z).getBlockType(x and 15, y, z and 15)
    }

    suspend fun getBlockDataAsync(
        x: Int,
        y: Int,
        z: Int,
    ): BlockData {
        if (y < world.minHeight || y > world.maxHeight) return Bukkit.createBlockData(Material.AIR)
        return getChunkSnapshotAsync(x, z).getBlockData(x and 15, y, z and 15)
    }

    suspend fun getSkyLightLevelAsync(
        x: Int,
        y: Int,
        z: Int,
    ): Int {
        if (y < world.minHeight || y > world.maxHeight) return 0
        return getChunkSnapshotAsync(x, z).getBlockSkyLight(x and 15, y, z and 15)
    }

    // Update methods that work with SmartChunkSnapshot
    suspend fun updateBlockType(
        x: Int,
        y: Int,
        z: Int,
        type: Material,
    ) {
        if (y < world.minHeight || y > world.maxHeight) return
        val snapshot = getChunkSnapshotAsync(x, z)
        snapshot.updateBlockType(x and 15, y, z and 15, type)
    }

    suspend fun updateBlockData(
        x: Int,
        y: Int,
        z: Int,
        data: BlockData,
    ) {
        if (y < world.minHeight || y > world.maxHeight) return
        val snapshot = getChunkSnapshotAsync(x, z)
        snapshot.updateBlockData(x and 15, y, z and 15, data)
    }

    // Batch update for multiple blocks
    suspend fun updateBlockTypesBatch(
        updates: List<Triple<Int, Int, Int>>,
        type: Material,
    ) {
        val groupedByChunk =
            updates.groupBy { (x, _, z) ->
                packChunkKey(x shr 4, z shr 4)
            }

        groupedByChunk.entries
            .map { (_, coords) ->
                Defcon.scope.async {
                    coords.forEach { (x, y, z) ->
                        if (y in world.minHeight..world.maxHeight) {
                            val snapshot = getChunkSnapshotAsync(x, z)
                            snapshot.updateBlockType(x and 15, y, z and 15, type)
                        }
                    }
                }
            }.awaitAll()
    }

    suspend fun updateBlockDataBatch(updates: Map<Triple<Int, Int, Int>, BlockData>) {
        val groupedByChunk =
            updates.keys.groupBy { (x, _, z) ->
                packChunkKey(x shr 4, z shr 4)
            }

        groupedByChunk.entries
            .map { (_, coords) ->
                Defcon.scope.async {
                    coords.forEach { (x, y, z) ->
                        if (y in world.minHeight..world.maxHeight) {
                            val snapshot = getChunkSnapshotAsync(x, z)
                            updates[Triple(x, y, z)]?.let { data ->
                                snapshot.updateBlockData(x and 15, y, z and 15, data)
                            }
                        }
                    }
                }
            }.awaitAll()
    }

    // Batch processing for multiple blocks (very efficient for area processing)
    suspend fun getBlockMaterialsBatch(coordinates: List<Triple<Int, Int, Int>>): List<Material> {
        val groupedByChunk =
            coordinates.groupBy { (x, _, z) ->
                packChunkKey(x shr 4, z shr 4)
            }

        val results = Array<Material?>(coordinates.size) { null }
        val coordToIndex = coordinates.withIndex().associate { it.value to it.index }

        groupedByChunk.entries
            .map { (_, coords) ->
                Defcon.scope.async {
                    val snapshot = getChunkSnapshotAsync(coords.first().first, coords.first().third)
                    coords.forEach { (x, y, z) ->
                        val index = coordToIndex[Triple(x, y, z)]!!
                        results[index] =
                            if (y < world.minHeight || y > world.maxHeight) {
                                Material.AIR
                            } else {
                                snapshot.getBlockType(x and 15, y, z and 15)
                            }
                    }
                }
            }.awaitAll()

        return results.map { it ?: Material.AIR }
    }

    suspend fun getBlockDataBatch(coordinates: List<Triple<Int, Int, Int>>): List<BlockData> {
        val groupedByChunk =
            coordinates.groupBy { (x, _, z) ->
                packChunkKey(x shr 4, z shr 4)
            }

        val results = Array<BlockData?>(coordinates.size) { null }
        val coordToIndex = coordinates.withIndex().associate { it.value to it.index }
        val airData = Bukkit.createBlockData(Material.AIR)

        groupedByChunk.entries
            .map { (_, coords) ->
                Defcon.scope.async {
                    val snapshot = getChunkSnapshotAsync(coords.first().first, coords.first().third)
                    coords.forEach { (x, y, z) ->
                        val index = coordToIndex[Triple(x, y, z)]!!
                        results[index] =
                            if (y < world.minHeight || y > world.maxHeight) {
                                airData
                            } else {
                                snapshot.getBlockData(x and 15, y, z and 15)
                            }
                    }
                }
            }.awaitAll()

        return results.map { it ?: airData }
    }

    // Cache invalidation for specific chunks
    suspend fun invalidateChunk(
        chunkX: Int,
        chunkZ: Int,
    ) {
        val chunkKey = packChunkKey(chunkX, chunkZ)
        if (localCache != null) {
            localCacheMutex.withLock {
                localCache.remove(chunkKey)
            }
        }
        sharedCacheMutex.withLock {
            sharedChunkCache.remove(chunkKey)
        }
    }

    // Invalidate multiple chunks efficiently
    suspend fun invalidateChunks(chunkKeys: Set<Long>) {
        if (localCache != null) {
            localCacheMutex.withLock {
                chunkKeys.forEach { localCache.remove(it) }
            }
        }
        sharedCacheMutex.withLock {
            chunkKeys.forEach { sharedChunkCache.remove(it) }
        }
    }

    // Force refresh a chunk (useful when blocks have been modified)
    suspend fun refreshChunk(
        chunkX: Int,
        chunkZ: Int,
    ) {
        invalidateChunk(chunkX, chunkZ)
        preloadChunksAsync(setOf(packChunkKey(chunkX, chunkZ)))
    }

    // Refresh multiple chunks
    suspend fun refreshChunks(chunkKeys: Set<Long>) {
        invalidateChunks(chunkKeys)
        preloadChunksAsync(chunkKeys)
    }

    // Get cache statistics
    fun getCacheStats(): CacheStats {
        val localSize = localCache?.size ?: 0
        val sharedSize = sharedChunkCache.size
        val loadingCount = loadingJobs.size
        return CacheStats(
            localCacheSize = localSize,
            sharedCacheSize = sharedSize,
            loadingJobs = loadingCount,
            memoryUsage = memoryStats.get(),
        )
    }

    data class CacheStats(
        val localCacheSize: Int,
        val sharedCacheSize: Int,
        val loadingJobs: Int,
        val memoryUsage: Long,
    )

    fun cleanup() {
        localCache?.clear()
        loadingJobs.clear()
    }
}
