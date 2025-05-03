/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.utils

import kotlin.math.PI

object MathFunctions {
    const val TAU = PI * 2
    const val EPSILON = 0.00001;
    const val EPSILON2 = EPSILON * EPSILON;

    const val MATH_SQRT2 = 1.4142135623730951;
    const val MATH_SQRT12 = 0.7071067811865476;



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

    fun remap(value: Double, inValueMin: Double, inValueMax: Double, outValueMin: Double, outValueMax: Double): Double {
        return (value - inValueMin) * (outValueMax - outValueMin) / (inValueMax - inValueMin) + outValueMin;
    }


}