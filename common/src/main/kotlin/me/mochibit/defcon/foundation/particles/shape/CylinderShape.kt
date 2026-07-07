package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Cylinder-shaped emitter that fills the entire volume.
 */
class CylinderShape(
    var radiusX: Float,
    var radiusZ: Float,
    var height: Float,
    var minX: Double = -radiusX.toDouble(),
    var maxX: Double = radiusX.toDouble(),
    var minZ: Double = -radiusZ.toDouble(),
    var maxZ: Double = radiusZ.toDouble(),
    var excludedXZRadius: Double? = null,
    density: Float = 1.0f
) : EmitterShape(density), ExclusionSupport {

    init {
        require(radiusX > 0f) { "X radius must be positive" }
        require(radiusZ > 0f) { "Z radius must be positive" }
        require(height > 0f) { "Height must be positive" }
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
        get() = height.toDouble()
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

        // Generate cylindrical coordinates with density adjustment
        val theta = nextAngle()

        // Square root gives uniform area distribution
        val r = sqrt(applyDensity(nextDouble()))
        val h = applyDensity(nextDouble()) * height

        var x = r * radiusX * cos(theta)
        var z = r * radiusZ * sin(theta)

        // Apply exclusion zone
        x = applyExclusion(x, z, excludedXZRadius)
        z = applyExclusion(z, x, excludedXZRadius)

        // Apply constraints
        x = x.constrain(minX, maxX)
        z = z.constrain(minZ, maxZ)

        // Apply final position
        location.add(x, h, z)
    }
}