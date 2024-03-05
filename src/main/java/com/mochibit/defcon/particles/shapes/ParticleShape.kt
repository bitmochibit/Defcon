package com.mochibit.defcon.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.HashSet
import kotlin.random.Random

abstract class ParticleShape(particle: Particle, val spawnPoint: Location) {
    val particleBuilder = ParticleBuilder(particle);
    private var points: MutableSet<Vector3> = ConcurrentHashMap.newKeySet()
        set(value) {
            // Empty the previous set
            field = HashSet();
            field.addAll(value);
        }
    private var transformedPoints = HashSet<Vector3>();

    var transform = Transform3D()
        set(value) {
            field = value;
            transformedPoints = value.xform(points.toHashSet());
            transformedCenter = value.xform(center);
        }

    var center: Vector3 = Vector3.ZERO;
    private var transformedCenter = Vector3.ZERO;

    open fun draw() {
        for (transformed in transformedPoints) {
            if (Random.nextInt(0, 100) <= 98) continue;
            val currentLoc = spawnPoint.clone().add(transformed.x, transformed.y, transformed.z);
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

    open fun snapToFloor(): ParticleShape {
        val result: HashSet<Vector3> = HashSet();
        for (point in build()) {
            val newLoc = Geometry.getMinY(spawnPoint.clone().add(point.x, point.y, point.z), 60.0);
            result.add(Vector3(point.x, (newLoc.y-spawnPoint.y)+1, point.z));
        }
        assign(result);
        return this;
    }

    fun color(color: Color, size: Float): ParticleShape {
        particleBuilder.color(color, size);
        return this;
    }

    fun directional(): ParticleShape {
        particleBuilder.count(0);
        return this;
    }

    fun isDirectional(): Boolean {
        return particleBuilder.count() == 0;
    }

}