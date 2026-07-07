package me.mochibit.defcon.foundation.particles.shape

import kotlin.math.sign
import kotlin.math.sqrt

/**
 * Interface for shapes that support exclusion zones
 */
interface ExclusionSupport {
    /**
     * Apply exclusion to a coordinate based on its distance from center
     *
     * @param coordinate The coordinate value to check
     * @param secondary Optional secondary coordinate for 2D distance calculations
     * @param excludedRadius The radius of the exclusion zone
     * @return The adjusted coordinate value
     */
    fun applyExclusion(coordinate: Double, secondary: Double? = null, excludedRadius: Double? = null): Double {
        if (excludedRadius == null) return coordinate

        val distanceSquared = if (secondary != null) {
            coordinate * coordinate + secondary * secondary
        } else {
            coordinate * coordinate
        }

        if (distanceSquared < excludedRadius * excludedRadius) {
            val displacement = excludedRadius - sqrt(distanceSquared)
            return coordinate + displacement * sign(coordinate)
        }
        return coordinate
    }
}