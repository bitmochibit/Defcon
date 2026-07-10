package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import kotlin.random.Random

sealed class TransformationOutcome {
    abstract fun transform(
        state: BlockState,
        explosionPower: Float,
        random: Random,
        x: Int = 0,
        z: Int = 0,
        y: Int = 0,
    ): BlockState

    data class ToMaterial(
        val state: BlockState,
    ) : TransformationOutcome() {

        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = state
    }

    data class ToRandomMaterial(
        val stateList: Set<BlockState>,
    ) : TransformationOutcome() {

        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = stateList.random(random)
    }

    data class ToPalette(
        val palette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = palette.getRandom()
    }

    data class ToPaletteWithNoise(
        val palette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = palette.getWithNoise(x, z, y)
    }

    data class ChanceOutcome(
        val chance: Float, // 0.0 to 1.0
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState =
            if (random.nextFloat() < chance) {
                trueOutcome.transform(state, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(state, explosionPower, random, x, z, y)
            }
    }

    data class ConditionalOutcome(
        val condition: (BlockState, Float) -> Boolean,
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState =
            if (condition(state, explosionPower)) {
                trueOutcome.transform(state, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(state, explosionPower, random, x, z, y)
            }
    }

    data object NoTransformation : TransformationOutcome() {
        override fun transform(
            state: BlockState,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): BlockState = state
    }
}