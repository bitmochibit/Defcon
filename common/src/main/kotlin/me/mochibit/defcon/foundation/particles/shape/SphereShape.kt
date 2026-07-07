package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Sphere-shaped emitter that fills the entire volume.
 */
class SphereShape(
    var xzRadius: Float,
    var yRadius: Float,
    var minY: Double = -yRadius.toDouble(),
    var maxY: Double = yRadius.toDouble(),
    var minXZ: Double = -xzRadius.toDouble(),
    var maxXZ: Double = xzRadius.toDouble(),
    var excludedXZRadius: Double? = null,
    var excludedYRadius: Double? = null,
    density: Float = 1.0f
) : EmitterShape(density), ExclusionSupport {

    init {
        require(xzRadius > 0f) { "XZ radius must be positive" }
        require(yRadius > 0f) { "Y radius must be positive" }
        validateBounds()
    }

    private fun validateBounds() {
        if (minY > maxY) {
            minY = maxY.also { maxY = minY }
        }
        if (minXZ > maxXZ) {
            minXZ = maxXZ.also { maxXZ = minXZ }
        }
    }

    override val minHeight: Double
        get() = minY
    override val maxHeight: Double
        get() = maxY
    override val minWidth: Double
        get() = minXZ
    override val maxWidth: Double
        get() = maxXZ
    override val minDepth: Double
        get() = minXZ
    override val maxDepth: Double
        get() = maxXZ

    override fun maskLoc(location: Vector3d) {
        validateBounds()

        // Use density to control radial distribution
        // Cube root for uniform volumetric distribution
        val r = applyDensity(nextDouble()).pow(1.0 / 3.0)

        // Generate spherical coordinates
        val theta = nextAngle()
        val phi = acos(2 * nextDouble() - 1)

        var x = r * sin(phi) * cos(theta) * xzRadius
        var y = r * cos(phi) * yRadius
        var z = r * sin(phi) * sin(theta) * xzRadius

        // Apply exclusion zones
        x = applyExclusion(x, z, excludedXZRadius)
        z = applyExclusion(z, x, excludedXZRadius)
        y = applyExclusion(y, excludedRadius = excludedYRadius)

        // Apply constraints
        x = x.constrain(minXZ, maxXZ)
        z = z.constrain(minXZ, maxXZ)
        y = y.constrain(minY, maxY)

        // Apply final position
        location.add(x, y, z)
    }
}