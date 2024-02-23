package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Particle
import org.bukkit.util.Vector

class FullCircleShape(
    private var particle: Particle,
    private var radiusWidth: Double,
    private var radiusHeight: Double,
    private var rate: Double,
    private var radiusRate: Double
) : ParticleShape(particle) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>()
        var dynamicRate = 0.0

        var i = 0.1
        var j = 0.1
        while (i < radiusWidth || j < radiusHeight) {
            // Dynamic rate depending both on the width and the height radius
            dynamicRate = rate / ((radiusWidth / radiusRate) * (radiusHeight / radiusRate))
            result.addAll(CircleShape(particle, i, j, 1.0, dynamicRate, 0.0).build());
            i += radiusRate
            j += radiusRate

            if (i > radiusWidth)
                i = radiusWidth

            if (j > radiusHeight)
                j = radiusHeight
        }
        return result;
    }

}