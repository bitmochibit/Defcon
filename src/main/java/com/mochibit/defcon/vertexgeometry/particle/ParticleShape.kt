package com.mochibit.defcon.vertexgeometry.particle

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import java.util.function.Predicate
import kotlin.random.Random

class ParticleShape(
    val shapeBuilder : VertexShapeBuilder,
    var particle: Particle,
    var spawnPoint: Location
) {
    val particleBuilder = ParticleBuilder(particle)


    var center: Vector3 = Vector3.ZERO
    private var transformedCenter = Vector3.ZERO

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
        get() = particleBuilder.count() == 0

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

    private var heightPredicate : Predicate<Double>? = null

    // Shape methods
    fun draw() {
        if (!visible) return;
        for (particleVertex in particleVertixes) {
            if (Random.nextInt(0, 100) < 99) continue;
            // Check if the vertex has been spawned in the last 1 second
            if (System.currentTimeMillis() - particleVertex.spawnTime < 6000) continue;


            val transformedVertex = particleVertex.vertex.transformedPoint;
            val currentLoc = spawnPoint.clone().add(transformedVertex.x, transformedVertex.y, transformedVertex.z)

            if (isTemperatureEmission)
                applyTemperatureEmission(particleVertex.vertex.point.y)

            if (isDirectional && radialSpeed != 0.0)
                radialDirectionFromCenter(particleVertex.vertex);

            if (heightPredicate != null && !heightPredicate!!.test(transformedVertex.y))
                continue;

            particleBuilder.location(currentLoc);
            particleBuilder.spawn();
            particleVertex.spawnTime = System.currentTimeMillis();
        }
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

    // Color methods
    private fun applyTemperatureEmission(height: Double): ParticleShape {
        if (particle != Particle.REDSTONE) return this;

        if (temperature > minTemperature)
            return this.also { it.color(ColorUtils.tempToRGB(temperature), baseSize.toFloat()) }

        // Remap the height to a value between 0 and 1 using the minY and maxY and use the transitionProgress to control how much the height affects the color
        val ratio = MathFunctions.remap(height, minY, maxY, transitionProgress, 1.0) * transitionProgress;
        return this.also { it.color(ColorUtils.lerpColor(minimumColor, baseColor, ratio), baseSize.toFloat()) }
    }

    // Directional methods
    private fun radialDirectionFromCenter(vertex: Vertex): ParticleShape {
        val direction = vertex.point - center;
        val normalized = direction.normalized() * radialSpeed;
        // Use the normalized direction as offset for the particle
        particleBuilder.offset(normalized.x, particleBuilder.offsetY(), normalized.z);

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
        particleBuilder.color(color, size);
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
        particleBuilder.count(0);
        return this;
    }

    fun baseColor(color: Color): ParticleShape {
        this.baseColor = color;
        return this;
    }

    fun particle(particle: Particle): ParticleShape {
        this.particle = particle;
        particleBuilder.data(null)
        particleBuilder.particle(particle)
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




}