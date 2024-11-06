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



import org.joml.Vector3f
import kotlin.math.acos
import kotlin.math.cbrt
import kotlin.math.cos
import kotlin.math.sin

abstract class EmitterShape {
    abstract fun maskLoc(location: Vector3f)
}

class SphereShape(
    val xzRadius: Float,
    val yRadius: Float
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate a random point within the bounds of a sphere
        val theta = Math.random() * 2 * Math.PI        // Azimuthal angle in [0, 2π]
        val phi = acos(2 * Math.random() - 1)     // Polar angle in [0, π], ensures uniform distribution

        // Generate a random radius factor for points inside the sphere
        val r = cbrt(Math.random())               // Cube root of random for uniform distribution in volume

        location.add(
            (r * xzRadius * sin(phi) * cos(theta)).toFloat(),
            (r * yRadius * cos(phi)).toFloat(),
            (r * xzRadius * sin(phi) * sin(theta)).toFloat()
        )
    }
}

class SphereSurfaceShape(
    val xzRadius: Float,
    val yRadius: Float
) : EmitterShape() {
    override fun maskLoc(location: Vector3f) {
        // Generate a random point on the surface of a sphere with the given radii
        val theta = Math.random() * 2 * Math.PI        // Azimuthal angle in [0, 2π]
        val phi = acos(2 * Math.random() - 1)     // Polar angle in [0, π], ensures uniform distribution

        // Fixed radius for surface points
        location.add(
            (xzRadius * sin(phi) * cos(theta)).toFloat(),
            (yRadius * cos(phi)).toFloat(),
            (xzRadius * sin(phi) * sin(theta)).toFloat()
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
        val theta = Math.random() * 2 * Math.PI        // Azimuthal angle in [0, 2π]
        val r = Math.random()                          // Random radius factor for points inside the cylinder
        val h = Math.random() * height                 // Random height factor for points inside the cylinder

        location.add(
            (r * radiusX * cos(theta)).toFloat(),
            h.toFloat(),
            (r * radiusZ * sin(theta)).toFloat()
        )
    }
}

