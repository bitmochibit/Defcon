package me.mochibit.defcon.content.explosion.processor.carver

import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap

interface CraterCarveContext {
    fun getState(x: Int, y: Int, z: Int): BlockState
    fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean = false)
    fun heightAt(x: Int, z: Int, type: Heightmap.Types): Int
}

class RuntimeCraterCarveContext(
    private val level: ServerLevel,
    private val blockChanger: BlockChanger,
) : CraterCarveContext {
    override fun getState(x: Int, y: Int, z: Int) = level.getBlockState(x, y, z)
    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        blockChanger.addBlockChange(x, y, z, state, updateBlock)
    }
    override fun heightAt(x: Int, z: Int, type: Heightmap.Types) = level.getHeight(type, x, z)
}

class WorldGenCraterCarveContext(private val chunk: LevelChunk) : CraterCarveContext {
    private val mutablePos = BlockPos.MutableBlockPos()
    override fun getState(x: Int, y: Int, z: Int): BlockState = chunk.getBlockState(x,y,z)
    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        chunk.setBlockState(mutablePos.set(x, y, z), state, false)
    }
    override fun heightAt(x: Int, z: Int, type: Heightmap.Types) = chunk.getHeight(type, x, z)
}