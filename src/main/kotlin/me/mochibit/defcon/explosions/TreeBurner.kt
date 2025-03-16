package me.mochibit.defcon.explosions

import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.roundToInt

class TreeBurner(
    private val world: World,
    private val center: Vector3i,
    private val transformationRule: TransformationRule
) {
    companion object {
        private const val LEAF_SUFFIX = "_LEAVES"
        private const val LOG_SUFFIX = "_LOG"
        private const val WOOD_SUFFIX = "_WOOD"
        private val TERRAIN_TYPES = setOf(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL)

        // Optimized properties for the tree falling feature
        private const val MIN_POWER_FOR_AUTOMATIC_DESTRUCTION = 0.7

        private const val BATCH_SIZE = 100

        // Maximum tree height to process
        private const val MAX_TREE_HEIGHT = 60

    }
    private val chunkCache = ChunkCache.getInstance(world)
    private val blockChanger = BlockChangerFactory.getBlockChanger(world)

    // Cache for material lookups
    private val materialCache = HashMap<Vector3i, Material>()

    // Batch processing for block changes
    private val blockChanges = mutableListOf<BlockChange>()

    fun processTreeBurn(initialBlock: Vector3i, normalizedExplosionPower: Double) {
        try {
            // Reset state for this tree processing
            materialCache.clear()
            blockChanges.clear()

            // Early exit if block is not part of a tree
            if (!isTreeBlock(initialBlock)) {
                return
            }

            // Find the base of the tree by going down from the initial block
            val treeBase = findTreeBase(initialBlock)
            val treeMaxHeight = initialBlock.y
            val treeMinHeight = treeBase.y

            // Enforce maximum tree height limit
            val effectiveMaxHeight = minOf(treeMinHeight + MAX_TREE_HEIGHT, treeMaxHeight)
            val heightRange = (effectiveMaxHeight - treeMinHeight).coerceAtLeast(1)

            // Calculate shockwave direction once
            val shockwaveDirection = Vector3f(
                (initialBlock.x - center.x).toFloat(),
                (initialBlock.y - center.y).toFloat(),
                (initialBlock.z - center.z).toFloat()
            ).normalize()

            // Process the vertical column from top to bottom, limited by MAX_TREE_HEIGHT
            for (y in effectiveMaxHeight downTo treeMinHeight) {
                val currentBlock = Vector3i(initialBlock.x, y, initialBlock.z)
                val material = chunkCache.getBlockMaterial(currentBlock.x, currentBlock.y, currentBlock.z)

                when {
                    material.name.endsWith(LEAF_SUFFIX) -> {
                        // Process leaves - always remove them
                        addBlockChange(currentBlock, Material.AIR)
                    }
                    material.name.endsWith(LOG_SUFFIX) -> {
                        // Process log blocks with tilt based on height
                        processLogBlock(
                            currentBlock,
                            treeMinHeight,
                            heightRange,
                            shockwaveDirection,
                            normalizedExplosionPower
                        )
                    }
                    material in TERRAIN_TYPES -> {
                        // Process terrain - convert to scorched earth
                        addBlockChange(currentBlock, Material.COARSE_DIRT)
                    }
                }
            }

            // Apply any remaining block changes
            applyBlockChanges()
        } catch (e: Exception) {
            // Log the error but prevent it from crashing the server
            println("Error in TreeBurner: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun findTreeBase(startBlock: Vector3i): Vector3i {
        var currentY = startBlock.y
        val currentBlock = Vector3i(startBlock)
        val minY = maxOf(0, currentY - MAX_TREE_HEIGHT) // Don't go below ground or more than MAX_TREE_HEIGHT down

        // Go down until we hit terrain or non-tree block, with a limit
        while (currentY > minY) {
            val material = chunkCache.getBlockMaterial(currentBlock.x, currentY, currentBlock.z)

            if (material == Material.AIR) {
                currentY--
                continue
            } else if (material in TERRAIN_TYPES) {
                // Found the base (terrain)
                currentBlock.y = currentY
                return currentBlock
            } else if (!isTreeBlock(currentBlock)) {
                // If we hit a non-tree block, return the block above
                currentBlock.y = currentY + 1
                return currentBlock
            }

            currentY--
        }

        // Fallback to the minimum height we're willing to check
        currentBlock.y = minY
        return currentBlock
    }

    fun isTreeBlock(block: Vector3i): Boolean {
        val material = chunkCache.getBlockMaterial(block.x, block.y, block.z)
        val materialName = material.name
        return materialName.endsWith(LOG_SUFFIX) ||
                materialName.endsWith(LEAF_SUFFIX) ||
                materialName.endsWith(WOOD_SUFFIX)
    }

    private fun processLogBlock(
        logLocation: Vector3i,
        treeMinHeight: Int,
        heightRange: Int,
        shockwaveDirection: Vector3f,
        normalizedExplosionPower: Double
    ) {
        // Completely destroy logs if explosion power is strong enough
        if (normalizedExplosionPower > MIN_POWER_FOR_AUTOMATIC_DESTRUCTION) {
            addBlockChange(logLocation, Material.AIR)
            return
        }

        // Calculate tilt based on height
        val blockHeight = logLocation.y - treeMinHeight
        val heightFactor = blockHeight.toDouble() / heightRange
        val tiltFactor = if (logLocation.y == treeMinHeight) {
            0.0 // Base of tree doesn't move
        } else {
            heightFactor * normalizedExplosionPower * 6 // Smooth gradient tilt
        }

        val newPosition = Vector3i(
            (logLocation.x + shockwaveDirection.x * tiltFactor).roundToInt(),
            logLocation.y,
            (logLocation.z + shockwaveDirection.z * tiltFactor).roundToInt()
        )

        // Change the block and mark it as processed
        addBlockChange(newPosition, Material.POLISHED_BASALT)

        // Remove the original block if it moved
        if (newPosition != logLocation) {
            addBlockChange(logLocation, Material.AIR)
        }
    }

    // Add a block change to our batch
    private fun addBlockChange(location: Vector3i, material: Material) {
        blockChanges.add(
            BlockChange(
                location.x,
                location.y,
                location.z,
                material
            )
        )

        // Apply changes in batches
        if (blockChanges.size >= BATCH_SIZE) {
            applyBlockChanges()
        }
    }

    // Apply all pending block changes
    private fun applyBlockChanges() {
        blockChanger.addBlockChanges(blockChanges)
        blockChanges.clear()
    }
}