package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector

class FullCylinderShape(val particle: Particle, spawnPoint: Location,
                        var height: Double, val radiusWidth: Double, val radiusHeight: Double, val rate: Double,
) : ParticleShape(particle, spawnPoint) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>();
        var y = 0.0
        while (y < height) {
            result.addAll(FullCircleShape(particle, spawnPoint, radiusWidth, radiusHeight, rate, 3.0).build().map { it + Vector3(0.0, y, 0.0) })
            y += 0.1
        }
        return result;
    }

}