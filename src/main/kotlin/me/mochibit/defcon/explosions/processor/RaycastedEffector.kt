package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.ktor.utils.io.core.*
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.threading.scheduling.intervalAsync
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.FluidCollisionMode
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Entity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Abstract base class for effects that use raycasting to determine visibility
 * and apply effects to entities within a given radius.
 *
 * @param center The center of the effect in the world
 * @param reach The maximum distance from the center at which the effect can be applied
 * @param raycastHeight Determines from what point relative to the center it should cast the effect
 * @param duration The duration of the effect in ticks (20 ticks = 1 second)
 * @param skyVisibilityRequired Percentage of sky that must be visible for distant effects to be seen (0.0-1.0)
 */
abstract class RaycastedEffector(
    protected val center: Location,
    protected val reach: Int,
    private val raycastHeight: Int = 100,
    protected val duration: Long = 10 * 20L,
    private val skyVisibilityRequired: Float = 0.3f
) {
    protected val world: World = center.world
    protected val reachSquared: Int = reach * reach

    // Distance thresholds for effect variations
    protected val closeRangeThreshold: Int = reach / 3
    protected val closeRangeThresholdSquared: Int = closeRangeThreshold * closeRangeThreshold
    protected val farRangeThreshold: Int = reach * 2 / 3
    protected val farRangeThresholdSquared: Int = farRangeThreshold * farRangeThreshold

    // Improved cast position calculation
    private val relativeCastPos = Location(world, center.x, center.y + raycastHeight, center.z)

    // Use ConcurrentHashMap for thread safety
    protected val affectedEntities = ConcurrentHashMap<UUID, EffectorData>()

    // Tracking task for checking entities entering the reach area
    private var trackingTask: Closeable? = null

    // Cache for sky visibility checks (entity UUID to last check time and result)
    private val skyVisibilityCache = ConcurrentHashMap<UUID, Pair<Long, Float>>()

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
    open fun start(trackingDuration: Long = duration) {
        // Check all applicable entities in the world initially
        Defcon.instance.launch {
            getTargetEntities().forEach { entity ->
                if (!affectedEntities.containsKey(entity.uniqueId)) {
                    checkAndApplyEffect(entity)
                }
            }
        }

        // Set up a tracking task to check for entities entering the range
        trackingTask = intervalAsync(0L, 5L) { // Check every 5 ticks (1/4 second)
            Defcon.instance.launch {
                getTargetEntities().forEach { entity ->
                    if (!affectedEntities.containsKey(entity.uniqueId)) {
                        checkAndApplyEffect(entity)
                    }
                }
            }
        }

        // Stop tracking after the specified duration
        runLater(trackingDuration) {
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
        affectedEntities.forEach { (_, effectData) ->
            effectData.task.close()
        }
        affectedEntities.clear()

        // Clear cache
        skyVisibilityCache.clear()
    }

    /**
     * Gets all entities that this effector should consider
     * Override in subclasses to filter specific entity types
     */
    protected open suspend fun getTargetEntities(): List<Entity> {
        val entities = withContext(Defcon.instance.minecraftDispatcher) {
            world.entities.filter { entity ->
                entity.location.distanceSquared(center) <= reachSquared
            }
        }
        return entities
    }

    /**
     * Checks if an entity should see/be affected by the effect and applies it if so
     */
    protected fun checkAndApplyEffect(entity: Entity) {
        // Quick distance check first for performance
        val distanceSquared = entity.location.distanceSquared(center)
        if (entity.world != world || distanceSquared > reachSquared) {
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
     * Use caching to improve performance
     */
    protected fun isPlayerInOpenAir(player: Player): Boolean {
        val now = System.currentTimeMillis()
        val cached = skyVisibilityCache[player.uniqueId]

        // Use cached value if it's recent enough
        if (cached != null && (now - cached.first) < SKY_VISIBILITY_CACHE_DURATION) {
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
    protected fun calculateSkyVisibility(player: Player): Float {
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
     * Determines if the entity has line of sight to the effect source
     */
    protected open fun hasLineOfSightToSource(entity: Entity): Boolean {
        val entityLocation = if (entity is Player) entity.eyeLocation else entity.location.add(0.0, entity.height / 2, 0.0)
        val directionToSource = relativeCastPos.clone().subtract(entityLocation).toVector().normalize()

        // Check if entity is looking somewhat towards the source (relevant for players)
        val lookingAngle = if (entity is Player) {
            entityLocation.direction.angle(directionToSource).toDouble()
        } else {
            0.0 // Non-players are always considered to be "looking" in all directions
        }

        // Use different angle thresholds based on distance
        val distanceSquared = entityLocation.distanceSquared(center)
        val maxAngle = when {
            distanceSquared <= closeRangeThresholdSquared -> Math.PI // Close range: can see in any direction
            distanceSquared <= farRangeThresholdSquared -> Math.PI * 0.75 // Mid range: wider angle
            else -> Math.PI / 2 // Far range: must be somewhat facing it
        }

        if (lookingAngle > maxAngle && entity is Player) {
            // Player is facing away from explosion
            // For close range, we'll still check from source to entity
            return distanceSquared <= closeRangeThresholdSquared &&
                    checkRaycastFromSource(entity.location.toVector())
        }

        // Check if there are blocks in the way
        val result = world.rayTraceBlocks(
            entityLocation,
            directionToSource,
            reach.toDouble(),
            FluidCollisionMode.NEVER,
            true
        )

        // If no hit or hit is very close to center, entity can see the source
        return result == null ||
                result.hitPosition.distanceSquared(center.toVector()) < 4.0
    }

    /**
     * Secondary check using raycasting from above the source down to entity
     */
    protected fun checkRaycastFromSource(entityPos: Vector): Boolean {
        // Cast multiple rays to improve detection
        val directions = listOf(
            center.toVector().subtract(relativeCastPos.toVector()).normalize(),
            Vector(0.0, -1.0, 0.0), // Straight down
            center.toVector().subtract(entityPos).normalize() // Directly towards entity
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

        // Clear cached sky visibility if it's a player
        if (entity is Player) {
            skyVisibilityCache.remove(entityUUID)
        }
    }

    /**
     * Creates and applies an effect to the entity
     * To be implemented by subclasses
     */
    protected abstract fun applyEffect(entity: Entity, effectType: EffectType)

    companion object {
        const val SKY_VISIBILITY_CACHE_DURATION = 5000L // 5 seconds cache
    }
}