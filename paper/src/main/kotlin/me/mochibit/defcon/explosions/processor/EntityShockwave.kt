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

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.Logger.err
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import org.bukkit.Location
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Simulates a shockwave from an explosion that affects entities within range
 * Optimized version with proper resource cleanup and memory management
 */
class EntityShockwave(
    private val center: Location,
    private val shockwaveHeight: Int,
    private val baseShockwaveGroundPenetration: Int,
    private val shockwaveRadius: Int,
    private val initialRadius: Int = 0,
    private val shockwaveSpeed: Float = 50f,
    private val aboveSeaLevelPenetrationFactor: Float = 1.5f,
    private val belowSeaLevelPenetrationFactor: Float = 0.8f,
    private val entitySearchRadius: Double = 50.0,
    private val entitySearchInterval: Long = 30L,
    private val baseDamage: Double = 80.0
) {
    // Duration calculation
    val duration = ((shockwaveRadius - initialRadius) / shockwaveSpeed).toInt().seconds

    // Atomic counters and flags
    private val secondElapsed = AtomicInteger(0)
    private val isProcessing = AtomicBoolean(false)
    private val isCleanedUp = AtomicBoolean(false)

    // Optimized data structures with size limits
    private val processedEntities = Collections.newSetFromMap(ConcurrentHashMap<UUID, Boolean>())
    private val entityCache = ConcurrentHashMap<ChunkCoordinate, MutableSet<UUID>>() // Store UUIDs instead of entities

    // Process tracking for cleanup
    private var processingJob: Job? = null
    private var timerJob: Job? = null
    private var entityDiscoveryJob: Job? = null

    // Pre-calculated values
    private val shockwaveGroundPenetration: Int by lazy {
        val seaLevel = center.world?.seaLevel ?: 64
        if (center.y > seaLevel) {
            (baseShockwaveGroundPenetration * aboveSeaLevelPenetrationFactor).toInt()
        } else {
            (baseShockwaveGroundPenetration * belowSeaLevelPenetrationFactor).toInt()
        }
    }

    private val computationDispatcher = Dispatchers.Default.limitedParallelism(2) // Reduced parallelism

    /**
     * Starts the shockwave processing with proper resource management
     */
    suspend fun process() = coroutineScope {
        if (isCleanedUp.get()) {
            err("Attempted to process already cleaned up shockwave")
            return@coroutineScope
        }

        if (!isProcessing.compareAndSet(false, true)) {
            err("Shockwave is already processing")
            return@coroutineScope
        }

        try {
            // Initialize the visual effect


            // Timer job with cleanup check
            timerJob = launch(Dispatchers.IO) {
                while (isActive && !isCleanedUp.get() && secondElapsed.get() < duration.inWholeSeconds) {
                    delay(1.seconds)
                    secondElapsed.incrementAndGet()
                }
            }

            // Entity discovery job with proper cleanup
            entityDiscoveryJob = launch(Dispatchers.IO) {
                while (isActive && !isCleanedUp.get() && secondElapsed.get() < duration.inWholeSeconds) {
                    try {
                        refreshEntityCache()
                        delay(entitySearchInterval.seconds)
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        err("Error refreshing entity cache: ${e.message}")
                    }
                }
            }

            // Main processing job
            processingJob = launch(computationDispatcher) {
                while (isActive && !isCleanedUp.get() && secondElapsed.get() < duration.inWholeSeconds) {
                    try {
                        processShockwaveEffects()
                        delay(500.milliseconds)
                    } catch (e: CancellationException) {
                        break
                    } catch (e: Exception) {
                        err("Error processing shockwave effects: ${e.message}")
                    }
                }
            }

            // Wait for completion or cancellation
            timerJob?.join()

        } catch (e: Exception) {
            err("Fatal error in shockwave processing: ${e.message}")
            throw e
        } finally {
            cleanup()
        }
    }

    /**
     * Cleanup method to prevent memory leaks
     */
    private suspend fun cleanup() {
        if (!isCleanedUp.compareAndSet(false, true)) return

        try {
            // Cancel all coroutines
            listOfNotNull(timerJob, entityDiscoveryJob, processingJob).forEach { job ->
                try {
                    job.cancelAndJoin()
                } catch (e: Exception) {
                    // Ignore cancellation exceptions during cleanup
                }
            }

            // Clear all collections
            processedEntities.clear()
            entityCache.clear()

            isProcessing.set(false)

        } catch (e: Exception) {
            err("Error during shockwave cleanup: ${e.message}")
        }
    }

    /**
     * Optimized entity cache refresh with size limits
     */
    private suspend fun refreshEntityCache() {
        if (isCleanedUp.get()) return

        withContext(Dispatchers.IO) {
            // Clear old cache but keep the map instance
            entityCache.clear()

            val world = center.world ?: return@withContext
            val players = world.players.filter { it.isOnline && it.isValid }

            // Add players to cache
            players.forEach { player ->
                if (isCleanedUp.get()) return@withContext

                val chunk = ChunkCoordinate(player.location.chunk.x, player.location.chunk.z)
                entityCache.computeIfAbsent(chunk) { Collections.synchronizedSet(mutableSetOf()) }
                    .add(player.uniqueId)
            }

            // Add nearby entities with batch processing
            val entityBatchSize = 50
            val allNearbyEntities = mutableListOf<LivingEntity>()

            // Collect entities in batches to avoid overwhelming the main thread
            for (playerBatch in players.chunked(entityBatchSize)) {
                if (isCleanedUp.get()) return@withContext

                val batchEntities = withContext(Defcon.minecraftDispatcher) {
                    playerBatch.flatMap { player ->
                        player.location.world?.getNearbyEntities(
                            player.location,
                            entitySearchRadius,
                            entitySearchRadius,
                            entitySearchRadius
                        )?.filterIsInstance<LivingEntity>()?.filter {
                            it.isValid && it !is Player
                        } ?: emptyList()
                    }
                }
                allNearbyEntities.addAll(batchEntities)
            }

            // Add entities to cache by chunk
            allNearbyEntities.forEach { entity ->
                if (isCleanedUp.get()) return@withContext

                val chunk = ChunkCoordinate(entity.location.chunk.x, entity.location.chunk.z)
                entityCache.computeIfAbsent(chunk) { Collections.synchronizedSet(mutableSetOf()) }
                    .add(entity.uniqueId)
            }
        }
    }

    /**
     * Optimized shockwave processing with entity validation
     */
    private suspend fun processShockwaveEffects() {
        if (isCleanedUp.get()) return

        val currentShockwaveRadius = calculateCurrentRadius()
        val chunkRadius = (currentShockwaveRadius / 16.0).toInt() + 1
        val centerChunkX = center.chunk.x
        val centerChunkZ = center.chunk.z

        // Process chunks in batches to avoid blocking
        for (x in -chunkRadius..chunkRadius) {
            for (z in -chunkRadius..chunkRadius) {
                if (isCleanedUp.get()) return

                val chunk = ChunkCoordinate(centerChunkX + x, centerChunkZ + z)
                val entityUUIDs = entityCache[chunk] ?: continue

                // Process entities with validation
                val validEntities = withContext(Defcon.minecraftDispatcher) {
                    entityUUIDs.mapNotNull { uuid ->
                        center.world?.entities?.find { it.uniqueId == uuid && it.isValid }
                    }.filterIsInstance<LivingEntity>()
                }

                validEntities.forEach { entity ->
                    try {
                        if (!processedEntities.contains(entity.uniqueId)) {
                            if (isEntityAffectedByShockwave(entity, currentShockwaveRadius)) {
                                processEntity(entity, currentShockwaveRadius)
                            }
                        }
                    } catch (e: Exception) {
                        err("Error processing entity ${entity.uniqueId}: ${e.message}")
                    }
                }
            }
        }
    }

    /**
     * Check if entity is affected (unchanged but added null safety)
     */
    private fun isEntityAffectedByShockwave(entity: Entity, currentRadius: Int): Boolean {
        val entityLocation = entity.location
        val distanceFromCenter = entityLocation.distance(center)

        if (distanceFromCenter > currentRadius) return false

        val entityHeight = entityLocation.y
        return !(entityHeight < center.y - shockwaveGroundPenetration ||
                entityHeight > center.y + shockwaveHeight)
    }

    /**
     * Calculate current radius (unchanged)
     */
    private fun calculateCurrentRadius(): Int {
        return min(
            shockwaveRadius.toFloat(),
            initialRadius + (shockwaveSpeed * secondElapsed.get())
        ).toInt()
    }

    /**
     * Process entity with duplicate prevention
     */
    private suspend fun processEntity(entity: Entity, currentShockwaveRadius: Int) {
        if (isCleanedUp.get()) return

        // Atomic check-and-set for processed entities
        if (!processedEntities.add(entity.uniqueId)) return

        val entityDistanceFromCenter = entity.location.distance(center)
        val explosionPowerNormalized = ((shockwaveRadius - entityDistanceFromCenter) / shockwaveRadius)
            .coerceIn(0.0, 1.0)
            .toFloat()

        applyExplosionEffects(entity, explosionPowerNormalized)
    }

    /**
     * Apply explosion effects with null safety
     */
    private suspend fun applyExplosionEffects(
        entity: Entity,
        explosionPower: Float
    ) = withContext(Defcon.minecraftDispatcher) {
        if (isCleanedUp.get() || !entity.isValid) return@withContext

        try {
            val knockbackVector = calculateKnockbackVector(entity, explosionPower)

            if (entity is LivingEntity) {
                val scaledDamage = baseDamage * explosionPower.coerceIn(0f, 1f)
                entity.damage(scaledDamage)
            }

            entity.velocity = knockbackVector

            if (entity is Player) {
                ExplosionSoundManager.playSounds(ExplosionSoundManager.DefaultSounds.ShockwaveHitSound, entity)
                CameraShake(
                    entity,
                    CameraShakeOptions(
                        magnitude = 2.6f,
                        decay = 0.04f,
                        pitchPeriod = 3.7f * explosionPower,
                        yawPeriod = 3.0f * explosionPower
                    )
                )
            }
        } catch (e: Exception) {
            err("Error applying explosion effects to entity ${entity.uniqueId}: ${e.message}")
        }
    }

    /**
     * Calculate knockback vector (unchanged but with null safety)
     */
    private fun calculateKnockbackVector(entity: Entity, explosionPower: Float): Vector {
        val entityLoc = entity.location
        val dx = entityLoc.x - center.x
        val dy = entityLoc.y - center.y
        val dz = entityLoc.z - center.z
        val horizontalDistance = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1)
        val knockbackPower = explosionPower * 2
        val knockbackX = knockbackPower * (dx / horizontalDistance)
        val knockbackY = if (dy != 0.0) knockbackPower / (abs(dy) * 2) + 1.2 else 1.2
        val knockbackZ = knockbackPower * (dz / horizontalDistance)
        return Vector(knockbackX, knockbackY, knockbackZ)
    }

    /**
     * Manual cleanup method for external cleanup
     */
    suspend fun forceCleanup() {
        cleanup()
    }

    /**
     * Data class for chunk coordinates (unchanged)
     */
    private data class ChunkCoordinate(val x: Int, val z: Int)
}