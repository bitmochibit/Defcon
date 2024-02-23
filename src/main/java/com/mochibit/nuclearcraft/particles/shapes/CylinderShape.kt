package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Particle
import org.bukkit.util.Vector

class CylinderShape(val particle: Particle, var height: Double, val radiusWidth: Double, val radiusHeight: Double, val rate: Double) : ParticleShape(particle) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>();
        var y = 0.0
        while (y < height) {
            result.addAll(CircleShape(particle, radiusWidth, radiusHeight, 1.0, rate, 0.0).build().map { it + Vector3(0.0, y, 0.0) })
            y += 0.1
        }
        return result;
    }

}