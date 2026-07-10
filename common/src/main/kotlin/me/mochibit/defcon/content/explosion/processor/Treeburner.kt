package me.mochibit.defcon.content.explosion.processor

import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector2f

@JvmInline
value class PackedLightVector private constructor(val packed: Long) {

    constructor(x: Int, y: Int, z: Int) : this(pack(x, y, z))

    val x: Int get() = unpackX(packed)
    val y: Int get() = unpackY(packed)
    val z: Int get() = unpackZ(packed)

    companion object {
        private const val X_BITS = 22
        private const val Y_BITS = 20
        private const val Z_BITS = 22

        private const val X_MASK = (1L shl X_BITS) - 1
        private const val Y_MASK = (1L shl Y_BITS) - 1
        private const val Z_MASK = (1L shl Z_BITS) - 1

        private const val Y_SHIFT = X_BITS
        private const val Z_SHIFT = X_BITS + Y_BITS

        private fun pack(x: Int, y: Int, z: Int): Long {
            val xLong = x.toLong() and X_MASK
            val yLong = y.toLong() and Y_MASK
            val zLong = z.toLong() and Z_MASK
            return xLong or (yLong shl Y_SHIFT) or (zLong shl Z_SHIFT)
        }


        private fun unpackX(packed: Long): Int {
            return ((packed shl (64 - X_BITS)) shr (64 - X_BITS)).toInt()
        }

        private fun unpackY(packed: Long): Int {
            return ((packed ushr Y_SHIFT shl (64 - Y_BITS)) shr (64 - Y_BITS)).toInt()
        }

        private fun unpackZ(packed: Long): Int {
            return (packed shr Z_SHIFT).toInt()
        }
    }
}

class TreeBurner(
    private val level: ServerLevel,
    private val center: BlockPos,
    val distanceRatioCompletelyDestroy: Double = 0.1,
) {
    companion object {
        private const val LEAF_SUFFIX = "_leaves"
        private const val LOG_SUFFIX = "_log"
        private const val WOOD_SUFFIX = "_wood"
        private const val MAX_TREE_HEIGHT = 60

        private val TREE_BLOCKS: Set<Block> =
            buildSet {
                BuiltInRegistries.BLOCK.forEach { block ->
                    val path = BuiltInRegistries.BLOCK.getKey(block).path ?: return@forEach
                    when {
                        path.endsWith(LEAF_SUFFIX) || path.endsWith(LOG_SUFFIX) || path.endsWith(WOOD_SUFFIX) -> add(block)
                    }
                }
            }

        private val LEAF_BLOCKS =
            TREE_BLOCKS.filterTo(mutableSetOf()) {
                BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LEAF_SUFFIX) == true
            }
        private val LOG_BLOCKS =
            TREE_BLOCKS.filterTo(mutableSetOf()) {
                BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LOG_SUFFIX) == true
            }
        private val WOOD_BLOCKS =
            TREE_BLOCKS.filterTo(mutableSetOf()) {
                BuiltInRegistries.BLOCK.getKey(it).path.endsWith(WOOD_SUFFIX) == true
            }

        private val BURNT_REPLACEMENTS: Map<Block, Block> =
            buildMap {
                TREE_BLOCKS.forEach { block ->
                    val path = BuiltInRegistries.BLOCK.getKey(block)?.path.orEmpty()
                    put(
                        block,
                        when {
                            block in LEAF_BLOCKS -> Blocks.AIR
                            path.contains("warped") || path.contains("crimson") -> Blocks.BLACKSTONE
                            else -> Blocks.POLISHED_BASALT
                        },
                    )
                }
            }
    }


    private val blockChanger = BlockChanger.getInstance(level)

    private val processedTreeBlocks = HashSet<PackedLightVector>()

    fun isPosProcessed(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean {
        val vec = PackedLightVector(x,y,z)
        return processedTreeBlocks.contains(vec).also {
            processedTreeBlocks.remove(vec)
        }
    }

    suspend fun processTreeBurn(
        initialBlock: BlockPos,
        explosionPower: Double,
    ) {
        if (!isTreeBlock(initialBlock)) {
            return
        }

        if (processedTreeBlocks.contains(PackedLightVector(initialBlock.x, initialBlock.y, initialBlock.z))) {
            return
        }

        try {
            val treeMaxHeight = initialBlock.y
            val treeMinHeight = findTreeBase(initialBlock)
            val effectiveMaxHeight = minOf(treeMinHeight + MAX_TREE_HEIGHT, treeMaxHeight)
            val heightRange = (effectiveMaxHeight - treeMinHeight).coerceAtLeast(1)

            var blocksProcessed = 0

            val dx = (initialBlock.x - center.x).toFloat()
            val dz = (initialBlock.z - center.z).toFloat()
            val invMag = 1.0f / kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            val shockwaveDirection = Vector2f(dx * invMag, dz * invMag)

            val currentX = initialBlock.x
            val currentZ = initialBlock.z

            for (y in effectiveMaxHeight downTo treeMinHeight) {
                val state = getBlockStateDirect(currentX, y, currentZ)
                val block = state.block

                if (block !in TREE_BLOCKS) continue

                blocksProcessed++

                when (block) {
                    in LEAF_BLOCKS -> {
                        blockChanger.addBlockChange(currentX, y, currentZ, Blocks.AIR.defaultBlockState(), updateBlock = true)
                        processedTreeBlocks.add(PackedLightVector(currentX, y, currentZ))
                    }

                    in LOG_BLOCKS, in WOOD_BLOCKS -> {
                        processWoodBlock(
                            currentX,
                            y,
                            currentZ,
                            block,
                            treeMinHeight,
                            heightRange,
                            shockwaveDirection,
                            explosionPower,
                        )
                    }

                    else -> {}
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun findTreeBase(startBlock: BlockPos): Int {
        val minY = maxOf(level.minBuildHeight, startBlock.y - MAX_TREE_HEIGHT)
        val currentX = startBlock.x
        val currentZ = startBlock.z
        var currentY = startBlock.y

        while (currentY > minY) {
            val state = getBlockStateDirect(currentX, currentY, currentZ)

            when {
                state.isAir -> currentY--
                state.block !in TREE_BLOCKS -> return currentY + 1
                else -> currentY--
            }
        }

        return minY
    }

    fun getTreeTerrain(startLoc: BlockPos): BlockPos = BlockPos(startLoc.x, findTreeBase(startLoc) - 1, startLoc.z)

    private fun isTreeBlock(
        x: Int,
        y: Int,
        z: Int,
    ): Boolean = getBlockStateDirect(x, y, z).block in TREE_BLOCKS

    fun isTreeBlock(block: BlockPos): Boolean = isTreeBlock(block.x, block.y, block.z)

    fun isTreeBlock(state: BlockState): Boolean = state.block in TREE_BLOCKS

    fun isTreeBlock(block: Block): Boolean = block in TREE_BLOCKS

    private fun getBlockStateDirect(
        x: Int,
        y: Int,
        z: Int,
    ): BlockState = level.getBlockState(BlockPos(x, y, z))

    private suspend fun processWoodBlock(
        x: Int,
        y: Int,
        z: Int,
        originalBlock: Block,
        treeMinHeight: Int,
        heightRange: Int,
        shockwaveDirection: Vector2f,
        explosionPower: Double, // 0.0 = far from explosion, 1.0 = close to explosion
    ) {
        if (explosionPower >= (1.0 - distanceRatioCompletelyDestroy)) {
            blockChanger.addBlockChange(x, y, z, Blocks.AIR.defaultBlockState(), updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(x, y, z))
            return
        }

        val burntBlock = BURNT_REPLACEMENTS[originalBlock] ?: Blocks.POLISHED_BASALT

        val tiltFactor = calculateTiltFactor(y, treeMinHeight, heightRange, explosionPower)

        if (tiltFactor > 0.0) {
            val newX = x + (shockwaveDirection.x * tiltFactor).toInt()
            val newZ = z + (shockwaveDirection.y * tiltFactor).toInt()
            tiltBlock(x, y, z, newX, newZ, burntBlock.defaultBlockState())
        } else {
            blockChanger.addBlockChange(x, y, z, burntBlock.defaultBlockState(), updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(x, y, z))
        }
    }

    @Suppress("NOTHING_TO_INLINE")
    private inline fun calculateTiltFactor(
        blockY: Int,
        treeMinHeight: Int,
        heightRange: Int,
        explosionPower: Double,
    ): Double {
        if (blockY == treeMinHeight) return 0.0
        val heightFactor = (blockY - treeMinHeight).toDouble() / heightRange
        return heightFactor * explosionPower * 6.0
    }

    private suspend fun tiltBlock(
        originalX: Int,
        originalY: Int,
        originalZ: Int,
        newX: Int,
        newZ: Int,
        state: BlockState,
    ) {
        if (newX != originalX || newZ != originalZ) {
            blockChanger.addBlockChange(originalX, originalY, originalZ, Blocks.AIR.defaultBlockState(), updateBlock = true)
            blockChanger.addBlockChange(newX, originalY, newZ, state, updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(newX, originalY, originalZ))
        } else {
            blockChanger.addBlockChange(originalX, originalY, originalZ, state, updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(originalX, originalY, originalZ))
        }
    }
}