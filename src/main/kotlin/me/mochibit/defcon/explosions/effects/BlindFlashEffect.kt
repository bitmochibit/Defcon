package me.mochibit.defcon.explosions.effects

import io.ktor.utils.io.core.*
import me.mochibit.defcon.explosions.processor.RaycastedEffector
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * BlindFlashEffect is a class that represents a visual effect of a flash
 * explosion in a Minecraft world, with effects that vary based on distance
 * @param center The center of the explosion effect in the world.
 * @param reach The maximum distance from the center at which the effect can be seen.
 * @param raycastHeight Determines from what point relative to the center it should cast the effect
 * @param duration The duration of the effect in ticks (20 ticks = 1 second)
 * @param skyVisibilityRequired Percentage of sky that must be visible for distant explosions to be seen (0.0-1.0)
 */
class BlindFlashEffect(
    center: Location,
    reach: Int,
    raycastHeight: Int = 100,
    duration: Long = 10 * 20L,
    skyVisibilityRequired: Float = 0.3f
) : RaycastedEffector(center, reach, raycastHeight, duration, skyVisibilityRequired) {

    // Use a specialized map for the text displays
    private val playerDisplays = ConcurrentHashMap<UUID, MutableMap<CubeFace, ClientSideTextDisplay>>()

    // Data for cube display effect
    private val textureScale = Vector3f(8f, 4f, 1f)
    private val cubeSize = 50f
    private val transforms = mapOf(
        CubeFace.BACK to Matrix4f() // Back
            .rotate(Quaternionf())
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale),

        CubeFace.RIGHT to Matrix4f() // Right
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 1))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x * cubeSize),

        CubeFace.FRONT to Matrix4f() // Front
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 2))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x),
        CubeFace.LEFT to Matrix4f() // Left
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 3))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x * cubeSize),

        CubeFace.TOP to Matrix4f() // TOP
            .rotate(Quaternionf().rotateX(Math.PI.toFloat() / 2))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.y * cubeSize),
        CubeFace.BOTTOM to Matrix4f() // BOTTOM
            .rotate(Quaternionf().rotateX(-Math.PI.toFloat() / 2))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize / 2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.y * cubeSize)
    )

    /**
     * Enum for cube faces used in the close-range effect
     */
    private enum class CubeFace {
        FRONT, RIGHT, BACK, LEFT, TOP, BOTTOM;
    }

    /**
     * Only target players for blind flash effect
     */
    override suspend fun getTargetEntities(): List<Entity> {
        return world.players.filter { player ->
            player.location.distanceSquared(center) <= reachSquared
        }
    }

    /**
     * Apply flash effect to an entity (which will be a player)
     */
    override fun applyEffect(entity: Entity, effectType: EffectType) {
        if (entity !is Player) return

        // Apply night vision effect with intensity based on distance
        if (!entity.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            runLater(1L) {
                // Lower amplifier for distant effects
                val amplifier = when (effectType) {
                    EffectType.CLOSE_RANGE -> 255
                    EffectType.MID_RANGE -> 200
                    EffectType.FAR_RANGE -> 150
                }

                entity.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        duration.toInt(),
                        amplifier
                    )
                )
            }
        }

        val effectDurationMs = duration * 50 // Convert ticks to milliseconds
        val effectTask = createEffectTask(entity, effectType)

        // Store reference to effect task
        val effectData = EffectorData(
            effectTask,
            System.currentTimeMillis(),
            effectDurationMs,
            effectType
        )

        // Store effect data
        affectedEntities[entity.uniqueId] = effectData

        // Initialize display map for this player
        playerDisplays[entity.uniqueId] = EnumMap(CubeFace::class.java)

        // Stop the effect after duration
        runLater(duration) {
            cleanup(entity)
        }
    }
    /**
     * Creates the appropriate visual effect task based on distance and effect type
     */
    private fun createEffectTask(player: Player, initialEffectType: EffectType): Closeable {
        return intervalAsync(0L, 2L) {
            // Skip if player left the world or game
            if (!player.isOnline || player.world != world) {
                cleanup(player)
                return@intervalAsync
            }

            val playerUUID = player.uniqueId
            val effectData = affectedEntities[playerUUID] ?: return@intervalAsync
            val displays = playerDisplays[playerUUID] ?: EnumMap(CubeFace::class.java)

            // Calculate time-based parameters
            val elapsed = System.currentTimeMillis() - effectData.startTime
            val progressFactor = elapsed.toDouble() / effectData.effectDuration

            if (progressFactor >= 1.0 || !hasLineOfSightToSource(player)) {
                cleanup(player)
                return@intervalAsync
            }

            val fadeFactor = (1.0 - progressFactor).toFloat()

            val smoothFadeFactor = if (progressFactor > 0.7) {
                val normalizedProgress = (progressFactor - 0.7) / 0.3
                (1.0 - normalizedProgress * normalizedProgress * normalizedProgress).toFloat() * 0.3f
            } else {
                fadeFactor
            }

            if (smoothFadeFactor < 0.05) {
                cleanup(player)
                return@intervalAsync
            }


            val distanceSquared = player.location.distanceSquared(center)
            val currentEffectType = determineEffectType(distanceSquared)

            // Update effect type if changed (player moved closer/further)
            if (currentEffectType != effectData.currentEffectType) {
                // Clean up previous effect visuals
                displays.values.forEach { it.remove() }
                displays.clear()
                effectData.currentEffectType = currentEffectType
            }

            // Update visuals based on current effect type
            when (currentEffectType) {
                EffectType.CLOSE_RANGE -> {
                    // Close range: Use cubic text display with fading opacity
                    updateCubeDisplayEffect(player, displays, smoothFadeFactor)
                }
                EffectType.MID_RANGE -> {
                    // Mid range: Use particles with perspective and moderate count
                    updateMidRangeEffect(player, distanceSquared, smoothFadeFactor)
                }
                EffectType.FAR_RANGE -> {
                    // Far range: Distant flash effect
                    if (isPlayerInOpenAir(player)) {
                        updateFarRangeEffect(player, distanceSquared, smoothFadeFactor)
                    }
                }
            }
        }
    }

    /**
     * Updates the cube display effect with 6 faces surrounding the player, properly oriented
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateCubeDisplayEffect(player: Player, displays: MutableMap<CubeFace, ClientSideTextDisplay>, fadeFactor: Float) {
        // Create display faces if they don't exist
        if (displays.isEmpty()) {
            CubeFace.entries.forEach { face ->
                val textDisplay = ClientSideTextDisplay(player)
                textDisplay.setBillboard(Display.Billboard.FIXED)
                textDisplay.summon()
                textDisplay.setInterpolationDuration(5)
                displays[face] = textDisplay
            }
        }

        // Calculate opacity based on fade factor (255 = full opacity, 0 = invisible)
        val opacityValue = (255 * fadeFactor).toInt().coerceIn(0, 255)

        // Get player's eye location
        val eyeLoc = player.location

        // Update each face's position and rotation
        CubeFace.entries.forEach { face ->
            val display = displays[face] ?: return@forEach

            // Apply rotation
            display.applyTransform(transforms[face] ?: return@forEach)

            // Update position and opacity
            display.teleport(eyeLoc)
            display.setOpacity(opacityValue)
        }
    }

    /**
     * Updates the mid-range particle effect with moderate intensity
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateMidRangeEffect(player: Player, distanceSquared: Double, fadeFactor: Float) {
        // Skip particle spawning if fade factor is too low
        if (fadeFactor < 0.05) return

        // Calculate effect intensity and distance based on player's position
        val distanceFactor = 1.0 - ((distanceSquared - closeRangeThresholdSquared) /
                (farRangeThresholdSquared - closeRangeThresholdSquared))
            .coerceIn(0.0, 1.0)
        val distanceToCenter = sqrt(distanceSquared)

        // Update particle position based on player's current position and distance to explosion
        val eyeLocation = player.eyeLocation
        val directionToExplosion = center.clone().subtract(eyeLocation).toVector().normalize()

        // Calculate perspective distance - particles appear further away as the explosion is further
        val perspectiveDistance = 2.0 + (distanceToCenter / reach) * 6.0
        val particleLocation = eyeLocation.clone().add(directionToExplosion.multiply(perspectiveDistance))

        // Scale particle count based on distance AND fade factor
        val baseParticleCount = (50 * distanceFactor).toInt().coerceAtLeast(8)
        val adjustedParticleCount = (baseParticleCount * fadeFactor).toInt().coerceAtLeast(1)

        // Scale particle size based on distance
        val particleSpread = (0.2 + (distanceFactor * 0.6)).coerceAtMost(0.9)

        player.spawnParticle(
            org.bukkit.Particle.FLASH,
            particleLocation,
            adjustedParticleCount,
            particleSpread * 0.3, // X spread
            particleSpread * 0.3, // Y spread
            particleSpread * 0.3, // Z spread
            0.0
        )
    }

    /**
     * Updates the far-range effect - distant nuclear flash
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateFarRangeEffect(player: Player, distanceSquared: Double, fadeFactor: Float) {
        // Calculate distance factor (0-1)
        val distanceFactor = 1.0 - ((distanceSquared - farRangeThresholdSquared) /
                (reachSquared - farRangeThresholdSquared))
            .coerceIn(0.0, 1.0)

        // Get direction to explosion
        val eyeLocation = player.eyeLocation
        val directionToExplosion = center.clone().subtract(eyeLocation).toVector().normalize()

        // Far distance - show a bright flash on the horizon
        val horizonDistance = 15.0 + (1.0 - distanceFactor) * 10.0
        val particleLocation = eyeLocation.clone().add(directionToExplosion.multiply(horizonDistance))

        // Use fewer particles but make them more spread out to simulate distant flash
        val particleCount = (5 * distanceFactor * fadeFactor).toInt().coerceAtLeast(1)
        val spreadFactor = 1.0 - distanceFactor // More spread for further distances

        // Add a slight vertical offset to place the flash near the horizon
        particleLocation.y += (horizonDistance * 0.1)

        // Spawn flash particles
        player.spawnParticle(
            org.bukkit.Particle.FLASH,
            particleLocation,
            particleCount,
            1.0 + spreadFactor, // Wider horizontal spread
            0.5, // Less vertical spread
            1.0 + spreadFactor, // Wider horizontal spread
            0.0
        )

        // Add a subtle glow effect
        if (fadeFactor > 0.3 && player.world.time in 13000..23000) { // More visible at night
            player.spawnParticle(
                org.bukkit.Particle.END_ROD,
                particleLocation,
                1,
                0.1,
                0.1,
                0.1,
                0.02
            )
        }
    }

    /**
     * Override the cleanup method to also handle text displays
     */
    override fun cleanup(entity: Entity) {
        // Clean up text displays if it's a player
        if (entity is Player) {
            val playerUUID = entity.uniqueId
            val displays = playerDisplays.remove(playerUUID)
            displays?.values?.forEach { it.remove() }
        }

        // Perform the standard cleanup
        super.cleanup(entity)
    }
}