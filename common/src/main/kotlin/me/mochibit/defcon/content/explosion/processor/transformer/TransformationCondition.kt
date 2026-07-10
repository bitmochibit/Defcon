package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.Block
import net.minecraft.world.level.block.state.BlockState

sealed class TransformationCondition {
    abstract fun matches(
        state: BlockState,
        explosionPower: Float,
    ): Boolean

    data class MaterialSet(
        val blocks: Set<BlockState>,
    ) : TransformationCondition() {
        override fun matches(
            state: BlockState,
            explosionPower: Float,
        ): Boolean = state in blocks
    }

    data class MaterialCategory(
        val predicate: (BlockState) -> Boolean,
    ) : TransformationCondition() {
        override fun matches(
            state: BlockState,
            explosionPower: Float,
        ): Boolean = predicate(state)
    }

    data class SpecificMaterial(
        val state: BlockState,
    ) : TransformationCondition() {
        override fun matches(
            state: BlockState,
            explosionPower: Float,
        ): Boolean = this.state == state
    }

    data class PowerThreshold(
        val condition: TransformationCondition,
        val minPower: Float? = null,
        val maxPower: Float? = null,
    ) : TransformationCondition() {
        override fun matches(
            state: BlockState,
            explosionPower: Float,
        ): Boolean {
            val powerMatches =
                when {
                    minPower != null && explosionPower < minPower -> false
                    maxPower != null && explosionPower > maxPower -> false
                    else -> true
                }
            return powerMatches && condition.matches(state, explosionPower)
        }
    }

    data class Combined(
        val conditions: List<TransformationCondition>,
        val operator: LogicalOperator = LogicalOperator.AND,
    ) : TransformationCondition() {
        override fun matches(
            state: BlockState,
            explosionPower: Float,
        ): Boolean =
            when (operator) {
                LogicalOperator.AND -> conditions.all { it.matches(state, explosionPower) }
                LogicalOperator.OR -> conditions.any { it.matches(state, explosionPower) }
            }
    }

    enum class LogicalOperator { AND, OR }
}