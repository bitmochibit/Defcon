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

package com.mochibit.defcon.vertexgeometry.particle

import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import com.mochibit.defcon.vertexgeometry.morphers.ShapeMorpher
import com.mochibit.defcon.vertexgeometry.morphers.SnapToFloor
import org.bukkit.Location

abstract class AbstractShape(
    shapeBuilder: VertexShapeBuilder,
    var spawnPoint: Location
) {
    var center: Vector3 = Vector3.ZERO
    var transformedCenter = Vector3.ZERO
        private set

    var visible = true; private set
    fun visible(value: Boolean) = apply { this.visible = value }

    var shapeMorpher: ShapeMorpher? = null; private set
    fun shapeMorpher(value: ShapeMorpher) = apply { this.shapeMorpher = value }
    var dynamicMorph = false; private set
    fun dynamicMorph(value: Boolean) = apply { this.dynamicMorph = value }

    private var minY = 0.0
    private var maxY = 0.0
    protected var vertexes: Array<Vertex> = emptyArray()
        set(value) {
            var result = value
            for (i in value.indices) {
                val point = value[i].point
                val transformedPoint = transform.xform(point)
                value[i].point = point
                value[i].transformedPoint = transformedPoint
                value[i].globalPosition = spawnPoint.clone().add(transformedPoint.toBukkitVector())
            }

            val x = value.map { it.point.x }.average()
            val y = value.map { it.point.y }.average()
            val z = value.map { it.point.z }.average()
            this.center = Vector3(x, y, z)

            minY = value.minOfOrNull { it.point.y } ?: 0.0
            maxY = value.maxOfOrNull { it.point.y } ?: 0.0

            this.transformedCenter = transform.xform(center)

            if (shapeMorpher != null && !dynamicMorph) {
                result = shapeMorpher!!.morph(value)
            }

            field = result
        }

    var transform = Transform3D()
        set(value) {
            field = value
            synchronized(vertexes) {
                for (vertex in vertexes) {
                    vertex.transformedPoint = value.xform(vertex.point)
                    vertex.globalPosition = spawnPoint.clone().add(vertex.transformedPoint.toBukkitVector())
                }
            }
            transformedCenter = value.xform(center)
        }

    private var xzPredicate: ((Double, Double) -> Boolean)? = null
    private var yPredicate: ((Double) -> Boolean)? = null

    fun xzPredicate(predicate: (Double, Double) -> Boolean) = apply { this.xzPredicate = predicate }
    fun yPredicate(predicate: (Double) -> Boolean) = apply { this.yPredicate = predicate }

    open fun draw(vertex: Vertex) {
        if (!visible) return
        val globalPosition = vertex.globalPosition
        if (xzPredicate != null && !xzPredicate!!.invoke(globalPosition.x, globalPosition.z)) return
        if (yPredicate != null && !yPredicate!!.invoke(globalPosition.y)) return
        effectiveDraw(if (dynamicMorph && shapeMorpher != null) shapeMorpher!!.morphVertex(vertex) else vertex)
    }

    abstract fun effectiveDraw(vertex: Vertex)

    // Shape morphing methods

    fun snapToFloor(maxDepth: Double = 0.0, startYOffset: Double = 0.0): AbstractShape {
        dynamicMorph = true
        shapeMorpher(SnapToFloor(maxDepth, startYOffset))
        return this
    }
}