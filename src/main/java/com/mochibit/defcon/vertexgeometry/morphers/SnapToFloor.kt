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

import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3d
import org.joml.Vector3f
import kotlin.math.pow

class SnapToFloor(
    val world: World,
    private val maxDepth: Double = 0.0,
    private val startYOffset: Double = 0.0,
    private val easeFromPoint: Vector3d? = null,
    private val chunkSnapshotCache: MutableMap<Pair<Int, Int>, ChunkSnapshot>
) : ShapeMorpher {
    override fun morphVertex(basis: Vertex): Vertex {
        val point = basis.point
        val groundedLoc = Geometry.getMinYUsingSnapshotFromCache(
            world,
            Vector3d(basis.globalPosition.x, basis.globalPosition.y + startYOffset, basis.globalPosition.z),
            maxDepth + startYOffset,
            chunkSnapshotCache
        )

        val finalY: Double

        if (easeFromPoint != null) {
            val distance = basis.globalPosition.distance(easeFromPoint)
            val maxDistance = 80.0

            val t = MathFunctions.clamp(distance / maxDistance, 0.0, 1.0)
            val smoothT = if (t < 0.5) {
                4 * t * t * t
            } else {
                1 - (-2 * t + 2).pow(3) / 2
            }

            // Interpolate towards the grounded location
            val easedLoc = Vector3d(basis.globalPosition.x, basis.globalPosition.y, basis.globalPosition.z).lerp(groundedLoc, smoothT)
            finalY = easedLoc.y

        } else {
            finalY = groundedLoc.y
        }

        // Adjust the global position: Set it directly to the ground level
        basis.globalPosition.y = finalY

        return basis
    }


    override fun morph(basis: Array<Vertex>) : Array<Vertex> {
        return basis.map { morphVertex(it) }.toTypedArray()
    }
}