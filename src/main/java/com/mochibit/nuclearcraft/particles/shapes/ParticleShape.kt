package com.mochibit.nuclearcraft.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.NuclearCraft.Companion.Logger.info
import com.mochibit.nuclearcraft.math.Transform3D
import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.random.Random

abstract class ParticleShape(particle: Particle) {
    var points = HashSet<Vector3>()
        set(value) {
            field.clear();
            field.addAll(value);
        }

    val particleBuilder = ParticleBuilder(particle);
    var transform = Transform3D();

    open fun draw(location: Location) {
        val transformedPoints = transform.xform(points);

        for (transformed in transformedPoints) {
            if (Random.nextInt(0, 100) <= 95)
                continue;
            particleBuilder.location(location.clone().add(transformed.x, transformed.y, transformed.z));
            particleBuilder.spawn();
        }
    }
    abstract fun build() : HashSet<Vector3>;

    open fun buildAndAssign() : ParticleShape {
        points = build();
        return this;
    }

    fun directional() : ParticleShape {
        particleBuilder.count(0);
        return this;
    }
    fun isDirectional() : Boolean {
        return particleBuilder.count() == 0;
    }

}