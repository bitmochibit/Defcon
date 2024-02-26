package com.mochibit.nuclearcraft.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.math.Transform3D
import com.mochibit.nuclearcraft.math.Vector3
import com.mochibit.nuclearcraft.utils.MathFunctions
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import kotlin.random.Random

abstract class ParticleShape(particle: Particle) {
    val particleBuilder = ParticleBuilder(particle);
    private var points = HashSet<Vector3>()
        set(value) {
            field.clear();
            field.addAll(value);
        }
    private var transformedPoints = HashSet<Vector3>();

    var transform = Transform3D()
        set(value) {
            field = value;
            transformedPoints = value.xform(points);
            transformedCenter = value.xform(center);
        }

    val center: Vector3
        get() {
            val x = points.map { it.x }.average();
            val y = points.map { it.y }.average();
            val z = points.map { it.z }.average();
            return Vector3(x, y, z);
        }

    private var transformedCenter = transform.xform(center);

    var startingColor: Color = Color.WHITE;
    var endingColor: Color = Color.WHITE;
    private var distanceColor : Boolean = false

    private var groundedPoints: HashSet<Vector3> = HashSet();



    open fun draw(location: Location) {
        for (transformed in transformedPoints) {
            if (Random.nextInt(0, 100) <= 98) continue;
            val currentLoc = location.clone().add(transformed.x, transformed.y, transformed.z);
            particleBuilder.location(currentLoc);
            if (distanceColor)
                centerDistanceColor(location, currentLoc);
            particleBuilder.spawn();
        }
    }



    private fun centerDistanceColor(spawnPoint: Location, currLoc: Location) {
        // Get the center from the spawn point and the transformed point y
        val center = spawnPoint.clone().add(transformedCenter.x, transformedCenter.y, transformedCenter.z);
        // Get distance from the center of the explosion
        val distanceSqr = center.distanceSquared(currLoc);

        val lerp = MathFunctions.map(distanceSqr, 0.0, 300.0, 0.0, 1.0);
        val color = Color.fromRGB(
            MathFunctions.lerp(startingColor.red, endingColor.red, lerp),
            MathFunctions.lerp(startingColor.green, endingColor.green, lerp),
            MathFunctions.lerp(startingColor.blue, endingColor.blue, lerp)
        );
        particleBuilder.color(color, 5.0f);
    }

    abstract fun build(): HashSet<Vector3>;

    open fun buildAndAssign(): ParticleShape {
        points = build();
        transformedPoints = transform.xform(points);
        return this;
    }

    fun directional(): ParticleShape {
        particleBuilder.count(0);
        return this;
    }

    fun lerpColorFromCenter(startingColor: Color, endingColor: Color): ParticleShape {
        this.startingColor = startingColor;
        this.endingColor = endingColor;
        distanceColor = true;
        return this;
    }

    fun isDirectional(): Boolean {
        return particleBuilder.count() == 0;
    }

}