package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

class SphereShape(particle: Particle, val heightRadius: Double, val widthRadius: Double, val rate: Double) : ParticleShape(particle) {
    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>()

        // Cache
        val rateDiv = java.lang.Math.PI / rate

        // To make a sphere we're going to generate multiple circles
        // next to each other.
        var phi = 0.0
        while (phi <= java.lang.Math.PI) {
            // Cache
            val y1 = heightRadius * cos(phi)
            val y2 = widthRadius * sin(phi)

            var theta = 0.0
            while (theta <= 2 * java.lang.Math.PI) {
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
                result.add(Vector3(x, y1, z))
                theta += rateDiv
            }
            phi += rateDiv
        }
        return result;
    }

}