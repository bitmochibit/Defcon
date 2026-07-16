package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import kotlin.random.Random

data class TransformationRule(
    val name: String,
    val priority: Int = 0,
    val condition: TransformationCondition,
    val outcome: TransformationOutcome,
) {
    @Suppress("NOTHING_TO_INLINE")
    inline fun matches(
        state: BlockState,
        explosionPower: Float,
    ): Boolean = condition.matches(state, explosionPower)

    @Suppress("NOTHING_TO_INLINE")
    inline fun transform(
        state: BlockState,
        explosionPower: Float,
        random: Random,
        x: Int = 0,
        y: Int = 0,
        z: Int = 0,
    ): BlockState = outcome.transform(state, explosionPower, random, x, z, y)
}