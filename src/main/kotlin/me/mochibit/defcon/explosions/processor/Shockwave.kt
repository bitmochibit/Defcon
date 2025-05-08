package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.*
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.SimplexNoise
import org.joml.Vector3i
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class Shockwave(
    private val center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val shockwaveSpeed: Long = 1000L,
    private val minDestructionPower: Double = 2.0,
    private val maxDestructionPower: Double = 5.0,
    private val transformationRule: TransformationRule = TransformationRule(maxDestructionPower),
) : Completable by CompletionDispatcher() {
    private val maximumDistanceForAction = 4.0
    private val world = center.world

    // Cache rule sets for faster access
    private val transformBlacklist = TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
    private val liquidMaterials = TransformationRule.LIQUID_MATERIALS
    private val attachedBlockCache = TransformationRule.ATTACHED_BLOCKS
    private val slabs = TransformationRule.SLABS
    private val walls = TransformationRule.WALLS
    private val stairs = TransformationRule.STAIRS

    // Services
    private val treeBurner = TreeBurner(world, center.toVector3i(), maxDestructionPower)
    private val chunkCache = ChunkCache.getInstance(world)
    private val rayCaster = RayCaster(world)
    private val blockChanger = BlockChanger.getInstance(world)


    // Processed rings counter for progress tracking
    private val processedRings = AtomicInteger(0)

    // Cache base directions for wall detection
    private val baseDirections = arrayOf(
        Vector3i(1, 0, 0),  // East
        Vector3i(-1, 0, 0), // West
        Vector3i(0, 0, 1),  // South
        Vector3i(0, 0, -1)  // North
    )

    private val processedBlocks = ConcurrentHashMap.newKeySet<Long>()

    private fun isBlockProcessed(x: Int, y: Int, z: Int): Boolean {
        return !processedBlocks.add(Geometry.packIntegerCoordinates(x, y, z))
    }

    fun explode(): Job {
        val blockProcessingDispatcher = Dispatchers.Default

        val ringCounter = AtomicInteger(0)

        return Defcon.instance.launch(Dispatchers.Default) {
            try {
                // Original visual/block shockwave processing at normal speed
                for (radius in radiusStart..shockwaveRadius) {
                    val radiusProgress = radius.toDouble() / shockwaveRadius.toDouble()
                    val explosionPower = MathFunctions.lerp(
                        maxDestructionPower,
                        minDestructionPower,
                        radiusProgress
                    )

                    // Generate columns for this radius and process visual effects and blocks
                    val columns = generateShockwaveCircleAsFlow(radius).buffer(128).toList()


                    // Process blocks in parallel (don't wait)
                    launch(blockProcessingDispatcher) {
                        // Split tree blocks from regular blocks
                        val (treeBlocks, nonTreeBlocks) = columns.partition { treeBurner.isTreeBlock(it) }

                        // Process tree blocks in parallel batches
                        treeBlocks.chunked(32).forEach { chunk ->
                            launch {
                                chunk.forEach { location ->
                                    treeBurner.processTreeBurn(location, explosionPower)
                                    treeBurner.processTreeBurn(treeBurner.getTreeTerrain(location), explosionPower)
                                }
                            }
                        }

                        // Process non-tree blocks in parallel batches
                        nonTreeBlocks.chunked(32).forEach { chunk ->
                            launch {
                                chunk.forEach { location ->
                                    processBlock(location, explosionPower)
                                }
                            }
                        }
                    }

                    ringCounter.incrementAndGet()
                    delay(1.milliseconds)
                }
            } finally {
                processedRings.set(ringCounter.get())
                cleanup()
            }
        }
    }

    // Fast wall detection using cached materials
    private fun detectWall(x: Int, y: Int, z: Int): Boolean {
        return baseDirections.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y, z + dir.z) == Material.AIR
        }
    }

    // Check if a block has attached blocks (signs, torches, etc)
    private fun detectAttached(x: Int, y: Int, z: Int): Boolean {
        return baseDirections.any { dir ->
            chunkCache.getBlockMaterial(x + dir.x, y + dir.y, z + dir.z) in attachedBlockCache
        }
    }

    private suspend fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double
    ) {
        val material = world.getBlockData(blockLocation.x, blockLocation.y, blockLocation.z).material
        val transformedMat = transformationRule.transformMaterial(material, normalizedExplosionPower)
        blockChanger.addBlockChange(blockLocation, transformedMat)
    }

    // Process vertical walls more efficiently with simplex noise
    private suspend fun processWall(
        blockLocation: Vector3i, normalizedExplosionPower: Double
    ) {
        // Return if already processed
        if (isBlockProcessed(blockLocation.x, blockLocation.y, blockLocation.z)) return

        val x = blockLocation.x
        val z = blockLocation.z
        val startY = blockLocation.y

        // Enhanced depth calculation
        val maxDepth = (shockwaveHeight * (0.7 + normalizedExplosionPower * 0.6)).toInt()

        for (depth in 0 until maxDepth) {
            val currentY = startY - depth

            // Stop if no longer a wall structure
            if (depth > 0 && !detectWall(x, currentY, z)) break

            val blockType = chunkCache.getBlockMaterial(x, currentY, z)

            // Skip blacklisted materials
            if (blockType in transformBlacklist || blockType == Material.AIR) continue

            // Stop at liquids
            if (blockType in liquidMaterials) break

            // Use simplex noise for material determination
            val noiseValue = (SimplexNoise.noise(x * 0.3f, currentY * 0.3f, z * 0.3f) + 1) * 0.5

            // Determine final material
            val finalMaterial = if (depth > 0 && noiseValue < normalizedExplosionPower) {
                transformationRule.transformMaterial(blockType, normalizedExplosionPower)
            } else {
                Material.AIR
            }

            // Check if we need to copy block data
            val shouldCopyData = finalMaterial in slabs || finalMaterial in walls || finalMaterial in stairs

            // Add to batch
            blockChanger.addBlockChange(
                x, currentY, z,
                finalMaterial,
                shouldCopyData,
                (finalMaterial == Material.AIR && currentY == startY) || detectAttached(x, currentY, z)
            )
        }
    }

    // Process roof/floor structures with optimized algorithm and simplex noise
    private suspend fun processRoof(
        blockLocation: Vector3i, normalizedExplosionPower: Double, maxPenetration: Int
    ) {
        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0

        // Enhanced power decay based on normalized power
        val powerDecay = 0.85 - (0.15 * (1 - normalizedExplosionPower.pow(1.5)))

        val x = blockLocation.x
        val z = blockLocation.z

        // Surface effect - always apply some surface damage even at low power
        if (normalizedExplosionPower >= 0.02 && normalizedExplosionPower < 0.05) {
            // For very low power explosions, still create surface effects
            val surfaceNoise = (SimplexNoise.noise(x * 0.5f, currentY * 0.5f, z * 0.5f) + 1) * 0.5
            if (surfaceNoise < normalizedExplosionPower * 10) {  // Scale up chance for low power
                val blockType = chunkCache.getBlockMaterial(x, currentY, z)
                if (blockType != Material.AIR && blockType !in transformBlacklist && blockType !in liquidMaterials) {
                    // Apply surface transformation
                    val surfaceMaterial = if (surfaceNoise < normalizedExplosionPower * 5) {
                        Material.AIR
                    } else {
                        transformationRule.transformMaterial(blockType, normalizedExplosionPower * 0.5)
                    }

                    val shouldCopyData =
                        surfaceMaterial in slabs || surfaceMaterial in walls || surfaceMaterial in stairs
                    blockChanger.addBlockChange(x, currentY, z, surfaceMaterial, shouldCopyData, true)
                }
            }
        }

        // Main penetration loop
        while (penetrationCount < maxPenetration && currentPower > 0.05 && currentY > 0) {
            // Calculate current position with offset
            val currentX = x
            val currentZ = z

            // Skip if already processed
            if (!isBlockProcessed(currentX, currentY, currentZ)) {
                val blockType = chunkCache.getBlockMaterial(currentX, currentY, currentZ)

                // Handle air blocks
                if (blockType == Material.AIR) {


                    val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                    val nextSolidY =
                        rayCaster.cachedRayTrace(currentX, currentY, currentZ, maxSearchDepth.toDouble())

                    if (nextSolidY < currentY - maxSearchDepth) break // Too far down

                    // Jump to next solid block
                    currentY = nextSolidY
                    currentPower *= 0.7
                }

                // Process the current block
                processRoofBlock(
                    currentX, currentY, currentZ, blockType,
                    currentPower, penetrationCount, maxPenetration
                )

                // Move down and update state
                currentY--

                // Apply power decay with slight randomization based on noise
                val noiseDecay = (SimplexNoise.noise(currentX * 0.2f, currentY * 0.2f, currentZ * 0.2f) + 1) * 0.05
                val powerDecayFactor = powerDecay + noiseDecay + (normalizedExplosionPower * 0.08)

                currentPower *= powerDecayFactor.coerceIn(0.7, 0.95)
                penetrationCount++
            } else {
                currentY--
                penetrationCount++
                continue
            }
        }
    }

    // Process individual roof blocks efficiently with simplex noise
    private suspend fun processRoofBlock(
        x: Int, y: Int, z: Int,
        blockType: Material,
        power: Double,
        penetrationCount: Int,
        maxPenetration: Int,
    ) {
        // Use simplex noise for power adjustment
        val noiseValue = (SimplexNoise.noise(x * 0.4f, y * 0.4f, z * 0.4f) + 1) * 0.5
        val adjustedPower = (power + (noiseValue * 0.2 - 0.1) * power).coerceIn(0.0, 1.0)
        val penetrationRatio = penetrationCount.toDouble() / maxPenetration

        // Fast material selection using efficient branching with noise influence
        val finalMaterial = when {
            // Surface layers - mostly air but allow some blocks to remain with very low power
            penetrationRatio < 0.3 -> {
                if (noiseValue < 0.1 && adjustedPower < 0.3) {
                    transformationRule.transformMaterial(blockType, adjustedPower * 0.5)
                } else {
                    Material.AIR
                }
            }

            // High power explosions create more cavities deeper
            adjustedPower > 0.7 && noiseValue < adjustedPower * 0.9 -> Material.AIR

            // Mid-depth with medium-high power - mix of air and debris
            penetrationRatio < 0.6 && adjustedPower > 0.5 -> {
                if (noiseValue < adjustedPower * 0.8) Material.AIR
                else transformationRule.transformMaterial(blockType, adjustedPower * 0.8)
            }

            // Deeper layers - scattered blocks/rubble pattern
            penetrationRatio >= 0.6 -> {
                if (noiseValue < 0.7 - (adjustedPower * 0.3))
                    transformationRule.transformMaterial(blockType, adjustedPower)
                else Material.AIR
            }

            // Noise-based destruction pockets
            noiseValue < adjustedPower * 0.8 -> Material.AIR

            // Some blocks remain slightly transformed
            else -> transformationRule.transformMaterial(blockType, adjustedPower * 0.6)
        }

        // Optimize physics update flags
        val updatePhysics = penetrationCount == 0 ||
                (finalMaterial != Material.AIR && noiseValue < 0.2)

        val shouldCopyData = finalMaterial in slabs ||
                finalMaterial in walls ||
                finalMaterial in stairs

        // Add to block change queue
        if (!isBlockProcessed(x, y + 1, z)) {
            val topBlock = chunkCache.getBlockMaterial(x, y + 1, z)
            if ((topBlock != Material.AIR && (topBlock in TransformationRule.PLANTS || topBlock in TransformationRule.LIGHT_WEIGHT_BLOCKS)) && topBlock !in liquidMaterials) {
                if (blockType.isFlammable) {
                    blockChanger.addBlockChange(x, y + 1, z, Material.FIRE, updateBlock = true)
                } else {
                    val topBlockMat = transformationRule.transformMaterial(topBlock, adjustedPower)
                    blockChanger.addBlockChange(x, y + 1, z, topBlockMat, shouldCopyData, updateBlock = false)
                }

            }
        }

        blockChanger.addBlockChange(x, y, z, finalMaterial, shouldCopyData, updatePhysics)
    }

    private fun generateShockwaveCircleAsFlow(radius: Int): Flow<Vector3i> = flow {
        // Get center coordinates
        val centerX = center.blockX
        val centerZ = center.blockZ

        // Special case for radius 0
        if (radius == 0) {
            val highestY = chunkCache.highestBlockYAt(centerX, centerZ)
            emit(Vector3i(centerX, highestY, centerZ))
            return@flow
        }

        // Bresenham's circle algorithm implementation
        var x = 0
        var z = radius
        var d = 1 - radius

        // Process points as we go
        while (x <= z) {
            // Process all 8-way symmetric points
            val pointsToProcess = listOf(
                Pair(x, z), Pair(z, x),
                Pair(z, -x), Pair(x, -z),
                Pair(-x, -z), Pair(-z, -x),
                Pair(-z, x), Pair(-x, z)
            )

            // Handle potential duplicates when x == z
            val uniquePoints = if (x == z) {
                pointsToProcess.take(4) // Take only half the points when x == z to avoid duplicates
            } else {
                pointsToProcess
            }

            // Process each point
            uniquePoints.forEach { (xOffset, zOffset) ->
                try {
                    // Apply center offset to coordinates
                    val worldX = centerX + xOffset
                    val worldZ = centerZ + zOffset

                    val highestY = chunkCache.highestBlockYAt(worldX, worldZ)
                    emit(Vector3i(worldX, highestY, worldZ))
                } catch (e: Exception) {
                    // Log the error but continue processing other points
                    Defcon.instance.logger.warning("Error processing point ($xOffset, $zOffset): ${e.message}")
                }
            }

            // Move to next point using Bresenham's circle algorithm
            x++
            if (d < 0) {
                d += 2 * x + 1
            } else {
                z--
                d += 2 * (x - z) + 1
            }
        }
    }


    // Clean up resources
    private fun cleanup() {

        // Clean up services
        chunkCache.cleanupCache()

        processedBlocks.clear()
        // Signal completion
        complete()
    }
}
