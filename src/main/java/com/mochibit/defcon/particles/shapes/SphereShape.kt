package com.mochibit.defcon.particles.shapes

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Location
import org.bukkit.Particle
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SphereShape(particle: Particle,
                  spawnPoint: Location,
                  val heightRadius: Double, val widthRadius: Double, val rate: Double
) : ParticleShape(particle, spawnPoint) {
    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>()

        // Cache
        val rateDiv = PI / rate

        // To make a sphere we're going to generate multiple circles
        // next to each other.
        var phi = 0.0
        while (phi <= PI) {
            // Cache
            val y1 = heightRadius * cos(phi)
            val y2 = widthRadius * sin(phi)

            var theta = 0.0
            while (theta <= 2 * PI) {
                val x = cos(theta) * y2
                val z = sin(theta) * y2

                // We're going to do the same thing from spreading circle.
                // Since this is a 3D shape we'll need to get the y value as well.
                // I'm not sure if this is the right way to do it.
                val omega = atan2(z, x)
                val directionX = cos(omega)
                val directionY = sin(atan2(y2, y1))
                val directionZ = sin(omega)

                particleBuilder.offset(directionX, directionY, directionZ)
                result.add(ParticleVertex(Vector3(x, y1, z)))
                theta += rateDiv
            }
            phi += rateDiv
        }
        return result.toTypedArray();
    }

}