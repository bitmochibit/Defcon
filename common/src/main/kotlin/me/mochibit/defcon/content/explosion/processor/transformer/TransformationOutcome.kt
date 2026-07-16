package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import kotlin.random.Random

sealed class TransformationOutcome {
    abstract fun transform(
        currentState: BlockState,
        explosionPower: Float,
        random: Random,
        x: Int = 0,
        z: Int = 0,
        y: Int = 0,
    ): BlockState

    data class ToMaterial(
        val targetState: BlockState,
    ) : TransformationOutcome() {

        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = this.targetState
    }

    data class ToRandomMaterial(
        val targetStateList: Set<BlockState>,
    ) : TransformationOutcome() {

        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = targetStateList.random(random)
    }

    data class ToPalette(
        val targetPalette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = targetPalette.getRandom()
    }

    data class ToPaletteWithNoise(
        val targetPalette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = targetPalette.getWithNoise(x, z, y)
    }

    data class ChanceOutcome(
        val chance: Float, // 0.0 to 1.0
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState =
            if (random.nextFloat() < chance) {
                trueOutcome.transform(currentState, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(currentState, explosionPower, random, x, z, y)
            }
    }

    data class ConditionalOutcome(
        val condition: (BlockState, Float) -> Boolean,
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState =
            if (condition(currentState, explosionPower)) {
                trueOutcome.transform(currentState, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(currentState, explosionPower, random, x, z, y)
            }
    }

    data object NoTransformation : TransformationOutcome() {
        override fun transform(
            currentState: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = currentState
    }
}