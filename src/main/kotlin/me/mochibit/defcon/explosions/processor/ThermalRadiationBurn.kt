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
import io.ktor.utils.io.core.*
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.toTicks
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.Damageable
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import java.util.*
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * This class handles the thermal radiation burn effect of a nuclear explosion.
 * It raycasts from the effectorOffset point towards each entity, and if it hits the entity, it applies the burn effect depending on the distance
 *
 * @param center The center of the explosion effect in the world
 * @param radius The maximum distance from the center at which the effect can be applied
 * @param effectorHeight Height offset from explosion center where thermal radiation originates
 * @param duration How long to track entities for this effect (in ticks)
 */
class ThermalRadiationBurn(
    center: Location,
    radius: Int,
    effectorHeight: Int = 100,
    duration: Duration
) : RaycastedEffector(center, radius, effectorHeight, duration) {

    // Map to store burn levels for entities
    private val burnLevels = mutableMapOf<UUID, Int>()

    // Configure burn damage parameters
    private val fireTicksClose = 400
    private val fireTicksMid = 200
    private val fireTicksFar = 100

    /**
     * Filter entities to only get living entities that can be damaged
     */
    override suspend fun getTargetEntities(): List<Entity> {
        val entities = withContext(Defcon.instance.minecraftDispatcher) {
            world.entities.filter { entity ->
                entity is LivingEntity && entity.isValid && !entity.isDead
            }
        }
        return entities
    }

    /**
     * Apply thermal radiation burn effect to an entity
     */
    override fun applyEffect(entity: Entity, effectType: EffectType) {
        if (entity !is LivingEntity) return

        // Skip if entity is already affected
        if (affectedEntities.containsKey(entity.uniqueId)) return

        // Calculate distance and damage factor
        val distanceSquared = entity.location.distanceSquared(center)
        val distance = sqrt(distanceSquared)

        // Calculate damage based on inverse square law (radiation intensity decreases with square of distance)
        val distanceFactor = when (effectType) {
            EffectType.CLOSE_RANGE -> {
                // Maximum damage for very close entities
                val normalizedDistance = distance / closeRangeThreshold
                1.0 - min(1.0, normalizedDistance).pow(1.5)
            }

            EffectType.MID_RANGE -> {
                // Moderate damage for mid-range entities
                val normalizedDistance = (distance - closeRangeThreshold) /
                        (farRangeThreshold - closeRangeThreshold)
                0.7 * (1.0 - min(1.0, normalizedDistance))
            }

            EffectType.FAR_RANGE -> {
                // Minimal damage for far entities
                val normalizedDistance = (distance - farRangeThreshold) /
                        (reach - farRangeThreshold)
                0.3 * (1.0 - min(1.0, normalizedDistance))
            }
        }

        // Calculate fire ticks based on distance
        val fireTicks = when (effectType) {
            EffectType.CLOSE_RANGE -> (fireTicksClose * distanceFactor).toInt().coerceAtLeast(60)
            EffectType.MID_RANGE -> (fireTicksMid * distanceFactor).toInt().coerceAtLeast(40)
            EffectType.FAR_RANGE -> (fireTicksFar * distanceFactor).toInt().coerceAtLeast(20)
        }

        // Determine burn level (1-3) for effect intensity
        val burnLevel = when {
            distanceFactor > 0.7 -> 3  // Severe burns
            distanceFactor > 0.4 -> 2  // Moderate burns
            else -> 1                   // Minor burns
        }

        // Store burn level
        burnLevels[entity.uniqueId] = burnLevel

        // Create continuous effect task
        val effectTask = createBurnEffectTask(entity, burnLevel)

        // Store in affected entities
        val effectData = EffectorData(
            effectTask,
            System.currentTimeMillis(),
            duration.inWholeMilliseconds,
            effectType
        )

        affectedEntities[entity.uniqueId] = effectData

        // Apply immediate effects
        Defcon.instance.launch {
            applyImmediateBurnEffects(entity, burnLevel, fireTicks)
        }

        // Stop the continuous effect after duration
        runLater(duration) {
            cleanup(entity)
        }
    }

    /**
     * Apply immediate burn effects to the entity
     */
    private fun applyImmediateBurnEffects(
        entity: LivingEntity,
        burnLevel: Int,
        fireTicks: Int
    ) {
        // Set entity on fire
        if (fireTicks > 0 && !entity.isDead) {
            entity.fireTicks = max(entity.fireTicks, fireTicks)
        }

        // Apply potion effects for players
        if (entity is Player) {
            when (burnLevel) {
                3 -> { // Severe burns - slowness, weakness, wither
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration.toTicks().toInt(), 2))
                    entity.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, duration.toTicks().toInt(), 2))
                    entity.addPotionEffect(PotionEffect(PotionEffectType.WITHER, (duration / 2).toTicks().toInt(), 1))
                }

                2 -> { // Moderate burns - slowness, weakness
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, duration.toTicks().toInt(), 1))
                    entity.addPotionEffect(PotionEffect(PotionEffectType.WEAKNESS, duration.toTicks().toInt(), 1))
                }

                1 -> { // Minor burns - just slowness
                    entity.addPotionEffect(PotionEffect(PotionEffectType.SLOWNESS, (duration / 2).toTicks().toInt(), 0))
                }
            }
        }
    }

    /**
     * Create task for continuous burn effects
     */
    private fun createBurnEffectTask(entity: Entity, burnLevel: Int): Closeable {
        if (entity !is LivingEntity) return Closeable { }

        // Calculate tick interval based on burn level
        val interval = when (burnLevel) {
            3 -> 1.seconds
            2 -> 2.seconds
            else -> 4.seconds
        }

        return intervalAsync(interval) {
            // Skip if entity is no longer valid
            if (!entity.isValid || entity.isDead) {
                cleanup(entity)
                return@intervalAsync
            }

            val entityUUID = entity.uniqueId
            val effectData = affectedEntities[entityUUID] ?: return@intervalAsync

            // Calculate time-based parameters
            val elapsed = System.currentTimeMillis() - effectData.startTime
            val progressFactor = elapsed.toDouble() / effectData.effectDuration

            // Calculate fade factor (1.0 at start, 0.0 at end)
            val fadeFactor = max(0.0, min(1.0, 1.0 - progressFactor)).toFloat()

            // Check if we should stop the effect (too faded)
            if (fadeFactor < 0.05) {
                cleanup(entity)
                return@intervalAsync
            }

            if (entity is Player) {
                // Check if player has an Elytra
                val chestPlateItem = entity.inventory.chestplate
                if (chestPlateItem != null && chestPlateItem.type == Material.ELYTRA) {
                    // Clone the item and set durability to 1
                    val newElytra = ItemStack(chestPlateItem.type, 1)
                    val meta = chestPlateItem.itemMeta?.clone() as? Damageable
                    if (meta != null) {
                        meta.damage =
                            if (meta.hasMaxDamage()) meta.maxDamage - 1 else newElytra.type.maxDurability.toInt() - 1 // Set to almost broken (1 durability left)
                        newElytra.itemMeta = meta
                        // Actually apply the modified elytra to the player's inventory
                        entity.inventory.chestplate = newElytra
                    }
                }
            }
        }
    }

    /**
     * Enhanced cleanup method that also handles burn-specific cleanup
     */
    override fun cleanup(entity: Entity) {
        super.cleanup(entity)

        // Remove from burn levels
        burnLevels.remove(entity.uniqueId)
    }

    companion object {
        // Constants for burn effect tuning
        private const val BURN_DAMAGE_MULTIPLIER = 1.5 // Multiplier for burn damage
    }
}
