package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import io.ktor.util.collections.*
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
import org.joml.SimplexNoise
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.*
import kotlin.time.Duration.Companion.seconds

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

    private val worldPlayers = ConcurrentSet<Player>()

    fun explode(): Job {
        // Use a more focused dispatcher for high-priority tasks
        val highPriorityDispatcher = Dispatchers.Default
        val lowPriorityDispatcher = Dispatchers.IO

        // Use bounded channels for entity damage to prevent memory pressure
        // while keeping shockwave channel unlimited for maximum throughput
        val entityDamageChannel = Channel<Pair<List<Vector3i>, Double>>(Channel.BUFFERED)
        val blockDestructionChannel = Channel<Pair<List<Vector3i>, Double>>(Channel.BUFFERED)
        val shockWaveColumnChannel = Channel<Pair<Int, List<Vector3i>>>(Channel.UNLIMITED)


        val shockwaveColumnsJob = Defcon.instance.launch(highPriorityDispatcher) {
            for (radius in radiusStart..shockwaveRadius) {
                val columns = generateShockwaveColumns(radius)
                if (columns.isNotEmpty()) {
                    shockWaveColumnChannel.send(Pair(radius, columns))
                }

                // Small yield to allow other high-priority tasks to run
                yield()
            }
        }

        val playerListUpdaterJob = Defcon.instance.launch(lowPriorityDispatcher) {
            worldPlayers.addAll(Defcon.instance.server.onlinePlayers)
            while (true) {
                val newPlayers = Defcon.instance.server.onlinePlayers
                for (newPlayer in newPlayers) {
                    if (!worldPlayers.contains(newPlayer)) {
                        worldPlayers.add(newPlayer)
                    }
                }
                // Remove players who are no longer online
                worldPlayers.removeIf { player -> !player.isOnline }
                delay(10.seconds)
            }
        }

        // Process entity damage with high priority
        val entityJob = Defcon.instance.launch(highPriorityDispatcher) {
            for (data in entityDamageChannel) {
                processEntityDamage(data.first, data.second)
            }
        }

        // Process player effects with high priority
        val playerEffectJob = Defcon.instance.launch(highPriorityDispatcher) {
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

        // Block destruction with lower priority and batch processing
        val blockJob = Defcon.instance.launch(
            lowPriorityDispatcher +
                    treeBlocks.asContextElement(ArrayList()) +
                    nonTreeBlocks.asContextElement(ArrayList())
        ) {
            for (data in blockDestructionChannel) {
                processDestruction(data.first, data.second)
            }
        }

        // The shockwave processor job - sends to entity channel first, then block channel
        val shockwaveJob = Defcon.instance.launch(highPriorityDispatcher) {
            for (shockwaveColumns in shockWaveColumnChannel) {
                val (radius, columns) = shockwaveColumns
                val explosionPower = MathFunctions.lerp(
                    maxDestructionPower,
                    minDestructionPower,
                    radius / shockwaveRadius.toDouble()
                )
                val normalizedExplosionPower =
                    remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)

                if (columns.isNotEmpty()) {
                    // Send to entity damage channel first (priority)
                    entityDamageChannel.send(Pair(columns, normalizedExplosionPower))

                    // Then send to block destruction
                    blockDestructionChannel.send(Pair(columns, normalizedExplosionPower))
                }
                processedRings.incrementAndGet()
            }
        }

        // Completion job remains similar to original
        val completionJob = Defcon.instance.launch(lowPriorityDispatcher) {
            // Ensure remaining work is completed
            shockwaveColumnsJob.join()
            shockwaveJob.join()

            playerListUpdaterJob.cancelAndJoin()


            // Close channels in order of priority
            entityDamageChannel.close()
            playerEffectChannel.close()
            blockDestructionChannel.close()
            shockWaveColumnChannel.close()

            // Wait for all processing to complete
            entityJob.join()
            playerEffectJob.join()
            blockJob.join()

            // Mark as complete
            cleanup()
        }

        return completionJob
    }

    private suspend fun processEntityDamage(
        columns: List<Vector3i>, explosionPower: Double
    ) {
        if (columns.isEmpty()) return
        withContext(Dispatchers.IO) {
            columns.forEach { col ->
                particleLoc.x = col.x.toDouble()
                particleLoc.y = (col.y + 1).toDouble()
                particleLoc.z = col.z.toDouble()
                world.spawnParticle(Particle.EXPLOSION, particleLoc, 0)
            }
        }
        withContext(Dispatchers.IO) {
            // Skip if no players to process
            if (worldPlayers.isEmpty()) return@withContext

            // Fast spatial lookup using grid
            val spatialGrid = buildSpatialGrid(columns, 2)

            // Process only entities near players
            worldPlayers.forEach { player ->
                // Process the player itself
                val playerInRange = processEntityIfInRange(player, spatialGrid, explosionPower)
                if (!playerInRange) return@forEach

                // Get entities near the player
                val nearbyEntities = withContext(Defcon.instance.minecraftDispatcher) {
                    player.getNearbyEntities(10.0, 10.0, 10.0)
                }

                // Process each nearby entity
                nearbyEntities.forEach { entity ->
                    processEntityIfInRange(entity, spatialGrid, explosionPower)
                }
            }
        }
    }

    // Process a single entity if it's in range of any column
    private suspend fun processEntityIfInRange(
        entity: Entity,
        spatialGrid: Map<Int, List<Vector3i>>,
        explosionPower: Double
    ): Boolean {
        val entityX = entity.location.blockX
        val entityY = entity.location.blockY
        val entityZ = entity.location.blockZ

        // Get grid cell and check entities within
        val gridKey = ((entityX shr 2) shl 16) or (entityZ shr 2)
        val columnsInCell = spatialGrid[gridKey] ?: return false

        // Check if entity is near any column in the cell
        for (column in columnsInCell) {
            if (abs(entityX - column.x) <= 1 &&
                entityY >= column.y - maximumDistanceForAction &&
                entityY <= column.y + shockwaveHeight + maximumDistanceForAction &&
                abs(entityZ - column.z) <= 1
            ) {
                applyExplosionEffects(entity, explosionPower.toFloat())
                return true
            }
        }
        return false
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

    private suspend fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        // Calculate knockback efficiently
        val dx = entity.location.x - center.x
        val dy = entity.location.y - center.y
        val dz = entity.location.z - center.z

        // Damage and knockback calculations
        val baseDamage = 80.0

        // Apply velocity directly rather than creating new vector
        val knockbackPower = explosionPower
        val knockbackX = knockbackPower / dx
        val knockbackY = knockbackPower / dy + 1
        val knockbackZ = knockbackPower / dz

        val velocity = entity.velocity
        velocity.x = knockbackX
        velocity.y = knockbackY
        velocity.z = knockbackZ

        withContext(Defcon.instance.minecraftDispatcher) {
            entity.velocity = velocity
        }

        if (entity !is LivingEntity) return

        // Apply damage
        withContext(Defcon.instance.minecraftDispatcher) {
            entity.damage(baseDamage * explosionPower)
        }

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
        // Skip if power is too low - lowering threshold to allow more distant effects
        if (normalizedExplosionPower < 0.02) return

        // Enhanced penetration with exponential scaling for more realistic results
        val powerFactor = normalizedExplosionPower.pow(1.2)
        val basePenetration = (powerFactor * 12).roundToInt().coerceIn(1, 15)


        // Use simplex noise for more natural terrain-like variation
        val noiseX = blockLocation.x * 0.1
        val noiseY = blockLocation.y * 0.1
        val noiseZ = blockLocation.z * 0.1

        // Generate noise values in range [-1, 1]
        val noise = SimplexNoise.noise(noiseX.toFloat(), noiseY.toFloat(), noiseZ.toFloat())

        // Use noise for variation (transforms noise from [-1,1] to [0,1] range)
        val noiseFactor = (noise + 1) * 0.5

        // Randomize penetration with noise
        val varianceFactor = (normalizedExplosionPower * 0.6).coerceIn(0.2, 0.5)
        val distanceNormalized = 1.0 - normalizedExplosionPower.coerceIn(0.0, 1.0)

        // Use noise instead of random for more coherent patterns
        val randomOffset = (noiseFactor * 2 - 1) * basePenetration * varianceFactor * (1 - distanceNormalized * 0.5)

        // Calculate max penetration - ensure at least 1 block penetration even at low power
        val maxPenetration = (basePenetration + randomOffset).roundToInt()
            .coerceIn(1, (basePenetration * 1.4).toInt())

        // Optimized wall detection
        val isWall = detectWall(blockLocation.x, blockLocation.y, blockLocation.z) ||
                detectWall(blockLocation.x, blockLocation.y - 1, blockLocation.z)

        // Process differently based on structure type
        if (isWall) {
            processWall(blockLocation, normalizedExplosionPower)
        } else {
            processRoof(blockLocation, normalizedExplosionPower, maxPenetration)
        }
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


    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        // For small radii, use Bresenham's circle algorithm for complete coverage
        if (radius <= 20) {
            return generateBresenhamCircle(radius)
        }

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

    // Implement Bresenham's circle algorithm for complete circle coverage
    private fun generateBresenhamCircle(radius: Int): List<Vector3i> {
        val result = ArrayList<Vector3i>()
        val centerX = center.blockX
        val centerZ = center.blockZ

        var x = 0
        var z = radius
        var d = 3 - 2 * radius

        // Add initial points
        addCirclePoints(centerX, centerZ, x, z, result)

        while (z >= x) {
            x++

            if (d > 0) {
                z--
                d = d + 4 * (x - z) + 10
            } else {
                d = d + 4 * x + 6
            }

            addCirclePoints(centerX, centerZ, x, z, result)
        }

        return result
    }

    // Helper to add all 8 symmetric points of the circle
    private fun addCirclePoints(centerX: Int, centerZ: Int, x: Int, z: Int, result: ArrayList<Vector3i>) {
        // Add all 8 octants
        addPoint(centerX + x, centerZ + z, result)
        addPoint(centerX - x, centerZ + z, result)
        addPoint(centerX + x, centerZ - z, result)
        addPoint(centerX - x, centerZ - z, result)
        addPoint(centerX + z, centerZ + x, result)
        addPoint(centerX - z, centerZ + x, result)
        addPoint(centerX + z, centerZ - x, result)
        addPoint(centerX - z, centerZ - x, result)
    }

    private fun addPoint(x: Int, z: Int, result: ArrayList<Vector3i>) {
        // Get height using chunk cache
        val highestY = chunkCache.highestBlockYAt(x, z)
        result.add(Vector3i(x, highestY, z))
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
//        if (chunkCoordinates.size <= 64) { // Avoid excessive chunk loading
//            chunkCache.preloadChunks(chunkCoordinates)
//        }

        // Second pass: generate Vector3i for each point
        points.forEach { (cosVal, sinVal) ->
            val x = (center.blockX + radius * cosVal).toInt()
            val z = (center.blockZ + radius * sinVal).toInt()

            // Get height using chunk cache
            val highestY = chunkCache.highestBlockYAt(x, z)

            result.add(Vector3i(x, highestY, z))
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

        processedBlocks.clear()
        circleCache.clear()

        // Signal completion
        complete()
    }
}
