package com.mochibit.nuclearcraft.utils

object Math {
    /**
     * Clamps a value to a given range.
     * @param value The value to clamp
     * @param min The minimum value
     * @param max The maximum value
     * @return The clamped value
     */
    fun clamp(value: Double, min: Double, max: Double): Double {
        return if (value < min) min else if (value > max) max else value
    }

    /**
     * Linearly interpolates between a and b by t.
     * The parameter t is clamped to the range [0, 1].
     * @param a The first value
     * @param b The second value
     * @param t The interpolation parameter
     * @return The interpolated value
     */
    fun lerp(a: Double, b: Double, t: Double): Double {
        val clampedT = clamp(t, 0.0, 1.0)
        return a + clampedT * (b - a)
    }

    fun lerp(a: Float, b: Float, t: Double): Float {
        return this.lerp(a.toDouble(), b.toDouble(), t).toFloat()
    }

    fun lerp(a: Int, b: Int, t: Double): Int {
        return this.lerp(a.toDouble(), b.toDouble(), t).toInt()
    }

}