package me.mochibit.defcon.explosions.processor

import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.utils.BlockChanger
import me.mochibit.defcon.utils.ChunkCache
import me.mochibit.defcon.utils.Geometry
import me.mochibit.defcon.utils.Geometry.wangNoise
import org.bukkit.Location
import org.bukkit.Material
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

    // Maximum depth for scorch ray casting
    private val maxScorchRayDepth = 20

    private val scorchMaterials = listOf(
        Material.TUFF,
        Material.DEEPSLATE,
        Material.BASALT,
        Material.BLACKSTONE,
        Material.COAL_BLOCK,
        Material.BLACK_CONCRETE_POWDER,
        Material.BLACK_CONCRETE,
    )

    // Efficient structure for processed blocks
    private val processedPositions = HashSet<Long>(
        (maxRadius * 2 * maxRadius * 2 * (radiusY + destructionHeight) / 4)
    )

    private val blockChanger = BlockChanger.getInstance(world)

    private fun isBlockProcessed(x: Int, y: Int, z: Int): Boolean {
        return !processedPositions.add(Geometry.packIntegerCoordinates(x, y, z))
    }

    suspend fun create(): Int {
        try {
            val effectiveRadius = createCrater()
            return effectiveRadius
        } catch (e: Exception) {
            e.printStackTrace()
            return max(radiusX, radiusZ)
        } finally {
            chunkCache.cleanupCache()
            processedPositions.clear()
        }
    }

    private suspend fun createCrater(): Int {
        // Generate paraboloid crater shape
        val craterShape = generateParaboloidShape()

        // Apply changes to the world
        val effectiveRadius = applyChanges(craterShape)

        return effectiveRadius
    }

    /**
     * Generates a paraboloid crater shape
     */
    private fun generateParaboloidShape(): Map<Pair<Int, Int>, Int> {
        // Maps (x,z) coordinates to the floor Y of paraboloid in that column
        val craterShape = HashMap<Pair<Int, Int>, Int>((maxX - minX + 1) * (maxZ - minZ + 1) / 2)

        for (x in minX..maxX) {
            for (z in minZ..maxZ) {
                // Calculate normalized square distance from center in xz plane
                val xComponent = (x - centerX).toDouble() / radiusX
                val zComponent = (z - centerZ).toDouble() / radiusZ
                val normalizedDistSquared = xComponent * xComponent + zComponent * zComponent

                // Only process points inside the paraboloid's circular boundary
                if (normalizedDistSquared <= 1.0) {
                    // Calculate depth at this point (correct paraboloid equation)
                    // Deepest at center (normalizedDistSquared = 0) and shallowest at edges (normalizedDistSquared = 1)
                    val depth = radiusY * (1.0 - normalizedDistSquared)
                    val craterFloorY = (centerY - depth).roundToInt()

                    // Store the floor Y coordinate if it's within valid range
                    if (craterFloorY >= minY) {
                        craterShape[Pair(x, z)] = craterFloorY+1
                    }
                }
            }
        }

        return craterShape
    }


    private suspend fun applyChanges(craterShape: Map<Pair<Int, Int>, Int>): Int {

        // Step 1: Create the crater hole (set blocks to air)
        for ((pos, floorY) in craterShape) {
            val (x, z) = pos

            // Remove blocks from the floor up to the destruction height
            for (y in floorY..min(centerY + destructionHeight, maxY)) {
                if (!isBlockProcessed(x, y, z)) {
                    val blockType = chunkCache.getBlockMaterial(x, y, z)
                    if (!blockType.isAir && blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST) {
                        blockChanger.addBlockChange(x,y,z, Material.AIR, updateBlock = false)
                    }
                }
            }
        }

        // Step 2: Apply ray-cast scorching effects
        val effectiveRadius = applyRayCastScorching(craterShape)

        // Step 3: Apply rim scorch effects
        applyRimScorching(craterShape)

        return  effectiveRadius
    }

    /**
     * Applies scorching by casting a ray downward from each floor point of the crater
     * and replacing the first solid block found
     */
    private suspend fun applyRayCastScorching(craterShape: Map<Pair<Int, Int>, Int>): Int {
        val scorchProcessed = HashSet<Long>()

        var effectiveRadiusSquared = 0.0
        for ((pos, floorY) in craterShape) {
            val (x, z) = pos

            // Calculate normalized distance for scorch intensity
            val xNorm = (x - centerX).toDouble() / radiusX
            val zNorm = (z - centerZ).toDouble() / radiusZ
            val normalizedDist = sqrt(xNorm * xNorm + zNorm * zNorm)

            // Ray cast downward from the floor of the crater
            for (rayDepth in 0..maxScorchRayDepth) {
                val targetY = floorY - rayDepth

                // Skip if out of bounds or already processed
                if (targetY < minY ||
                    isBlockProcessed(x, targetY, z) ||
                    scorchProcessed.contains(Geometry.packIntegerCoordinates(x, targetY, z))
                ) {
                    continue
                }

                val blockType = chunkCache.getBlockMaterial(x, targetY, z)

                // Found a valid block to scorch
                if (!blockType.isAir &&
                    blockType !in TransformationRule.LIQUID_MATERIALS &&
                    blockType !in TransformationRule.BLOCK_TRANSFORMATION_BLACKLIST
                ) {
                    // Using normalized distance directly for all points including center
                    val scorchMaterial = selectScorchMaterial(normalizedDist, x, z)
                    blockChanger.addBlockChange(x, targetY, z, scorchMaterial, updateBlock = false)
                    scorchProcessed.add(Geometry.packIntegerCoordinates(x, targetY, z))

                    val dx = (x - centerX).toDouble()
                    val dz = (z - centerZ).toDouble()
                    effectiveRadiusSquared = max(effectiveRadiusSquared, dx * dx + dz * dz)
                    break // Stop ray casting after finding first solid block
                }
            }
        }
        return sqrt(effectiveRadiusSquared).roundToInt()
    }

    /**
     * Applies scorching to the rim of the crater
     */
    private suspend fun applyRimScorching(craterShape: Map<Pair<Int, Int>, Int>) {
        val scorchProcessed = HashSet<Long>()

        // Fast lookup for positions in the crater
        val craterPositionSet = craterShape.keys.toHashSet()

        // Find rim positions (neighbors of crater positions that aren't in the crater)
        val rimPositions = HashSet<Pair<Int, Int>>()
        for ((pos, _) in craterShape) {
            val (x, z) = pos
            val neighbors = listOf(
                Pair(x + 1, z), Pair(x - 1, z),
                Pair(x, z + 1), Pair(x, z - 1),
                Pair(x + 1, z + 1), Pair(x + 1, z - 1),
                Pair(x - 1, z + 1), Pair(x - 1, z - 1)
            )

            for (neighbor in neighbors) {
                val (nx, nz) = neighbor
                if (nx in minX..maxX && nz in minZ..maxZ && neighbor !in craterPositionSet) {
                    rimPositions.add(neighbor)
                }
            }
        }

        // Process rim positions
        for ((x, z) in rimPositions) {
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
            if (topY != -1 &&
                !isBlockProcessed(x, topY, z) &&
                !scorchProcessed.contains(Geometry.packIntegerCoordinates(x, topY, z))
            ) {

                // Calculate distance from center for rim intensity
                val xNorm = (x - centerX).toDouble() / radiusX
                val zNorm = (z - centerZ).toDouble() / radiusZ
                val distance = sqrt(xNorm * xNorm + zNorm * zNorm)

                // Apply gradually increasing scorch effect based on distance from rim
                val offsetDist = 1.4
                if (distance < offsetDist) { // Removed distance > 0.8 condition to include center
                    // Calculate scorch intensity - inverse of distance for more intense center
                    val intensity = max(0.0, 1.0 - (distance / offsetDist))
                    val material = selectScorchMaterial(1.0 - intensity, x, z)
                    blockChanger.addBlockChange(x, topY, z, material, updateBlock = true)
                    scorchProcessed.add(Geometry.packIntegerCoordinates(x, topY, z))
                }
            }
        }
    }

    private fun selectScorchMaterial(normalizedDistance: Double, x: Int, z: Int): Material {
        val clampedDistance = normalizedDistance.coerceIn(0.0, 1.0)

        // Add some noise for variation
        val variation = 0.25
        val distortion = (wangNoise(x, 0, z) - 0.5) * variation
        val distortedDistance = (clampedDistance + distortion).coerceIn(0.0, 1.0)

        // Select material based on distance - closer to center means more intense scorching
        // IMPORTANT: This logic is correct - lower distortedDistance (center) means higher index (more intense scorch)
        val index = ((1.0 - distortedDistance) * (scorchMaterials.size - 1)).roundToInt()
        return scorchMaterials.getOrElse(index) { scorchMaterials.first() }
    }


}