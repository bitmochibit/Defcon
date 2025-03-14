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
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap

// ChunkCache implementation remains mostly the same
// It's already well-optimized with the soft reference caching system
class ChunkCache private constructor(
    private val world: World,
    private val maxAccessCount: Int = 20
) {
    companion object {
        private val sharedChunkCache =
            object : LinkedHashMap<Pair<Int, Int>, SoftReference<ChunkSnapshot>>(16, 0.75f, true) {
                private val MAX_SHARED_CACHE_SIZE = 100

                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, SoftReference<ChunkSnapshot>>): Boolean {
                    return size > MAX_SHARED_CACHE_SIZE
                }
            }

        private val instanceCache = ConcurrentHashMap<World, ChunkCache>()

        fun getInstance(world: World, maxAccessCount: Int = 20): ChunkCache {
            return instanceCache.computeIfAbsent(world) { ChunkCache(world, maxAccessCount) }
        }
    }

    private val localCache = object : LinkedHashMap<Pair<Int, Int>, ChunkSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ChunkSnapshot>): Boolean {
            if (size > maxAccessCount) {
                sharedChunkCache[eldest.key] = SoftReference(eldest.value)
                return true
            }
            return false
        }
    }

    private fun getChunkSnapshot(x: Int, z: Int): ChunkSnapshot {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val key = chunkX to chunkZ

        return localCache.getOrPut(key) {
            val sharedSnapshot = sharedChunkCache[key]?.get()
            if (sharedSnapshot != null) {
                return@getOrPut sharedSnapshot
            }
            val snapshot = world.getChunkAt(chunkX, chunkZ).chunkSnapshot
            sharedChunkCache[key] = SoftReference(snapshot)
            snapshot
        }
    }

    fun highestBlockYAt(x: Int, z: Int): Int {
        return getChunkSnapshot(x, z).getHighestBlockYAt(x and 15, z and 15)
    }

    fun getBlockMaterial(x: Int, y: Int, z: Int): Material {
        if (y < 0 || y > 255) return Material.AIR
        return getChunkSnapshot(x, z).getBlockType(x and 15, y, z and 15)
    }

    fun getBlockData(x: Int, y: Int, z: Int): BlockData {
        if (y < 0 || y > 255) return Bukkit.createBlockData(Material.AIR)
        return getChunkSnapshot(x, z).getBlockData(x and 15, y, z and 15)
    }
}