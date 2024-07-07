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

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.AbstractParticle
import com.mochibit.defcon.particles.PluginParticle
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import org.bukkit.*
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.inventory.meta.LeatherArmorMeta
import java.util.function.Predicate
import kotlin.random.Random

class ParticleShape(
    val shapeBuilder: VertexShapeBuilder,
    var particle: AbstractParticle,
    var spawnPoint: Location
) {

    var center: Vector3 = Vector3.ZERO
    var transformedCenter = Vector3.ZERO
        private set

    private var visible = true

    private var minY = 0.0
    private var maxY = 0.0

    private var minTemperature = 0.0
    private var maxTemperature = 100.0

    var temperature = 0.0
        set(value) {
            field = value.coerceIn(minTemperature, maxTemperature)
        }

    // When the temperature is at min, the color will slowly transition to this color (or via height or via time) since the base color is the color when emission is at min.
    var baseColor = Color.WHITE
    var baseSize = 5.0;
    var minimumColor = ColorUtils.tempToRGB(minTemperature);
    var transitionProgress = 0.0;

    val isDirectional: Boolean
        get() = false

    var isTemperatureEmission: Boolean = false
    var radialSpeed = 0.0;

    private var particleVertixes = emptyArray<ParticleVertex>()
    var transform = Transform3D()
        set(value) {
            field = value;
            synchronized(particleVertixes) {
                for (particleVertex in particleVertixes) {
                    particleVertex.vertex.transformedPoint = value.xform(particleVertex.vertex.point);
                }
            }
            transformedCenter = value.xform(center);
        }

    private var heightPredicate: Predicate<Double>? = null
    private var xPredicate: Predicate<Double>? = null
    private var zPredicate: Predicate<Double>? = null

    private var velocity = Vector3.ZERO

    // Shape methods

    fun randomDraw(chance: Double = 0.8, repetitions: Int = 10) {
        if (!visible) return;
        if (particleVertixes.isEmpty()) return;
        // Call draw (emitter), get a random vertex and draw it a random number of times consecutively
        val randomCount = Random.nextInt(1, repetitions) * (if (Random.nextDouble() < chance) 1 else 0);
        for (i in 0 until randomCount) {
            draw(particleVertixes.random())
        }

    }

    fun draw(particleVertex: ParticleVertex) {
        if (!visible) return;

        // Treat particles vertexes as particle emitters
        val transformedVertex = particleVertex.vertex.transformedPoint;
        val currentLoc = spawnPoint.clone().add(transformedVertex.x, transformedVertex.y, transformedVertex.z)

        // spawn a display entity
//            if (isDirectional && radialSpeed != 0.0)
//                radialDirectionFromCenter(particleVertex.vertex);

        if (heightPredicate != null && !heightPredicate!!.test(transformedVertex.y))
            return

        if (xPredicate != null && !xPredicate!!.test(transformedVertex.x))
            return

        if (zPredicate != null && !zPredicate!!.test(transformedVertex.z))
            return;

        particle.spawn(currentLoc);

        particleVertex.spawnTime = System.currentTimeMillis();
    }

    fun buildAndAssign(): ParticleShape {
        assign(
            shapeBuilder.build()
        )
        return this
    }

    fun assign(newVertexes: Array<Vertex>): ParticleShape {
        val result = Array(newVertexes.size) { ParticleVertex(newVertexes[it]) }
        for (i in newVertexes.indices) {
            result[i] = ParticleVertex(
                Vertex(newVertexes[i].point, transform.xform(newVertexes[i].point))
            )
        }
        particleVertixes = result

        val x = particleVertixes.map { it.vertex.point.x }.average()
        val y = particleVertixes.map { it.vertex.point.y }.average();
        val z = particleVertixes.map { it.vertex.point.z }.average();
        this.center = Vector3(x, y, z);

        minY = particleVertixes.minOfOrNull { it.vertex.point.y } ?: 0.0;
        maxY = particleVertixes.maxOfOrNull { it.vertex.point.y } ?: 0.0;

        this.transformedCenter = transform.xform(center)
        return this;
    }



    // Directional methods
    private fun radialDirectionFromCenter(vertex: Vertex): ParticleShape {
        val direction = vertex.point - center;
        val normalized = direction.normalized() * radialSpeed;
        // Use the normalized direction as offset for the particle
        //particleBuilder.offset(normalized.x, particleBuilder.offsetY(), normalized.z);

        return this;
    }

    // Options
    fun visible(value: Boolean): ParticleShape {
        this.visible = value;
        return this;
    }

    fun isVisible(): Boolean {
        return visible;
    }

    fun snapToFloor(maxDepth: Double = 0.0, startYOffset: Double = 0.0): ParticleShape {
        val vertexes = shapeBuilder.build()

        for (i in vertexes.indices) {
            val point = vertexes[i].point;
            val newLoc = Geometry.getMinY(spawnPoint.clone().add(point.x, point.y + startYOffset, point.z), maxDepth);
            vertexes[i] = Vertex(Vector3(point.x, (newLoc.y - spawnPoint.y) + 1, point.z));
        }
        assign(vertexes);
        return this;
    }

    fun color(color: Color, size: Float): ParticleShape {
        //particleBuilder.color(color, size);
        return this;
    }

    fun temperature(temperature: Double): ParticleShape {
        this.temperature = temperature;
        return this;
    }

    fun temperature(temperature: Double, min: Double, max: Double): ParticleShape {
        this.minTemperature = min;
        this.maxTemperature = max;
        this.temperature = temperature;
        minimumColor = ColorUtils.tempToRGB(minTemperature);
        return this;
    }

    fun temperature(min: Double, max: Double): ParticleShape {
        return temperature(temperature, min, max)
    }

    fun directional(): ParticleShape {
        //particleBuilder.count(0);
        return this;
    }

    fun baseColor(color: Color): ParticleShape {
        this.baseColor = color;
        return this;
    }

    fun particle(particle: Particle): ParticleShape {
        //this.particle = particle;
        //particleBuilder.data(null)
        //particleBuilder.particle(particle)
        return this;
    }

    fun temperatureEmission(value: Boolean): ParticleShape {
        this.isTemperatureEmission = value;
        return this;
    }

    fun radialSpeed(speed: Double): ParticleShape {
        this.radialSpeed = speed;
        return this;
    }

    fun heightPredicate(predicate: Predicate<Double>): ParticleShape {
        this.heightPredicate = predicate;
        return this;
    }

    fun xPredicate(predicate: Predicate<Double>): ParticleShape {
        this.xPredicate = predicate;
        return this;
    }

    fun zPredicate(predicate: Predicate<Double>): ParticleShape {
        this.zPredicate = predicate;
        return this;
    }

    fun velocity(vel: Vector3): ParticleShape {
        this.velocity = vel;
        return this;
    }


}