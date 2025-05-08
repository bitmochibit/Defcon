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
import kotlin.math.*

/**
 * Base class for all particle emitter shapes.
 * Handles the positioning of particles according to specific geometric distributions.
 */
abstract class EmitterShape(
    var density: Float = 1.0f
) {
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
     * Constrains a value between min and max bounds.
     */
    protected fun Double.constrain(min: Double, max: Double): Double = coerceIn(min, max)
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
) : EmitterShape(density) {

    override fun maskLoc(location: Vector3d) {
        if (minY > maxY) {
            minY = maxY.also { maxY = minY }
        }

        // Use density to control radial distribution
        // Cube root for uniform volumetric distribution
        val r = applyDensity(Math.random()).pow(1.0/3.0)

        // Generate spherical coordinates
        val theta = Math.random() * MathFunctions.TAU
        val phi = acos(2 * Math.random() - 1)

        var x = r * sin(phi) * cos(theta) * xzRadius
        var y = r * cos(phi) * yRadius
        var z = r * sin(phi) * sin(theta) * xzRadius

        // Apply exclusion zones
        x = applyExclusionXZ(x, z, excludedXZRadius)
        z = applyExclusionXZ(z, x, excludedXZRadius)
        y = applyExclusionY(y, excludedYRadius)

        // Apply constraints
        x = x.constrain(minXZ, maxXZ)
        z = z.constrain(minXZ, maxXZ)
        y = y.constrain(minY, maxY)

        // Apply final position
        location.add(x, y, z)
    }

    private fun applyExclusionXZ(primary: Double, secondary: Double, excludedRadius: Double?): Double {
        if (excludedRadius == null) return primary

        val distanceSquared = primary * primary + secondary * secondary
        if (distanceSquared < excludedRadius * excludedRadius) {
            val displacement = excludedRadius - sqrt(distanceSquared)
            return primary + displacement * sign(primary)
        }
        return primary
    }

    private fun applyExclusionY(y: Double, excludedRadius: Double?): Double {
        if (excludedRadius == null) return y

        if (abs(y) < excludedRadius) {
            val displacement = excludedRadius - abs(y)
            return y + displacement * sign(y)
        }
        return y
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
    var skipBottomFace: Boolean = false,
    var excludedXZRadius: Double? = null,
    var excludedYRadius: Double? = null,
    density: Float = 1.0f
) : EmitterShape(density) {

    var xzRadius = xzRadius
        set(value) {
            if (minXZ == -field.toDouble())
                minXZ = -value.toDouble()

            if (maxXZ == field.toDouble())
                maxXZ = value.toDouble()
            field = value
        }

    var yRadius = yRadius
        set(value) {
            if (minY == -field.toDouble())
                minY = -value.toDouble()

            if (maxY == field.toDouble())
                maxY = value.toDouble()

            field = value
        }

    override fun maskLoc(location: Vector3d) {
        // Generate spherical coordinates with density adjustment
        val theta = Math.random() * MathFunctions.TAU

        // Adjust phi based on skipBottomFace parameter and apply density
        val phiBase = if (skipBottomFace) {
            applyDensity(Math.random()) // Range [0, 1]
        } else {
            applyDensity(Math.random() * 2 - 1) // Range [-1, 1]
        }

        val phi = if (skipBottomFace) {
            acos(phiBase) // Range [0, π/2]
        } else {
            acos(phiBase) // Range [0, π]
        }

        var x = sin(phi) * cos(theta) * xzRadius
        var y = cos(phi) * yRadius
        var z = sin(phi) * sin(theta) * xzRadius

        // Apply exclusion zones
        if (excludedXZRadius != null) {
            val xzDistanceSquared = x * x + z * z
            if (xzDistanceSquared < excludedXZRadius!! * excludedXZRadius!!) {
                val xzDisplacement = excludedXZRadius!! - sqrt(xzDistanceSquared)
                x += xzDisplacement * sign(x)
                z += xzDisplacement * sign(z)
            }
        }

        if (excludedYRadius != null) {
            if (abs(y) < excludedYRadius!!) {
                val yDisplacement = excludedYRadius!! - abs(y)
                y += yDisplacement * sign(y)
            }
        }

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
) : EmitterShape(density) {

    override fun maskLoc(location: Vector3d) {
        // Generate cylindrical coordinates with density adjustment
        val theta = Math.random() * MathFunctions.TAU

        // Square root gives uniform area distribution
        val r = sqrt(applyDensity(Math.random()))
        val h = applyDensity(Math.random()) * height

        var x = r * radiusX * cos(theta)
        var z = r * radiusZ * sin(theta)

        // Apply exclusion zone
        if (excludedXZRadius != null) {
            val xzDistanceSquared = x * x + z * z
            if (xzDistanceSquared < excludedXZRadius!! * excludedXZRadius!!) {
                val xzDisplacement = excludedXZRadius!! - sqrt(xzDistanceSquared)
                x += xzDisplacement * sign(x)
                z += xzDisplacement * sign(z)
            }
        }

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

    override fun maskLoc(location: Vector3d) {
        // Generate toroidal coordinates with density adjustment
        val theta = Math.random() * MathFunctions.TAU
        val phi = Math.random() * MathFunctions.TAU

        // Apply density to control distribution along the ring
        val effectiveTheta = if (density != 1.0f) {
            // Map random value to periodic function to maintain continuity
            val normalizedRandom = applyDensity(Math.random())
            normalizedRandom * MathFunctions.TAU
        } else {
            theta
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
) : EmitterShape(density) {

    override fun maskLoc(location: Vector3d) {
        // Generate polar coordinates with density adjustment
        val theta = Math.random() * MathFunctions.TAU

        // Square root for uniform area distribution
        val r = sqrt(applyDensity(Math.random()))

        var x = r * radiusX * cos(theta)
        var z = r * radiusZ * sin(theta)

        // Apply exclusion zone
        if (excludedRadius != null) {
            val distanceSquared = x * x + z * z
            if (distanceSquared < excludedRadius!! * excludedRadius!!) {
                val displacement = excludedRadius!! - sqrt(distanceSquared)
                x += displacement * sign(x)
                z += displacement * sign(z)
            }
        }

        // Apply constraints
        x = x.constrain(minX, maxX)
        z = z.constrain(minZ, maxZ)

        // Apply final position (no y adjustment)
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

    enum class Direction { X, Y, Z }

    override fun maskLoc(location: Vector3d) {
        // Apply density to control distribution along the line
        val pos = applyDensity(Math.random()) * length

        // Apply position based on direction
        when (direction) {
            Direction.X -> location.add(pos, 0.0, 0.0)
            Direction.Y -> location.add(0.0, pos, 0.0)
            Direction.Z -> location.add(0.0, 0.0, pos)
        }
    }
}