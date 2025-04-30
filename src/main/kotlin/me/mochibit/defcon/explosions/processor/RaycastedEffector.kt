package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.ktor.utils.io.core.*
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.ticks
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.BoundingBox
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Abstract base class for effects that use raycasting to determine visibility
 * and apply effects to entities within a given radius.
 *
 * @param center The center of the effect in the world
 * @param reach The maximum distance from the center at which the effect can be applied
 * @param raycastHeight Determines from what point relative to the center it should cast the effect
 * @param duration The duration of the effect
 * @param skyVisibilityRequired Percentage of sky that must be visible for distant effects to be seen (0.0-1.0)
 */
abstract class RaycastedEffector(
    protected val center: Location,
    protected val reach: Int,
    private val raycastHeight: Int = 100,
    protected val duration: Duration = 20.seconds,
    private val skyVisibilityRequired: Float = 0.3f
) {
    protected val world: World = center.world
    protected val reachSquared: Int = reach * reach

    // Distance thresholds for effect variations (calculated once)
    protected val closeRangeThreshold: Int = reach / 3
    protected val closeRangeThresholdSquared: Int = closeRangeThreshold * closeRangeThreshold
    protected val farRangeThreshold: Int = reach * 2 / 3
    protected val farRangeThresholdSquared: Int = farRangeThreshold * farRangeThreshold

    // Pre-calculate the effect bounding box for faster entity lookups
    private val effectBoundingBox: BoundingBox = BoundingBox.of(
        center.clone().subtract(reach.toDouble(), reach.toDouble(), reach.toDouble()),
        center.clone().add(reach.toDouble(), reach.toDouble(), reach.toDouble())
    )

    // Improved cast position calculation (stored as single object)
    private val relativeCastPos = Location(world, center.x, center.y + raycastHeight, center.z)

    // Store center as vector for more efficient calculations
    private val centerVector = center.toVector()

    // Use ConcurrentHashMap for thread safety
    protected val affectedEntities = ConcurrentHashMap<UUID, EffectorData>()

    // Tracking task for checking entities entering the reach area
    private var trackingTask: Closeable? = null

    // Multi-level caching strategy
    private val skyVisibilityCache = ConcurrentHashMap<UUID, Pair<Long, Float>>()
    private val lineOfSightCache = ConcurrentHashMap<UUID, Pair<Long, Boolean>>()

    /**
     * Data class to store effect-related information for each entity
     */
    protected data class EffectorData(
        val task: Closeable,
        val startTime: Long = System.currentTimeMillis(),
        val effectDuration: Long = 0, // Duration in milliseconds
        var currentEffectType: EffectType = EffectType.MID_RANGE // Track current effect type to detect changes
    )

    // Effect types based on distance
    protected enum class EffectType {
        CLOSE_RANGE, // Inside closeRangeThreshold
        MID_RANGE,   // Between closeRangeThreshold and farRangeThreshold
        FAR_RANGE    // Beyond farRangeThreshold
    }

    /**
     * Starts tracking entities that might come into the range of the effect
     * @param trackingDuration How long to track entities (in ticks)
     */
    open fun start(trackingDuration: Duration = duration) {
        // Set up a tracking task to check for entities entering the range
        trackingTask = intervalAsync(0.25.seconds) { // Check every 5 ticks
            getTargetEntities().forEach { entity ->
                checkAndApplyEffect(entity)
            }
        }

        runLater(duration) {
            // Stop tracking after the specified duration
            stop()
        }
    }

    /**
     * Stops tracking new entities and cleans up resources
     */
    open fun stop() {
        trackingTask?.close()
        trackingTask = null

        // Cancel all remaining effect tasks
        affectedEntities.values.forEach { effectData ->
            effectData.task.close()
        }
        affectedEntities.clear()

        // Clear caches
        skyVisibilityCache.clear()
        lineOfSightCache.clear()
    }

    /**
     * Gets all entities that this effector should consider
     * Optimized to use the bounding box for faster entity collection
     */
    protected open suspend fun getTargetEntities(): List<Entity> {
        return withContext(Defcon.instance.minecraftDispatcher) {
            // Use getNearbyEntities instead of filtering all world entities
            center.world.getNearbyEntities(effectBoundingBox).filter { entity ->
                // Quick distance check for spherical range
                entity.location.distanceSquared(center) <= reachSquared
            }
        }
    }

    /**
     * Checks if an entity should see/be affected by the effect and applies it if so
     */
    private fun checkAndApplyEffect(entity: Entity) {
        val entityId = entity.uniqueId

        // Skip already affected entities (fast path)
        if (affectedEntities.containsKey(entityId)) return

        // Quick distance check for performance (avoid creating new Location/Vector objects)
        val location = entity.location
        if (entity.world != world || !effectBoundingBox.contains(location.toVector())) {
            return
        }

        val distanceSquared = location.distanceSquared(center)
        if (distanceSquared > reachSquared) {
            return
        }

        // Determine effect type based on distance
        val effectType = determineEffectType(distanceSquared)

        // For far range effects, check if entity is in open air (if it's a player)
        if (effectType == EffectType.FAR_RANGE && entity is Player && !isPlayerInOpenAir(entity)) {
            return
        }

        // Check if the entity has line of sight to the effect source
        if (!hasLineOfSightToSource(entity)) {
            return
        }

        // Apply the effect
        applyEffect(entity, effectType)
    }

    /**
     * Determines the effect type based on distance from effect source
     * Quick constant-time check using pre-calculated thresholds
     */
    protected fun determineEffectType(distanceSquared: Double): EffectType {
        return when {
            distanceSquared <= closeRangeThresholdSquared -> EffectType.CLOSE_RANGE
            distanceSquared <= farRangeThresholdSquared -> EffectType.MID_RANGE
            else -> EffectType.FAR_RANGE
        }
    }

    /**
     * Checks if a player is in the open air with sufficient sky visibility
     * Uses caching to improve performance
     */
    protected fun isPlayerInOpenAir(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val entityId = player.uniqueId

        // Use cached value if it's recent enough
        skyVisibilityCache[entityId]?.let { (timestamp, visibility) ->
            if (now - timestamp < SKY_VISIBILITY_CACHE_DURATION) {
                return visibility >= skyVisibilityRequired
            }
        }

        // Calculate sky visibility
        val skyVisibility = calculateSkyVisibility(player)
        skyVisibilityCache[entityId] = Pair(now, skyVisibility)

        return skyVisibility >= skyVisibilityRequired
    }

    /**
     * Calculates how much sky is visible to a player (0.0-1.0)
     * Optimized to use fewer ray casts while maintaining accuracy
     */
    private fun calculateSkyVisibility(player: Player): Float {
        val location = player.eyeLocation
        val samples = 5 // Reduced from 9 for performance
        var visibleSky = 0

        // Use a cross pattern instead of a 3x3 grid (5 rays instead of 9)
        val directions = listOf(
            Vector(0.0, 1.0, 0.0),     // Straight up
            Vector(0.5, 1.0, 0.0),     // Angled NE
            Vector(-0.5, 1.0, 0.0),    // Angled SW
            Vector(0.0, 1.0, 0.5),     // Angled SE
            Vector(0.0, 1.0, -0.5)     // Angled NW
        )

        for (dir in directions) {
            val rayVector = dir.normalize()
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

        return visibleSky.toFloat() / samples
    }

    /**
     * Determines if the entity has line of sight to the effect source
     * Uses caching and optimized raycasting
     */
    protected open fun hasLineOfSightToSource(entity: Entity): Boolean {
        val now = System.currentTimeMillis()
        val entityId = entity.uniqueId

        // Use cached line of sight result if available and recent
        lineOfSightCache[entityId]?.let { (timestamp, hasLineOfSight) ->
            if (now - timestamp < LINE_OF_SIGHT_CACHE_DURATION) {
                return hasLineOfSight
            }
        }

        // Get entity eye position without creating new objects
        val entityEyeLocation = if (entity is Player) {
            entity.eyeLocation
        } else {
            // For non-players, approximate eye location
            entity.location.apply { y += entity.height / 2 }
        }

        // Compute direction vector (reuse existing vector objects)
        val entityPos = entityEyeLocation.toVector()
        val directionToSource = relativeCastPos.toVector().subtract(entityPos).normalize()

        // Check if entity is looking somewhat towards the source (relevant for players)
        val distanceSquared = entityEyeLocation.distanceSquared(center)

        if (entity is Player) {
            val lookingAngle = entityEyeLocation.direction.angle(directionToSource).toDouble()

            // Use different angle thresholds based on distance
            val maxAngle = when {
                distanceSquared <= closeRangeThresholdSquared -> PI // Close range: can see in any direction
                distanceSquared <= farRangeThresholdSquared -> PI * 0.75 // Mid range: wider angle
                else -> PI / 2 // Far range: must be somewhat facing it
            }

            if (lookingAngle > maxAngle) {
                // Player is facing away from explosion
                // For close range, we'll still check from source to entity
                val result = if (distanceSquared <= closeRangeThresholdSquared) {
                    checkRaycastFromSource(entityPos)
                } else {
                    false
                }

                // Cache the result
                lineOfSightCache[entityId] = Pair(now, result)
                return result
            }
        }

        // Check if there are blocks in the way
        val result = world.rayTraceBlocks(
            entityEyeLocation,
            directionToSource,
            reach.toDouble(),
            FluidCollisionMode.NEVER,
            true
        )

        // If no hit or hit is very close to center, entity can see the source
        val hasLineOfSight = result == null ||
                result.hitPosition.distanceSquared(centerVector) < 4.0

        // Cache the result
        lineOfSightCache[entityId] = Pair(now, hasLineOfSight)
        return hasLineOfSight
    }

    /**
     * Secondary check using raycasting from above the source down to entity
     * Optimized to use fewer rays
     */
    private fun checkRaycastFromSource(entityPos: Vector): Boolean {
        // Optimize by using only 2 rays instead of 3
        val directions = listOf(
            center.toVector().subtract(relativeCastPos.toVector()).normalize(), // Towards source
            Vector(0.0, -1.0, 0.0) // Straight down
        )

        for (direction in directions) {
            val result = world.rayTraceBlocks(
                relativeCastPos,
                direction,
                reach.toDouble(),
                FluidCollisionMode.NEVER,
                true
            ) ?: return true  // No obstacles means entity can be affected

            val hitBlock = result.hitBlock ?: return true
            val distanceToEntity = entityPos.distanceSquared(hitBlock.location.toVector())

            // If entity is close to where the ray hit, they're probably visible
            if (distanceToEntity < 9.0) {
                return true
            }
        }

        return false
    }

    /**
     * Cleans up resources for an entity
     */
    protected open fun cleanup(entity: Entity) {
        // Get and remove the effect data
        val entityUUID = entity.uniqueId
        val effectData = affectedEntities.remove(entityUUID) ?: return

        // Cancel the task
        effectData.task.close()

        // Clear cached values
        skyVisibilityCache.remove(entityUUID)
        lineOfSightCache.remove(entityUUID)
    }

    /**
     * Creates and applies an effect to the entity
     * To be implemented by subclasses
     */
    protected abstract fun applyEffect(entity: Entity, effectType: EffectType)

    companion object {
        const val SKY_VISIBILITY_CACHE_DURATION = 5000L // 5 seconds cache
        const val LINE_OF_SIGHT_CACHE_DURATION = 500L   // 0.5 seconds cache
    }
}