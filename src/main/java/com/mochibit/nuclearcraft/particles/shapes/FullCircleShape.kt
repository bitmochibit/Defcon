package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Particle

class FullCircleShape(
    private var particle: Particle,
    private var radiusX: Double,
    private var radiusZ: Double,
    private var rate: Double,
    private var radiusRate: Double
) : ParticleShape(particle) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>()
        var dynamicRate = 0.0

        val invRadiusRate = 1 / radiusRate

        var i = 0.1
        var j = 0.1
        while (i < radiusX || j < radiusZ) {
            // Dynamic rate depending both on the width and the height radius
            dynamicRate = rate / ((radiusX / radiusRate) * (radiusZ / radiusRate))
            result.addAll(CircleShape(particle, i, j, 1.0, dynamicRate, 0.0).build());
            i += invRadiusRate
            j += invRadiusRate

            if (i > radiusX)
                i = radiusX

            if (j > radiusZ)
                j = radiusZ
        }
        return result;
    }

}