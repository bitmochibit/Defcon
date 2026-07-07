package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Ring/torus-shaped emitter that generates particles on the surface.
 */
class RingSurfaceShape(
    var ringRadius: Float,
    var tubeRadius: Float,
    density: Float = 1.0f
) : EmitterShape(density) {

    init {
        require(ringRadius > 0f) { "Ring radius must be positive" }
        require(tubeRadius > 0f) { "Tube radius must be positive" }
        require(ringRadius > tubeRadius) { "Ring radius must be greater than tube radius" }
    }

    override val minHeight: Double
        get() = -tubeRadius.toDouble()
    override val maxHeight: Double
        get() = tubeRadius.toDouble()
    override val minWidth: Double
        get() = -(ringRadius + tubeRadius).toDouble()
    override val maxWidth: Double
        get() = (ringRadius + tubeRadius).toDouble()
    override val minDepth: Double
        get() = -(ringRadius + tubeRadius).toDouble()
    override val maxDepth: Double
        get() = (ringRadius + tubeRadius).toDouble()

    override fun maskLoc(location: Vector3d) {
        // Generate toroidal coordinates with density adjustment
        val phi = nextAngle()

        // Apply density to control distribution along the ring
        val effectiveTheta = if (density != 1.0f) {
            // Map random value to periodic function to maintain continuity
            val normalizedRandom = applyDensity(nextDouble())
            normalizedRandom * 2 * PI
        } else {
            nextAngle()
        }

        // Parametrize the torus shape
        val x = (ringRadius + tubeRadius * cos(phi)) * cos(effectiveTheta)
        val y = tubeRadius * sin(phi)
        val z = (ringRadius + tubeRadius * cos(phi)) * sin(effectiveTheta)

        // Apply final position
        location.add(x, y, z)
    }
}