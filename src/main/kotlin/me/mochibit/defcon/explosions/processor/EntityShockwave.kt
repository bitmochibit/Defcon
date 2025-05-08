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

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Logger.err
import me.mochibit.defcon.effects.explosion.generic.ShockwaveEffect
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
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.seconds

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
    private val entitySearchInterval: Long = 30L
) {
    private val shockwaveEffect = ShockwaveEffect(
        center,
        shockwaveRadius,
        initialRadius,
        shockwaveSpeed,
    )

    private val worldPlayers = ConcurrentHashMap<UUID, Player>()
    private val nearbyEntities = ConcurrentHashMap<UUID, MutableSet<LivingEntity>>()

    val duration =
        ((shockwaveRadius - initialRadius) / shockwaveSpeed).toInt().seconds

    private val secondElapsed = AtomicInteger(0)

    // Calculate actual ground penetration based on sea level
    private val shockwaveGroundPenetration: Int by lazy {
        val seaLevel = center.world.seaLevel
        if (center.y > seaLevel) {
            (baseShockwaveGroundPenetration * aboveSeaLevelPenetrationFactor).toInt()
        } else {
            (baseShockwaveGroundPenetration * belowSeaLevelPenetrationFactor).toInt()
        }
    }

    suspend fun process() {
        val jobs = mutableListOf<Job>()

        // Player update job
        jobs.add(Defcon.instance.launch(Dispatchers.IO) {
            while (secondElapsed.get() < duration.inWholeSeconds) {
                try {
                    val players = center.world.players
                    // Remove offline players from our tracking map
                    worldPlayers.entries.removeIf { entry ->
                        val player = entry.value
                        !player.isOnline || !players.contains(player)
                    }

                    // Add new players
                    for (player in players) {
                        if (player.isOnline && !worldPlayers.containsKey(player.uniqueId)) {
                            worldPlayers[player.uniqueId] = player
                        }
                    }
                } catch (e: Exception) {
                    err("Error updating player list: ${e.message}")
                }
                delay(10.seconds)
            }
        })

        // Entity search job - finds entities near each player
        jobs.add(Defcon.instance.launch(Dispatchers.IO) {
            while (secondElapsed.get() < duration.inWholeSeconds) {
                try {
                    updateNearbyEntities()
                } catch (e: Exception) {
                    err("Error updating nearby entities: ${e.message}")
                }
                delay(entitySearchInterval.seconds)
            }
        })

        // Time tracker job
        jobs.add(Defcon.instance.launch(Dispatchers.IO) {
            while (secondElapsed.get() < duration.inWholeSeconds) {
                delay(1.seconds)
                secondElapsed.incrementAndGet()
            }
        })

        // Damage processor job
        jobs.add(Defcon.instance.launch(Dispatchers.IO) {
            while (secondElapsed.get() < duration.inWholeSeconds) {
                try {
                    checkDamageEntities()
                } catch (e: Exception) {
                    err("Error processing entity damage: ${e.message}")
                }
                delay(0.5.seconds)
            }
        })

        try {
            shockwaveEffect.instantiate(true)
            delay(duration)
        } finally {
            // Cancel all jobs when we're done
            jobs.forEach { it.cancel() }
        }
    }

    private suspend fun updateNearbyEntities() = coroutineScope {
        // Clear previous entity lists
        nearbyEntities.clear()

        // For each online player, find nearby entities
        for (player in worldPlayers.values) {
            if (!player.isOnline) continue

            val playerEntities = mutableSetOf<LivingEntity>()
            val entitiesNearPlayer = async(Defcon.instance.minecraftDispatcher) {player.location.world.getNearbyEntities(
                player.location,
                entitySearchRadius,
                entitySearchRadius,
                entitySearchRadius
            ).filterIsInstance<LivingEntity>()}

            // Don't include players in this list
            playerEntities.addAll(entitiesNearPlayer.await().filter { it !is Player })

            // Store the entities for this player
            nearbyEntities[player.uniqueId] = playerEntities
        }
    }

    private suspend fun checkDamageEntities() {
        val currentShockwaveRadius = min(
            shockwaveRadius.toFloat(),
            initialRadius + (shockwaveSpeed * secondElapsed.get())
        ).toInt()

        // Process players
        for (player in worldPlayers.values) {
            if (!player.isOnline) continue

            // Process the player
            processEntity(player, currentShockwaveRadius)

            // Process entities near this player
            nearbyEntities[player.uniqueId]?.forEach { entity ->
                if (entity.isValid) {
                    processEntity(entity, currentShockwaveRadius)
                }
            }
        }
    }

    private suspend fun processEntity(entity: Entity, currentShockwaveRadius: Int) {
        val entityDistanceFromCenter = entity.location.distance(center)
        // Skip entities outside the current shockwave radius
        if (entityDistanceFromCenter > currentShockwaveRadius) return

        val entityHeight = entity.location.y
        // Skip entities outside the vertical range of the shockwave
        if (entityHeight < center.y - shockwaveGroundPenetration ||
            entityHeight > center.y + shockwaveHeight
        ) return

        // Calculate normalized power based on distance
        val explosionPowerNormalized = ((shockwaveRadius - entityDistanceFromCenter) / shockwaveRadius)
            .coerceIn(0.0, 1.0)
            .toFloat()

        applyExplosionKnockback(
            entity,
            explosionPowerNormalized * shockwaveSpeed
        )

        // Apply camera shake only to players
        if (entity is Player) {
            try {
                ExplosionSoundManager.playSounds(ExplosionSoundManager.DefaultSounds.ShockwaveHitSound, entity)
                CameraShake(
                    entity,
                    CameraShakeOptions(
                        2.6f,
                        0.04f,
                        3.7f * explosionPowerNormalized,
                        3.0f * explosionPowerNormalized
                    )
                )
            } catch (e: Exception) {
                err("Error applying effects to player ${entity.name}: ${e.message}")
            }
        }
    }

    private suspend fun applyExplosionKnockback(
        entity: Entity,
        explosionPower: Float
    ) {
        // Calculate knockback vector components
        val dx = entity.location.x - center.x
        val dy = entity.location.y - center.y
        val dz = entity.location.z - center.z

        val distance = sqrt(dx * dx + dz * dz).coerceAtLeast(0.1) // Avoid division by zero

        val knockbackPower = explosionPower * 2 // Increased for better effect
        val knockbackX = knockbackPower * (dx / distance)
        val knockbackY = if (dy != 0.0) knockbackPower / (abs(dy) * 2) + 1.2 else 1.2
        val knockbackZ = knockbackPower * (dz / distance)

        // Apply knockback and damage on the Minecraft thread
        withContext(Defcon.instance.minecraftDispatcher) {
            try {
                if (entity is LivingEntity) {
                    val baseDamage = 80.0
                    val scaledDamage = baseDamage * explosionPower.coerceIn(0f, 1f)
                    entity.damage(scaledDamage)
                }
                entity.velocity = Vector(knockbackX, knockbackY, knockbackZ)
            } catch (e: Exception) {
                err("Error applying knockback to entity ${entity.uniqueId}: ${e.message}")
            }
        }
    }
}