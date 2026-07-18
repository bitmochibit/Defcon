package me.mochibit.defcon.content.explosion.processor.carver

import me.mochibit.defcon.content.explosion.processor.TreeBurnCore
import me.mochibit.defcon.content.explosion.processor.TreeBurner
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.state.BlockState

data class RuntimeColumnCarveContext(private val level: ServerLevel, private val center: BlockPos) :
    ColumnCarveContext {
    private val blockChanger = BlockChanger.getInstance(level)
    private val lightPos = BlockPos.MutableBlockPos()
    private val treePos = BlockPos.MutableBlockPos()

    private val treeBurner = TreeBurner(this, center)

    override fun getState(x: Int, y: Int, z: Int): BlockState = level.getBlockState(x, y, z)

    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        blockChanger.addBlockChange(x, y, z, state, updateBlock = updateBlock)
    }

    override fun skylightAt(x: Int, y: Int, z: Int): Int {
        lightPos.set(x, y, z)
        return level.getBrightness(LightLayer.SKY, lightPos)
    }

    override fun originalStateAt(
        x: Int,
        y: Int,
        z: Int
    ): BlockState = getState(x,y,z)

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