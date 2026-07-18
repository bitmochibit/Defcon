package me.mochibit.defcon.content.explosion.processor.carver

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap
import me.mochibit.defcon.content.explosion.processor.PackedPos
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.TreeBurner
import net.minecraft.core.BlockPos
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.WorldGenLevel
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess

data class WorldGenColumnCarveContext(
    private val level: WorldGenLevel,
    val chunk: ChunkAccess,
    private val center: BlockPos
) : ColumnCarveContext {
    private val lightPos = BlockPos.MutableBlockPos()
    private val blockPos = BlockPos.MutableBlockPos()
    private val treeBurner = TreeBurner(this, center)

    private val originalCache = Long2ObjectOpenHashMap<BlockState>()

    override fun getState(x: Int, y: Int, z: Int): BlockState {
        blockPos.set(x, y, z)
        return level.getBlockState(blockPos)
    }

    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        val inChunk = (x shr 4) == chunk.pos.x && (z shr 4) == chunk.pos.z
        if (!inChunk) return
        blockPos.set(x, y, z)
        chunk.setBlockState(blockPos, state, true)
    }

    override fun originalStateAt(x: Int, y: Int, z: Int): BlockState {
        val inChunk = (x shr 4) == chunk.pos.x && (z shr 4) == chunk.pos.z
        if (!inChunk) return Blocks.STONE.defaultBlockState()
        val key = PackedPos(x, y, z).packed
        return originalCache.getOrPut(key) {
            blockPos.set(x, y, z)
            level.getBlockState(blockPos)
        }
    }

    override fun skylightAt(x: Int, y: Int, z: Int): Int {
        lightPos.set(x, y, z)
        return level.getBrightness(LightLayer.SKY, lightPos)
    }

    override fun isTreeBlock(x: Int, y: Int, z: Int): Boolean = treeBurner.isTreeBlock(x, y, z)

    override fun isLogOrWood(state: BlockState): Boolean {
        val block = state.block
        return block in TreeBurnCore.LOG_BLOCKS || block in TreeBurnCore.WOOD_BLOCKS
    }

    override fun findLocalTrunkBase(x: Int, y: Int, z: Int): Int =
        treeBurner.findLocalTrunkBase(x, y, z)

    override fun burnTreeBlock(x: Int, y: Int, z: Int, power: Double, trunkTopY: Int?, trunkBaseY: Int?) {
        treeBurner.processTreeBlockAt(x, y, z, power, trunkTopY, trunkBaseY)
    }

    override fun isPosAlreadyBurnedByTree(x: Int, y: Int, z: Int): Boolean =
        treeBurner.isPosProcessed(x, y, z)
}