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
import com.mochibit.defcon.particles.AbstractParticle
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import org.bukkit.*
import kotlin.random.Random

class ParticleShape(
    shapeBuilder: VertexShapeBuilder,
    var particle: AbstractParticle,
    var spawnPoint: Location
) {
    var center: Vector3 = Vector3.ZERO
    var transformedCenter = Vector3.ZERO
        private set

    var visible = true; private set
    fun visible(value: Boolean) = apply { this.visible = value }

    private var minY = 0.0
    private var maxY = 0.0

    private var particleVertexes : Array<ParticleVertex> = emptyArray()

    var transform = Transform3D()
        set(value) {
            field = value;
            synchronized(particleVertexes) {
                for (particleVertex in particleVertexes) {
                    particleVertex.vertex.transformedPoint = value.xform(particleVertex.vertex.point);
                }
            }
            transformedCenter = value.xform(center);
        }

    private var xzPredicate: ((Double, Double) -> Boolean)? = null
    private var yPredicate: ((Double) -> Boolean)? = null

    fun xzPredicate(predicate: (Double, Double) -> Boolean) = apply { this.xzPredicate = predicate }
    fun yPredicate(predicate: (Double) -> Boolean) = apply { this.yPredicate = predicate }

    init {
        assign(shapeBuilder.build())
    }

    // Shape methods
    fun randomDraw(chance: Double = 0.8, repetitions: Int = 10) {
        if (!visible) return
        if (particleVertexes.isEmpty()) return
        // Call draw (emitter), get a random vertex and draw it a random number of times consecutively
        val randomCount = Random.nextInt(1, repetitions) * (if (Random.nextDouble() < chance) 1 else 0);
        for (i in 0 until randomCount) {
            draw(particleVertexes.random())
        }

    }

    fun draw(particleVertex: ParticleVertex) {
        if (!visible) return
        val transformedVertex = particleVertex.vertex.transformedPoint
        if (xzPredicate != null && !xzPredicate!!.invoke(transformedVertex.x, transformedVertex.z)) return
        if (yPredicate != null && !yPredicate!!.invoke(transformedVertex.y)) return
        // Treat particles vertexes as particle emitters
        val currentLoc = spawnPoint.clone().add(transformedVertex.x, transformedVertex.y, transformedVertex.z)
        particle.spawn(currentLoc)
        particleVertex.spawnTime = System.currentTimeMillis()
    }

    private fun assign(newVertexes: Array<Vertex>): ParticleShape {
        val result = Array(newVertexes.size) { ParticleVertex(newVertexes[it]) }
        for (i in newVertexes.indices) {
            result[i] = ParticleVertex(
                Vertex(newVertexes[i].point, transform.xform(newVertexes[i].point))
            )
        }
        particleVertexes = result

        val x = particleVertexes.map { it.vertex.point.x }.average()
        val y = particleVertexes.map { it.vertex.point.y }.average();
        val z = particleVertexes.map { it.vertex.point.z }.average();
        this.center = Vector3(x, y, z);

        minY = particleVertexes.minOfOrNull { it.vertex.point.y } ?: 0.0;
        maxY = particleVertexes.maxOfOrNull { it.vertex.point.y } ?: 0.0;

        this.transformedCenter = transform.xform(center)
        return this;
    }

    // Shape morphing methods

    fun snapToFloor(maxDepth: Double = 0.0, startYOffset: Double = 0.0): ParticleShape {
        val vertexes = Array(particleVertexes.size) { Vertex(Vector3.ZERO) }
        for (i in particleVertexes.indices) {
            val vertex = particleVertexes[i].vertex
            val point = vertex.point;
            val newLoc = Geometry.getMinY(spawnPoint.clone().add(point.x, point.y + startYOffset, point.z), maxDepth)
            vertexes[i] = Vertex(Vector3(point.x, (newLoc.y - spawnPoint.y) + 1, point.z))
        }
        assign(vertexes)
        return this
    }



}