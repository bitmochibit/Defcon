package com.mochibit.nuclearcraft.particles.shapes

import com.mochibit.nuclearcraft.math.Vector3
import org.bukkit.Particle
import org.bukkit.util.Vector

class FullSphereShape(private val particle: Particle, private val heightRadius: Double, private val widthRadius: Double): ParticleShape(particle){

    override fun build(): HashSet<Vector3> {
        val result = HashSet<Vector3>();

        for (x in -widthRadius.toInt()..widthRadius.toInt()) {
            for (y in -heightRadius.toInt()..heightRadius.toInt()) {
                for (z in -widthRadius.toInt()..widthRadius.toInt()) {
                    if ((x * x) / (widthRadius * widthRadius) + (y * y) / (heightRadius * heightRadius) + (z * z) / (widthRadius * widthRadius) <= 1)
                        result.add(Vector3(x.toDouble(), y.toDouble(), z.toDouble()))
                }
            }
        }

        return result;
    }



}