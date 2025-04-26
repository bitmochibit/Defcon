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

import com.github.retrooper.packetevents.protocol.packettype.PacketType.Play
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.*
import me.mochibit.defcon.utils.MathFunctions.remap
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
) : Completable by CompletionDispatcher() {
    private val maximumDistanceForAction = 4.0
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0

    private val world = center.world


    private val circleCache = mutableMapOf<Int, List<Pair<Double, Double>>>()

    private val completedExplosion = AtomicBoolean(false)
    private val treeBurner = TreeBurner(world, center.toVector3i())
    private val chunkCache = ChunkCache.getInstance(world)
    private val rayCaster = RayCaster(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val processedBlocks = ConcurrentHashMap.newKeySet<Long>()

    private fun isBlockProcessed(x: Int, y: Int, z: Int): Boolean {
        return !processedBlocks.add(Geometry.packIntegerCoordinates(x,y,z))
    }

    // Cache common calculations
    private val baseDirections = arrayOf(
        Vector3i(1, 0, 0),  // East
        Vector3i(-1, 0, 0), // West
        Vector3i(0, 0, 1),  // South
        Vector3i(0, 0, -1),  // North
    )

    fun explode() {
        val startTime = System.nanoTime()

        Defcon.instance.launch {
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
                            maxDestructionPower, minDestructionPower, radius / shockwaveRadius.toDouble()
                        )
                        val normalizedExplosionPower =
                            remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)

                        val columns = generateShockwaveColumns(radius)
                        if (columns.isNotEmpty()) {
                            if (radius >= radiusDestroyStart){
                                withContext(Dispatchers.Default) {
                                   processDestruction(columns, normalizedExplosionPower)
                                }
                            }

                            withContext(Defcon.instance.minecraftDispatcher) {
                                processEntityDamage(columns, normalizedExplosionPower, visitedEntities)
                            }
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
                cleanup()
            }
        }

    }

    private suspend fun processEntityDamage(
        columns: List<Vector3i>, explosionPower: Double, visitedEntities: MutableSet<Entity>
    ) {
        if (columns.isEmpty()) return

        withContext(Defcon.instance.minecraftDispatcher) {
            columns.forEach { col ->
                val loc = Location(world, col.x.toDouble(), (col.y + 1).toDouble(), col.z.toDouble())
                world.spawnParticle(Particle.EXPLOSION, loc, 0)
            }
        }

        val locationCursor = Location(world, 0.0, 0.0, 0.0)
        val halfShockwaveHeight = (shockwaveHeight / 2).toDouble()

        columns.chunked(10).forEach { columnChunk ->
            withContext(Defcon.instance.minecraftDispatcher) {
                columnChunk.forEach { column ->
                    locationCursor.set(
                        column.x.toDouble(), column.y.toDouble(), column.z.toDouble()
                    )

                    val entities = world.getNearbyEntities(
                        locationCursor, 1.0, halfShockwaveHeight, 1.0
                    ) {
                        it !in visitedEntities && it.y in locationCursor.y - maximumDistanceForAction..(locationCursor.y + shockwaveHeight + maximumDistanceForAction)
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

        val baseDamage = 20.0  // Higher base damage
        val adjustedDistance = max(distance, 1.0)
        val falloffFactor = min(1.0, 15.0 / (adjustedDistance * adjustedDistance))
        val finalDamage = baseDamage * explosionPower * falloffFactor

        // Improved knockback calculation
        val knockbackPower = explosionPower * 40.0 / adjustedDistance
        val knockback = Vector(dx * knockbackPower / distance,
            dy * knockbackPower / distance + 0.2, // Slight upward push
            dz * knockbackPower / distance)

        entity.velocity = knockback

        if (entity !is LivingEntity) return

        // Apply the improved damage
        entity.damage(finalDamage)

        if (entity !is Player) return

        val inv = (1 / explosionPower) * 3
        try {
            CameraShake(entity, CameraShakeOptions(2.6f, 0.04f, 3.7f * inv, 3.0f * inv))
        } catch (e: Exception) {
            println("Error applying CameraShake: ${e.message}")
        }
    }

    private suspend fun processDestruction(locations: List<Vector3i>, explosionPower: Double) {
        coroutineScope {
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
                launch {
                    chunk.forEach { treeBlock ->
                        treeBurner.processTreeBurn(treeBlock, explosionPower)
                    }
                }
            }

            // Process normal blocks in chunks to avoid overwhelming the server
            nonTreeBlocks.chunked(1000).forEach { chunk ->
                launch {
                    chunk.forEach { location ->
                        processBlock(location, explosionPower)
                    }
                }
            }
        }
    }

    private fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
    ) {
        // Skip if power is too low
        if (normalizedExplosionPower < 0.05) return

        // Enhanced penetration based on explosion power - key improvement
        val powerFactor = normalizedExplosionPower.pow(1.2) // Exponential scaling for higher power values
        val basePenetration = (powerFactor * 12).roundToInt().coerceIn(1, 15) // Increased max from 8 to 15

        // Create deterministic but varied penetration value based on position
        // Using prime numbers and XOR for more pseudo-random distribution
        val positionSeed = (blockLocation.x * 73) xor (blockLocation.z * 31) xor (blockLocation.y * 13)
        val random = Random(positionSeed)

        // Randomize penetration with constraints based on explosion power
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val distanceNormalized = 1.0 - normalizedExplosionPower.coerceIn(0.0, 1.0)
        val randomOffset =
            (random.nextDouble() * 2 - 1) * basePenetration * varianceFactor * (1 - distanceNormalized * 0.5)

        // Calculate final max penetration value with higher ceiling for high power
        val maxPenetration = (basePenetration + randomOffset).roundToInt()
            .coerceIn(max(1, (basePenetration * 0.7).toInt()), (basePenetration * 1.4).toInt())

        // Enhanced wall detection - check more efficiently
        var isWall = detectWall(blockLocation.x, blockLocation.y, blockLocation.z)
        if (!isWall) { // Second chance for the block below
            isWall = detectWall(blockLocation.x, blockLocation.y - 1, blockLocation.z)
        }

        // Different handling for walls vs. roofs
        if (isWall) {
            // Walls should be destroyed almost entirely - handle vertically
            processWall(blockLocation, normalizedExplosionPower, random)
        } else {
            // Roof/floor - implement enhanced penetration based on power
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
        val attachedBlockCache = TransformationRule.ATTACHED_BLOCKS
        return baseDirections.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y + dir.y, z + dir.z) in attachedBlockCache
        }
    }

    // Process a wall - destroy it almost entirely vertically
    private fun processWall(
        blockLocation: Vector3i, normalizedExplosionPower: Double, random: Random
    ) {
        // Return if the wall is already been processed
        if (isBlockProcessed(blockLocation.x, blockLocation.y, blockLocation.z)) return
        val x = blockLocation.x
        val z = blockLocation.z
        val startY = blockLocation.y

        // Enhanced depth based on power
        val maxDepth = (shockwaveHeight * (0.7 + normalizedExplosionPower * 0.6)).toInt()
        val transformBlacklist = TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
        val liquidMaterials = TransformationRule.LIQUID_MATERIALS

        // Batch changes for performance
        val blockChanges = ArrayList<Triple<Int, Int, Int>>(maxDepth)

        for (depth in 0 until maxDepth) {
            val currentY = startY - depth
            if (depth > 0 && !detectWall(x, currentY, z)) break

            val blockType = chunkCache.getBlockMaterial(x, currentY, z)
            if (blockType in transformBlacklist || blockType == Material.AIR) continue
            if (blockType in liquidMaterials) break

            val finalMaterial = if (depth > 0 && random.nextDouble() < normalizedExplosionPower) {
                transformationRule.transformMaterial(blockType, normalizedExplosionPower)
            } else {
                Material.AIR
            }

            val shouldCopyData =
                finalMaterial in TransformationRule.SLABS || finalMaterial in TransformationRule.WALLS || finalMaterial in TransformationRule.STAIRS

            blockChanges.add(Triple(currentY, finalMaterial.ordinal, if (shouldCopyData) 1 else 0))
        }

        // Apply changes in batch
        blockChanges.forEach { (y, materialOrdinal, copyData) ->
            val material = Material.entries[materialOrdinal]
            val shouldCopy = copyData == 1
            val updatePhysics = (material == Material.AIR && y == startY) || detectAttached(x, y, z)
            blockChanger.addBlockChange(x, y, z, material, shouldCopy, updatePhysics)
        }
    }


    // Process a roof/floor - penetrate downward until power is too low
    private fun processRoof(
        blockLocation: Vector3i, normalizedExplosionPower: Double, maxPenetration: Int, random: Random
    ) {
        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0

        // Enhanced power decay based on normalized power
        // Higher power = slower decay = deeper penetration
        val powerDecay = 0.85 - (0.15 * (1 - normalizedExplosionPower.pow(1.5)))

        val x = blockLocation.x
        val z = blockLocation.z

        // Create horizontal offsets for irregular patterns - enhanced with power scaling
        val maxOffset = ((normalizedExplosionPower * 3) + 1).toInt().coerceAtLeast(1)
        val offsetX = ArrayList<Int>(maxPenetration)
        val offsetZ = ArrayList<Int>(maxPenetration)

        // Pre-generate offsets with more variation for high power explosions
        for (i in 0 until maxPenetration) {
            // More variance in deeper levels and with higher power
            val levelVariance = min(1.0, i * 0.15 + normalizedExplosionPower * 0.2)
            if (random.nextDouble() < 0.3 + levelVariance) {
                offsetX.add(random.nextInt(-maxOffset, maxOffset + 1))
                offsetZ.add(random.nextInt(-maxOffset, maxOffset + 1))
            } else {
                offsetX.add(0)
                offsetZ.add(0)
            }
        }

        // Keep track of processed blocks with optimized hashset initial capacity

        // Cache transformation blacklist and liquid materials for performance
        val transformBlacklist = TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
        val liquidMaterials = TransformationRule.LIQUID_MATERIALS

        // Enhanced penetration logic for high power explosions
        while (penetrationCount < maxPenetration && currentPower > 0.08 && currentY > 0) {
            // Calculate current position with offset for irregular pattern
            val currentX = if (penetrationCount < offsetX.size) x + offsetX[penetrationCount] else x
            val currentZ = if (penetrationCount < offsetZ.size) z + offsetZ[penetrationCount] else z

            // Skip if we've already processed this block
            if (!isBlockProcessed(currentX, currentY, currentZ)) {
                currentY--
                penetrationCount++
                continue
            }

            // Get block type efficiently
            val blockType = chunkCache.getBlockMaterial(currentX, currentY, currentZ)

            // Skip if block is in blacklist or is liquid
            if (blockType in transformBlacklist || blockType in liquidMaterials) {
                // Try to continue penetration in straight line if offset block is invalid
                if (currentX != x || currentZ != z) {
                    val straightBlockType = chunkCache.getBlockMaterial(x, currentY, z)
                    if (straightBlockType !in transformBlacklist && straightBlockType !in liquidMaterials && straightBlockType != Material.AIR) {
                        processRoofBlock(
                            x, currentY, z, straightBlockType, currentPower, penetrationCount, maxPenetration, random
                        )
                    }
                }
                break
            }

            // Handle air blocks by looking for the next solid block below
            if (blockType == Material.AIR) {
                // Try to continue in straight line if offset led to air
                if (currentX != x || currentZ != z) {
                    val straightBlockType = chunkCache.getBlockMaterial(x, currentY, z)
                    if (straightBlockType != Material.AIR) {
                        processRoofBlock(
                            x, currentY, z, straightBlockType, currentPower, penetrationCount, maxPenetration, random
                        )
                    }
                }

                // Find next solid block with power-scaled search radius to create cavities
                val searchRadius = (normalizedExplosionPower * 4).toInt().coerceAtLeast(1)
                var foundSolid = false

                // Enhanced random search for solid blocks - more attempts for high power
                val searchAttempts = (3 + normalizedExplosionPower.pow(2) * 3).toInt()
                if (random.nextDouble() < 0.6 + normalizedExplosionPower * 0.2 && searchRadius > 1) {
                    for (attempt in 0 until searchAttempts) {
                        val randX = currentX + random.nextInt(-searchRadius, searchRadius + 1)
                        val randZ = currentZ + random.nextInt(-searchRadius, searchRadius + 1)

                        // Enhanced penetration distance based on power
                        val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                        val solidY = rayCaster.cachedRayTrace(randX, currentY, randZ, maxSearchDepth.toDouble())

                        if (solidY > currentY - maxSearchDepth) {
                            // Continue penetration from this new position
                            val newPos = Vector3i(randX, solidY, randZ)
                            if (!isBlockProcessed(newPos.x, newPos.y, newPos.z)) {
                                processRoofBlock(
                                    randX,
                                    solidY,
                                    randZ,
                                    chunkCache.getBlockMaterial(randX, solidY, randZ),
                                    currentPower * 0.8,
                                    penetrationCount,
                                    maxPenetration,
                                    random
                                )
                                foundSolid = true
                                break
                            }
                        }
                    }
                }

                if (!foundSolid) {
                    // Enhanced maximum search depth based on power
                    val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                    val nextSolidY = rayCaster.cachedRayTrace(currentX, currentY, currentZ, maxSearchDepth.toDouble())
                    if (nextSolidY < currentY - maxSearchDepth) break // Too far down, skip

                    // Jump to the next solid block
                    currentY = nextSolidY
                    currentPower *= 0.7 // Reduce power more significantly for jumps
                    continue
                } else {
                    // We handled an alternative path, move on
                    currentY--
                    penetrationCount++
                    currentPower *= powerDecay
                    continue
                }
            }

            // Process the current block

            processRoofBlock(
                currentX, currentY, currentZ, blockType, currentPower, penetrationCount, maxPenetration, random
            )

            // Move down and update state
            currentY--

            // Enhanced power decay - high power explosions maintain power longer for deeper penetration
            val powerDecayFactor = powerDecay + (random.nextDouble() * 0.1 - 0.05) + // Random variance
                    (normalizedExplosionPower * 0.08) // Power boost for high explosions

            currentPower *= powerDecayFactor.coerceIn(0.7, 0.95)
            penetrationCount++
        }
    }


    // Helper function to process individual blocks in the roof penetration
    private fun processRoofBlock(
        x: Int, y: Int, z: Int,
        blockType: Material,
        power: Double,
        penetrationCount: Int,
        maxPenetration: Int,
        random: Random
    ) {
        // Add randomness to power
        val powerVariance = (random.nextDouble() * 0.2 - 0.1) * power
        val adjustedPower = (power + powerVariance).coerceIn(0.0, 1.0)

        val penetrationRatio = penetrationCount.toDouble() / maxPenetration

        // Enhanced material transformation logic for deeper penetration
        val finalMaterial = when {
            // Near surface layers - more likely to be air
            penetrationRatio < 0.3 -> Material.AIR // Always destroy surface layers

            // High power explosions create more cavities deeper
            adjustedPower > 0.7 && random.nextDouble() < adjustedPower * 0.9 -> Material.AIR

            // Mid-depth with medium-high power - mix of air and debris
            penetrationRatio < 0.6 && adjustedPower > 0.5 -> {
                if (random.nextDouble() < adjustedPower * 0.8) Material.AIR
                else transformationRule.transformMaterial(blockType, adjustedPower * 0.8)
            }

            // Deeper layers - scattered blocks/rubble pattern
            penetrationRatio >= 0.6 -> {
                // Transform for deeper layers - more debris for high power
                if (random.nextDouble() < 0.7 - (adjustedPower * 0.3))
                    transformationRule.transformMaterial(blockType, adjustedPower)
                else Material.AIR
            }

            // Random destruction pockets throughout
            random.nextDouble() < adjustedPower * 0.8 -> Material.AIR

            // Some blocks remain slightly transformed (stalactite-like formations)
            else -> transformationRule.transformMaterial(blockType, adjustedPower * 0.6)
        }

        // Special case: Add falling blocks occasionally for more dynamism
        val updatePhysics = penetrationCount == 0 ||
                (finalMaterial != Material.AIR && random.nextDouble() < 0.2)

        blockChanger.addBlockChange(
            x, y, z,
            finalMaterial,
            finalMaterial in TransformationRule.SLABS ||
                    finalMaterial in TransformationRule.WALLS ||
                    finalMaterial in TransformationRule.STAIRS,
            updateBlock = updatePhysics
        )
    }

    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        // Use cached circle coordinates when possible
        if (radius !in circleCache) {
            // Enhanced adaptive density with power scaling
            val density = max(2, min(6, (radius / 15) + 2)) // More precise density calculation
            val steps = (2 * Math.PI * radius).toInt() * density
            val angleIncrement = 2 * Math.PI / steps

            // Pre-calculate sin/cos values and store them
            val coordinates = ArrayList<Pair<Double, Double>>(steps)
            for (i in 0 until steps) {
                val angle = angleIncrement * i
                coordinates.add(cos(angle) to sin(angle))
            }
            circleCache[radius] = coordinates
        }

        // Use cached sin/cos values
        val coordinates = circleCache[radius]!!
        val result = ArrayList<Vector3i>(coordinates.size)

        // Batch highestBlockYAt calls for improved performance
        coordinates.forEach { (cosVal, sinVal) ->
            val x = round(center.blockX + radius * cosVal).toInt()
            val z = round(center.blockZ + radius * sinVal).toInt()
            // Use chunkCache which should be more efficient than rayCaster for this purpose
            val highestY = chunkCache.highestBlockYAt(x, z)
            result.add(Vector3i(x, highestY, z))
        }

        return result
    }

    private fun cleanup() {
        circleCache.clear()
        chunkCache.cleanupCache()
        processedBlocks.clear()
        complete()
    }
}

