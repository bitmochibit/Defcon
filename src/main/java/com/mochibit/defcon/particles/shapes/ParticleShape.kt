package com.mochibit.defcon.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.ColorUtils
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet
import kotlin.random.Random

abstract class ParticleShape(particle: Particle, val spawnPoint: Location) {
    val particleBuilder = ParticleBuilder(particle)
    var center: Vector3 = Vector3.ZERO
    private var transformedCenter = Vector3.ZERO

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

    var maxHeightTransition = 100.0;

    val isDirectional: Boolean
        get() = particleBuilder.count() == 0

    private var points: MutableSet<Vector3> = ConcurrentHashMap.newKeySet()
        set(value) {
            // Empty the previous set
            field = HashSet();
            field.addAll(value);
        }
    private var transformedPoints = HashSet<Vector3>()
    var transform = Transform3D()
        set(value) {
            field = value;
            transformedPoints = value.xform(points.toHashSet());
            transformedCenter = value.xform(center);
        }

    // Shape methods
    open fun draw() {
        for (transformed in transformedPoints) {
            if (Random.nextInt(0, 100) < 99) continue;
            val currentLoc = spawnPoint.clone().add(transformed.x, transformed.y, transformed.z)
            applyTemperatureEmission(transformed.y)

            particleBuilder.location(currentLoc);
            particleBuilder.spawn();
        }
    }

    abstract fun build(): HashSet<Vector3>;

    open fun buildAndAssign(): ParticleShape {
        assign(build());
        return this;
    }

    fun assign(points: HashSet<Vector3>): ParticleShape {
        this.points = points;
        transformedPoints = transform.xform(points);

        val x = points.map { it.x }.average();
        val y = points.map { it.y }.average();
        val z = points.map { it.z }.average();
        this.center = Vector3(x, y, z);

        this.transformedCenter = transform.xform(center)
        return this;
    }

    // Color methods
    private fun applyTemperatureEmission(height: Double): ParticleShape {
        if (temperature > minTemperature)
            return this.also { it.color(ColorUtils.tempToRGB(temperature), baseSize.toFloat()) }

        val ratio = height / maxHeightTransition;

        return this.also { it.color(MathFunctions.lerpColor(minimumColor, baseColor, ratio), baseSize.toFloat()) }
    }

    // Options
    open fun snapToFloor(maxDepth: Double = 0.0, startYOffset: Double = 0.0): ParticleShape {
        val result: HashSet<Vector3> = HashSet();
        for (point in build()) {
            val newLoc = Geometry.getMinY(spawnPoint.clone().add(point.x, point.y + startYOffset, point.z), maxDepth);
            result.add(Vector3(point.x, (newLoc.y - spawnPoint.y) + 1, point.z));
        }
        assign(result);
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

}