package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import com.mochibit.nuclearcraft.utils.MathFunctions
import org.bukkit.Particle
import kotlin.math.*

class CircleShape(
    particle: Particle,
    private var radiusX: Double,
    private var radiusZ: Double,
    private var extension: Double,
    private var rate: Double,
    private var limit: Double,
) : ParticleShape(particle) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>()

        val rateDiv = PI / abs(rate)

        // If no limit is specified do a full loop.
        if (limit == 0.0) limit = MathFunctions.TAU
        else if (limit == -1.0) limit = MathFunctions.TAU / abs(extension)

        // If the extension changes (isn't 1), the wave might not do a full
        // loop anymore. So by simply dividing PI from the extension you can get the limit for a full loop.
        // By full loop it means: sin(bx) {0 < x < PI} if b (the extension) is equal to 1
        // Using period => T = 2PI/|b|
        var theta = 0.0
        while (theta <= limit) {
            // In order to curve our straight line in the loop, we need to
            // use cos and sin. It doesn't matter, you can get x as sin and z as cos.
            // But you'll get weird results if you use si+n or cos for both or using tan or cot.
            val x = radiusX * cos(extension * theta)
            val z = radiusZ * sin(extension * theta)


            if (isDirectional()) {
                val phi = atan2(z, x)
                val directionX = cos(extension * phi)
                val directionZ = sin(extension * phi)

                this.particleBuilder.offset(directionX, this.particleBuilder.offsetY(), directionZ)
            }

            result.add(Vector3(x, 0.0, z))
            theta += rateDiv
        }

        return result;
    }

}