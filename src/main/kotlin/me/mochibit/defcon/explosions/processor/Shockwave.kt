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

package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.BlockChanger
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*
import kotlin.random.Random

class Shockwave(
    private val center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val shockwaveSpeed: Long = 300L,
    private val transformationRule: TransformationRule = TransformationRule(),
    val radiusDestroyStart: Int = 0
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
    private val blockChanger = BlockChanger.getInstance(world)

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
                var lastProcessedRadius = radiusStart
                val visitedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

                while (lastProcessedRadius <= shockwaveRadius) {
                    // Calculate elapsed time in seconds
                    val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0

                    // Determine the current radius based on shockwaveSpeed (blocks per second)
                    val currentRadius = (radiusStart + elapsedSeconds * shockwaveSpeed).toInt()

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
                            if (radius >= radiusDestroyStart)
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
                world.spawnParticle(Particle.EXPLOSION, loc, 0)
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
                    nonTreeBlocks.add(treeBurner.getTreeTerrain(location))
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

        // Create deterministic but varied penetration value based on position
        val positionSeed = blockLocation.x * 73 + blockLocation.z * 31
        val random = Random(positionSeed)

        // Randomize penetration with constraints based on explosion power
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val distanceNormalized = 1.0 - normalizedExplosionPower.coerceIn(0.0, 1.0)
        val randomOffset =
            (random.nextDouble() * 2 - 1) * basePenetration * varianceFactor * (1 - distanceNormalized * 0.5)

        // Calculate final max penetration value
        val maxPenetration = (basePenetration + randomOffset).roundToInt()
            .coerceIn(max(1, (basePenetration * 0.7).toInt()), (basePenetration * 1.3).toInt())

        // Check if this is a wall block by examining surrounding blocks
        var isWall = detectWall(blockLocation.x, blockLocation.y, blockLocation.z)
        if (!isWall) { // Second chance for the block below
            isWall = detectWall(blockLocation.x, blockLocation.y - 1, blockLocation.z)
        }

        // Different handling for walls vs. roofs
        if (isWall) {
            // Walls should be destroyed almost entirely - handle vertically
            processWall(blockLocation, normalizedExplosionPower, random)
        } else {
            // Roof/floor should be penetrated downward
            processRoof(blockLocation, normalizedExplosionPower, maxPenetration, random)
        }
    }

    // Detect if a block is a wall by checking if it has air on at least one side
    private fun detectWall(x: Int, y: Int, z: Int): Boolean {
        return baseDirections.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y, z + dir.z) == Material.AIR
        }
    }

    private fun detectAttached(x: Int, y: Int, z: Int): Boolean {
        return baseDirections.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y + dir.y, z + dir.z) in TransformationRule.ATTACHED_BLOCKS
        }
    }

    // Process a wall - destroy it almost entirely vertically
    private fun processWall(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
        random: Random
    ) {
        val x = blockLocation.x
        val z = blockLocation.z
        val startY = blockLocation.y
        val maxDepth = shockwaveHeight

        for (depth in 0 until maxDepth) {
            val currentY = startY - depth

            if (depth > 0 && !detectWall(x, currentY, z)) break

            val blockType = chunkCache.getBlockMaterial(x, currentY, z)
            if (blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST || blockType == Material.AIR) continue
            if (blockType in TransformationRule.LIQUID_MATERIALS) break


            val finalMaterial = if (depth > 0 && random.nextDouble() < normalizedExplosionPower) {
                transformationRule.transformMaterial(blockType, normalizedExplosionPower)
            } else {
                Material.AIR
            }

            val shouldCopyData = finalMaterial in TransformationRule.SLABS ||
                    finalMaterial in TransformationRule.WALLS ||
                    finalMaterial in TransformationRule.STAIRS

            blockChanger.addBlockChange(
                x, currentY, z,
                finalMaterial,
                shouldCopyData,
                (finalMaterial == Material.AIR && depth == 0) || detectAttached(x, currentY, z)
            )
        }
    }

    // Process a roof/floor - penetrate downward until power is too low
    private fun processRoof(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
        maxPenetration: Int,
        random: Random
    ) {
        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0
        val powerDecay = 0.85  // Faster decay for roofs to create pockmarks rather than clean holes

        val x = blockLocation.x
        val z = blockLocation.z

        while (penetrationCount < maxPenetration && currentPower > 0.1 && currentY > 0) {
            // Get block type efficiently
            val blockType = chunkCache.getBlockMaterial(x, currentY, z)

            // Skip if block is in blacklist or is liquid
            if (blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST || blockType in TransformationRule.LIQUID_MATERIALS)
                break

            // Handle air blocks by looking for the next solid block below
            if (blockType == Material.AIR) {
                val nextSolidY = rayCaster.cachedRayTrace(x, currentY, z, 30.0)
                if (nextSolidY < currentY - 30) break // Too far down, skip

                // Jump to the next solid block
                currentY = nextSolidY
                currentPower *= 0.8 // Reduce power more significantly for jumps
                continue
            }

            // Add randomness to power
            val powerVariance = (random.nextDouble() * 0.2 - 0.1) * currentPower
            val adjustedPower = (currentPower + powerVariance).coerceIn(0.0, 1.0)

            val penetrationRatio = penetrationCount.toDouble() / maxPenetration
            val finalMaterial = if (penetrationRatio < 0.7 && adjustedPower > 0.3) {
                Material.AIR // Destroy completely
            } else {
                // Transform for deeper layers or when power is lower
                transformationRule.transformMaterial(blockType, adjustedPower)
            }

            blockChanger.addBlockChange(
                blockLocation.x,
                currentY,
                blockLocation.z,
                finalMaterial,
                updateBlock = penetrationCount == 0
            )

            // Move down and update state
            currentY--
            currentPower *= powerDecay + (random.nextDouble() * 0.05) // Slightly randomize decay
            penetrationCount++
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
//            val highestY = rayCaster.cachedRayTrace(x, center.blockY, z, shockwaveHeight.toDouble())
            result.add(Vector3i(x, highestY, z))
        }

        return result
    }
}

