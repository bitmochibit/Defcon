package me.mochibit.defcon.explosions

import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.MathFunctions
import me.mochibit.defcon.utils.MathFunctions.remap
import org.bukkit.*
import org.bukkit.block.BlockFace
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3f
import org.joml.Vector3i
import java.lang.ref.SoftReference
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.round
import kotlin.math.sin

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val shockwaveSpeed: Long = 300L,
    private val transformationRule: TransformationRule = TransformationRule()
) {

    private val maximumDistanceForAction = 4.0
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0

    private val world = center.world
    private val explosionColumns: ConcurrentLinkedQueue<Pair<Double, List<Vector3i>>> = ConcurrentLinkedQueue()

    private val executorService = ForkJoinPool.commonPool()

    private val completedExplosion = AtomicBoolean(false)

    private val treeBurner = TreeBurner(world, 100, transformationRule)
    private val chunkCache = ChunkCache.getInstance(world)

    fun explode() {
        startExplosionProcessor()

        val startTime = System.nanoTime()
        executorService.submit {
            var lastProcessedRadius = shockwaveRadiusStart

            val visitedEntities: MutableSet<Entity> = mutableSetOf()

            while (lastProcessedRadius <= shockwaveRadius) {
                // Calculate elapsed time in seconds
                val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0

                // Determine the current radius based on shockwaveSpeed (blocks per second)
                val currentRadius = (shockwaveRadiusStart + elapsedSeconds * shockwaveSpeed).toInt()

                // Skip if the radius hasn't advanced yet
                if (currentRadius <= lastProcessedRadius) {
                    Thread.yield()
                    continue
                }

                // Process new radii
                (lastProcessedRadius + 1..currentRadius).forEach { radius ->
                    val explosionPower = MathFunctions.lerp(
                        maxDestructionPower,
                        minDestructionPower,
                        radius / shockwaveRadius.toDouble()
                    )
                    val columns = generateShockwaveColumns(radius)
                    explosionColumns.add(explosionPower to columns)
                    for (location in columns) {
                        world.spawnParticle(
                            Particle.EXPLOSION_LARGE,
                            location.x.toDouble(),
                            location.y.toDouble(),
                            location.z.toDouble(), 0
                        )
                        runLater(1L) {
                            val locRef = WeakReference(
                                Location(
                                    world,
                                    location.x.toDouble(),
                                    location.y.toDouble(),
                                    location.z.toDouble()
                                )
                            )
                            val locationCursor = locRef.get() ?: return@runLater
                            val entities = world.getNearbyEntities(
                                locationCursor, 5.0,
                                (shockwaveHeight / 2).toDouble(), 5.0
                            )
                            {
                                it !in visitedEntities &&
                                        it.y in locationCursor.y - maximumDistanceForAction..(locationCursor.y + shockwaveHeight + maximumDistanceForAction)
                            }

                            if (entities.isEmpty()) return@runLater
                            // Check if the entity is within range and height bounds
                            for (entity in entities) {
                                applyExplosionEffects(entity, explosionPower.toFloat())
                                visitedEntities.add(entity)
                            }
                        }
                    }
                }

                lastProcessedRadius = currentRadius
            }
            completedExplosion.set(true)
        }
    }

    private fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        val knockback =
            Vector(entity.x - center.x, entity.y - center.y, entity.z - center.z)
                .normalize().multiply(explosionPower * 2.0)
        runLater(1L) {
            entity.velocity = knockback
        }

        if (entity !is LivingEntity) return

        runLater(1L) {
            entity.damage(explosionPower * 4.0)
        }
        if (entity !is Player) return

        val inv = 2 / explosionPower
        try {
            CameraShake(entity, CameraShakeOptions(1.6f, 0.04f, 3.7f * inv, 3.0f * inv))
        } catch (e: Exception) {
            println("Error applying CameraShake: ${e.message}")
        }

    }

    private fun startExplosionProcessor() {
        executorService.submit {
            while (explosionColumns.isNotEmpty() || !completedExplosion.get()) {
                val rColumns = explosionColumns.poll() ?: continue
                val (explosionPower, locations) = rColumns

                locations.parallelStream().forEach { location ->
                    simulateExplosion(location, explosionPower)
                }

            }
        }
    }

    private fun simulateExplosion(location: Vector3i, explosionPower: Double) {
        val baseX = location.x
        val baseY = location.y
        val baseZ = location.z

        val direction = Vector3f(
            (baseX - center.x).toFloat(),
            (baseY - center.y).toFloat(),
            (baseZ - center.z).toFloat()
        ).normalize()

        val normalizedExplosionPower = remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)

        fun rayTraceHeight(loc: Location): Int {
            return world.rayTraceBlocks(
                loc,
                Vector(0.0, -1.0, 0.0),
                30.0,
                FluidCollisionMode.ALWAYS
            )?.hitPosition?.blockY
                ?: (location.y - 30.0).toInt()
        }

        fun processHeightDifference(blockY: Int, targetY: Int) {
            val minY = minOf(blockY, targetY)
            val maxY = maxOf(blockY, targetY)
            for (y in minY..maxY) {
                processBlock(Vector3i(location.x, y, location.z), normalizedExplosionPower, direction)
            }
        }

        val nextLocation =
            Location(world, (baseX + direction.x).toDouble(), baseY.toDouble(), (baseZ + direction.z).toDouble())
        val prevLocation =
            Location(world, (baseX - direction.x).toDouble(), baseY.toDouble(), (baseZ - direction.z).toDouble())
        val leftLocation =
            Location(world, (baseX - direction.z).toDouble(), baseY.toDouble(), (baseZ + direction.x).toDouble())
        val rightLocation =
            Location(world, (baseX + direction.z).toDouble(), baseY.toDouble(), (baseZ - direction.x).toDouble())

        if (!processBlock(location, normalizedExplosionPower, direction)) return

        val previousBlockMaxY = rayTraceHeight(prevLocation)
        val nextBlockMaxY = rayTraceHeight(nextLocation)
        val leftBlockMaxY = rayTraceHeight(leftLocation)
        val rightBlockMaxY = rayTraceHeight(rightLocation)


        val heightDiffBefore = abs(baseY - previousBlockMaxY)
        if (heightDiffBefore > 0) {
            processHeightDifference(baseY, previousBlockMaxY)
        }

        val heightDiffAfter = abs(baseY - nextBlockMaxY)
        if (heightDiffAfter > 0) {
            processHeightDifference(baseY, nextBlockMaxY)
        }

        val heightDiffLeft = abs(baseY - leftBlockMaxY)
        if (heightDiffLeft > 0) {
            processHeightDifference(baseY, leftBlockMaxY)
        }

        val heightDiffRight = abs(baseY - rightBlockMaxY)
        if (heightDiffRight > 0) {
            processHeightDifference(baseY, rightBlockMaxY)
        }
    }

    private fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double,
        shockwaveDirection: Vector3f
    ): Boolean {
        val block = world.getBlockAt(blockLocation.x, blockLocation.y, blockLocation.z)
        val blockType = block.type

        // Skip processing for AIR, blacklisted, or liquid materials
        if (blockType == Material.AIR || blockType in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST || blockType in TransformationRule.LIQUID_MATERIALS) return false

        if (block.getMetadata("processedByTreeBurn").isNotEmpty())
            return false

        if (blockType.name.endsWith("_LOG") || blockType.name.endsWith("_LEAVES")) {
            treeBurner.processTreeBurn(block, normalizedExplosionPower, shockwaveDirection)
            return false
        }

        // Apply custom transformation
        val newMaterial = transformationRule.customTransformation(blockType, normalizedExplosionPower)
        BlockChanger.addBlockChange(block, newMaterial, true)

        // If there's a block above (like grass or flowers, get the transformation) and apply it
        val blockAbove = block.getRelative(BlockFace.UP)
        val blockAboveType = blockAbove.type
        if (blockAboveType != Material.AIR) {
            val newMaterialAbove = transformationRule.customTransformation(blockAboveType, normalizedExplosionPower)
            BlockChanger.addBlockChange(blockAbove, newMaterialAbove, true)
        }

        return true
    }

    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        val ringElements = calculateCircle(radius)
        // Process each point and interpolate walls
        for (pos in ringElements) {
            val highestY = chunkCache.highestBlockYAt(pos.x, pos.z)
            pos.y = highestY
        }
        return ringElements
    }

    private fun calculateCircle(radius: Int): MutableList<Vector3i> {
        val density = 4
        val steps = (2 * Math.PI * radius).toInt() * density
        val angleIncrement = 2 * Math.PI / steps

        return MutableList(steps) { i ->
            val angle = angleIncrement * i
            val x = round(center.blockX + radius * cos(angle)).toInt()
            val z = round(center.blockZ + radius * sin(angle)).toInt()
            Vector3i(x, 0, z)
        }
    }
}

class ChunkCache private constructor(
    private val world: World,
    private val maxAccessCount: Int = 20
) {
    companion object {
        private val sharedChunkCache =
            object : LinkedHashMap<Pair<Int, Int>, SoftReference<ChunkSnapshot>>(16, 0.75f, true) {
                private val MAX_SHARED_CACHE_SIZE = 100

                override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, SoftReference<ChunkSnapshot>>): Boolean {
                    return size > MAX_SHARED_CACHE_SIZE
                }
            }

        fun getInstance(world: World, maxAccessCount: Int = 20): ChunkCache {
            return ChunkCache(world, maxAccessCount)
        }
    }

    private val localCache = object : LinkedHashMap<Pair<Int, Int>, ChunkSnapshot>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ChunkSnapshot>): Boolean {
            if (size > maxAccessCount) {
                sharedChunkCache[eldest.key] = SoftReference(eldest.value) // Push evicted entries to the shared cache
                return true
            }
            return false
        }
    }

    private fun getChunkSnapshot(x: Int, z: Int): ChunkSnapshot {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val key = chunkX to chunkZ

        // Check shared cache first
        return localCache.getOrPut(key) {
            val sharedSnapshot = sharedChunkCache[key]?.get()
            if (sharedSnapshot != null) {
                return@getOrPut sharedSnapshot
            }
            val snapshot = world.getChunkAt(chunkX, chunkZ).chunkSnapshot
            sharedChunkCache[key] = SoftReference(snapshot)
            snapshot
        }
    }

    fun highestBlockYAt(x: Int, z: Int): Int {
        return getChunkSnapshot(x, z).getHighestBlockYAt(x and 15, z and 15)
    }

    fun getBlockMaterial(x: Int, y: Int, z: Int): Material {
        return getChunkSnapshot(x, z).getBlockType(x and 15, y, z and 15)
    }
}

