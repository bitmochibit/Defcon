package me.mochibit.defcon.content.explosion.processor

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.block.state.properties.BlockStateProperties
import org.joml.Vector2f
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
                        path.endsWith(LEAF_SUFFIX) || path.endsWith(LOG_SUFFIX) || path.endsWith(WOOD_SUFFIX) -> add(
                            block
                        )
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

    fun isPosProcessed(x: Int, y: Int, z: Int): Boolean =
        processedTreeBlocks.contains(PackedLightVector(x, y, z))

    suspend fun processTreeBurn(
        initialBlock: BlockPos,
        explosionPower: Double,
    ) {
        if (!isTreeBlock(initialBlock)) return

        val logSeed = findNearestLog(initialBlock) ?: return
        if (processedTreeBlocks.contains(PackedLightVector(logSeed.x, logSeed.y, logSeed.z))) return

        try {
            val tree = floodFillTree(logSeed)
            if (tree.logs.isEmpty()) return

            val treeMinHeight = tree.logs.minOf { it.y }
            val treeMaxHeight = tree.logs.maxOf { it.y }
            val heightRange = (treeMaxHeight - treeMinHeight).coerceAtLeast(1)


            val dx = (initialBlock.x - center.x).toFloat()
            val dz = (initialBlock.z - center.z).toFloat()
            val invMag = 1.0f / kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            val shockwaveDirection = Vector2f(dx * invMag, dz * invMag)


            tree.leaves.forEach {
                if (processedTreeBlocks.contains(PackedLightVector(it.x, it.y, it.z))) return@forEach
                blockChanger.addBlockChange(it.x, it.y, it.z, Blocks.AIR.defaultBlockState(), updateBlock = true)
                processedTreeBlocks.add(PackedLightVector(it.x, it.y, it.z))
            }

            tree.logs.sortedByDescending { it.y }.forEach { pos ->
                if (processedTreeBlocks.contains(PackedLightVector(pos.x, pos.y, pos.z))) return@forEach
                val originalBlock = level.getBlockState(pos).block
                processWoodBlock(
                    pos.x,
                    pos.y,
                    pos.z,
                    originalBlock,
                    treeMinHeight,
                    heightRange,
                    shockwaveDirection,
                    explosionPower,
                )
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

    private fun findNearestLog(start: BlockPos, maxSearch: Int = 40): BlockPos? {
        var pos = start
        repeat(maxSearch) {
            val state = level.getBlockState(pos)
            val block = state.block
            if (block in LOG_BLOCKS || block in WOOD_BLOCKS) return pos
            if (!isTreeBlock(block) && !state.isAir) return null
            pos = pos.below()
        }
        return null
    }

    data class TreeStructure(val logs: List<BlockPos>, val leaves: List<BlockPos>)

    private fun floodFillTree(initialLog: BlockPos, maxBlocks: Int = 400): TreeStructure {
        val visited = HashSet<Long>()
        val logs = mutableListOf<BlockPos>()
        val leaves = mutableListOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()

        queue.add(initialLog)
        visited.add(initialLog.asLong())

        while (queue.isNotEmpty() && visited.size < maxBlocks) {
            val pos = queue.removeFirst()
            val state = level.getBlockState(pos)
            val block = state.block

            val isLog = block in LOG_BLOCKS || block in WOOD_BLOCKS
            val isLeaf = block in LEAF_BLOCKS

            if (!isLog && !isLeaf) continue

            if (isLog) logs.add(pos) else leaves.add(pos)

            val isBridgeableLeaf = isLeaf &&
                    state.hasProperty(BlockStateProperties.DISTANCE) &&
                    !(state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT))
            val myDistance = if (isBridgeableLeaf) state.getValue(BlockStateProperties.DISTANCE) else -1

            for (dir in Direction.entries) {
                val n = pos.relative(dir)
                val key = n.asLong()
                if (key in visited) continue

                val nState = level.getBlockState(n)
                val nBlock = nState.block

                when {
                    nBlock in LOG_BLOCKS || nBlock in WOOD_BLOCKS -> {
                        visited.add(key)
                        queue.add(n)
                    }

                    nBlock in LEAF_BLOCKS && nState.hasProperty(BlockStateProperties.DISTANCE) -> {
                        val nDistance = nState.getValue(BlockStateProperties.DISTANCE)
                        val canBridge = !isLeaf || (isBridgeableLeaf && nDistance >= myDistance)
                        if (canBridge) {
                            visited.add(key)
                            queue.add(n)
                        }
                    }
                }
            }
        }

        return TreeStructure(logs, leaves)
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
            blockChanger.addBlockChange(
                originalX,
                originalY,
                originalZ,
                Blocks.AIR.defaultBlockState(),
                updateBlock = true
            )
            blockChanger.addBlockChange(newX, originalY, newZ, state, updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(newX, originalY, newZ))
        } else {
            blockChanger.addBlockChange(originalX, originalY, originalZ, state, updateBlock = true)
            processedTreeBlocks.add(PackedLightVector(originalX, originalY, originalZ))
        }
    }
}

//TODO: refactor this to a proper abstraction

object TreeBurnCore {
    private const val LEAF_SUFFIX = "_leaves"
    private const val LOG_SUFFIX = "_log"
    private const val WOOD_SUFFIX = "_wood"

    val TREE_BLOCKS: Set<Block> = buildSet {
        BuiltInRegistries.BLOCK.forEach { block ->
            val path = BuiltInRegistries.BLOCK.getKey(block).path
            if (path.endsWith(LEAF_SUFFIX) || path.endsWith(LOG_SUFFIX) || path.endsWith(WOOD_SUFFIX)) add(block)
        }
    }

    val LEAF_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LEAF_SUFFIX) }
    val LOG_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LOG_SUFFIX) }
    val WOOD_BLOCKS = TREE_BLOCKS.filterTo(mutableSetOf()) { BuiltInRegistries.BLOCK.getKey(it).path.endsWith(WOOD_SUFFIX) }

    val BURNT_REPLACEMENTS: Map<Block, Block> = buildMap {
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

    data class TreeStructure(val logs: List<BlockPos>, val leaves: List<BlockPos>)

    inline fun floodFillTree(
        initialLog: BlockPos,
        maxBlocks: Int = 600,
        maxRadius: Int = 12,
        getState: (BlockPos) -> BlockState,
    ): TreeStructure {
        val visited = HashSet<Long>()
        val logs = mutableListOf<BlockPos>()
        val leaves = mutableListOf<BlockPos>()
        val queue = ArrayDeque<BlockPos>()
        val maxRadiusSq = maxRadius * maxRadius

        queue.add(initialLog)
        visited.add(initialLog.asLong())

        while (queue.isNotEmpty() && visited.size < maxBlocks) {
            val pos = queue.removeFirst()
            val state = getState(pos)
            val block = state.block

            val isLog = block in LOG_BLOCKS || block in WOOD_BLOCKS
            val isLeaf = block in LEAF_BLOCKS
            if (!isLog && !isLeaf) continue

            if (isLog) logs.add(pos) else leaves.add(pos)

            val isBridgeableLeaf = isLeaf &&
                    state.hasProperty(BlockStateProperties.DISTANCE) &&
                    !(state.hasProperty(BlockStateProperties.PERSISTENT) && state.getValue(BlockStateProperties.PERSISTENT))
            val myDistance = if (isBridgeableLeaf) state.getValue(BlockStateProperties.DISTANCE) else -1

            for (dir in Direction.entries) {
                val n = pos.relative(dir)
                val key = n.asLong()
                if (key in visited) continue

                val distSq = initialLog.distSqr(n)
                if (distSq > maxRadiusSq) continue

                val nState = getState(n)
                val nBlock = nState.block

                when {
                    nBlock in LOG_BLOCKS || nBlock in WOOD_BLOCKS -> {
                        visited.add(key); queue.add(n)
                    }
                    nBlock in LEAF_BLOCKS && nState.hasProperty(BlockStateProperties.DISTANCE) -> {
                        val nDistance = nState.getValue(BlockStateProperties.DISTANCE)
                        val canBridge = !isLeaf || (isBridgeableLeaf && nDistance >= myDistance)
                        if (canBridge) { visited.add(key); queue.add(n) }
                    }
                }
            }
        }

        return TreeStructure(logs, leaves)
    }

    fun calculateTiltFactor(blockY: Int, treeMinHeight: Int, heightRange: Int, explosionPower: Float): Double {
        if (blockY == treeMinHeight) return 0.0
        val heightFactor = (blockY - treeMinHeight) / heightRange
        return heightFactor * explosionPower * 6.0
    }
}

object WorldgenTreeBurner {

    private const val MAX_TREE_BLOCKS = 700


    fun burnTree(
        level: WorldGenLevel,
        trunkOrAnyLogPos: BlockPos,
        zoneCenterX: Int,
        zoneCenterZ: Int,
        explosionPower: Float,
        distanceRatioCompletelyDestroy: Double = 0.1,
    ) {


        val tree = TreeBurnCore.floodFillTree(trunkOrAnyLogPos, MAX_TREE_BLOCKS) { level.getBlockState(it) }
        if (tree.logs.isEmpty()) return

        val treeMinHeight = tree.logs.minOf { it.y }
        val treeMaxHeight = tree.logs.maxOf { it.y }
        val heightRange = (treeMaxHeight - treeMinHeight).coerceAtLeast(1)

        val dx = (trunkOrAnyLogPos.x - zoneCenterX).toFloat()
        val dz = (trunkOrAnyLogPos.z - zoneCenterZ).toFloat()
        val invMag = 1.0f / sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        val shockwave = Vector2f(dx * invMag, dz * invMag)


        for (leaf in tree.leaves) {
            level.setBlock(leaf, Blocks.AIR.defaultBlockState(), 2)
        }

        for (pos in tree.logs.sortedByDescending { it.y }) {
            val originalBlock = level.getBlockState(pos).block

            if (explosionPower >= (1.0 - distanceRatioCompletelyDestroy)) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
                continue
            }

            val burnt = TreeBurnCore.BURNT_REPLACEMENTS[originalBlock] ?: Blocks.POLISHED_BASALT
            val tilt = TreeBurnCore.calculateTiltFactor(pos.y, treeMinHeight, heightRange, explosionPower)

            if (tilt > 0.0) {
                val newX = pos.x + (shockwave.x * tilt).roundToInt()
                val newZ = pos.z + (shockwave.y * tilt).roundToInt()
                if (newX != pos.x || newZ != pos.z) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
                    val newPos = BlockPos(newX, pos.y, newZ)
                    level.setBlock(newPos, burnt.defaultBlockState(), 2)
                    continue
                }
            }
            level.setBlock(pos, burnt.defaultBlockState(), 2)
        }
    }
}