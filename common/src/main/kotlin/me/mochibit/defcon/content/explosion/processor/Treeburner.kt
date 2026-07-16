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

/**
 * Packs a block position into a single Long. Cheaper than BlockPos for use as
 * set keys in hot paths, especially paired with fastutil's primitive collections.
 */
@JvmInline
value class PackedPos private constructor(val packed: Long) {

    constructor(pos: BlockPos) : this(pack(pos.x, pos.y, pos.z))
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

        private const val Y_SHIFT = X_BITS
        private const val Z_SHIFT = X_BITS + Y_BITS

        private fun pack(x: Int, y: Int, z: Int): Long {
            val xLong = x.toLong() and X_MASK
            val yLong = y.toLong() and Y_MASK
            val zLong = z.toLong() and ((1L shl Z_BITS) - 1)
            return xLong or (yLong shl Y_SHIFT) or (zLong shl Z_SHIFT)
        }

        private fun unpackX(packed: Long): Int = ((packed shl (64 - X_BITS)) shr (64 - X_BITS)).toInt()
        private fun unpackY(packed: Long): Int = ((packed ushr Y_SHIFT shl (64 - Y_BITS)) shr (64 - Y_BITS)).toInt()
        private fun unpackZ(packed: Long): Int = (packed shr Z_SHIFT).toInt()
    }
}

/**
 * Shared tree data & flood-fill logic used by both the runtime [TreeBurner] and
 * the worldgen [WorldgenTreeBurner], so the two never drift apart.
 */
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

    val LEAF_BLOCKS: Set<Block> = TREE_BLOCKS.filterTo(mutableSetOf()) {
        BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LEAF_SUFFIX)
    }
    val LOG_BLOCKS: Set<Block> = TREE_BLOCKS.filterTo(mutableSetOf()) {
        BuiltInRegistries.BLOCK.getKey(it).path.endsWith(LOG_SUFFIX)
    }
    val WOOD_BLOCKS: Set<Block> = TREE_BLOCKS.filterTo(mutableSetOf()) {
        BuiltInRegistries.BLOCK.getKey(it).path.endsWith(WOOD_SUFFIX)
    }

    val BURNT_REPLACEMENTS: Map<Block, Block> = buildMap {
        TREE_BLOCKS.forEach { block ->
            val path = BuiltInRegistries.BLOCK.getKey(block)?.path.orEmpty()
            put(
                block,
                when {
                    block in LEAF_BLOCKS -> Blocks.AIR
                    "warped" in path || "crimson" in path -> Blocks.BLACKSTONE
                    else -> Blocks.POLISHED_BASALT
                },
            )
        }
    }

    fun isTreeBlockType(block: Block): Boolean = block in TREE_BLOCKS

    fun burntReplacementFor(block: Block): Block = BURNT_REPLACEMENTS[block] ?: Blocks.POLISHED_BASALT

    data class TreeStructure(val logs: List<BlockPos>, val leaves: List<BlockPos>) {
        val isEmpty: Boolean get() = logs.isEmpty()
    }

    inline fun findTrunkBaseY(start: BlockPos, maxSearch: Int = 40, getState: (BlockPos) -> BlockState): Int {
        var pos = start
        var lastLogY = start.y
        repeat(maxSearch) {
            val block = getState(pos).block
            if (block in LOG_BLOCKS || block in WOOD_BLOCKS) {
                lastLogY = pos.y
                pos = pos.below()
            } else return lastLogY
        }
        return lastLogY
    }

    /** Nearest log/wood block reachable walking straight down from [start] through tree-type blocks. */
    inline fun findNearestLog(start: BlockPos, maxSearch: Int = 40, getState: (BlockPos) -> BlockState): BlockPos? {
        var pos = start
        repeat(maxSearch) {
            val state = getState(pos)
            val block = state.block
            if (block in LOG_BLOCKS || block in WOOD_BLOCKS) return pos
            if (block !in TREE_BLOCKS && !state.isAir) return null
            pos = pos.below()
        }
        return null
    }

    fun isTrunk(pos: BlockPos, getState: (BlockPos) -> BlockState): Boolean {
        val b = getState(pos).block
        return b in LOG_BLOCKS || b in WOOD_BLOCKS
    }
    inline fun findTrunkExtent(seed: BlockPos, maxSearch: Int = 48, noinline getState: (BlockPos) -> BlockState): IntRange {

        var base = seed.y
        var steps = 0
        while (steps < maxSearch && isTrunk(BlockPos(seed.x, base - 1, seed.z), getState)) {
            base--; steps++
        }

        var top = seed.y
        steps = 0
        while (steps < maxSearch && isTrunk(BlockPos(seed.x, top + 1, seed.z), getState)) {
            top++; steps++
        }

        return base..top
    }

    /** 0.0 at the tree base, growing with height and explosion power - used to tilt trunks away from the blast. */
    fun calculateTiltFactor(blockY: Int, treeMinHeight: Int, heightRange: Int, explosionPower: Double): Double {
        if (blockY == treeMinHeight) return 0.0
        val heightFactor = (blockY - treeMinHeight).toDouble() / heightRange
        return heightFactor * explosionPower * 6.0
    }
}

/**
 * Runtime (post-worldgen) tree burning, driven by the shockwave processor.
 * Always resolves and consumes an entire connected tree at once: leaves are
 * always removed, trunks are always tilted and turned to basalt/blackstone.
 */
class TreeBurner(private val level: ServerLevel, private val center: BlockPos) {
    private val blockChanger = BlockChanger.getInstance(level)
    private val processedTreeBlocks = LongOpenHashSet()

    fun isPosProcessed(x: Int, y: Int, z: Int) = processedTreeBlocks.contains(PackedPos(x, y, z).packed)


    fun isTreeBlock(pos: BlockPos): Boolean = TreeBurnCore.isTreeBlockType(level.getBlockState(pos).block)

    suspend fun processTreeBlockAt(pos: BlockPos, explosionPower: Double, trunkTopY: Int?, trunkBaseY: Int?) {
        if (!processedTreeBlocks.add(PackedPos(pos).packed)) return
        val state = level.getBlockState(pos)
        val block = state.block

        if (block in TreeBurnCore.LEAF_BLOCKS) {
            blockChanger.addBlockChange(pos.x, pos.y, pos.z, Blocks.AIR.defaultBlockState(), updateBlock = true)
            return
        }

        val top = trunkTopY ?: pos.y
        val base = trunkBaseY ?: pos.y
        val heightRange = (top - base).coerceAtLeast(1)
        val tilt = TreeBurnCore.calculateTiltFactor(pos.y, base, heightRange, explosionPower)
        val burnt = TreeBurnCore.burntReplacementFor(block).defaultBlockState()
        val direction = shockwaveDirectionFrom(pos)
        applyTiltedTrunk(pos, tilt, direction, burnt)
    }

    fun findLocalTrunkBase(pos: BlockPos) =
        TreeBurnCore.findTrunkBaseY(pos) { level.getBlockState(it) }

    fun getTreeTerrain(startLoc: BlockPos): BlockPos = BlockPos(startLoc.x, findTreeBase(startLoc) - 1, startLoc.z)

    private fun shockwaveDirectionFrom(origin: BlockPos): Vector2f {
        val dx = (origin.x - center.x).toFloat()
        val dz = (origin.z - center.z).toFloat()
        val invMag = 1.0f / sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        return Vector2f(dx * invMag, dz * invMag)
    }

    private suspend fun applyTiltedTrunk(pos: BlockPos, tilt: Double, direction: Vector2f, burntState: BlockState) {
        val newX = pos.x + (direction.x * tilt).toInt()
        val newZ = pos.z + (direction.y * tilt).toInt()
        if (tilt <= 0.0 || (newX == pos.x && newZ == pos.z)) {
            blockChanger.addBlockChange(pos.x, pos.y, pos.z, burntState, updateBlock = true)
            return
        }
        blockChanger.addBlockChange(pos.x, pos.y, pos.z, Blocks.AIR.defaultBlockState(), updateBlock = true)
        blockChanger.addBlockChange(newX, pos.y, newZ, burntState, updateBlock = true)
        processedTreeBlocks.add(PackedPos(newX, pos.y, newZ).packed)
    }

    private fun findTreeBase(startBlock: BlockPos): Int {
        val minY = maxOf(level.minBuildHeight, startBlock.y)
        val currentX = startBlock.x
        val currentZ = startBlock.z
        var currentY = startBlock.y

        while (currentY > minY) {
            val state = level.getBlockState(BlockPos(currentX, currentY, currentZ))
            when {
                state.isAir -> currentY--
                !TreeBurnCore.isTreeBlockType(state.block) -> return currentY + 1
                else -> currentY--
            }
        }

        return minY
    }
}

/** Worldgen-time tree burning: same rules as [TreeBurner], applied synchronously during chunk decoration. */
object WorldgenTreeBurner {
    private const val MAX_TREE_BLOCKS = 700

    fun burnColumnBlock(
        level: WorldGenLevel,
        pos: BlockPos,
        state: BlockState,
        zoneCenterX: Int,
        zoneCenterZ: Int,
        explosionPower: Float,
        trunkExtentCache: HashMap<Long, IntRange>,
    ): Boolean {
        val block = state.block
        val isLog = block in TreeBurnCore.LOG_BLOCKS || block in TreeBurnCore.WOOD_BLOCKS
        val isLeaf = block in TreeBurnCore.LEAF_BLOCKS
        if (!isLog && !isLeaf) return false

        if (isLeaf) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
            return true
        }

        val columnKey = (pos.x.toLong() shl 32) or (pos.z.toLong() and 0xFFFFFFFFL)
        val extent = trunkExtentCache.getOrPut(columnKey) {
            TreeBurnCore.findTrunkExtent(pos) { level.getBlockState(it) }
        }

        val heightRange = (extent.last - extent.first).coerceAtLeast(1)
        val tilt = TreeBurnCore.calculateTiltFactor(pos.y, extent.first, heightRange, explosionPower.toDouble())

        val dx = (pos.x - zoneCenterX).toFloat()
        val dz = (pos.z - zoneCenterZ).toFloat()
        val invMag = 1.0f / sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        val newX = pos.x + (dx * invMag * tilt).roundToInt()
        val newZ = pos.z + (dz * invMag * tilt).roundToInt()
        val burnt = TreeBurnCore.burntReplacementFor(block).defaultBlockState()

        if (tilt > 0.0 && (newX != pos.x || newZ != pos.z)) {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2)
            level.setBlock(BlockPos(newX, pos.y, newZ), burnt, 2)
        } else {
            level.setBlock(pos, burnt, 2)
        }
        return true
    }
}