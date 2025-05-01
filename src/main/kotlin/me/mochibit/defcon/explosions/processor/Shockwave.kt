package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.CameraShake
import me.mochibit.defcon.explosions.effects.CameraShakeOptions
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.utils.*
import me.mochibit.defcon.utils.MathFunctions.remap
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import kotlin.random.Random

class Shockwave(
    private val center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val shockwaveSpeed: Long = 1000L,
    private val transformationRule: TransformationRule = TransformationRule(),
) : Completable by CompletionDispatcher() {
    private val maximumDistanceForAction = 4.0
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0
    private val world = center.world

    // Cache rule sets for faster access
    private val transformBlacklist = TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
    private val liquidMaterials = TransformationRule.LIQUID_MATERIALS
    private val attachedBlockCache = TransformationRule.ATTACHED_BLOCKS
    private val slabs = TransformationRule.SLABS
    private val walls = TransformationRule.WALLS
    private val stairs = TransformationRule.STAIRS

    // Use LRU cache with fixed size to limit memory usage
    private val circleCache = LRUCache<Int, Array<CirclePoint>>(100)

    // Services
    private val treeBurner = TreeBurner(world, center.toVector3i())
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

    // For rapid particle spawning
    private val particleLoc = Location(world, 0.0, 0.0, 0.0)

    // Reusable work queues for better memory usage
    private val treeBlocks = ThreadLocal<ArrayList<Vector3i>>()
    private val nonTreeBlocks = ThreadLocal<ArrayList<Vector3i>>()
    // Data class for efficient circle point representation
    private data class CirclePoint(val cosValue: Double, val sinValue: Double)

    // LRU Cache implementation for circle points
    private class LRUCache<K, V>(private val capacity: Int) : LinkedHashMap<K, V>(capacity, 0.75f, true) {
        override fun removeEldestEntry(eldest: Map.Entry<K, V>): Boolean = size > capacity
    }


    private val playerEffectChannel = Channel<Pair<Player, Double>>(Channel.BUFFERED)
    fun explode() {
        val startTime = System.nanoTime()

        // Use a dispatcher optimized for computational work
        val explosionDispatcher = Dispatchers.Default.limitedParallelism(8)

        val mainJob = Defcon.instance.launch {
            // Create channels with appropriate capacity
            val entityDamageChannel = Channel<Pair<List<Vector3i>, Double>>(Channel.BUFFERED)
            val blockDestructionChannel = Channel<Pair<List<Vector3i>, Double>>(Channel.BUFFERED)

            // Entity processing coroutine
            val entityJob = launch(explosionDispatcher) {
                val visitedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()

                for (data in entityDamageChannel) {
                    processEntityDamage(data.first, data.second, visitedEntities)
                    // Clear periodically to prevent memory growth
                    if (visitedEntities.size > 1000) {
                        visitedEntities.clear()
                    }
                }
            }

            val playerEffectJob = launch(explosionDispatcher) {
                for (effect in playerEffectChannel) {
                    val player = effect.first
                    val explosionPower = effect.second

                    ExplosionSoundManager.playSounds(ExplosionSoundManager.DefaultSounds.ShockwaveHitSound, player)

                    val inv = ((1f / explosionPower) * 3).toFloat()
                    try {
                        CameraShake(player, CameraShakeOptions(2.6f, 0.04f, 3.7f * inv, 3.0f * inv))
                    } catch (e: Exception) {
                        println("Error applying CameraShake: ${e.message}")
                    }
                }
            }

            // Block destruction coroutine
            val blockJob = launch(
                explosionDispatcher +
                        treeBlocks.asContextElement(ArrayList()) +
                        nonTreeBlocks.asContextElement(ArrayList())
            ) {
                for (data in blockDestructionChannel) {
                    processDestruction(data.first, data.second)
                }
            }

            // Main shockwave propagation
            withContext(explosionDispatcher) {
                try {
                    var lastProcessedRadius = radiusStart

                    // Optimized speed multipliers
                    val pvpSpeedMultiplier = 3.0

                    while (lastProcessedRadius <= shockwaveRadius) {
                        val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0

                        // Calculate current wave positions
                        val currentPvpRadius =
                            (radiusStart + elapsedSeconds * shockwaveSpeed * pvpSpeedMultiplier).toInt()
                                .coerceAtMost(shockwaveRadius)

                        val currentDestructionRadius =
                            (radiusStart + elapsedSeconds * shockwaveSpeed).toInt()
                                .coerceAtMost(shockwaveRadius)

                        if (currentPvpRadius <= lastProcessedRadius) {
                            delay(1)
                            continue
                        }

                        // Process rings in batches for better throughput
                        val radiusBatch = (lastProcessedRadius + 1..currentPvpRadius).toList()

                        // Concurrently process each radius in the batch
                        radiusBatch.chunked(4).forEach { chunk ->
                            coroutineScope {
                                chunk.forEach { radius ->
                                    launch {
                                        val explosionPower = MathFunctions.lerp(
                                            maxDestructionPower,
                                            minDestructionPower,
                                            radius / shockwaveRadius.toDouble()
                                        )
                                        val normalizedExplosionPower =
                                            remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)

                                        // Use pre-computed columns if available
                                        val columns = generateShockwaveColumns(radius)

                                        if (columns.isNotEmpty()) {
                                            // PVP damage takes priority
                                            entityDamageChannel.send(Pair(columns, normalizedExplosionPower))

                                            // Process destruction if in range
                                            if (radius <= currentDestructionRadius) {
                                                blockDestructionChannel.send(Pair(columns, normalizedExplosionPower))
                                            }
                                        }

                                        processedRings.incrementAndGet()
                                    }
                                }
                            }
                        }

                        lastProcessedRadius = currentPvpRadius
                    }
                } finally {
                    // Close channels when done
                    entityDamageChannel.close()
                    blockDestructionChannel.close()

                    // Ensure remaining work is completed
                    entityJob.join()
                    blockJob.join()
                    playerEffectJob.join()

                    // Mark as complete
                    cleanup()
                }
            }
        }
    }

    private suspend fun processEntityDamage(
        columns: List<Vector3i>, explosionPower: Double, visitedEntities: MutableSet<Entity>
    ) {
        if (columns.isEmpty()) return

        withContext(Dispatchers.IO) {
            columns.chunked(30).forEach { chunk ->
                chunk.forEach { col ->
                    particleLoc.x = col.x.toDouble()
                    particleLoc.y = (col.y + 1).toDouble()
                    particleLoc.z = col.z.toDouble()
                    world.spawnParticle(Particle.EXPLOSION, particleLoc, 0)
                }
            }
        }
        withContext(Defcon.instance.minecraftDispatcher) {
            // Calculate bounding box for entity detection - more efficient than individual checks
            val bounds = calculateBoundingBox(columns, maximumDistanceForAction, shockwaveHeight)

            // Get entities within the calculated bounds
            val allEntities =
                world.getNearbyEntities(bounds.center, bounds.halfWidth, bounds.halfHeight, bounds.halfDepth) {
                    it !in visitedEntities
                }

            // Skip if no entities to process
            if (allEntities.isEmpty()) return@withContext

            // Fast spatial lookup using grid
            val spatialGrid = buildSpatialGrid(columns, 2)

            // Process entities with spatial grid for faster lookups
            allEntities.forEach { entity ->
                val entityX = entity.location.blockX
                val entityY = entity.location.blockY
                val entityZ = entity.location.blockZ

                // Get grid cell and check entities within
                val gridKey = ((entityX shr 2) shl 16) or (entityZ shr 2)
                val columnsInCell = spatialGrid[gridKey]

                if (columnsInCell != null) {
                    // Check if entity is near any column in the cell
                    for (column in columnsInCell) {
                        if (abs(entityX - column.x) <= 1 &&
                            entityY >= column.y - maximumDistanceForAction &&
                            entityY <= column.y + shockwaveHeight + maximumDistanceForAction &&
                            abs(entityZ - column.z) <= 1
                        ) {
                            applyExplosionEffects(entity, explosionPower.toFloat())
                            visitedEntities.add(entity)
                            break
                        }
                    }
                }
            }
        }
    }

    // Fast spatial grid for column lookups
    private fun buildSpatialGrid(columns: List<Vector3i>, cellSize: Int): Map<Int, List<Vector3i>> {
        val grid = HashMap<Int, MutableList<Vector3i>>(columns.size / 3)

        columns.forEach { column ->
            val gridKey = ((column.x shr cellSize) shl 16) or (column.z shr cellSize)
            grid.getOrPut(gridKey) { ArrayList() }.add(column)
        }

        return grid
    }

    // Calculate tight bounding box for entity detection
    private data class BoundingBox(
        val center: Location,
        val halfWidth: Double,
        val halfHeight: Double,
        val halfDepth: Double
    )

    private fun calculateBoundingBox(columns: List<Vector3i>, verticalPadding: Double, height: Int): BoundingBox {
        var minX = Int.MAX_VALUE
        var minY = Int.MAX_VALUE
        var minZ = Int.MAX_VALUE
        var maxX = Int.MIN_VALUE
        var maxY = Int.MIN_VALUE
        var maxZ = Int.MIN_VALUE

        // Find bounds in a single pass
        columns.forEach { col ->
            if (col.x < minX) minX = col.x
            if (col.x > maxX) maxX = col.x
            if (col.y < minY) minY = col.y
            if (col.y > maxY) maxY = col.y
            if (col.z < minZ) minZ = col.z
            if (col.z > maxZ) maxZ = col.z
        }

        // Apply padding
        minY -= verticalPadding.toInt()
        maxY += height + verticalPadding.toInt()

        // Add 1 block padding for entity collision boxes
        minX -= 1
        maxX += 1
        minZ -= 1
        maxZ += 1

        // Calculate center and half dimensions
        val centerX = (minX + maxX) / 2.0
        val centerY = (minY + maxY) / 2.0
        val centerZ = (minZ + maxZ) / 2.0

        val halfWidth = (maxX - minX) / 2.0
        val halfHeight = (maxY - minY) / 2.0
        val halfDepth = (maxZ - minZ) / 2.0

        return BoundingBox(
            Location(world, centerX, centerY, centerZ),
            halfWidth,
            halfHeight,
            halfDepth
        )
    }

    private suspend fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        // Calculate knockback efficiently
        val dx = entity.location.x - center.x
        val dy = entity.location.y - center.y
        val dz = entity.location.z - center.z
        val distance = sqrt(dx * dx + dy * dy + dz * dz)

        // Safety check
        if (distance < 0.1) return

        // Damage and knockback calculations
        val baseDamage = 80.0

        // Apply velocity directly rather than creating new vector
        val knockbackPower = explosionPower
        val knockbackX = dx * knockbackPower
        val knockbackY = dy * knockbackPower + 0.2
        val knockbackZ = dz * knockbackPower

        entity.velocity.x = knockbackX
        entity.velocity.y = knockbackY
        entity.velocity.z = knockbackZ

        if (entity !is LivingEntity) return

        // Apply damage
        entity.damage(baseDamage * explosionPower)

        if (entity is Player) {
            playerEffectChannel.send(Pair(entity, explosionPower.toDouble()))
        }
    }

    private suspend fun processDestruction(locations: List<Vector3i>, explosionPower: Double) {
        coroutineScope {
            // Reuse arrays for better memory efficiency
            val treeBlocksLocal = treeBlocks.get()
            val nonTreeBlocksLocal = nonTreeBlocks.get()
            treeBlocksLocal.clear()
            nonTreeBlocksLocal.clear()

            // Initial separation of tree and non-tree blocks
            locations.forEach { location ->
                if (treeBurner.isTreeBlock(location)) {
                    treeBlocksLocal.add(location)
                    treeBurner.getTreeTerrain(location).let { nonTreeBlocksLocal.add(it) }
                } else {
                    nonTreeBlocksLocal.add(location)
                }
            }

            // Process tree blocks first
            if (treeBlocksLocal.isNotEmpty()) {
                launch {
                    treeBlocksLocal.chunked(100).forEach { chunk ->
                        chunk.forEach { treeBlock ->
                            treeBurner.processTreeBurn(treeBlock, explosionPower)
                        }
                    }
                }
            }

            // Process regular blocks in parallel batches
            nonTreeBlocksLocal.chunked(1000).map { chunk ->
                launch {
                    chunk.forEach { location ->
                        processBlock(location, explosionPower)
                    }
                }
            }.joinAll()
        }
    }

    private suspend fun processBlock(
        blockLocation: Vector3i,
        normalizedExplosionPower: Double
    ) {
        // Skip if power is too low
        if (normalizedExplosionPower < 0.05) return

        // Enhanced penetration with exponential scaling for more realistic results
        val powerFactor = normalizedExplosionPower.pow(1.2)
        val basePenetration = (powerFactor * 12).roundToInt().coerceIn(1, 15)

        // Create deterministic but varied penetration based on position
        val positionSeed = (blockLocation.x * 73) xor (blockLocation.z * 31) xor (blockLocation.y * 13)
        val random = Random(positionSeed)

        // Randomize penetration
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val distanceNormalized = 1.0 - normalizedExplosionPower.coerceIn(0.0, 1.0)
        val randomOffset =
            (random.nextDouble() * 2 - 1) * basePenetration * varianceFactor * (1 - distanceNormalized * 0.5)

        // Calculate max penetration
        val maxPenetration = (basePenetration + randomOffset).roundToInt()
            .coerceIn(max(1, (basePenetration * 0.7).toInt()), (basePenetration * 1.4).toInt())

        // Optimized wall detection
        val isWall = detectWall(blockLocation.x, blockLocation.y, blockLocation.z) ||
                detectWall(blockLocation.x, blockLocation.y - 1, blockLocation.z)

        // Process differently based on structure type
        if (isWall) {
            processWall(blockLocation, normalizedExplosionPower, random)
        } else {
            processRoof(blockLocation, normalizedExplosionPower, maxPenetration, random)
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

    // Process vertical walls more efficiently
    private suspend fun processWall(
        blockLocation: Vector3i, normalizedExplosionPower: Double, random: Random
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

            // Determine final material
            val finalMaterial = if (depth > 0 && random.nextDouble() < normalizedExplosionPower) {
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

    // Process roof/floor structures with optimized algorithm
    private suspend fun processRoof(
        blockLocation: Vector3i, normalizedExplosionPower: Double, maxPenetration: Int, random: Random
    ) {
        var currentY = blockLocation.y
        var currentPower = normalizedExplosionPower
        var penetrationCount = 0

        // Enhanced power decay based on normalized power
        val powerDecay = 0.85 - (0.15 * (1 - normalizedExplosionPower.pow(1.5)))

        val x = blockLocation.x
        val z = blockLocation.z

        // Create horizontal offsets for irregular patterns
        val maxOffset = ((normalizedExplosionPower * 3) + 1).toInt().coerceAtLeast(1)

        // Pre-generate offsets - more efficient than allocating during iteration
        val offsetX = IntArray(maxPenetration)
        val offsetZ = IntArray(maxPenetration)

        for (i in 0 until maxPenetration) {
            val levelVariance = min(1.0, i * 0.15 + normalizedExplosionPower * 0.2)
            if (random.nextDouble() < 0.3 + levelVariance) {
                offsetX[i] = random.nextInt(-maxOffset, maxOffset + 1)
                offsetZ[i] = random.nextInt(-maxOffset, maxOffset + 1)
            }
        }

        // Main penetration loop
        while (penetrationCount < maxPenetration && currentPower > 0.08 && currentY > 0) {
            // Calculate current position with offset
            val currentX = if (penetrationCount < offsetX.size) x + offsetX[penetrationCount] else x
            val currentZ = if (penetrationCount < offsetZ.size) z + offsetZ[penetrationCount] else z

            // Skip if already processed
            if (isBlockProcessed(currentX, currentY, currentZ)) {
                val blockType = chunkCache.getBlockMaterial(currentX, currentY, currentZ)

                // Handle special cases
                if (blockType in transformBlacklist || blockType in liquidMaterials) {
                    // Try continuing in straight line if offset led to invalid block
                    if (currentX != x || currentZ != z) {
                        val straightBlockType = chunkCache.getBlockMaterial(x, currentY, z)
                        if (straightBlockType !in transformBlacklist &&
                            straightBlockType !in liquidMaterials &&
                            straightBlockType != Material.AIR
                        ) {
                            processRoofBlock(
                                x, currentY, z, straightBlockType,
                                currentPower, penetrationCount, maxPenetration, random
                            )
                        }
                    }
                    break
                }

                // Handle air blocks
                if (blockType == Material.AIR) {
                    // Try straight line if offset led to air
                    if (currentX != x || currentZ != z) {
                        val straightBlockType = chunkCache.getBlockMaterial(x, currentY, z)
                        if (straightBlockType != Material.AIR) {
                            processRoofBlock(
                                x, currentY, z, straightBlockType,
                                currentPower, penetrationCount, maxPenetration, random
                            )
                        }
                    }

                    // Enhanced search for solid blocks
                    val searchRadius = (normalizedExplosionPower * 4).toInt().coerceAtLeast(1)
                    var foundSolid = false

                    val searchAttempts = (3 + normalizedExplosionPower.pow(2) * 3).toInt()
                    if (random.nextDouble() < 0.6 + normalizedExplosionPower * 0.2 && searchRadius > 1) {
                        // Try to find nearby solid blocks
                        for (attempt in 0 until searchAttempts) {
                            val randX = currentX + random.nextInt(-searchRadius, searchRadius + 1)
                            val randZ = currentZ + random.nextInt(-searchRadius, searchRadius + 1)

                            val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                            val solidY = rayCaster.cachedRayTrace(randX, currentY, randZ, maxSearchDepth.toDouble())

                            if (solidY > currentY - maxSearchDepth) {
                                // Process this new position
                                if (!isBlockProcessed(randX, solidY, randZ)) {
                                    processRoofBlock(
                                        randX, solidY, randZ,
                                        chunkCache.getBlockMaterial(randX, solidY, randZ),
                                        currentPower * 0.8,
                                        penetrationCount,
                                        maxPenetration,
                                        random
                                    )
                                    foundSolid = true
                                    break
                                }
                            }
                        }
                    }

                    if (!foundSolid) {
                        // Try to find next solid block directly below
                        val maxSearchDepth = (10.0 + normalizedExplosionPower * 15.0).toInt()
                        val nextSolidY =
                            rayCaster.cachedRayTrace(currentX, currentY, currentZ, maxSearchDepth.toDouble())

                        if (nextSolidY < currentY - maxSearchDepth) break // Too far down

                        // Jump to next solid block
                        currentY = nextSolidY
                        currentPower *= 0.7
                        continue
                    } else {
                        // Move on after processing alternative path
                        currentY--
                        penetrationCount++
                        currentPower *= powerDecay
                        continue
                    }
                }

                // Process the current block
                processRoofBlock(
                    currentX, currentY, currentZ, blockType,
                    currentPower, penetrationCount, maxPenetration, random
                )

                // Move down and update state
                currentY--

                // Apply power decay with slight randomization
                val powerDecayFactor = powerDecay +
                        (random.nextDouble() * 0.1 - 0.05) +
                        (normalizedExplosionPower * 0.08)

                currentPower *= powerDecayFactor.coerceIn(0.7, 0.95)
                penetrationCount++
            } else {
                currentY--
                penetrationCount++
                continue
            }
        }
    }

    // Process individual roof blocks efficiently
    private suspend fun processRoofBlock(
        x: Int, y: Int, z: Int,
        blockType: Material,
        power: Double,
        penetrationCount: Int,
        maxPenetration: Int,
        random: Random
    ) {
        // Add randomness to power with minimal calculation
        val adjustedPower = (power + (random.nextDouble() * 0.2 - 0.1) * power).coerceIn(0.0, 1.0)
        val penetrationRatio = penetrationCount.toDouble() / maxPenetration

        // Fast material selection using efficient branching
        val finalMaterial = when {
            // Surface layers - always air
            penetrationRatio < 0.3 -> Material.AIR

            // High power explosions create more cavities deeper
            adjustedPower > 0.7 && random.nextDouble() < adjustedPower * 0.9 -> Material.AIR

            // Mid-depth with medium-high power - mix of air and debris
            penetrationRatio < 0.6 && adjustedPower > 0.5 -> {
                if (random.nextDouble() < adjustedPower * 0.8) Material.AIR
                else transformationRule.transformMaterial(blockType, adjustedPower * 0.8)
            }

            // Deeper layers - scattered blocks/rubble pattern
            penetrationRatio >= 0.6 -> {
                if (random.nextDouble() < 0.7 - (adjustedPower * 0.3))
                    transformationRule.transformMaterial(blockType, adjustedPower)
                else Material.AIR
            }

            // Random destruction pockets
            random.nextDouble() < adjustedPower * 0.8 -> Material.AIR

            // Some blocks remain slightly transformed
            else -> transformationRule.transformMaterial(blockType, adjustedPower * 0.6)
        }

        // Optimize physics update flags
        val updatePhysics = penetrationCount == 0 ||
                (finalMaterial != Material.AIR && random.nextDouble() < 0.2)

        val shouldCopyData = finalMaterial in slabs ||
                finalMaterial in walls ||
                finalMaterial in stairs

        // Add to block change queue
        blockChanger.addBlockChange(x, y, z, finalMaterial, shouldCopyData, updatePhysics)
    }

    // Generate shockwave columns with optimized algorithm
    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        // Retrieve from cache if available
        circleCache[radius]?.let { cachedPoints ->
            // Reuse cached points but generate new Vector3i list
            return generateColumnsFromCirclePoints(cachedPoints, radius)
        }

        // Calculate appropriate point density based on radius
        val circumference = 2 * Math.PI * radius
        val density = if (radius <= 15) {
            6 // Higher density for small explosions
        } else if (radius <= 50) {
            4 // Medium density
        } else {
            max(2, min(3, 200 / radius)) // Lower density for large explosions
        }

        val steps = (circumference * density).toInt().coerceAtLeast(8)
        val angleIncrement = 2 * Math.PI / steps

        // Pre-calculate sin/cos values
        val circlePoints = Array(steps) { i ->
            val angle = angleIncrement * i
            CirclePoint(cos(angle), sin(angle))
        }

        // Cache for future use
        circleCache[radius] = circlePoints

        // Generate columns from circle points
        return generateColumnsFromCirclePoints(circlePoints, radius)
    }

    // Generate columns from cached circle points
    private fun generateColumnsFromCirclePoints(points: Array<CirclePoint>, radius: Int): List<Vector3i> {
        // Pre-allocate result array with exact size
        val result = ArrayList<Vector3i>(points.size)

        // Prepare for batch chunk loading
        val chunkCoordinates = HashSet<Long>()

        // First pass: identify all chunks that need to be loaded
        points.forEach { (cosVal, sinVal) ->
            val x = (center.blockX + radius * cosVal).toInt()
            val z = (center.blockZ + radius * sinVal).toInt()

            // Pack chunk coordinates for efficient storage
            val chunkKey = (x shr 4).toLong() shl 32 or ((z shr 4).toLong() and 0xFFFFFFFFL)
            chunkCoordinates.add(chunkKey)
        }

        // Batch load all needed chunks
        if (chunkCoordinates.size <= 64) { // Avoid excessive chunk loading
            chunkCache.preloadChunks(chunkCoordinates)
        }

        // Second pass: generate Vector3i for each point
        points.forEach { (cosVal, sinVal) ->
            val x = (center.blockX + radius * cosVal).toInt()
            val z = (center.blockZ + radius * sinVal).toInt()

            // Get height using chunk cache
            val highestY = chunkCache.highestBlockYAt(x, z)

            // Only add if valid Y position
            if (highestY > 0) {
                result.add(Vector3i(x, highestY, z))
            }
        }

        return result
    }

    // Clean up resources
    private fun cleanup() {

        // Clean up services
        chunkCache.cleanupCache()

        // Release cached data when no longer needed
        treeBlocks.remove()
        nonTreeBlocks.remove()
        // Signal completion
        complete()
    }
}
