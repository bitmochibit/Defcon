package me.mochibit.defcon.explosions.effects

import io.ktor.utils.io.core.*
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
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
    private val center: Location,
    private val reach: Int,
    private val raycastHeight: Int = 100,
    private val duration: Long = 10 * 20L, // Default duration of 10 seconds
    private val skyVisibilityRequired: Float = 0.3f // At least 30% of sky must be visible for distant explosions
) {
    private val world: World = center.world
    private val reachSquared: Int = reach * reach

    // Distance thresholds for effect variations
    private val closeRangeThreshold: Int = reach / 3
    private val closeRangeThresholdSquared: Int = closeRangeThreshold * closeRangeThreshold
    private val farRangeThreshold: Int = reach * 2 / 3
    private val farRangeThresholdSquared: Int = farRangeThreshold * farRangeThreshold

    // Improved cast position calculation
    private val relativeCastPos = Location(world, center.x, center.y + raycastHeight, center.z)

    // Use ConcurrentHashMap for thread safety
    private val affectedPlayers = ConcurrentHashMap<UUID, EffectData>()

    // Tracking task for checking players entering the reach area
    private var trackingTask: Closeable? = null

    // Cache for sky visibility checks (player UUID to last check time and result)
    private val skyVisibilityCache = ConcurrentHashMap<UUID, Pair<Long, Float>>()


    // Data for cube display effect
    private val textureScale = Vector3f(8f, 4f, 1f)
    private val cubeSize = 30f
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
    private enum class CubeFace {
        FRONT, RIGHT, BACK, LEFT, TOP, BOTTOM;
    }

    // Effect types based on distance
    private enum class EffectType {
        CLOSE_RANGE, // Inside closeRangeThreshold - full cube effect
        MID_RANGE,   // Between closeRangeThreshold and farRangeThreshold - particles with high intensity
        FAR_RANGE    // Beyond farRangeThreshold - distant glow effect and reduced particles
    }

    /**
     * Data class to store effect-related information for each player
     */
    private data class EffectData(
        val task: Closeable,
        val textDisplays: MutableMap<CubeFace, ClientSideTextDisplay> = EnumMap(CubeFace::class.java),
        val startTime: Long = System.currentTimeMillis(),
        val effectDuration: Long = 0, // Duration in milliseconds
        var currentEffectType: EffectType = EffectType.MID_RANGE // Track current effect type to detect changes
    )

    /**
     * Starts tracking players who might come into the range of the flash effect
     * @param trackingDuration How long to track players (in ticks)
     */
    fun start(trackingDuration: Long = duration) {
        // Check all players in the world initially
        world.players.forEach { player ->
            if (!affectedPlayers.containsKey(player.uniqueId)) {
                checkAndApplyEffect(player)
            }
        }

        // Set up a tracking task to check for players entering the range
        trackingTask = intervalAsync(0L, 5L) { // Check every 5 ticks (1/4 second)
            world.players.forEach { player ->
                if (!affectedPlayers.containsKey(player.uniqueId)) {
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
        affectedPlayers.forEach { (_, effectData) ->
            effectData.task.close()
            effectData.textDisplays.values.forEach { it.remove() }
        }
        affectedPlayers.clear()

        // Clear cache
        skyVisibilityCache.clear()
    }

    /**
     * Checks if a player should see the effect and applies it if so
     */
    private fun checkAndApplyEffect(player: Player) {
        // Quick distance check first for performance
        val distanceSquared = player.location.distanceSquared(center)
        if (player.world != world || distanceSquared > reachSquared) {
            return
        }

        // Determine effect type based on distance
        val effectType = determineEffectType(distanceSquared)

        // For far range explosions, check if player is in open air
        if (effectType == EffectType.FAR_RANGE && !isPlayerInOpenAir(player)) {
            return
        }

        // Check if the player has line of sight to the explosion
        if (!hasLineOfSightToExplosion(player)) {
            return
        }

        // Apply the effect
        applyFlashEffect(player, effectType)
    }

    /**
     * Determines the effect type based on distance from explosion
     */
    private fun determineEffectType(distanceSquared: Double): EffectType {
        return when {
            distanceSquared <= closeRangeThresholdSquared -> EffectType.CLOSE_RANGE
            distanceSquared <= farRangeThresholdSquared -> EffectType.MID_RANGE
            else -> EffectType.FAR_RANGE
        }
    }

    /**
     * Checks if a player is in the open air with sufficient sky visibility
     * Use caching to improve performance
     */
    private fun isPlayerInOpenAir(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val cached = skyVisibilityCache[player.uniqueId]

        // Use cached value if it's recent enough
        if (cached != null && (now - cached.first) < Companion.SKY_VISIBILITY_CACHE_DURATION) {
            return cached.second >= skyVisibilityRequired
        }

        // Calculate sky visibility
        val skyVisibility = calculateSkyVisibility(player)
        skyVisibilityCache[player.uniqueId] = Pair(now, skyVisibility)

        return skyVisibility >= skyVisibilityRequired
    }

    /**
     * Calculates how much sky is visible to a player (0.0-1.0)
     */
    private fun calculateSkyVisibility(player: Player): Float {
        val location = player.eyeLocation
        val samples = 9 // Number of sample rays to cast upward
        var visibleSky = 0

        // Cast rays in a 3x3 grid pattern above the player
        for (x in -1..1) {
            for (z in -1..1) {
                val rayVector = Vector(x * 0.5, 1.0, z * 0.5).normalize()
                val result = world.rayTraceBlocks(
                    location,
                    rayVector,
                    256.0, // Check up to world height
                    FluidCollisionMode.NEVER,
                    true
                )

                if (result == null) {
                    // Ray reached the sky
                    visibleSky++
                }
            }
        }

        return visibleSky.toFloat() / samples
    }

    /**
     * Determines if the player has line of sight to the explosion
     * Using improved detection with additional checks
     */
    private fun hasLineOfSightToExplosion(player: Player): Boolean {
        val playerEyes = player.eyeLocation
        val directionToExplosion = relativeCastPos.clone().subtract(playerEyes).toVector().normalize()

        // Check if player is looking somewhat towards the explosion
        val lookingAngle = playerEyes.direction.angle(directionToExplosion)

        // Use different angle thresholds based on distance
        val distanceSquared = playerEyes.distanceSquared(center)
        val maxAngle = when {
            distanceSquared <= closeRangeThresholdSquared -> Math.PI // Close range: can see in any direction
            distanceSquared <= farRangeThresholdSquared -> Math.PI * 0.75 // Mid range: wider angle
            else -> Math.PI / 2 // Far range: must be somewhat facing it
        }

        if (lookingAngle > maxAngle) {
            // Player is facing away from explosion
            // For close range, we'll still check from explosion to player
            return distanceSquared <= closeRangeThresholdSquared &&
                    checkRaycastFromExplosion(player.eyeLocation.toVector())
        }

        // Player is facing towards explosion, check if there are blocks in the way
        val result = world.rayTraceBlocks(
            playerEyes,
            directionToExplosion,
            reach.toDouble(),
            FluidCollisionMode.NEVER,
            true
        )

        // If no hit or hit is very close to center, player can see the explosion
        return result == null ||
                result.hitPosition.distanceSquared(center.toVector()) < 4.0
    }

    /**
     * Secondary check using raycasting from above the explosion down to player
     * Improved to handle multiple ray angles
     */
    private fun checkRaycastFromExplosion(playerPos: Vector): Boolean {
        // Cast multiple rays to improve detection
        val directions = listOf(
            center.toVector().subtract(relativeCastPos.toVector()).normalize(),
            Vector(0.0, -1.0, 0.0), // Straight down
            center.toVector().subtract(playerPos).normalize() // Directly towards player
        )

        for (direction in directions) {
            val result = world.rayTraceBlocks(
                relativeCastPos,
                direction,
                reach.toDouble(),
                FluidCollisionMode.NEVER,
                true
            ) ?: return true  // No obstacles means player can be affected

            val hitBlock = result.hitBlock ?: return true
            val distanceToPlayer = playerPos.distanceSquared(hitBlock.location.toVector())

            // If player is close to where the ray hit, they're probably visible
            if (distanceToPlayer < 9.0) {
                return true
            }
        }

        return false
    }

    /**
     * Applies the flash effect to a player with the appropriate effect type
     */
    private fun applyFlashEffect(player: Player, effectType: EffectType) {
        // Apply night vision effect with intensity based on distance
        if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION)) {
            runLater(1L) {
                // Lower amplifier for distant effects
                val amplifier = when (effectType) {
                    EffectType.CLOSE_RANGE -> 255
                    EffectType.MID_RANGE -> 200
                    EffectType.FAR_RANGE -> 150
                }

                player.addPotionEffect(
                    PotionEffect(
                        PotionEffectType.NIGHT_VISION,
                        duration.toInt(),
                        amplifier
                    )
                )
            }
        }

        val effectDurationMs = duration * 50 // Convert ticks to milliseconds
        val effectData = EffectData(
            createEffectTask(player, effectType),
            EnumMap(CubeFace::class.java),
            System.currentTimeMillis(),
            effectDurationMs,
            effectType
        )

        // Store reference to created objects
        affectedPlayers[player.uniqueId] = effectData

        // Stop the effect after duration
        runLater(duration) {
            cleanup(player)
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
            val effectData = affectedPlayers[playerUUID] ?: return@intervalAsync

            // Calculate time-based parameters
            val elapsed = System.currentTimeMillis() - effectData.startTime
            val progressFactor = elapsed.toDouble() / effectData.effectDuration

            // Calculate fade factor (1.0 at start, 0.0 at end)
            val fadeFactor = max(0.0, min(1.0, 1.0 - progressFactor)).toFloat()

            // Check if we should stop the effect (no visibility or too faded)
            if (fadeFactor < 0.05 || !hasLineOfSightToExplosion(player)) {
                cleanup(player)
                return@intervalAsync
            }

            val distanceSquared = player.location.distanceSquared(center)
            val currentEffectType = determineEffectType(distanceSquared)

            // Update effect type if changed (player moved closer/further)
            if (currentEffectType != effectData.currentEffectType) {
                // Clean up previous effect visuals
                effectData.textDisplays.values.forEach { it.remove() }
                effectData.textDisplays.clear()
                effectData.currentEffectType = currentEffectType
            }

            // Update visuals based on current effect type
            when (currentEffectType) {
                EffectType.CLOSE_RANGE -> {
                    // Close range: Use cubic text display with fading opacity
                    updateCubeDisplayEffect(player, fadeFactor)
                }
                EffectType.MID_RANGE -> {
                    // Mid range: Use particles with perspective and moderate count
                    updateMidRangeEffect(player, distanceSquared, fadeFactor)
                }
                EffectType.FAR_RANGE -> {
                    // Far range: Distant flash effect
                    if (isPlayerInOpenAir(player)) {
                        updateFarRangeEffect(player, distanceSquared, fadeFactor)
                    }
                }
            }
        }
    }

    /**
     * Updates the cube display effect with 6 faces surrounding the player, properly oriented
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateCubeDisplayEffect(player: Player, fadeFactor: Float) {
        val playerUUID = player.uniqueId
        val effectData = affectedPlayers[playerUUID] ?: return
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
            display.setOpacity(opacityValue)
        }
    }

    /**
     * Updates the mid-range particle effect with moderate intensity
     * @param fadeFactor Factor from 1.0 (full effect) to 0.0 (no effect)
     */
    private fun updateMidRangeEffect(player: Player, distanceSquared: Double, fadeFactor: Float) {
        val playerUUID = player.uniqueId
        val effectData = affectedPlayers[playerUUID] ?: return

        // Remove text displays if they exist
        if (effectData.textDisplays.isNotEmpty()) {
            effectData.textDisplays.values.forEach { it.remove() }
            effectData.textDisplays.clear()
        }

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
        val playerUUID = player.uniqueId
        val effectData = affectedPlayers[playerUUID] ?: return

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
     * Cleans up resources for a player
     */
    private fun cleanup(player: Player) {
        // Get and remove the effect data
        val playerUUID = player.uniqueId
        val effectData = affectedPlayers.remove(playerUUID) ?: return

        // Cancel the task
        effectData.task.close()

        // Remove all text displays
        effectData.textDisplays.values.forEach { it.remove() }

        // Clear cached sky visibility
        skyVisibilityCache.remove(playerUUID)
    }

    companion object {
        private const val SKY_VISIBILITY_CACHE_DURATION = 5000L // 5 seconds cache
    }
}
