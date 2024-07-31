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

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import org.bukkit.Location

class SnapToFloor(val maxDepth: Double = 0.0, val startYOffset: Double = 0.0) : ShapeMorpher {
    override fun morphVertex(basis: Vertex): Vertex {
        val point = basis.point
        val groundedLoc = Geometry.getMinY(basis.globalPosition.clone().add(0.0,startYOffset, 0.0), maxDepth + startYOffset)
        basis.globalPosition = groundedLoc.add(0.0, point.y, 0.0)
        return basis
    }

    override fun morph(basis: Array<Vertex>) : Array<Vertex> {
        return basis.map { morphVertex(it) }.toTypedArray()
    }
}