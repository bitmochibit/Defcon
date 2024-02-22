package com.mochibit.nuclearcraft.particles.shapes

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector

abstract class ParticleShape(particle: Particle) {
    val points = HashSet<Vector3>();
    val particleBuilder = ParticleBuilder(particle);

    open fun draw(location: Location) {

    }
    abstract fun build() : HashSet<Vector3>;

    fun directional() : ParticleShape {
        particleBuilder.count(0);
        return this;
    }
    fun isDirectional() : Boolean {
        return particleBuilder.count() == 0;
    }

}