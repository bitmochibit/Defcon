package me.mochibit.defcon.explosions

import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.MathFunctions
import me.mochibit.defcon.utils.MathFunctions.remap
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3i
import java.lang.ref.SoftReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val shockwaveSpeed: Long = 300L,
    private val transformationRule: TransformationRule = TransformationRule()
) {
    private val maximumDistanceForAction = 4.0
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0

    private val world = center.world

    // Use a dedicated thread pool with a fixed size to avoid overloading the system
    private val executorService = ForkJoinPool(
        Runtime.getRuntime().availableProcessors(),
        ForkJoinPool.defaultForkJoinWorkerThreadFactory,
        null, true
    )

    private val completedExplosion = AtomicBoolean(false)
    private val destructionQueue = ConcurrentLinkedQueue<Pair<List<Vector3i>, Double>>()
    private val treeBurner = TreeBurner(world, center.toVector3i(), 200, transformationRule)
    private val chunkCache = ChunkCache.getInstance(world)

    // Cache common calculations
    private val directionsToCheck = arrayOf(
        Vector3i(1, 0, 0),  // East
        Vector3i(-1, 0, 0), // West
        Vector3i(0, 0, 1),  // South
        Vector3i(0, 0, -1)  // North
    )

    // Reusable position objects to reduce object creation
    private val positionCache = ThreadLocal.withInitial { Vector3i() }
    private val blockChangeCache = ThreadLocal.withInitial { ArrayList<Triple<Vector3i, Material, BlockData>>(16) }
    private val processedPositionsCache = ThreadLocal.withInitial { HashSet<Vector3i>(32) }

    fun explode() {
        val startTime = System.nanoTime()

        // First task: Calculate shockwave progression and queue destruction
        executorService.submit {
            try {
                var lastProcessedRadius = shockwaveRadiusStart
                val visitedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

                while (lastProcessedRadius <= shockwaveRadius) {
                    // Calculate elapsed time in seconds
                    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0

                    // Determine the current radius based on shockwaveSpeed (blocks per second)
                    val currentRadius = (shockwaveRadiusStart + elapsedSeconds * shockwaveSpeed).toInt()

                    // Skip if the radius hasn't advanced yet
                    if (currentRadius <= lastProcessedRadius) {
                        Thread.sleep(5) // Small sleep to reduce CPU usage
                        continue
                    }

                    // Process new radii
                    for (radius in lastProcessedRadius + 1..currentRadius.coerceAtMost(shockwaveRadius)) {
                        val explosionPower = MathFunctions.lerp(
                            maxDestructionPower,
                            minDestructionPower,
                            radius / shockwaveRadius.toDouble()
                        )
                        val normalizedExplosionPower =
                            remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)

                        val columns = generateShockwaveColumns(radius)
                        if (columns.isNotEmpty()) {
                            destructionQueue.add(columns to normalizedExplosionPower)
                            processEntityDamage(columns, normalizedExplosionPower, visitedEntities)
                        }
                    }

                    // Clear entity cache periodically to prevent memory bloat
                    if (visitedEntities.size > 100) {
                        visitedEntities.clear()
                    }

                    lastProcessedRadius = currentRadius
                }
            } finally {
                completedExplosion.set(true)
            }
        }

        // Second task: Process the destruction queue
        executorService.submit {
            try {
                while (!completedExplosion.get() || destructionQueue.isNotEmpty()) {
                    val explosionData = destructionQueue.poll()

                    if (explosionData == null) {
                        // If queue is empty but explosion isn't complete, wait a bit
                        if (!completedExplosion.get()) {
                            Thread.sleep(10)
                        }
                    } else {
                        processDestruction(explosionData.first, explosionData.second)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                if (completedExplosion.get() && destructionQueue.isEmpty()) {
                    executorService.shutdown()
                }
            }
        }
    }

    private fun processEntityDamage(
        columns: List<Vector3i>,
        explosionPower: Double,
        visitedEntities: MutableSet<Entity>
    ) {
        if (columns.isEmpty()) return

        // Batch particle spawning for better performance
        val particleLocations = columns.map {
            Location(world, it.x.toDouble(), (it.y + 1).toDouble(), it.z.toDouble())
        }

        runLater(1L) {
            // Spawn particles in batches
            particleLocations.forEach { loc ->
                world.spawnParticle(Particle.EXPLOSION_LARGE, loc, 0)
            }

            val locationCursor = Location(world, 0.0, 0.0, 0.0)
            val halfShockwaveHeight = (shockwaveHeight / 2).toDouble()

            // Process entity damage in batches to reduce server load
            columns.chunked(10).forEach { columnChunk ->
                columnChunk.forEach { column ->
                    locationCursor.set(
                        column.x.toDouble(),
                        column.y.toDouble(),
                        column.z.toDouble()
                    )

                    val entities = world.getNearbyEntities(
                        locationCursor, 1.0,
                        halfShockwaveHeight, 1.0
                    ) {
                        it !in visitedEntities &&
                                it.y in locationCursor.y - maximumDistanceForAction..(locationCursor.y + shockwaveHeight + maximumDistanceForAction)
                    }

                    entities.forEach { entity ->
                        applyExplosionEffects(entity, explosionPower.toFloat())
                        visitedEntities.add(entity)
                    }
                }
            }
        }
    }

    private fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        // Calculate knockback more efficiently
        val dx = entity.x - center.x
        val dy = entity.y - center.y
        val dz = entity.z - center.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        // Avoid division by zero
        if (distance < 0.1) return

        val multiplier = explosionPower * 30.0 / distance
        val knockback = Vector(dx * multiplier, dy * multiplier, dz * multiplier)
        entity.velocity = knockback

        if (entity !is LivingEntity) return

        entity.damage(explosionPower * 15.0)
        if (entity !is Player) return

        val inv = 2 / explosionPower
        try {
            CameraShake(entity, CameraShakeOptions(1.6f, 0.04f, 3.7f * inv, 3.0f * inv))
        } catch (e: Exception) {
            println("Error applying CameraShake: ${e.message}")
        }
    }

    private fun processDestruction(locations: List<Vector3i>, explosionPower: Double) {
        // Pre-allocate collections with estimated size
        val treeBlocks = ArrayList<Vector3i>(locations.size / 10)
        val nonTreeBlocks = ArrayList<Vector3i>(locations.size)

        // Process in smaller batches to avoid overwhelming the server
        locations.chunked(500).forEach { batch ->
            batch.forEach { location ->
                val blockType = chunkCache.getBlockMaterial(location.x, location.y, location.z)
                if (blockType.name.endsWith("_LOG") || blockType.name.endsWith("_LEAVES")) {
                    treeBlocks.add(location)
                } else {
                    nonTreeBlocks.add(location)
                }
            }
        }

        // Process trees first as they might be more complex
        treeBlocks.forEach { treeBlock ->
            val block = world.getBlockAt(treeBlock.x, treeBlock.y, treeBlock.z)
            treeBurner.processTreeBurn(block, explosionPower)
        }

        // Process normal blocks in chunks to avoid overwhelming the server
        nonTreeBlocks.chunked(500).forEach { chunk ->
            chunk.parallelStream().forEach { location ->
                simulateExplosion(location, explosionPower)
            }
        }
    }

    private fun rayTrace(location: Vector3i, maxDistance: Double = 200.0): Int {
        val loc = Location(world, location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
        return world.rayTraceBlocks(
            loc,
            Vector(0.0, -1.0, 0.0),
            maxDistance,
            FluidCollisionMode.ALWAYS
        )?.hitPosition?.blockY
            ?: (location.y - maxDistance).toInt()
    }

    // Improved block penetration system
    private fun simulateExplosion(location: Vector3i, normalizedExplosionPower: Double) {
        // Process the main block first
        processBlock(location, normalizedExplosionPower)

        val baseY = location.y

        for (dir in directionsToCheck) {
            val tempPos = positionCache.get()
            tempPos.set(location.x + dir.x, location.y, location.z + dir.z)
            val wallTopY = rayTrace(tempPos)

            // Only process columns with significant height differences
            val heightDiff = baseY - wallTopY
            if (heightDiff > 2) {
                // Calculate how many blocks to process based on explosion power
                val blocksToProcess = heightDiff.coerceIn(1, heightDiff)

                // Process from top to bottom with decreasing power
                for (i in 0 until blocksToProcess) {
                    val y = baseY - i
                    tempPos.set(location.x, y, location.z)

                    processBlock(
                        tempPos,
                        normalizedExplosionPower,
                        processPenetration = false
                    )
                }
            }
        }
    }

    // Completely rewritten and optimized block penetration system
    private fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
        processPenetration: Boolean = true
    ) {
        // Skip if power is too low
        if (normalizedExplosionPower < 0.05) return

        // Calculate penetration parameters based on explosion power
        val maxPenetration = (normalizedExplosionPower * 8).roundToInt().coerceIn(1, 12)
        val powerDecay = 0.95

        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0

        // Get thread-local data structures
        val processedPositions = processedPositionsCache.get()
        val blockChangeList = blockChangeCache.get()
        val currentPos = positionCache.get()

        // Clear reused collections
        processedPositions.clear()
        blockChangeList.clear()

        // Initial capacity for collections
        blockChangeList.ensureCapacity(maxPenetration)

        do {
            currentPos.set(blockLocation.x, currentY, blockLocation.z)

            // Skip if we've already processed this position
            // Create a new Vector3i for the key to avoid mutating the currentPos
            val posKey = Vector3i(currentPos.x, currentY, currentPos.z)
            if (!processedPositions.add(posKey)) break

            // Get block type efficiently
            val blockType = chunkCache.getBlockMaterial(currentPos.x, currentY, currentPos.z)

            // Skip if block is in blacklist or is liquid
            if (blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST ||
                blockType in TransformationRule.LIQUID_MATERIALS
            ) {
                break
            }

            // Handle air blocks by looking for the next solid block below
            if (blockType == Material.AIR) {
                val nextSolidY = rayTrace(currentPos, 30.0)
                if (nextSolidY < currentY - 30) break // Too far down, skip

                // Jump to the next solid block
                currentY = nextSolidY
                currentPower *= 0.9 // Reduce power slightly for the jump
                continue
            }

            // Get the block data once, cache for reuse
            val blockData = chunkCache.getBlockData(currentPos.x, currentY, currentPos.z)

            // Determine final block state
            val finalMaterial = if (currentPower > 0.3 && penetrationCount < maxPenetration - 1) {
                Material.AIR // Destroy if power is high enough
            } else {
                // Transform if it's the last block or power is low
                transformationRule.transformMaterial(blockType, currentPower)
            }

            // Add to our batch instead of queuing immediately
            blockChangeList.add(Triple(
                Vector3i(currentPos.x, currentY, currentPos.z),
                finalMaterial,
                blockData
            ))

            // Stop if we're not processing penetration
            if (!processPenetration) break

            // Move down and update state
            currentY--
            currentPower *= powerDecay
            penetrationCount++

        } while (processPenetration &&
            penetrationCount < maxPenetration &&
            currentPower > 0.1 &&
            currentY > 0
        )

        // Batch-process all block changes
        blockChangeList.forEach { (pos, material, data) ->
            val block = world.getBlockAt(pos.x, pos.y, pos.z)
            BlockChanger.addBlockChange(
                block,
                material,
                (material != Material.AIR),
                blockData = data
            )
        }
    }

    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        // More efficient circle generation with adaptive density
        val density = max(2, min(4, (radius / 20) + 2)) // Adaptive density based on radius
        val steps = (2 * Math.PI * radius).toInt() * density
        val angleIncrement = 2 * Math.PI / steps

        // Pre-allocate capacity
        val result = ArrayList<Vector3i>(steps)

        for (i in 0 until steps) {
            val angle = angleIncrement * i
            val x = round(center.blockX + radius * cos(angle)).toInt()
            val z = round(center.blockZ + radius * sin(angle)).toInt()
            val highestY = chunkCache.highestBlockYAt(x, z)
            result.add(Vector3i(x, highestY, z))
        }

        return result
    }
}

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