package com.mochibit.nuclearcraft.utils

object Math {
    fun clamp(value: Double, min: Double, max: Double): Double {
        return if (value < min) min else if (value > max) max else value
    }
}