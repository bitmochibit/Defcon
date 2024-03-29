package com.mochibit.defcon.particles.shapes

import com.mochibit.defcon.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle

class FullCircleShape(
    private var particle: Particle,
    spawnPoint: Location,
    private var radiusX: Double,
    private var radiusZ: Double,
    private var rate: Double,
    private var radiusRate: Double
) : ParticleShape(particle, spawnPoint) {
    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>()

        var dynamicRate = 0.0;
        var i = 0.1
        var j = 0.1
        while (i < radiusX || j < radiusZ) {
            // Dynamic rate depending both on the width and the height radius
            dynamicRate += rate / (radiusX / radiusRate)
            result.addAll(CircleShape(particle, spawnPoint, i, j, 1.0, dynamicRate, 0.0).build());

            i += radiusRate
            j += radiusRate

            if (i > radiusX)
                i = radiusX

            if (j > radiusZ)
                j = radiusZ
        }
        return result.toTypedArray();
    }

}