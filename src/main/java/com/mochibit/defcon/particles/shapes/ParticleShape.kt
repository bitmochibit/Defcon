package com.mochibit.defcon.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import kotlin.random.Random

abstract class ParticleShape(particle: Particle, val spawnPoint: Location) {
    val particleBuilder = ParticleBuilder(particle)
    var center: Vector3 = Vector3.ZERO
    private var transformedCenter = Vector3.ZERO

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

    private var vertexes = emptyArray<ParticleVertex>()
    var transform = Transform3D()
        set(value) {
            field = value;
            synchronized(vertexes) {
                for (vertex in vertexes) {
                    vertex.transformedPoint = value.xform(vertex.point);
                }
            }
            transformedCenter = value.xform(center);
        }

    // Shape methods
    open fun draw() {
        for (vertex in vertexes) {
            if (Random.nextInt(0, 100) < 99) continue;
            // Check if the vertex has been spawned in the last 1 second
            if (System.currentTimeMillis() - vertex.spawnTime < 6000) continue;

            val transformedVertex = vertex.transformedPoint;
            val currentLoc = spawnPoint.clone().add(transformedVertex.x, transformedVertex.y, transformedVertex.z)

            if (isTemperatureEmission)
                applyTemperatureEmission(vertex.point.y)

            if (isDirectional && radialSpeed != 0.0)
                radialDirectionFromCenter(vertex);

            particleBuilder.location(currentLoc);
            particleBuilder.spawn();
            vertex.spawnTime = System.currentTimeMillis();
        }
    }

    abstract fun build(): Array<ParticleVertex>;

    open fun buildAndAssign(): ParticleShape {
        assign(build());
        return this;
    }

    fun assign(newVertexes: Array<ParticleVertex>): ParticleShape {
        vertexes = newVertexes;

        val x = newVertexes.map { it.point.x }.average()
        val y = newVertexes.map { it.point.y }.average();
        val z = newVertexes.map { it.point.z }.average();
        this.center = Vector3(x, y, z);

        minY = newVertexes.minOfOrNull { it.point.y } ?: 0.0;
        maxY = newVertexes.maxOfOrNull { it.point.y } ?: 0.0;

        this.transformedCenter = transform.xform(center)
        return this;
    }

    // Color methods
    private fun applyTemperatureEmission(height: Double): ParticleShape {
        if (temperature > minTemperature)
            return this.also { it.color(ColorUtils.tempToRGB(temperature), baseSize.toFloat()) }

        // Remap the height to a value between 0 and 1 using the minY and maxY and use the transitionProgress to control how much the height affects the color
        val ratio = MathFunctions.remap(height, minY, maxY, transitionProgress, 1.0) * transitionProgress;
        return this.also { it.color(ColorUtils.lerpColor(minimumColor, baseColor, ratio), baseSize.toFloat()) }
    }

    // Directional methods
    private fun radialDirectionFromCenter(vertex: ParticleVertex): ParticleShape {
        val direction = vertex.point - center;
        val normalized = direction.normalized() * radialSpeed;
        // Use the normalized direction as offset for the particle
        particleBuilder.offset(normalized.x, particleBuilder.offsetY(), normalized.z);

        return this;
    }

    // Options
    open fun snapToFloor(maxDepth: Double = 0.0, startYOffset: Double = 0.0): ParticleShape {
        val vertexes = build();

        for (i in vertexes.indices) {
            val point = vertexes[i].point;
            val newLoc = Geometry.getMinY(spawnPoint.clone().add(point.x, point.y + startYOffset, point.z), maxDepth);
            vertexes[i] = ParticleVertex(Vector3(point.x, (newLoc.y - spawnPoint.y) + 1, point.z));
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
        particleBuilder.particle(particle);
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




}