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
    var yMin: Double = -yRadius.toDouble(), // Start of the ellipsoid (to allow for half-ellipsoid)
    var yMax: Double = yRadius.toDouble()
) : EmitterShape() {

    override fun maskLoc(location: Vector3f) {
        if (yMin > yMax) {
          yMin = yMax.also { yMax = yMin }
        }

        val r = cbrt(Math.random()) // Cube root gives uniform distribution in volume
        // Generate a random point within the bounds of a sphere
        val theta = Math.random() * MathFunctions.TAU         // Azimuthal angle in [0, 2π]
        val phi = acos(2 * Math.random() - 1)            // Polar angle in [0, π] for spherical symmetry

        location.add(
            (r * sin(phi) * cos(theta) * xzRadius).toFloat(),
            if (yMin == yMax) (r * cos(phi) * yRadius).coerceAtLeast(yMin).toFloat() else (r * cos(phi) * yRadius).coerceIn(yMin, yMax).toFloat(),
            (r * sin(phi) * sin(theta) * xzRadius).toFloat()
        )
    }
}

class SphereSurfaceShape(
    val xzRadius: Float,
    val yRadius: Float,
    val yMin: Float = -yRadius, // Start of the ellipsoid (to allow for half-ellipsoid)
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate random spherical coordinates
        val theta = Math.random() * MathFunctions.TAU         // Azimuthal angle in [0, 2π]
        val phi = acos(2 * Math.random() - 1)            // Polar angle in [0, π]

        location.add(
            (sin(phi) * cos(theta) * xzRadius).toFloat(),
            (cos(phi) * yRadius).coerceAtLeast(yMin.toDouble()).toFloat(),
            (sin(phi) * sin(theta) * xzRadius).toFloat()
        )

    }
}

class CylinderShape(
    var radiusX: Float,
    var radiusZ: Float,
    var height: Float
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate a random point within the bounds of a cylinder
        val theta = Math.random() * MathFunctions.TAU        // Azimuthal angle in [0, 2π]
        val r = Math.random()                          // Random radius factor for points inside the cylinder
        val h = Math.random() * height                 // Random height factor for points inside the cylinder

        location.add(
            (r * radiusX * cos(theta)).toFloat(),
            h.toFloat(),
            (r * radiusZ * sin(theta)).toFloat()
        )
    }
}

