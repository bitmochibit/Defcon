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

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder

class CylinderBuilder() : VertexShapeBuilder {
    private var height: Double = 1.0
    private var radiusX: Double = 1.0
    private var radiusZ: Double = 1.0
    private var rate: Double = 1.0
    private var hollow = false
    override fun build(): Array<Vertex> {
        val result = HashSet<Vertex>();
        var y = 0.0

        info("Building cylinder with height: $height, radiusX: $radiusX, radiusZ: $radiusZ, rate: $rate, hollow: $hollow")


        val circleBuilder = CircleBuilder()
            .withRadiusX(radiusX)
            .withRadiusZ(radiusZ)
            .withRate(rate)
            .hollow(hollow)

        val circle = circleBuilder.build()

        while (y < height) {
            result.addAll(
                circle.map { vertex ->
                    Vertex(Vector3(vertex.point.x, y, vertex.point.z))
                }
            )
            y += 0.1
        }
        return result.toTypedArray();
    }

    // Builder methods
    fun withHeight(height: Double): CylinderBuilder {
        this.height = height
        return this
    }

    fun withRadiusX(radiusX: Double): CylinderBuilder {
        this.radiusX = radiusX
        return this
    }

    fun withRadiusZ(radiusZ: Double): CylinderBuilder {
        this.radiusZ = radiusZ
        return this
    }

    fun withRate(rate: Double): CylinderBuilder {
        this.rate = rate
        return this
    }

    fun hollow(hollow: Boolean): CylinderBuilder {
        this.hollow = hollow
        return this
    }

    fun getHollow(): Boolean {
        return hollow
    }

    fun getHeight(): Double {
        return height
    }

    fun getRadiusX(): Double {
        return radiusX
    }

    fun getRadiusZ(): Double {
        return radiusZ
    }

    fun getRate(): Double {
        return rate
    }

}