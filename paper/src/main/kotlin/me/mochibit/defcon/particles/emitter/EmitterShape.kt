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

package me.mochibit.defcon.particles.emitter

import me.mochibit.defcon.utils.MathFunctions
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
    protected fun nextAngle(): Double = nextDouble(MathFunctions.TAU)

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
            normalizedRandom * MathFunctions.TAU
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

/**
 * Line-shaped emitter that distributes particles along a straight line.
 */
class LineShape(
    var length: Float,
    var direction: Direction = Direction.Y,
    density: Float = 1.0f
) : EmitterShape(density) {

    /**
     * Direction enum represents the axis along which the line extends
     */
    enum class Direction { X, Y, Z }

    init {
        require(length > 0f) { "Length must be positive" }
    }

    override val minHeight: Double
        get() = if (direction == Direction.Y) 0.0 else -0.5
    override val maxHeight: Double
        get() = if (direction == Direction.Y) length.toDouble() else 0.5

    override val minWidth: Double
        get() = if (direction == Direction.X) 0.0 else -0.5
    override val maxWidth: Double
        get() = if (direction == Direction.X) length.toDouble() else 0.5

    override val minDepth: Double
        get() = if (direction == Direction.Z) 0.0 else -0.5
    override val maxDepth: Double
        get() = if (direction == Direction.Z) length.toDouble() else 0.5

    override fun maskLoc(location: Vector3d) {
        // Apply density to control distribution along the line
        val pos = applyDensity(nextDouble()) * length

        // Apply position based on direction
        when (direction) {
            Direction.X -> location.add(pos, 0.0, 0.0)
            Direction.Y -> location.add(0.0, pos, 0.0)
            Direction.Z -> location.add(0.0, 0.0, pos)
        }
    }
}