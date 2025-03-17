package me.mochibit.defcon.explosions

import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.MathFunctions
import me.mochibit.defcon.utils.MathFunctions.remap
import me.mochibit.defcon.utils.RayCaster
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3i
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

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
    private val treeBurner = TreeBurner(world, center.toVector3i())
    private val chunkCache = ChunkCache.getInstance(world)
    private val rayCaster = RayCaster(world)
    private val blockChanger = BlockChanger(world)

    // Cache common calculations
    private val baseDirections = arrayOf(
        Vector3i(1, 0, 0),  // East
        Vector3i(-1, 0, 0), // West
        Vector3i(0, 0, 1),  // South
        Vector3i(0, 0, -1),  // North
    )
    private val completeDirections = arrayOf(
        Vector3i(1, 0, 0),  // East
        Vector3i(-1, 0, 0), // West
        Vector3i(0, 0, 1),  // South
        Vector3i(0, 0, -1),  // North
        Vector3i(0, 1, 0)   // Up
    )

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
                    chunkCache.cleanupCache()
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

        val inv = (1 / explosionPower) * 3
        try {
            CameraShake(entity, CameraShakeOptions(2.6f, 0.04f, 3.7f * inv, 3.0f * inv))
        } catch (e: Exception) {
            println("Error applying CameraShake: ${e.message}")
        }
    }

    private fun processDestruction(locations: List<Vector3i>, explosionPower: Double) {
        // Pre-allocate collections with estimated size
        val treeBlocks = ArrayList<Vector3i>(locations.size / 10)
        val nonTreeBlocks = ArrayList<Vector3i>(locations.size)

        locations.chunked(500).forEach { batch ->
            batch.forEach { location ->
                if (treeBurner.isTreeBlock(location)) {
                    treeBlocks.add(location)
                } else {
                    nonTreeBlocks.add(location)
                }
            }
        }

        // Process trees first as they might be more complex
        treeBlocks.chunked(100).forEach { chunk ->
            chunk.forEach { treeBlock ->
                treeBurner.processTreeBurn(treeBlock, explosionPower)
            }
        }

        // Process normal blocks in chunks to avoid overwhelming the server
        nonTreeBlocks.chunked(500).forEach { chunk ->
            chunk.parallelStream().forEach { location ->
                processBlock(location, explosionPower)
            }
        }

        treeBlocks.clear()
        nonTreeBlocks.clear()
    }

    private fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
    ) {
        // Skip if power is too low
        if (normalizedExplosionPower < 0.05) return
        val basePenetration = (normalizedExplosionPower * 8).roundToInt().coerceIn(1, 8)

        // Create a deterministic but varied penetration value based on position
        // This ensures patterns rather than pure randomness
        val positionSeed = blockLocation.x * 73 + blockLocation.z * 31
        val random = Random(positionSeed)

        // Randomize penetration with constraints based on explosion power
        // Higher power = higher variance allowed
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val randomOffset = (random.nextDouble() * 2 - 1) * basePenetration * varianceFactor

        // Apply smoothing based on distance from explosion center
        // This creates more natural-looking crater edges
        val distanceNormalized = 1.0 - (normalizedExplosionPower / 1.0).coerceIn(0.0, 1.0)
        val smoothingFactor = distanceNormalized * 0.5

        // Calculate final max penetration value
        val maxPenetration = (basePenetration + randomOffset * (1 - smoothingFactor)).roundToInt()
            .coerceIn(max(1, (basePenetration * 0.7).toInt()), (basePenetration * 1.3).toInt())

        val powerDecay = 0.95

        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0


        var isWall = false

        do {
            // Get block type efficiently
            val blockType = chunkCache.getBlockMaterial(blockLocation.x, currentY, blockLocation.z)

            // Skip if block is in blacklist or is liquid
            if (blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST || blockType in TransformationRule.LIQUID_MATERIALS) {
                break
            }

            // Handle air blocks by looking for the next solid block below
            if (blockType == Material.AIR) {
                val nextSolidY = rayCaster.cachedRayTrace(blockLocation.x, currentY, blockLocation.z, 30.0)
                if (nextSolidY < currentY - 30) break // Too far down, skip

                // Jump to the next solid block
                currentY = nextSolidY
                currentPower *= 0.9 // Reduce power slightly for the jump
                continue
            }

            val powerVariance = (random.nextDouble() * 0.2 - 0.1) * currentPower
            val adjustedPower = (currentPower + powerVariance).coerceIn(0.0, 1.0)

            // Determine final block state
            val finalMaterial = if (adjustedPower > 0.3 && penetrationCount < maxPenetration - 1) {
                Material.AIR // Destroy if power is high enough
            } else {
                // Transform if it's the last block or power is low
                transformationRule.transformMaterial(blockType, adjustedPower)
            }

            val updateBlock = if (finalMaterial == Material.AIR) {
                checkUpdatableBlocks(blockLocation.x, currentY, blockLocation.z)
            } else {
                false
            }

            isWall = false
            for (dir in baseDirections) {
                if (chunkCache.getBlockMaterial(
                        blockLocation.x + dir.x,
                        currentY,
                        blockLocation.z + dir.z
                    ) == Material.AIR
                ) {
                    isWall = true
                    break
                }
            }

            // Add to our batch instead of queuing immediately
            blockChanger.addBlockChange(
                blockLocation.x,
                currentY,
                blockLocation.z,
                finalMaterial,
                updateBlock = updateBlock
            )

            // Stop if we're not processing penetration
            // Move down and update state
            currentY--
            currentPower *= if (isWall) 0.98 else powerDecay
            penetrationCount++

        } while (
            (penetrationCount < maxPenetration &&
            currentPower > 0.1 &&
            currentY > 0)
            ||
            isWall
        )
    }

    private val updatableBlocks: Set<Material> = EnumSet.noneOf(Material::class.java).apply {
        addAll(TransformationRule.PLANTS)
        addAll(TransformationRule.DEAD_PLANTS)

        add(Material.TORCH)
        add(Material.REDSTONE_TORCH)
        add(Material.REDSTONE_WALL_TORCH)
        add(Material.WALL_TORCH)
        add(Material.REDSTONE_WIRE)
        add(Material.REDSTONE)
        add(Material.RAIL)
        add(Material.ACTIVATOR_RAIL)
        add(Material.DETECTOR_RAIL)
        add(Material.POWERED_RAIL)
        add(Material.LEVER)
        add(Material.STONE_BUTTON)


        add(Material.CYAN_CARPET)
        add(Material.BLUE_CARPET)
        add(Material.BROWN_CARPET)
        add(Material.BLACK_CARPET)
        add(Material.GRAY_CARPET)
        add(Material.GREEN_CARPET)
        add(Material.LIGHT_BLUE_CARPET)
        add(Material.LIGHT_GRAY_CARPET)
        add(Material.LIME_CARPET)
        add(Material.MAGENTA_CARPET)
        add(Material.ORANGE_CARPET)
        add(Material.PINK_CARPET)
        add(Material.PURPLE_CARPET)
        add(Material.RED_CARPET)
        add(Material.WHITE_CARPET)
        add(Material.YELLOW_CARPET)


    }

    private fun checkUpdatableBlocks(
        x: Int,
        y: Int,
        z: Int
    ): Boolean {
        // Check around all the block faces for updatable blocks
        for (dir in completeDirections) {
            val blockType = chunkCache.getBlockMaterial(x + dir.x, y + dir.y, z + dir.z)
            if (blockType in updatableBlocks) {
                return true
            }
        }
        return false
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

