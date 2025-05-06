package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.Location
import org.bukkit.Material
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlin.math.*

class Crater(
    val center: Location,
    private val radiusX: Int,
    private val radiusY: Int, // This will be depth for the paraboloid
    private val radiusZ: Int,
    private val transformationRule: TransformationRule,
    private val destructionHeight: Int,
) {
    private val world = center.world
    private val chunkCache = ChunkCache.getInstance(world)
    private val centerX = center.blockX
    private val centerY = min(center.blockY, world.seaLevel)
    private val centerZ = center.blockZ
    private val maxRadius = maxOf(radiusX, radiusZ)

    // Pre-calculate bounding box for optimization
    private val minX = centerX - radiusX - 2
    private val maxX = centerX + radiusX + 2
    private val minZ = centerZ - radiusZ - 2
    private val maxZ = centerZ + radiusZ + 2
    private val minY = max(centerY - radiusY, world.minHeight)
    private val maxY = min(centerY + destructionHeight, world.maxHeight - 1)

    // Pre-calculated squared radiuses for faster distance checks
    private val radiusXSquared = radiusX * radiusX.toDouble()
    private val radiusZSquared = radiusZ * radiusZ.toDouble()

    // Maximum depth for scorch ray casting
    private val maxScorchRayDepth = 20

    // Ordered from least to most scorched
    private val scorchMaterials = listOf(
        Material.TUFF,
        Material.DEEPSLATE,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.COAL_BLOCK,
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
    )

    private val blockChanger = BlockChanger.getInstance(world)

    suspend fun create(): Int {
        try {
            val effectiveRadius = createCrater()
            return effectiveRadius
        } catch (e: Exception) {
            e.printStackTrace()
            return max(radiusX, radiusZ)
        } finally {
            chunkCache.cleanupCache()
        }
    }

    private suspend fun createCrater(): Int = coroutineScope {
        // Generate paraboloid crater shape - optimized to use a map of columns
        val craterShape = generateParaboloidShape()

        // Apply changes to the world
        applyChanges(craterShape)
    }

    /**
     * Generates a paraboloid crater shape more efficiently
     * Returns a map of (x,z) coordinates to the floor Y of paraboloid in that column
     */
    private fun generateParaboloidShape(): Map<Long, Int> {
        // Use packed long keys instead of Pair objects to reduce memory usage
        val craterShape = HashMap<Long, Int>((maxX - minX + 1) * (maxZ - minZ + 1) / 2)
        val invRadiusXSquared = 1.0 / radiusXSquared
        val invRadiusZSquared = 1.0 / radiusZSquared

        // Compute the elliptical boundary once and check against it
        for (x in minX..maxX) {
            val xDist = x - centerX
            val xComponent = xDist * xDist * invRadiusXSquared

            if (xComponent > 1.0) continue

            for (z in minZ..maxZ) {
                val zDist = z - centerZ
                val zComponent = zDist * zDist * invRadiusZSquared

                val normalizedDistSquared = xComponent + zComponent

                // Only process points inside the elliptical boundary
                if (normalizedDistSquared <= 1.0) {
                    // Calculate depth at this point (paraboloid equation)
                    val depth = radiusY * (1.0 - normalizedDistSquared)
                    val craterFloorY = (centerY - depth).roundToInt()

                    // Store the floor Y coordinate if it's within valid range
                    if (craterFloorY >= minY) {
                        craterShape[Geometry.packCoordinates(x, z)] = craterFloorY
                    }
                }
            }
        }

        return craterShape
    }

    private suspend fun applyChanges(craterShape: Map<Long, Int>): Int = coroutineScope {
        // Step 1: Create the crater hole in parallel chunks
        val chunkSize = 16
        val chunks = mutableListOf<Pair<Int, Int>>()

        for (chunkX in minX / chunkSize..maxX / chunkSize) {
            for (chunkZ in minZ / chunkSize..maxZ / chunkSize) {
                chunks.add(Pair(chunkX, chunkZ))
            }
        }

        // Process chunks in parallel
        val chunkJobs = chunks.map { (chunkX, chunkZ) ->
            async {
                processChunk(chunkX, chunkZ, chunkSize, craterShape)
            }
        }

        // Wait for all chunk processing to complete
        chunkJobs.awaitAll()

        // Step 2 & 3: Apply scorching effects
        val effectiveRadius = applyScorchingEffects(craterShape)

        effectiveRadius
    }

    private suspend fun processChunk(chunkX: Int, chunkZ: Int, chunkSize: Int, craterShape: Map<Long, Int>) {
        val startX = chunkX * chunkSize
        val startZ = chunkZ * chunkSize
        val endX = min(startX + chunkSize - 1, maxX)
        val endZ = min(startZ + chunkSize - 1, maxZ)

        for (x in startX..endX) {
            for (z in startZ..endZ) {
                val key = Geometry.packCoordinates(x, z)
                val floorY = craterShape[key] ?: continue

                // Remove blocks from the floor up to the destruction height
                // We inherently know which blocks to process based on the crater shape
                for (y in floorY + 1..min(centerY + destructionHeight, maxY)) {
                    val blockType = chunkCache.getBlockMaterial(x, y, z)
                    if (!blockType.isAir && blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                        blockChanger.addBlockChange(x, y, z, Material.AIR, updateBlock = false)
                    }
                }
            }
        }
    }

    /**
     * Optimized method that combines ray cast and rim scorching into a single pass
     * without tracking processed blocks
     */
    private suspend fun applyScorchingEffects(craterShape: Map<Long, Int>): Int = coroutineScope {
        val invRadiusX = 1.0 / radiusX
        val invRadiusZ = 1.0 / radiusZ
        var effectiveRadiusSquared = 0.0

        // Precompute rim positions for better performance
        val rimPositions = findRimPositions(craterShape)

        // Process crater floor scorching - guaranteed to be unique positions since they come from the crater shape map
        val craterFloorJobs = craterShape.entries.map { (packedPos, floorY) ->
            async {
                val x = Geometry.unpackX(packedPos)
                val z = Geometry.unpackZ(packedPos)

                // Calculate normalized distance for scorch intensity - once
                val xNorm = (x - centerX) * invRadiusX
                val zNorm = (z - centerZ) * invRadiusZ
                val normalizedDist = sqrt(xNorm * xNorm + zNorm * zNorm)

                // Ray cast downward from the floor of the crater - can use exact floor Y from our map
                val targetY = floorY  // Floor Y is the first block that should be scorched

                if (targetY >= minY) {
                    val blockType = chunkCache.getBlockMaterial(x, targetY, z)

                    // Found a valid block to scorch
                    if (!blockType.isAir &&
                        blockType !in TransformationRule.LIQUID_MATERIALS &&
                        blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
                    ) {
                        val scorchMaterial = selectScorchMaterial(normalizedDist, x, z)
                        blockChanger.addBlockChange(x, targetY, z, scorchMaterial, updateBlock = false)

                        val distSq = (x - centerX).toDouble().pow(2) + (z - centerZ).toDouble().pow(2)
                        return@async distSq
                    }
                }

                0.0 // Return 0 if no block was processed
            }
        }

        // Process rim scorching in parallel - positions guaranteed to be outside crater shape
        val rimJobs = rimPositions.map { packedPos ->
            async {
                val x = Geometry.unpackX(packedPos)
                val z = Geometry.unpackZ(packedPos)

                // Find top non-air block in this rim column
                var topY = -1

                // Start searching from the crater level and work downward
                for (y in maxY downTo minY) {
                    val blockType = chunkCache.getBlockMaterial(x, y, z)
                    if (!blockType.isAir &&
                        blockType !in TransformationRule.LIQUID_MATERIALS &&
                        blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
                    ) {
                        topY = y
                        break
                    }
                }

                // If found a block to scorch
                if (topY != -1) {
                    // Calculate distance from center for rim intensity - once
                    val xNorm = (x - centerX) * invRadiusX
                    val zNorm = (z - centerZ) * invRadiusZ
                    val distance = sqrt(xNorm * xNorm + zNorm * zNorm)

                    // Apply gradually increasing scorch effect based on distance from rim
                    val offsetDist = 1.4
                    if (distance < offsetDist) {
                        // Calculate scorch intensity
                        val intensity = max(0.0, 1.0 - (distance / offsetDist))
                        val material = selectScorchMaterial(1.0 - intensity, x, z)
                        blockChanger.addBlockChange(x, topY, z, material, updateBlock = true)
                    }
                }
            }
        }

        // Get the maximum radius squared from all processed blocks
        val distancesSquared = craterFloorJobs.awaitAll().filterNot { it == 0.0 }
        if (distancesSquared.isNotEmpty()) {
            effectiveRadiusSquared = distancesSquared.maxOrNull() ?: 0.0
        }

        // Wait for rim jobs to complete
        rimJobs.awaitAll()

        sqrt(effectiveRadiusSquared).roundToInt()
    }

    /**
     * More efficient method to find rim positions
     */
    private fun findRimPositions(craterShape: Map<Long, Int>): Set<Long> {
        val rimPositions = HashSet<Long>()
        val directions = arrayOf(
            1 to 0, -1 to 0, 0 to 1, 0 to -1,
            1 to 1, 1 to -1, -1 to 1, -1 to -1
        )

        for (packedPos in craterShape.keys) {
            val x = Geometry.unpackX(packedPos)
            val z = Geometry.unpackZ(packedPos)

            for ((dx, dz) in directions) {
                val nx = x + dx
                val nz = z + dz
                val neighborPos = Geometry.packCoordinates(nx, nz)

                if (nx in minX..maxX && nz in minZ..maxZ && neighborPos !in craterShape) {
                    rimPositions.add(neighborPos)
                }
            }
        }

        return rimPositions
    }

    /**
     * Optimized scorch material selection with cached calculations
     */
    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add some noise for variation
        val variation = 0.25
        val noise = wangNoise(x, 0, z)
        val distortion = (noise - 0.5) * variation
        val distortedDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on distance - closer to center means more intense scorching
        val materialIndex = ((1.0 - distortedDistance) * (scorchMaterials.size - 1)).roundToInt()
            .coerceIn(0, scorchMaterials.size - 1)

        return scorchMaterials[materialIndex]
    }
}