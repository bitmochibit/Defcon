/*
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
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.data.BlockData
import java.lang.invoke.MethodHandle
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.util.concurrent.ConcurrentHashMap

object NMSReflectionCache {
    private val lookup = MethodHandles.lookup()
    private val materialCache = ConcurrentHashMap<Any, Material>()

    // Version detection
    private val serverVersion by lazy {
        Bukkit.getServer()::class.java.`package`.name.split('.').last()
    }
    private val isModern by lazy { !serverVersion.startsWith("v") }

    // Core classes
    private val craftWorld by lazy { findClass("org.bukkit.craftbukkit", "CraftWorld") }
    private val craftBlockData by lazy { findClass("org.bukkit.craftbukkit", "block.data.CraftBlockData") }
    private val craftMagicNumbers by lazy { findClass("org.bukkit.craftbukkit", "util.CraftMagicNumbers") }

    private val serverLevel by lazy {
        findClass(if (isModern) "net.minecraft.server.level" else "net.minecraft.server.$serverVersion",
            if (isModern) "ServerLevel" else "WorldServer")
    }
    private val blockPos by lazy {
        findClass(if (isModern) "net.minecraft.core" else "net.minecraft.server.$serverVersion",
            if (isModern) "BlockPos" else "BlockPosition")
    }
    private val blockState by lazy {
        findClass(if (isModern) "net.minecraft.world.level.block.state" else "net.minecraft.server.$serverVersion",
            if (isModern) "BlockState" else "IBlockData")
    }
    private val block by lazy {
        findClass(if (isModern) "net.minecraft.world.level.block" else "net.minecraft.server.$serverVersion", "Block")
    }
    private val chunk by lazy {
        findClass(if (isModern) "net.minecraft.world.level.chunk" else "net.minecraft.server.$serverVersion",
            if (isModern) "LevelChunk" else "Chunk")
    }

    private val heightmapTypes by lazy {
        findClass(if (isModern) "net.minecraft.world.level.levelgen" else "net.minecraft.server.$serverVersion",
            if (isModern) $$"Heightmap$Types" else $$"HeightMap$Type"
        )
    }

    private val lightEngine by lazy {
        findClass(if (isModern) "net.minecraft.world.level.lighting" else "net.minecraft.server.$serverVersion",
            if (isModern) "LightEngine" else "LightEngine")
    }

    private val chunkProvider by lazy {
        findClass(if (isModern) "net.minecraft.server.level" else "net.minecraft.server.$serverVersion",
            if (isModern) "ServerChunkCache" else "ChunkProviderServer")
    }

    // Method handles
    private val getHandle by lazy {
        findMethod(craftWorld, "getHandle", MethodType.methodType(serverLevel))
    }

    private val getState by lazy {
        findMethod(craftBlockData, "getState", MethodType.methodType(blockState))
    }

    private val setBlock by lazy {
        val methodName = if (isModern) "setBlock" else "setTypeAndData"
        findMethodWithFallbacks(serverLevel, methodName, listOf(
            MethodType.methodType(Boolean::class.javaPrimitiveType, blockPos, blockState, Int::class.javaPrimitiveType),
            MethodType.methodType(Void.TYPE, blockPos, blockState, Int::class.javaPrimitiveType),
            MethodType.methodType(Boolean::class.javaPrimitiveType, blockPos, blockState),
            MethodType.methodType(Void.TYPE, blockPos, blockState)
        ))
    }

    private val getBlockState by lazy {
        val methodName = if (isModern) "getBlockState" else "getType"
        findMethod(serverLevel, methodName, MethodType.methodType(blockState, blockPos))
    }

    private val getBlockFromState by lazy {
        findMethod(blockState, "getBlock", MethodType.methodType(block))
    }

    private val getMaterial by lazy {
        findStaticMethod(craftMagicNumbers, "getMaterial", MethodType.methodType(Material::class.java, block))
    }

    private val getBlockData by lazy {
        findStaticMethod(craftMagicNumbers, "getBlock", MethodType.methodType(block, Material::class.java))
    }

    private val getDefaultBlockState by lazy {
        findMethod(block, if (isModern) "defaultBlockState" else "getBlockData", MethodType.methodType(blockState))
    }

    private val createBlockPos by lazy {
        findConstructor(blockPos, MethodType.methodType(Void.TYPE, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType))
    }

    private val getHeight by lazy {
        val methodName = if (isModern) "getHeight" else "getHighestBlockYAt"
        findMethodWithFallbacks(serverLevel, methodName, listOf(
            MethodType.methodType(Int::class.javaPrimitiveType, heightmapTypes, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType),
            MethodType.methodType(Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        ))
    }

    private val getChunkAt by lazy {
        val methodName = if (isModern) "getChunk" else "getChunkAt"
        findMethodWithFallbacks(serverLevel, methodName, listOf(
            MethodType.methodType(chunk, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType, Boolean::class.javaPrimitiveType),
            MethodType.methodType(chunk, Int::class.javaPrimitiveType, Int::class.javaPrimitiveType)
        ))
    }

    private val getChunkProvider by lazy {
        findMethod(serverLevel, if (isModern) "getChunkSource" else "getChunkProvider", MethodType.methodType(chunkProvider))
    }

    private val setType by lazy {
        findMethodWithFallbacks(chunk, "setType", listOf(
            MethodType.methodType(blockState, blockPos, blockState, Boolean::class.javaPrimitiveType),
            MethodType.methodType(Void.TYPE, blockPos, blockState, Boolean::class.javaPrimitiveType)
        ))
    }

    private val getLightEngine by lazy {
        findMethod(chunkProvider, "getLightEngine", MethodType.methodType(lightEngine))
    }

    private val checkBlock by lazy {
        findMethodWithFallbacks(lightEngine, if (isModern) "checkBlock" else "a", listOf(
            MethodType.methodType(Void.TYPE, blockPos),
            MethodType.methodType(Boolean::class.javaPrimitiveType, blockPos)
        ))
    }

    // Heightmap type constants
    private val motionBlockingType by lazy {
        heightmapTypes.enumConstants.find { it.toString() == "MOTION_BLOCKING" }
            ?: heightmapTypes.enumConstants.first()
    }

    private val worldSurfaceType by lazy {
        heightmapTypes.enumConstants.find { it.toString() == "WORLD_SURFACE" }
            ?: heightmapTypes.enumConstants.first()
    }

    // Helper functions
    private fun findClass(packageName: String, className: String): Class<*> {
        val fullName = if (isModern || packageName.startsWith("net.minecraft")) {
            "$packageName.$className"
        } else {
            "$packageName.$serverVersion.$className"
        }

        return try {
            Class.forName(fullName)
        } catch (e: ClassNotFoundException) {
            // Try without version for modern servers
            if (!isModern && !packageName.startsWith("net.minecraft")) {
                Class.forName("$packageName.$className")
            } else {
                throw e
            }
        }
    }

    private fun findMethod(clazz: Class<*>, name: String, type: MethodType): MethodHandle {
        return lookup.findVirtual(clazz, name, type)
    }

    private fun findStaticMethod(clazz: Class<*>, name: String, type: MethodType): MethodHandle {
        return lookup.findStatic(clazz, name, type)
    }

    private fun findConstructor(clazz: Class<*>, type: MethodType): MethodHandle {
        return lookup.findConstructor(clazz, type)
    }

    private fun findMethodWithFallbacks(clazz: Class<*>, name: String, types: List<MethodType>): MethodHandle {
        for (type in types) {
            try {
                return lookup.findVirtual(clazz, name, type)
            } catch (e: NoSuchMethodException) {
                continue
            }
        }
        throw NoSuchMethodException("Could not find method $name in $clazz with any of the provided signatures")
    }

    // Public API

    /**
     * Set a block using NMS for better performance
     */
    fun setBlockFast(world: World, x: Int, y: Int, z: Int, blockData: BlockData): Boolean {
        return try {
            val level = getHandle.invoke(world)
            val pos = createBlockPos.invoke(x, y, z)
            val state = getState.invoke(blockData)

            val result = setBlock.invoke(level, pos, state, 2)
            result as? Boolean ?: true
        } catch (e: Throwable) {
            // Fallback to Bukkit API
            world.getBlockAt(x, y, z).blockData = blockData
            true
        }
    }

    /**
     * Batch block modification for distant chunks with light updates
     * Optimized for large-scale modifications without packet sending
     */
    fun setBatchBlocks(world: World, blocks: Map<Triple<Int, Int, Int>, Material>, updateLights: Boolean = true): Boolean {
        return try {
            val level = getHandle.invoke(world)
            val provider = getChunkProvider.invoke(level)
            val engine = if (updateLights) getLightEngine.invoke(provider) else null

            val modifiedChunks = mutableSetOf<Any>()
            val lightUpdatePositions = mutableListOf<Any>()

            // Apply all block changes
            for ((position, material) in blocks) {
                val (x, y, z) = position
                try {
                    val pos = createBlockPos.invoke(x, y, z)
                    val chunkX = x shr 4
                    val chunkZ = z shr 4

                    // Get or load chunk
                    val nmsChunk = try {
                        getChunkAt.invoke(level, chunkX, chunkZ, true)
                    } catch (e: Throwable) {
                        getChunkAt.invoke(level, chunkX, chunkZ)
                    }

                    modifiedChunks.add(nmsChunk)

                    // Convert Material to BlockState
                    val block = getBlockData.invoke(null, material)
                    val state = getDefaultBlockState.invoke(block)

                    // Set block in chunk (no physics)
                    setType.invoke(nmsChunk, pos, state, false)

                    if (updateLights && engine != null) {
                        lightUpdatePositions.add(pos)
                    }
                } catch (e: Throwable) {
                    // Fallback for individual blocks
                    world.getBlockAt(x, y, z).type = material
                }
            }

            // Update lighting for all modified positions
            if (updateLights && engine != null) {
                for (pos in lightUpdatePositions) {
                    try {
                        checkBlock.invoke(engine, pos)
                    } catch (e: Throwable) {
                        // Light update failed, continue
                    }
                }
            }

            true
        } catch (e: Throwable) {
            // Complete fallback to Bukkit API
            for ((position, material) in blocks) {
                val (x, y, z) = position
                world.getBlockAt(x, y, z).type = material
            }
            false
        }
    }

    /**
     * Batch block modification using BlockData for more control
     */
    fun setBatchBlockData(world: World, blocks: Map<Triple<Int, Int, Int>, BlockData>, updateLights: Boolean = true): Boolean {
        return try {
            val level = getHandle.invoke(world)
            val provider = getChunkProvider.invoke(level)
            val engine = if (updateLights) getLightEngine.invoke(provider) else null

            val modifiedChunks = mutableSetOf<Any>()
            val lightUpdatePositions = mutableListOf<Any>()

            // Apply all block changes
            for ((position, blockData) in blocks) {
                val (x, y, z) = position
                try {
                    val pos = createBlockPos.invoke(x, y, z)
                    val chunkX = x shr 4
                    val chunkZ = z shr 4

                    // Get or load chunk
                    val nmsChunk = try {
                        getChunkAt.invoke(level, chunkX, chunkZ, true)
                    } catch (e: Throwable) {
                        getChunkAt.invoke(level, chunkX, chunkZ)
                    }

                    modifiedChunks.add(nmsChunk)

                    // Convert BlockData to BlockState
                    val state = getState.invoke(blockData)

                    // Set block in chunk (no physics)
                    setType.invoke(nmsChunk, pos, state, false)

                    if (updateLights && engine != null) {
                        lightUpdatePositions.add(pos)
                    }
                } catch (e: Throwable) {
                    // Fallback for individual blocks
                    world.getBlockAt(x, y, z).blockData = blockData
                }
            }

            // Update lighting for all modified positions
            if (updateLights && engine != null) {
                for (pos in lightUpdatePositions) {
                    try {
                        checkBlock.invoke(engine, pos)
                    } catch (e: Throwable) {
                        // Light update failed, continue
                    }
                }
            }

            true
        } catch (e: Throwable) {
            // Complete fallback to Bukkit API
            for ((position, blockData) in blocks) {
                val (x, y, z) = position
                world.getBlockAt(x, y, z).blockData = blockData
            }
            false
        }
    }

    /**
     * Get block material using NMS for better performance
     */
    fun getBlockMaterial(world: World, x: Int, y: Int, z: Int): Material {
        return try {
            val level = getHandle.invoke(world)
            val pos = createBlockPos.invoke(x, y, z)
            val state = getBlockState.invoke(level, pos)
            val blockObj = getBlockFromState.invoke(state)

            // Use cache to avoid repeated calls
            materialCache.computeIfAbsent(blockObj) {
                getMaterial.invoke(null, it) as Material
            }
        } catch (e: Throwable) {
            // Fallback to Bukkit API
            world.getBlockAt(x, y, z).type
        }
    }

    /**
     * Get multiple block materials efficiently
     */
    fun getBulkBlockMaterials(world: World, positions: List<Triple<Int, Int, Int>>): Map<Triple<Int, Int, Int>, Material> {
        val results = mutableMapOf<Triple<Int, Int, Int>, Material>()

        try {
            val level = getHandle.invoke(world)

            for ((x, y, z) in positions) {
                try {
                    val pos = createBlockPos.invoke(x, y, z)
                    val state = getBlockState.invoke(level, pos)
                    val blockObj = getBlockFromState.invoke(state)

                    val material = materialCache.computeIfAbsent(blockObj) {
                        getMaterial.invoke(null, it) as Material
                    }

                    results[Triple(x, y, z)] = material
                } catch (e: Throwable) {
                    // Use Bukkit fallback for problematic positions
                    results[Triple(x, y, z)] = world.getBlockAt(x, y, z).type
                }
            }
        } catch (e: Throwable) {
            // Complete fallback to Bukkit API
            for ((x, y, z) in positions) {
                results[Triple(x, y, z)] = world.getBlockAt(x, y, z).type
            }
        }

        return results
    }

    /**
     * Get the highest block Y coordinate at given X,Z position
     */
    fun getHighestBlockY(world: World, x: Int, z: Int, includeFluid: Boolean = false): Int {
        return try {
            val level = getHandle.invoke(world)
            val heightmapType = if (includeFluid) worldSurfaceType else motionBlockingType

            val result = getHeight.invoke(level, heightmapType, x, z)
            result as Int
        } catch (e: Throwable) {
            // Fallback to Bukkit API
            world.getHighestBlockYAt(x, z)
        }
    }

    /**
     * Clear the material cache to prevent memory leaks
     */
    fun clearCache() {
        materialCache.clear()
    }

    /**
     * Get cache size for monitoring
     */
    fun getCacheSize(): Int = materialCache.size

    /**
     * Initialize and validate the cache
     */
    fun initialize(): Boolean {
        return try {
            // Test all critical components
            craftWorld
            serverLevel
            blockPos
            blockState
            block
            chunk
            craftBlockData
            craftMagicNumbers
            heightmapTypes
            lightEngine
            chunkProvider

            getHandle
            getState
            setBlock
            getBlockState
            getBlockFromState
            getMaterial
            getBlockData
            getDefaultBlockState
            createBlockPos
            getHeight
            getChunkAt
            getChunkProvider
            setType
            getLightEngine
            checkBlock

            // Test heightmap types
            motionBlockingType
            worldSurfaceType

            println("NMS Reflection Cache initialized successfully for version: $serverVersion")
            true
        } catch (e: Exception) {
            println("Failed to initialize NMS Reflection Cache: ${e.message}")
            false
        }
    }

    /**
     * Check if NMS methods are available
     */
    fun isAvailable(): Boolean {
        return try {
            initialize()
        } catch (e: Exception) {
            false
        }
    }
}