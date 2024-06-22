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

package com.mochibit.defcon.vertexgeometry.shapes

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import kotlin.math.*

class CircleBuilder() : VertexShapeBuilder {
    private var radiusX: Double = 0.0
    private var radiusZ: Double = 0.0
    private var extension: Double = 1.0
    private var rate: Double = 1.0
    private var maxAngle: Double = 0.0
    private var hollow: Boolean = false
    private var minRadiusX: Double = 0.0;
    private var minRadiusZ: Double = 0.0;
    override fun build(): Array<Vertex> {


        if (hollow)
            return makeCircle(radiusX, radiusZ, rate).toTypedArray()


        val result = HashSet<Vertex>()
        var x = 0.0
        var z = 0.0
        var dynamicRate = 0.0
        val radiusRate = 3.0
        while ( (x < radiusX || z < radiusZ)) {

            dynamicRate += rate / (radiusX / radiusRate)

            result.addAll(makeCircle(x,z, dynamicRate))

            x += radiusRate
            z += radiusRate

            x = x.coerceIn(minRadiusX, radiusX);
            z = z.coerceIn(minRadiusZ, radiusZ);
        }


        return result.toTypedArray();
    }

    private fun makeCircle(radiusX: Double, radiusZ: Double, rate: Double) : HashSet<Vertex> {
        val result = HashSet<Vertex>()

        val rateDiv = PI / abs(rate)

        // If no limit is specified do a full loop.
        if (maxAngle == 0.0) maxAngle = MathFunctions.TAU
        else if (maxAngle == -1.0) maxAngle = MathFunctions.TAU / abs(extension)

        // If the extension changes (isn't 1), the wave might not do a full
        // loop anymore. So by simply dividing PI from the extension you can get the limit for a full loop.
        // By full loop it means: sin(bx) {0 < x < PI} if b (the extension) is equal to 1
        // Using period => T = 2PI/|b|
        var theta = 0.0
        while (theta <= maxAngle) {
            // In order to curve our straight line in the loop, we need to
            // use cos and sin. It doesn't matter, you can get x as sin and z as cos.
            // But you'll get weird results if you use si+n or cos for both or using tan or cot.
            val x = radiusX * cos(extension * theta)
            val z = radiusZ * sin(extension * theta)

            result.add(Vertex(Vector3(x, 0.0, z)))
            theta += rateDiv
        }

        return result
    }

    // Builder methods
    fun withRadiusX(radiusX: Double): CircleBuilder {
        this.radiusX = radiusX
        return this
    }

    fun withRadiusZ(radiusZ: Double): CircleBuilder {
        this.radiusZ = radiusZ
        return this
    }

    fun withExtension(extension: Double): CircleBuilder {
        this.extension = extension
        return this
    }

    fun withRate(rate: Double): CircleBuilder {
        this.rate = rate
        return this
    }

    fun withMaxAngle(maxAngle: Double): CircleBuilder {
        this.maxAngle = maxAngle
        return this
    }

    fun hollow(hollow: Boolean): CircleBuilder {
        this.hollow = hollow
        return this
    }

    fun withMinRadiusX(minRadiusX: Double): CircleBuilder {
        this.minRadiusX = minRadiusX
        return this
    }

    fun withMinRadiusZ(minRadiusZ: Double): CircleBuilder {
        this.minRadiusZ = minRadiusZ
        return this
    }



    // Getters

    fun getRadiusX(): Double {
        return radiusX
    }

    fun getRadiusZ(): Double {
        return radiusZ
    }

    fun getExtension(): Double {
        return extension
    }

    fun getRate(): Double {
        return rate
    }

    fun getMaxAngle(): Double {
        return maxAngle
    }

    fun isHollow(): Boolean {
        return hollow
    }

    fun getMinRadiusX(): Double {
        return minRadiusX
    }

    fun getMinRadiusZ(): Double {
        return minRadiusZ
    }

}