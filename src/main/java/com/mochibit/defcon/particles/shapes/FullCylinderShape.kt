package com.mochibit.defcon.particles.shapes

import com.mochibit.defcon.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle

class FullCylinderShape(val particle: Particle, spawnPoint: Location,
                        var height: Double, val radiusWidth: Double, val radiusHeight: Double, val rate: Double,
) : ParticleShape(particle, spawnPoint) {
    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>();
        var y = 0.0
        while (y < height) {
            result.addAll(FullCircleShape(particle, spawnPoint, radiusWidth, radiusHeight, rate, 3.0).build()
                .map {
                    particleVertex -> ParticleVertex(Vector3(particleVertex.point.x, y, particleVertex.point.z))
                })
            y += 0.1
        }
        return result.toTypedArray();
    }

}