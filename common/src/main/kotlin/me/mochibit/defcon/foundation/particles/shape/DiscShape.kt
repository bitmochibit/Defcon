package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Disc-shaped emitter that fills a circular area.
 */
class DiscShape(
    var radiusX: Float,
    var radiusZ: Float,
    var minX: Double = -radiusX.toDouble(),
    var maxX: Double = radiusX.toDouble(),
    var minZ: Double = -radiusZ.toDouble(),
    var maxZ: Double = radiusZ.toDouble(),
    var excludedRadius: Double? = null,
    density: Float = 1.0f
) : EmitterShape(density), ExclusionSupport {

    init {
        require(radiusX > 0f) { "X radius must be positive" }
        require(radiusZ > 0f) { "Z radius must be positive" }
        validateBounds()
    }

    private fun validateBounds() {
        if (minX > maxX) {
            minX = maxX.also { maxX = minX }
        }
        if (minZ > maxZ) {
            minZ = maxZ.also { maxZ = minZ }
        }
    }

    override val minHeight: Double
        get() = 0.0
    override val maxHeight: Double
        get() = 0.0
    override val minWidth: Double
        get() = minX
    override val maxWidth: Double
        get() = maxX
    override val minDepth: Double
        get() = minZ
    override val maxDepth: Double
        get() = maxZ

    override fun maskLoc(location: Vector3d) {
        validateBounds()

        // Generate polar coordinates with density adjustment
        val theta = nextAngle()

        // Square root for uniform area distribution
        val r = sqrt(applyDensity(nextDouble()))

        var x = r * radiusX * cos(theta)
        var z = r * radiusZ * sin(theta)

        // Apply exclusion zone
        x = applyExclusion(x, z, excludedRadius)
        z = applyExclusion(z, x, excludedRadius)

        // Apply constraints
        x = x.constrain(minX, maxX)
        z = z.constrain(minZ, maxZ)

        // Apply final position (no y adjustment for disc)
        location.add(x, 0.0, z)
    }
}