package me.mochibit.defcon.content.explosion.processor.carver

import net.minecraft.world.level.block.state.BlockState

interface ColumnCarveContext {
    fun getState(x: Int, y: Int, z: Int): BlockState

    fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean = true)

    fun skylightAt(x: Int, y: Int, z: Int): Int

    fun originalStateAt(x: Int, y: Int, z: Int): BlockState

    fun isTreeBlock(x: Int, y: Int, z: Int): Boolean
    fun isLogOrWood(state: BlockState): Boolean
    fun findLocalTrunkBase(x: Int, y: Int, z: Int): Int?
    fun burnTreeBlock(x: Int, y: Int, z: Int, power: Double, trunkTopY: Int?, trunkBaseY: Int?)

    fun isPosAlreadyBurnedByTree(x: Int, y: Int, z: Int): Boolean
}