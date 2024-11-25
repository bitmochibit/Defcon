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
import org.joml.Vector3f
import kotlin.math.*

abstract class EmitterShape {
    abstract fun maskLoc(location: Vector3f)
}

class SphereShape(
    var xzRadius: Float,
    var yRadius: Float,
    var minY: Double = -yRadius.toDouble(), // Start of the ellipsoid (to allow for half-ellipsoid)
    var maxY: Double = yRadius.toDouble(),
    val minXZ: Double = -xzRadius.toDouble(),
    val maxXZ: Double = xzRadius.toDouble(),
    var excludedXZRadius: Double? = null, // Radius to exclude in the XZ plane
    var excludedYRadius: Double? = null,  // Radius to exclude in the Y axis
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        if (minY > maxY) {
            minY = maxY.also { maxY = minY }
        }

        val r = cbrt(Math.random()) // Cube root gives uniform distribution in volume

        // Generate a random point within the bounds of a sphere
        val theta = Math.random() * MathFunctions.TAU         // Azimuthal angle in [0, 2π]
        val phi = acos(2 * Math.random() - 1)            // Polar angle in [0, π] for spherical symmetry

        var x = r * sin(phi) * cos(theta) * xzRadius
        var y = r * cos(phi) * yRadius
        var z = r * sin(phi) * sin(theta) * xzRadius

        // Exclude XZ region: Check if the point is inside the excluded XZ radius
        excludedXZRadius?.let {
            val xzDistanceSquared = x * x + z * z
            if (xzDistanceSquared < it * it) {
                // Push out of excluded XZ region based on direction
                val xzDisplacement = it - sqrt(xzDistanceSquared)
                x += xzDisplacement * sign(x) // Adjust in the x-direction
                z += xzDisplacement * sign(z) // Adjust in the z-direction
            }
        }

        // Exclude Y region: Check if the point is inside the excluded Y radius
        excludedYRadius?.let {
            if (abs(y) < it) {
                val yDisplacement = it - abs(y)
                y += yDisplacement * sign(y) // Adjust in the y-direction
            }
        }

        // Apply min/max constraints on Y and XZ
        x = x.coerceIn(minXZ, maxXZ)
        z = z.coerceIn(minXZ, maxXZ)
        y = y.coerceIn(minY, maxY)

        location.add(x.toFloat(), y.toFloat(), z.toFloat())
    }
}

class SphereSurfaceShape(
    xzRadius: Float,
    yRadius: Float,
    var minY: Double = -yRadius.toDouble(), // Start of the ellipsoid (to allow for half-ellipsoid)
    var maxY: Double = yRadius.toDouble(),
    var minXZ: Double = -xzRadius.toDouble(),
    var maxXZ: Double = xzRadius.toDouble(),
    var skipBottomFace: Boolean = false,
    var excludedXZRadius: Double? = null, // Radius to exclude in the XZ plane
    var excludedYRadius: Double? = null,  // Radius to exclude in the Y axis
) : EmitterShape() {

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

    override fun maskLoc(location: Vector3f) {
        // Generate random spherical coordinates within the adjusted ranges
        val theta = Math.random() * MathFunctions.TAU // Azimuthal angle in [0, 2π]
        val phi = if (skipBottomFace) {
            acos(Math.random()) // Polar angle in [0, π/2]
        } else {
            acos(2 * Math.random() - 1) // Polar angle in [0, π]
        }

        // Calculate spherical coordinates
        var x = sin(phi) * cos(theta) * xzRadius
        var y = cos(phi) * yRadius
        var z = sin(phi) * sin(theta) * xzRadius

        // Exclude XZ region: Check if the point is inside the excluded XZ radius
        excludedXZRadius?.let {
            val xzDistanceSquared = x * x + z * z
            if (xzDistanceSquared < it * it) {
                // Push out of excluded XZ region based on direction
                val xzDisplacement = it - sqrt(xzDistanceSquared)
                x += xzDisplacement * sign(x) // Adjust in the x-direction
                z += xzDisplacement * sign(z) // Adjust in the z-direction
            }
        }

        // Exclude Y region: Check if the point is inside the excluded Y radius
        excludedYRadius?.let {
            if (abs(y) < it) {
                val yDisplacement = it - abs(y)
                y += yDisplacement * sign(y) // Adjust in the y-direction
            }
        }

        // Apply min/max constraints on Y and XZ
        x = x.coerceIn(minXZ, maxXZ)
        z = z.coerceIn(minXZ, maxXZ)
        y = y.coerceIn(minY, maxY)


        // Set the location directly
        location.add(x.toFloat(), y.toFloat(), z.toFloat())
    }
}


class CylinderShape(
    var radiusX: Float,
    var radiusZ: Float,
    var height: Float,
    var minX: Double = -radiusX.toDouble(),
    var maxX: Double = radiusX.toDouble(),
    var minZ: Double = -radiusZ.toDouble(),
    var maxZ: Double = radiusZ.toDouble(),
    var excludedXZRadius: Double? = null, // Radius to exclude in the XZ plane

) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate a random point within the bounds of a cylinder
        val theta = Math.random() * MathFunctions.TAU // Azimuthal angle in [0, 2π]
        val r = Math.random() // Random radius factor for points inside the cylinder
        val h = Math.random() * height // Random height factor for points inside the cylinder

        var x = r * radiusX * cos(theta)
        var z = r * radiusZ * sin(theta)

        excludedXZRadius?.let {
            val xzDistanceSquared = x * x + z * z
            if (xzDistanceSquared < it * it) {
                // Push out of excluded XZ region based on direction
                val xzDisplacement = it - sqrt(xzDistanceSquared)
                x += xzDisplacement * sign(x) // Adjust in the x-direction
                z += xzDisplacement * sign(z) // Adjust in the z-direction
            }
        }

        x = x.coerceIn(minX, maxX)
        z = z.coerceIn(minZ, maxZ)

        location.add(
            x.toFloat(),
            h.toFloat(),
            z.toFloat()
        )
    }
}

class RingSurfaceShape(
    var ringRadius: Float,  // Radius from the center to the center of the tube
    var tubeRadius: Float   // Radius of the tube
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate random angles for the ring and tube
        val theta = Math.random() * MathFunctions.TAU // Angle around the ring's central axis
        val phi = Math.random() * MathFunctions.TAU   // Angle around the tube's circular cross-section

        // Parametrize the torus shape
        val x = (ringRadius + tubeRadius * cos(phi)) * cos(theta)
        val y = tubeRadius * sin(phi)
        val z = (ringRadius + tubeRadius * cos(phi)) * sin(theta)

        // Set the location to the calculated point
        location.add(x.toFloat(), y.toFloat(), z.toFloat())
    }
}


