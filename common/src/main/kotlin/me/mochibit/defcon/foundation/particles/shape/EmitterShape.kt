package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import java.security.SecureRandom
import kotlin.math.*

/**
 * Base class for all particle emitter shapes.
 * Handles the positioning of particles according to specific geometric distributions.
 */
sealed class EmitterShape(
    var density: Float = 1.0f,
) {
    /**
     * Minimum height value for the emitter
     */
    abstract val minHeight: Double

    /**
     * Maximum height value for the emitter
     */
    abstract val maxHeight: Double

    /**
     * Minimum width value for the emitter
     */
    abstract val minWidth: Double

    /**
     * Maximum width value for the emitter
     */
    abstract val maxWidth: Double

    /**
     * Minimum depth value for the emitter
     */
    abstract val minDepth: Double

    /**
     * Maximum depth value for the emitter
     */
    abstract val maxDepth: Double

    // Use shared random number generator for better performance and security
    protected val random = SecureRandom()

    init {
        require(density > 0f) { "Density must be positive" }
    }

    /**
     * Modifies the provided location vector according to the shape's distribution.
     * Higher density values will result in more particles being clustered together.
     *
     * @param location The vector to be modified
     */
    abstract fun maskLoc(location: Vector3d)

    /**
     * Applies density-based adjustment to a distribution value.
     *
     * @param value The original distribution value between 0 and 1
     * @return A modified value adjusted by density
     */
    protected fun applyDensity(value: Double): Double {
        return when {
            // Values > 1 concentrate particles toward center
            density > 1.0f -> value.pow(density.toDouble())
            // Values < 1 spread particles toward edges
            density < 1.0f -> value.pow(1.0 / (2.0 - density))
            // Value = 1 maintains uniform distribution
            else -> value
        }
    }

    /**
     * Generates a random number between 0 and 1
     */
    protected fun nextDouble(): Double = random.nextDouble()

    /**
     * Generates a random number between 0 and the specified bound
     */
    protected fun nextDouble(bound: Double): Double = random.nextDouble() * bound

    /**
     * Generates a random angle in radians (0 to 2π)
     */
    protected fun nextAngle(): Double = nextDouble(2 * PI)

    /**
     * Constrains a value between min and max bounds.
     */
    protected fun Double.constrain(min: Double, max: Double): Double = coerceIn(min, max)

    /**
     * Creates a validation function for property setters
     */
    protected fun validateRadiusUpdate(field: Float, value: Float, minVar: Double, maxVar: Double): Pair<Double, Double> {
        val newMin = if (minVar == -field.toDouble()) -value.toDouble() else minVar
        val newMax = if (maxVar == field.toDouble()) value.toDouble() else maxVar
        return Pair(newMin, newMax)
    }
}

