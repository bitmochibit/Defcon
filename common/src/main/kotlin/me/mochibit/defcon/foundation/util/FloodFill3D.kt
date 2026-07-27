package me.mochibit.defcon.foundation.util

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import net.minecraft.core.BlockPos
import net.minecraft.core.Direction
import net.minecraft.core.registries.BuiltInRegistries
import net.minecraft.world.level.LevelReader
import net.minecraft.world.level.block.state.BlockState
import java.util.ArrayDeque
import java.util.concurrent.ConcurrentHashMap

object FloodFill3D {

    fun getFloodFillAdvanced(
        startPos: BlockPos,
        level: LevelReader,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
        ignoreEmpty: Boolean = false,
        blockFilter: ((BlockPos, BlockState) -> Boolean)? = null,
    ): Map<BlockState, MutableSet<BlockPos>> {
        val result = HashMap<BlockState, MutableSet<BlockPos>>()
        val visited = LongOpenHashSet()
        val queue = ArrayDeque<BlockPos>()

        queue.add(startPos)
        visited.add(startPos.asLong())

        while (queue.isNotEmpty() && visited.size < maxRange) {
            val current = queue.poll()
            val state = level.getBlockState(current)

            if (!isValidBlock(state, nonSolidOnly, ignoreEmpty) ||
                (blockFilter != null && !blockFilter(current, state))
            ) continue

            result.getOrPut(state) { HashSet() }.add(current)

            for (direction in Direction.entries) {
                val next = current.relative(direction)
                if (visited.add(next.asLong())) {
                    queue.add(next)
                }
            }
        }

        return result
    }

    fun getFloodFill(
        startPos: BlockPos,
        level: LevelReader,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
    ): List<BlockPos> {
        val visited = LongOpenHashSet()
        val result = ArrayList<BlockPos>()
        val queue = ArrayDeque<BlockPos>()

        queue.add(startPos)
        visited.add(startPos.asLong())

        while (queue.isNotEmpty() && result.size < maxRange) {
            val current = queue.poll()
            val state = level.getBlockState(current)

            if (nonSolidOnly && state.isSolid) continue

            result.add(current)

            for (direction in Direction.entries) {
                val next = current.relative(direction)
                if (visited.add(next.asLong())) {
                    queue.add(next)
                }
            }
        }

        return result
    }


    suspend fun getFloodFillAsync(
        startPos: BlockPos,
        level: LevelReader,
        maxRange: Int,
        nonSolidOnly: Boolean = false,
    ): List<BlockPos> = withContext(Dispatchers.Default) {
        getFloodFill(startPos, level, maxRange, nonSolidOnly)
    }

    private fun isValidBlock(
        state: BlockState,
        nonSolidOnly: Boolean,
        ignoreEmpty: Boolean,
    ): Boolean =
        (!nonSolidOnly || !state.isSolid) &&
                (!ignoreEmpty || !state.isAir)

}