package me.mochibit.defcon.explosions.effects

import io.ktor.utils.io.core.*
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.Color
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Display
import org.bukkit.entity.Player
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector
import org.joml.Matrix4f
import org.joml.Quaternionf
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.math.min

/**
 * BlindFlashEffect is a class that represents a visual effect of a flash
 * explosion in a Minecraft world, with effects that vary based on distance
 * @param center The center of the explosion effect in the world.
 * @param reach The maximum distance from the center at which the effect can be seen.
 * @param raycastHeight Determines from what point relative to the center it should cast the effect
 * @param duration The duration of the effect in ticks (20 ticks = 1 second)
 */
class BlindFlashEffect(
    private val center: Location,
    private val reach: Int,
    private val raycastHeight: Int = 100,
    private val duration: Long = 10 * 20L // Default duration of 10 seconds
) {
    private val world: World = center.world
    private val reachSquared: Int = reach * reach
    private val closeRangeThreshold: Int = reach / 3
    private val closeRangeThresholdSquared: Int = closeRangeThreshold * closeRangeThreshold
    private val relativeCastPos = Location(world, center.x, center.y + raycastHeight, center.z)

    // Use ConcurrentHashMap for thread safety
    private val affectedPlayers = ConcurrentHashMap<Player, EffectData>()

    // Tracking task for checking players entering the reach area
    private var trackingTask: Closeable? = null

    private val textureScale = Vector3f(8f, 4f, 1f)
    private val cubeSize = 30f
    private val textureCenter = Vector3f(textureScale.x /2, textureScale.y /2, 0f)

    private val transforms = mapOf(
        CubeFace.BACK to Matrix4f() // Back
            .rotate(Quaternionf())
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale)
        ,

        CubeFace.RIGHT to Matrix4f() // Right
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 1))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x * cubeSize)
        ,

        CubeFace.FRONT to Matrix4f() // Front
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 2))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x)
        ,
        CubeFace.LEFT to Matrix4f() // Left
            .rotate(Quaternionf().rotateY(Math.PI.toFloat() / 2 * 3))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.x * cubeSize)
        ,

        CubeFace.TOP to Matrix4f() // TOP
            .rotate(Quaternionf().rotateX(Math.PI.toFloat() / 2))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.y * cubeSize)
        ,
        CubeFace.BOTTOM to Matrix4f() // BOTTOM
            .rotate(Quaternionf().rotateX(-Math.PI.toFloat() / 2 ))
            .scale(cubeSize, cubeSize, 1f)
            .translate(-.5f, -.5f, -cubeSize/2)
            .translate(-0.1f + .5f, -.5f + .5f, 0f)
            .scale(textureScale.x, textureScale.y, textureScale.y * cubeSize)
    )

    // Face positions for the cube
    private enum class CubeFace {
        FRONT, RIGHT, BACK, LEFT, TOP, BOTTOM;
    }


    /**
     * Data class to store effect-related information for each player
     */
    private data class EffectData(
        val task: Closeable,
        val textDisplays: MutableMap<CubeFace, ClientSideTextDisplay> = EnumMap(CubeFace::class.java),
        val startTime: Long = System.currentTimeMillis(),
        val effectDuration: Long = 0 // Duration in milliseconds
    )

    /**
     * Starts tracking players who might come into the range of the flash effect
     * @param trackingDuration How long to track players (in ticks)
     */
    fun start(trackingDuration: Long = duration) {
        // Check all players in the world initially
        world.players.forEach { player ->
            if (!affectedPlayers.containsKey(player)) {
                checkAndApplyEffect(player)
            }
        }

        // Set up a tracking task to check for players entering the range
        trackingTask = intervalAsync(0L, 5L) { // Check every 5 ticks (1/4 second)
            world.players.forEach { player ->
                if (!affectedPlayers.containsKey(player)) {
                    checkAndApplyEffect(player)
                }
            }
        }

        // Stop tracking after the specified duration
        runLater(trackingDuration) {
            stop()
        }
    }

    /**
     * Stops tracking new players and cleans up resources
     */
    fun stop() {
        trackingTask?.close()
        trackingTask = null

        // Cancel all remaining effect tasks and remove displays
        affectedPlayers.forEach { (player, effectData) ->
            effectData.task.close()
            effectData.textDisplays.values.forEach { it.remove() }
        }
        affectedPlayers.clear()
    }

    /**
     * Checks if a player should see the effect and applies it if so
     */
    private fun checkAndApplyEffect(player: Player) {
        // Quick distance check first for performance
        if (player.world != world || player.location.distanceSquared(center) > reachSquared) {
            return
        }

        // Check if the player has line of sight to the explosion
        if (!hasLineOfSightToExplosion(player)) {
            return
        }

        // Apply the effect
        applyFlashEffect(player)
    }

    /**
     * Determines if the player has line of sight to the explosion
     */
    private fun hasLineOfSightToExplosion(player: Player): Boolean {
        // First check: Direct ray cast from player eye to explosion center
        val playerEyes = player.eyeLocation
        val directionToExplosion = center.clone().subtract(playerEyes).toVector().normalize()

        // Check if player is looking somewhat towards the explosion
        val lookingAngle = playerEyes.direction.angle(directionToExplosion)
        if (lookingAngle > Math.PI / 2) { // Player is facing away from explosion
            // Try a second ray cast from explosion to player to handle cases
            // where the player might still be affected (e.g., very close to explosion)
            return checkRaycastFromExplosion(player.eyeLocation.toVector())
        }

        // Player is facing somewhat towards explosion, check if there are blocks in the way
        val result = world.rayTraceBlocks(
            playerEyes,
            directionToExplosion,
            reach.toDouble(),
            FluidCollisionMode.NEVER,
            true
        )

        // If no hit or hit is very close to center, player can see the explosion
        return result == null ||
                result.hitPosition.distanceSquared(center.toVector()) < 3.0
    }

    /**
     * Secondary check using raycasting from above the explosion down to player
     */
    private fun checkRaycastFromExplosion(playerPos: Vector): Boolean {
        // Direction from cast position to center, then to player's general area
        val direction = center.toVector().subtract(relativeCastPos.toVector()).normalize()

        val result = world.rayTraceBlocks(
            relativeCastPos,
            direction,
            reach.toDouble(),
            FluidCollisionMode.NEVER,
            true
        ) ?: return true  // No obstacles means player can be affected

        val hitBlock = result.hitBlock ?: return true
        val distanceToPlayer = playerPos.distanceSquared(hitBlock.location.toVector())

        // If player is close to the obstacle that was hit, they're likely behind cover
        return distanceToPlayer > 4.0
    }

    /**
     * Applies the flash effect to a player
     */
    private fun applyFlashEffect(player: Player) {
        // Apply night vision effect
        if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            runLater(1L) {
                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        duration.toInt(),
                        255
                    )
                )
            }
        }

        val effectDurationMs = duration * 50 // Convert ticks to milliseconds
        val effectData = EffectData(
            createEffectTask(player),
            EnumMap(CubeFace::class.java),
            System.currentTimeMillis(),
            effectDurationMs
        )

        // Store reference to created objects
        affectedPlayers[player] = effectData

        // Stop the effect after duration
        runLater(duration) {
            cleanup(player)
        }
    }

    /**
     * Creates the appropriate visual effect task based on distance
     */
    private fun createEffectTask(player: Player): Closeable {
        return intervalAsync(0L, 2L) {
            // Skip if player left the world or game
            if (!player.isOnline || player.world != world || !hasLineOfSightToExplosion(player)) {
                cleanup(player)
                return@intervalAsync
            }

            val effectData = affectedPlayers[player] ?: return@intervalAsync
            val elapsed = System.currentTimeMillis() - effectData.startTime
            val progressFactor = elapsed.toDouble() / effectData.effectDuration

            // Calculate fade factor (1.0 at start, 0.0 at end)
            val fadeFactor = max(0.0, min(1.0, 1.0 - progressFactor)).toFloat()

            val distanceSquared = player.location.distanceSquared(center)

            // Check if player moved in/out of close range and update effect type
            if (distanceSquared <= closeRangeThresholdSquared) {
                // Close range: Use cubic text display with fading opacity
                updateCubeDisplayEffect(player, fadeFactor)
            } else {
                // Distance range: Use particles with perspective and decreasing count
                updateDistanceEffect(player, distanceSquared, fadeFactor)
            }
        }
    }

    /**
     * Updates the cube display effect with 6 faces surrounding the player, properly oriented
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateCubeDisplayEffect(player: Player, fadeFactor: Float) {
        val effectData = affectedPlayers[player] ?: return
        val displays = effectData.textDisplays

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
            when (face) {
                CubeFace.FRONT -> display.setColor(Color.RED)
                CubeFace.BACK -> display.setColor(Color.BLUE)

                CubeFace.LEFT -> display.setColor(Color.GREEN)
                CubeFace.RIGHT -> display.setColor(Color.YELLOW)

                CubeFace.TOP -> display.setColor(Color.FUCHSIA)
                CubeFace.BOTTOM -> display.setColor(Color.PURPLE)
            }
//            display.setOpacity(opacityValue)
        }
    }


    /**
     * Updates the distance-based particle effect with decreasing intensity
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateDistanceEffect(player: Player, distanceSquared: Double, fadeFactor: Float) {
        val effectData = affectedPlayers[player] ?: return

        // Remove text displays if they exist (player moved from close to far range)
        if (effectData.textDisplays.isNotEmpty()) {
            effectData.textDisplays.values.forEach { it.remove() }
            effectData.textDisplays.clear()
        }

        // Skip particle spawning if fade factor is too low
        if (fadeFactor < 0.05) return

        // Calculate effect intensity and distance based on player's position
        val distanceFactor = 1.0 - (distanceSquared / reachSquared).coerceAtMost(1.0)
        val distanceToCenter = Math.sqrt(distanceSquared)

        // Update particle position based on player's current position and distance to explosion
        val currentEyeLocation = player.eyeLocation.clone()
        val directionToExplosion = center.clone().subtract(currentEyeLocation).toVector().normalize()

        // Calculate perspective distance - particles appear further away as the explosion is further
        val perspectiveDistance = 1.5 + (distanceToCenter / reach) * 5.0
        val particleLocation = currentEyeLocation.add(directionToExplosion.multiply(perspectiveDistance))

        // Scale particle count based on distance AND fade factor (gradually decreasing)
        val baseParticleCount = (60 * distanceFactor).toInt().coerceAtLeast(10)
        val adjustedParticleCount = (baseParticleCount * fadeFactor).toInt().coerceAtLeast(1)

        // Scale particle size based on distance (explosion appears larger when closer)
        val particleSpread = (0.2 + (distanceFactor * 0.8)).coerceAtMost(1.0)

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
     * Cleans up resources for a player
     */
    private fun cleanup(player: Player) {
        // Get and remove the effect data
        val effectData = affectedPlayers.remove(player) ?: return

        // Cancel the task
        effectData.task.close()

        // Remove all text displays
        effectData.textDisplays.values.forEach { it.remove() }
    }
}