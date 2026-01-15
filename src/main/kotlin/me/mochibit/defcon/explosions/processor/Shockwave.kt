package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.observer.Completable
import me.mochibit.defcon.observer.CompletionDispatcher
import me.mochibit.defcon.transformer.material.MaterialCategories
import me.mochibit.defcon.transformer.material.MaterialTransformer
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.NMSReflectionCache
import org.bukkit.Location
import org.bukkit.Material
import org.joml.Vector3i
import kotlin.random.Random

class Shockwave(
    center: Location,
    private val radiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
    private val materialTransformer: MaterialTransformer = MaterialTransformer(),
) : Completable by CompletionDispatcher() {
    private val world = center.world
    private val centerX = center.blockX
    private val centerZ = center.blockZ
    private val centerVec = center.toVector3i()

    // Services
    private val treeBurner = TreeBurner(world, centerVec)
    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChanger.getInstance(world)

    private val worldSeaLevel = world.seaLevel
    private val worldMaxHeight = world.maxHeight
    private val seaLevelMinus3 = worldSeaLevel - 3
    private val seaLevelPlus5 = worldSeaLevel + 5

    // Pre-compute inverse radius for faster calculations
    private val invShockwaveRadius = 1.0f / shockwaveRadius.toFloat()

    // Ground level tracking for better structure detection
    private var groundLevelSum = 0L
    private var groundLevelCount = 0
    private val meanGroundLevel: Int
        get() = if (groundLevelCount > 0) (groundLevelSum / groundLevelCount).toInt() else worldSeaLevel

    @OptIn(ExperimentalCoroutinesApi::class)
    fun explode(): Job =
        Defcon.launch(Dispatchers.IO) {
            try {
                println("Shockwave starting from crater edge (radius $radiusStart) to $shockwaveRadius")
                // Shockwave processes from crater edge (radiusStart) to max radius
                // Maximum power at crater edge, decreasing outward
                val effectiveShockwaveRange = (shockwaveRadius - radiusStart).toFloat()

                var blocksProcessed = 0

                for (currentRadius in radiusStart..shockwaveRadius) {
                    if ((currentRadius - radiusStart) % 50 == 0) {
                        println("Processing shockwave radius: $currentRadius/$shockwaveRadius (blocks processed: $blocksProcessed)")
                    }

                    // Calculate progress from crater edge (radiusStart) to max radius
                    // At radiusStart: radiusProgress = 0.0 (maximum power)
                    // At shockwaveRadius: radiusProgress = 1.0 (minimum power)
                    val distanceFromCraterEdge = (currentRadius - radiusStart).toFloat()
                    val radiusProgress = distanceFromCraterEdge / effectiveShockwaveRange

                    // Invert progress so power is maximum at crater edge
                    // At crater edge (radiusStart): power = 1.0
                    // At max radius: power approaches 0.0
                    val power = 1.0f - radiusProgress

                    // Apply non-linear falloff for more realistic shockwave behavior
                    val adjustedPower =
                        when {
                            radiusProgress < 0.3f -> power

                            // Full power in inner 30%
                            radiusProgress < 0.6f -> power * power

                            // Quadratic falloff in middle
                            else -> power * power * power // Cubic falloff in outer region
                        }

                    // Process blocks in the current radius ring
                    generateShockwaveCircleBresenham(currentRadius)
                        .flowOn(Dispatchers.Default)
                        .collect { loc ->
                            blocksProcessed++
                            // getHighestBlockY returns the Y coordinate ABOVE the highest block
                            // So we need to subtract 1 to get the actual block
                            val highestY = NMSReflectionCache.getHighestBlockY(world, loc.x, loc.z, true)
                            loc.y = highestY - 1
                            val firstMaterial = NMSReflectionCache.getBlockMaterial(world, loc.x, loc.y, loc.z)
                            if (treeBurner.isTreeBlock(firstMaterial)) {
                                processTrees(loc, adjustedPower)
                            } else {
                                processBlock(loc, adjustedPower, firstMaterial)
                            }
                        }
                }
            } catch (e: Exception) {
                println("ERROR in Shockwave: ${e.message}")
                e.printStackTrace()
            } finally {
                cleanup()
            }
        }

    private suspend fun processTrees(
        location: Vector3i,
        power: Float,
    ) {
        treeBurner.processTreeBurn(location, power.toDouble())
        val terrainLocation = treeBurner.getTreeTerrain(location)
        val terrainMaterial = chunkCache.getBlockMaterialAsync(terrainLocation.x, terrainLocation.y, terrainLocation.z)
        processBlock(terrainLocation, power, terrainMaterial)
    }

    private suspend fun processBlock(
        blockLocation: Vector3i,
        power: Float, // power: 1.0 = max destruction (crater edge), 0.0 = min destruction (far from center)
        firstBlockType: Material,
    ) {
        val x = blockLocation.x
        val y = blockLocation.y
        val z = blockLocation.z

        // Pre-calculate all values once
        val randomOffset = Random.nextInt(1, 6)
        // Higher power = less height conversion (more aggressive at crater edge)
        val convertToAirMinY = (worldSeaLevel + randomOffset) + (shockwaveHeight * 0.5f * (1.0f - power)).toInt()

        // Noise parameters - stronger with higher power (closer to crater edge)
        val terrainNoiseStrength = 0.3f + power * 0.4f
        val baseTerrainBreakChance = 0.7f + power * 0.25f

        // Skylight threshold - higher power (crater edge) needs less light to destroy
        val skylightThreshold = ((1.0f - power) * 12).toInt().coerceIn(2, 15)

        // Structure detection threshold - if ground is too far above mean, it's likely a structure
        val structureHeightThreshold = meanGroundLevel + 8 // 8 blocks above mean ground level

        // Use primitive counters
        var consecutiveTerrainBlocks = 0
        var consecutiveAirBlocks = 0
        var consecutiveFluids = 0
        var consecutiveBlacklisted = 0
        var hasSeenSignificantAir = false // Track if we've seen air above (for elevated structure detection)

        for (currentY in y downTo seaLevelMinus3) {
            // Skip blocks that have been processed by TreeBurner
            if (treeBurner.isPosProcessed(x, currentY, z)) continue

            val currentBlock =
                if (currentY == y) {
                    firstBlockType
                } else {
                    chunkCache.getBlockMaterialAsync(x, currentY, z)
                }

            // Skip tree blocks - let TreeBurner handle them exclusively
            if (treeBurner.isTreeBlock(currentBlock)) continue

            // Early exit with when expression
            when (currentBlock) {
                in MaterialCategories.INDESTRUCTIBLE_BLOCKS -> {
                    if (++consecutiveBlacklisted >= 2) break
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    continue
                }

                in MaterialCategories.LIQUID_MATERIALS -> {
                    if (++consecutiveFluids >= 2) break
                    consecutiveTerrainBlocks = 0
                    consecutiveAirBlocks = 0
                    continue
                }

                Material.AIR -> {
                    if (++consecutiveAirBlocks >= 10) break
                    if (consecutiveAirBlocks >= 5) hasSeenSignificantAir = true // Mark that we've seen air above
                    consecutiveTerrainBlocks = 0
                    continue
                }

                else -> {
                    consecutiveBlacklisted = 0
                    consecutiveFluids = 0
                    consecutiveAirBlocks = 0
                }
            }

            val isTerrainBlock = currentBlock in MaterialCategories.TERRAIN_BLOCKS
            val shouldConvertToAir = currentY > convertToAirMinY

            // Check if this terrain block is part of an elevated structure (like a grass balcony)
            // It's elevated if: it's terrain, higher than mean ground level + threshold, and we've seen air above
            val isElevatedStructure = isTerrainBlock && y > structureHeightThreshold && hasSeenSignificantAir

            // Handle wall blocks with skylight detection
            if (isHeuristicallyWallBlock(x, currentY, z)) {
                consecutiveTerrainBlocks = 0
                val skylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY, z)

                when {
                    currentY > seaLevelPlus5 -> {
                        blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = false)
                    }

                    skylightLevel >= skylightThreshold -> {
                        if (Random.nextDouble() > 0.3) {
                            blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = false)
                        } else {
                            val lightInfluence = skylightLevel * 0.02f // 0.02f = 1/15 * 0.3
                            val transformedBlock =
                                materialTransformer.transformMaterial(
                                    currentBlock,
                                    1.0f - power + lightInfluence, // Convert power back to distance for transformer
                                )
                            blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                        }
                    }

                    else -> {
                        val lightInfluence = skylightLevel * 0.01f // 0.01f = 1/15 * 0.15
                        val transformedBlock =
                            materialTransformer.transformMaterial(
                                currentBlock,
                                1.0f - power + lightInfluence, // Convert power back to distance for transformer
                            )
                        blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                    }
                }
                continue
            }

            if (isTerrainBlock) {
                // If this is an elevated structure (grass balcony), treat it as a collapsible block
                if (isElevatedStructure) {
                    val skylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY, z)
                    if (Random.nextDouble() > 0.2) { // 80% chance to collapse elevated structures
                        blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = false)
                    } else {
                        val lightInfluence = skylightLevel * 0.02f
                        val transformedBlock =
                            materialTransformer.transformMaterial(
                                currentBlock,
                                1.0f - power + lightInfluence,
                            )
                        blockChanger.addBlockChange(x, currentY, z, transformedBlock)
                    }
                    continue
                }

                if (++consecutiveTerrainBlocks >= 3) break

                val heightFactor = (currentY - seaLevelMinus3).toFloat() / (y - seaLevelMinus3).coerceAtLeast(1)
                val noiseValue = generateTerrainNoise(x, currentY, z, terrainNoiseStrength)

                if (consecutiveTerrainBlocks == 1) {
                    val finalBreakChance = baseTerrainBreakChance + noiseValue - (heightFactor * 0.15f)
                    val shouldBreakTerrain =
                        shouldConvertToAir ||
                            (Random.nextDouble() < finalBreakChance && currentY > seaLevelMinus3)

                    if (shouldBreakTerrain) {
                        blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = false)
                        val adjacentNoise = generateTerrainNoise(x, currentY - 1, z, terrainNoiseStrength * 0.5f)
                        if (adjacentNoise > 0.15f) {
                            val belowMaterial = chunkCache.getBlockMaterialAsync(x, currentY - 1, z)
                            if (belowMaterial in MaterialCategories.TERRAIN_BLOCKS) {
                                blockChanger.addBlockChange(x, currentY - 1, z, Material.AIR, updateBlock = false)
                            }
                        }
                        continue
                    }
                }

                // Transform terrain with noise and light
                val skylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY, z)
                val noiseInfluence = noiseValue * 0.3f
                val lightInfluence = skylightLevel * 0.0133f // 0.0133f ≈ 1/15 * 0.2
                val transformedBlock =
                    materialTransformer.transformMaterial(
                        currentBlock,
                        1.0f - power + noiseInfluence + lightInfluence, // Convert power back for transformer
                    )
                blockChanger.addBlockChange(x, currentY, z, transformedBlock)

                // Process block above
                val aboveMaterial = chunkCache.getBlockMaterialAsync(x, currentY + 1, z)
                if (aboveMaterial != Material.AIR && !treeBurner.isTreeBlock(aboveMaterial)) {
                    val aboveSkylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY + 1, z)
                    val aboveNoise = generateTerrainNoise(x, currentY + 1, z, terrainNoiseStrength * 0.7f)
                    val aboveLightInfluence = aboveSkylightLevel * 0.01f // 1/15 * 0.15
                    val transformedAbove =
                        materialTransformer.transformMaterial(
                            aboveMaterial,
                            1.0f - power + (aboveNoise * 0.2f) + aboveLightInfluence,
                        )
                    blockChanger.addBlockChange(x, currentY + 1, z, transformedAbove)
                }
            } else {
                consecutiveTerrainBlocks = 0

                if (shouldConvertToAir) {
                    blockChanger.addBlockChange(x, currentY, z, Material.AIR, updateBlock = false)
                } else {
                    val skylightLevel = chunkCache.getSkyLightLevelAsync(x, currentY, z)
                    val blockNoise = generateTerrainNoise(x, currentY, z, terrainNoiseStrength * 0.5f)
                    val lightInfluence = skylightLevel * 0.00667f // 1/15 * 0.1
                    val transformedBlock =
                        materialTransformer.transformMaterial(
                            currentBlock,
                            1.0f - power + (blockNoise * 0.2f) + lightInfluence,
                        )
                    blockChanger.addBlockChange(x, currentY, z, transformedBlock, updateBlock = false)
                }
            }
        }
    }

    /**
     * Generates terrain noise for more natural destruction patterns.
     * Inline for better performance since it's called frequently.
     */
    @Suppress("NOTHING_TO_INLINE")
    private inline fun generateTerrainNoise(
        x: Int,
        y: Int,
        z: Int,
        strength: Float,
    ): Float {
        // Fast pseudo-random using bit mixing
        val seed = ((x * 374761393L + y * 668265263L + z * 1274126177L) and 0x7FFFFFFF).toInt()
        val random = Random(seed)

        // Generate noise octaves as Float
        val noise1 = (random.nextDouble() - 0.5).toFloat() // -0.5 to 0.5
        val noise2 = ((random.nextDouble() - 0.5) * 0.5).toFloat() // -0.25 to 0.25
        val noise3 = ((random.nextDouble() - 0.5) * 0.25).toFloat() // -0.125 to 0.125

        // Combine and normalize: total range is -0.875 to 0.875, normalize to -1 to 1
        return ((noise1 + noise2 + noise3) * 1.143f * strength).coerceIn(-1.0f, 1.0f)
    }

    private suspend fun isHeuristicallyWallBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        // Check cardinal directions, return early if we find 2 air blocks
        val east = chunkCache.getBlockMaterialAsync(x + 1, y, z) == Material.AIR
        val west = chunkCache.getBlockMaterialAsync(x - 1, y, z) == Material.AIR
        if (east && west) return true

        val south = chunkCache.getBlockMaterialAsync(x, y, z + 1) == Material.AIR
        if ((east || west) && south) return true

        val north = chunkCache.getBlockMaterialAsync(x, y, z - 1) == Material.AIR
        return ((east || west) && north) || (south && north)
    }

    /**
     * Generates all points in a complete circle ring at the given radius.
     * Uses inclusive bounds to prevent gaps at large radii.
     */
    private fun generateShockwaveCircleBresenham(radius: Int): Flow<Vector3i> =
        flow {
            // Special case for center
            if (radius == 0) {
                emit(Vector3i(centerX, worldMaxHeight, centerZ))
                return@flow
            }

            // Calculate radius bounds for the ring with tolerance to prevent gaps
            val radiusSquared = radius * radius
            // For large radii, the gap between consecutive squared radii grows
            // Use a threshold that's proportional to the radius to catch all blocks
            val threshold = radius * 2 + 1 // This ensures we don't miss blocks in the ring
            val innerBound = maxOf(0, radiusSquared - threshold)

            // Track emitted to avoid duplicates
            val emitted = mutableSetOf<Long>()

            // Scan all points in the bounding square
            for (dx in -radius..radius) {
                for (dz in -radius..radius) {
                    val distSq = dx * dx + dz * dz

                    // Check if point is in the current radius ring with tolerance
                    if (distSq > innerBound && distSq <= radiusSquared) {
                        val wx = centerX + dx
                        val wz = centerZ + dz
                        val key = (wx.toLong() shl 32) or (wz.toLong() and 0xFFFFFFFFL)
                        if (emitted.add(key)) {
                            emit(Vector3i(wx, worldMaxHeight, wz))
                        }
                    }
                }
            }
        }

    private suspend fun cleanup() {
        chunkCache.cleanup()
        complete()
        println("Shockwave completed")
    }
}
