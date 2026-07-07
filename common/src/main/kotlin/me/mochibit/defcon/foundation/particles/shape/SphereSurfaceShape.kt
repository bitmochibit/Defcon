package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import kotlin.math.acos
import kotlin.math.cos
import kotlin.math.sin

/**
 * Emitter that generates particles on the surface of a sphere.
 */
class SphereSurfaceShape(
    xzRadius: Float,
    yRadius: Float,
    var minY: Double = -yRadius.toDouble(),
    var maxY: Double = yRadius.toDouble(),
    var minXZ: Double = -xzRadius.toDouble(),
    var maxXZ: Double = xzRadius.toDouble(),
    var skipBottomFace: Boolean = true,
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

    var xzRadius = xzRadius
        set(value) {
            require(value > 0f) { "XZ radius must be positive" }
            val (newMin, newMax) = validateRadiusUpdate(field, value, minXZ, maxXZ)
            field = value
            minXZ = newMin
            maxXZ = newMax
        }

    var yRadius = yRadius
        set(value) {
            require(value > 0f) { "Y radius must be positive" }
            val (newMin, newMax) = validateRadiusUpdate(field, value, minY, maxY)
            field = value
            minY = newMin
            maxY = newMax
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

        // Generate spherical coordinates with density adjustment
        val theta = nextAngle()

        // Adjust phi based on skipBottomFace parameter and apply density
        val phiBase = if (skipBottomFace) {
            applyDensity(nextDouble()) // Range [0, 1]
        } else {
            applyDensity(nextDouble() * 2 - 1) // Range [-1, 1]
        }

        val phi = acos(phiBase) // Range depends on skipBottomFace

        var x = sin(phi) * cos(theta) * xzRadius
        var y = cos(phi) * yRadius
        var z = sin(phi) * sin(theta) * xzRadius

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