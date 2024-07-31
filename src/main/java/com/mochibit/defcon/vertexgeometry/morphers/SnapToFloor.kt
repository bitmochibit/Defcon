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

package com.mochibit.defcon.vertexgeometry.morphers

import com.mochibit.defcon.extensions.lerp
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import org.bukkit.Location
import kotlin.math.pow

class SnapToFloor(val maxDepth: Double = 0.0, val startYOffset: Double = 0.0, private val easeFromPoint: Location? = null) : ShapeMorpher {
    override fun morphVertex(basis: Vertex): Vertex {
        val point = basis.point
        val groundedLoc = Geometry.getMinY(basis.globalPosition.clone().add(0.0,startYOffset, 0.0), maxDepth + startYOffset)
        if (easeFromPoint != null) {
            val from = easeFromPoint.clone()
            val distance = basis.globalPosition.distance(from)
            val maxDistance = 80.0

            val t = MathFunctions.clamp(distance / maxDistance, 0.0, 1.0)
            val smoothT = if (t < 0.5) {
                4 * t * t * t
            } else {
                1 - (-2 * t + 2).pow(3) / 2
            }

            // Interpolate towards the grounded location
            val easedLoc = basis.globalPosition.lerp(groundedLoc, smoothT)

            // Adjust the global position based on the eased location and original height
            basis.globalPosition = easedLoc.add(0.0, point.y, 0.0)

        } else {
            basis.globalPosition = groundedLoc.add(0.0, point.y, 0.0)
        }
        return basis
    }

    override fun morph(basis: Array<Vertex>) : Array<Vertex> {
        return basis.map { morphVertex(it) }.toTypedArray()
    }
}