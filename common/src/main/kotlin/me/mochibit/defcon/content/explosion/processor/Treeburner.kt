package me.mochibit.defcon.content.explosion.processor

import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap
import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarveContext
import net.minecraft.core.BlockPos
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import org.joml.Vector2f
import kotlin.math.sqrt

/**
 * Packs a block position into a single Long. Cheaper than BlockPos for use as
 * set keys in hot paths, especially paired with fastutil's primitive collections.
 */
@JvmInline
value class PackedPos private constructor(val packed: Long) {
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
            val path = BuiltInRegistries.BLOCK.getKey(block).path.orEmpty()
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

    fun isLeafBlockType(block: Block): Boolean = block in LEAF_BLOCKS

    fun isTreeBlockType(block: Block): Boolean = block in TREE_BLOCKS

    fun burntReplacementFor(block: Block): Block = BURNT_REPLACEMENTS[block] ?: Blocks.POLISHED_BASALT

    data class TreeStructure(val logs: List<BlockPos>, val leaves: List<BlockPos>) {
        val isEmpty: Boolean get() = logs.isEmpty()
    }

    inline fun findTrunkBaseY(
        startX: Int, startY: Int, startZ: Int, maxSearch: Int = 40,
        getState: (x: Int, y: Int, z: Int) -> BlockState,
    ): Int {
        var posY = startY
        var lastLogY = startY
        repeat(maxSearch) {
            val block = getState(startX, posY, startZ).block
            if (block in LOG_BLOCKS || block in WOOD_BLOCKS) {
                lastLogY = posY
                posY -= 1
            } else {
                return lastLogY
            }
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
        if (blockY <= treeMinHeight) return 0.0
        val heightFactor = (blockY - treeMinHeight).toDouble() / heightRange
        return heightFactor * explosionPower * 6.0
    }

    /**
     * For identifying a column as a tree like structure, it would look for ordered leaf block -> log block, from up to down
     */
    inline fun isColumnTreeLike(
        x: Int, y: Int, z: Int, maxSearch: Int = 40,
        getState: (x: Int, y: Int, z: Int) -> BlockState,
    ): Boolean {
        var foundLeaf = false

        for (offsetY in 0 until maxSearch) {
            val block = getState(x, y - offsetY, z).block

            when {
                block in LEAF_BLOCKS -> {
                    foundLeaf = true
                }
                block in LOG_BLOCKS || block in WOOD_BLOCKS -> {
                    return foundLeaf
                }
                else -> {
                    return false
                }
            }
        }

        // Abbiamo esaurito i tentativi (maxSearch) senza trovare un tronco
        return false
    }
}


/**
 * Runtime (post-worldgen) tree burning, driven by the shockwave processor.
 * Always resolves and consumes an entire connected tree at once: leaves are
 * always removed, trunks are always tilted and turned to basalt/blackstone,
 * unless the explosion is close enough to obliterate the tree entirely.
 */
class TreeBurner(
    private val ctx: ColumnCarveContext,
    private val center: BlockPos,
    /** Explosion power at/above which trees are removed entirely instead of being burnt/tilted. */
    private val fullRemovalPowerThreshold: Double = 0.85,
    /** Max change in tilt magnitude (in blocks) allowed between two vertically adjacent trunk blocks. */
    private val maxTiltStepPerBlock: Double = 1.0,
) {
    private val processedTreeBlocks = LongOpenHashSet()

    // Tracks the last tilt offset applied per trunk column (packed x,z), so the
    // trunk bends gradually instead of jumping between independently computed offsets.
    private val columnTiltOffset = Long2DoubleOpenHashMap().apply { defaultReturnValue(Double.NaN) }

    fun isPosProcessed(x: Int, y: Int, z: Int) = processedTreeBlocks.contains(PackedPos(x, y, z).packed)

    fun isTreeBlock(x: Int, y: Int, z: Int): Boolean = TreeBurnCore.isTreeBlockType(ctx.getState(x, y, z).block)

    fun isLeafBlock(x: Int, y: Int, z: Int): Boolean = TreeBurnCore.isLeafBlockType(ctx.getState(x, y, z).block)

    fun processTreeBlockAt(x: Int, y: Int, z: Int, explosionPower: Double, trunkTopY: Int?, trunkBaseY: Int?) {
        if (!processedTreeBlocks.add(PackedPos(x, y, z).packed)) return
        val state = ctx.getState(x, y, z)
        val block = state.block

        if (explosionPower >= fullRemovalPowerThreshold) {
            ctx.setState(x, y, z, Blocks.AIR.defaultBlockState())
            return
        }
        if (block in TreeBurnCore.LEAF_BLOCKS) {
            ctx.setState(x, y, z, Blocks.AIR.defaultBlockState())
            return
        }

        val top = trunkTopY ?: y
        val base = trunkBaseY ?: y
        val heightRange = (top - base).coerceAtLeast(1)
        val rawTilt = TreeBurnCore.calculateTiltFactor(y, base, heightRange, explosionPower)
        val tilt = steppedTilt(x, z, y, base, rawTilt)
        val burnt = TreeBurnCore.burntReplacementFor(block).defaultBlockState()
        val direction = shockwaveDirectionFrom(x, z)
        applyTiltedTrunk(x, y, z, base, tilt, direction, burnt)
    }


    /**
     * Clamps the tilt so it only ever changes by [maxTiltStepPerBlock] relative to the
     * previously processed block in the same trunk column. Columns are always scanned
     * top-to-bottom (see ColumnCarver), so this makes the trunk bend gradually instead
     * of jumping between independently computed offsets, and guarantees the block
     * touching the ground (y == base) stays fixed.
     */
    private fun steppedTilt(x: Int, z: Int, y: Int, base: Int, rawTilt: Double): Double {
        val key = packColumn(x, z)
        if (y <= base) {
            columnTiltOffset.remove(key)
            return 0.0
        }

        val previous = columnTiltOffset.get(key)
        val stepped = if (previous.isNaN()) {
            rawTilt
        } else {
            val delta = (rawTilt - previous).coerceIn(-maxTiltStepPerBlock, maxTiltStepPerBlock)
            previous + delta
        }
        columnTiltOffset.put(key, stepped)
        return stepped
    }

    private fun packColumn(x: Int, z: Int): Long = (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)

    fun findLocalTrunkBase(x: Int, y: Int, z: Int): Int =
        TreeBurnCore.findTrunkBaseY(x, y, z) { x, y, z -> ctx.getState(x, y, z) }

    private fun shockwaveDirectionFrom(originX: Int, originZ: Int): Vector2f {
        val dx = (originX - center.x).toFloat()
        val dz = (originZ - center.z).toFloat()
        val invMag = 1.0f / sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
        return Vector2f(dx * invMag, dz * invMag)
    }

    private fun applyTiltedTrunk(x: Int, y: Int, z: Int, base: Int, tilt: Double, direction: Vector2f, burntState: BlockState) {
        if (y <= base || tilt <= 0.0) {
            ctx.setState(x, y, z, burntState)
            return
        }

        val newX = x + (direction.x * tilt).toInt()
        val newZ = z + (direction.y * tilt).toInt()
        if (newX == x && newZ == z) {
            ctx.setState(x, y, z, burntState)
            return
        }

        if (isSolidNonTreeGround(newX, y, newZ)) {
            ctx.setState(x, y, z, burntState)
            return
        }

        ctx.setState(x, y, z, Blocks.AIR.defaultBlockState())
        ctx.setState(newX, y, newZ, burntState)
        processedTreeBlocks.add(PackedPos(newX, y, newZ).packed)
    }

    private fun isSolidNonTreeGround(x: Int, y: Int, z: Int): Boolean {
        val state = ctx.getState(x, y, z)
        return !state.isAir && !TreeBurnCore.isTreeBlockType(state.block)
    }
}