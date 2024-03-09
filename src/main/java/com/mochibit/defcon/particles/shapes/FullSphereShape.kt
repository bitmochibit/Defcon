package com.mochibit.defcon.particles.shapes

import com.mochibit.defcon.math.Vector3
import org.bukkit.Location
import org.bukkit.Particle

/**
 * This class represents a full sphere shape, which is a sphere that is filled with particles
 * @param particle The particle to use
 * @param spawnPoint The spawn point of the particle
 * @param radiusY The radius in the Y axis
 * @param radiusXZ The radius in the X and Z axis
 * @param skipRadius The radius to skip (useful for creating holes in the sphere)
 * @constructor
 */
class FullSphereShape(private val particle: Particle, spawnPoint: Location,
                      private val radiusY: Double, private val radiusXZ: Double,
                      private val skipRadiusY: Double = 0.0, private val skipRadiusXZ: Double = 0.0,
                      private val density: Double = 1.0
): ParticleShape(particle, spawnPoint){

    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>();
        val incrementValue = 1.0/ density;

        var x = -radiusXZ
        while (x < radiusXZ) {
            var y = -radiusY
            while (y < radiusY) {
                var z = -radiusXZ
                while (z < radiusXZ) {
                    if (skipRadiusY > 0 || skipRadiusXZ > 0)
                        if ((x * x) / (skipRadiusXZ * skipRadiusXZ) + (y * y) / (skipRadiusY * skipRadiusY) + (z * z) / (skipRadiusXZ * skipRadiusXZ) <= 1) {
                            z += incrementValue
                            continue;
                        }

                    if ((x * x) / (radiusXZ * radiusXZ) + (y * y) / (radiusY * radiusY) + (z * z) / (radiusXZ * radiusXZ) <= 1)
                        result.add(ParticleVertex(Vector3(x, y, z)))
                    z += incrementValue
                }
                y += incrementValue
            }
            x += incrementValue
        }

        return result.toTypedArray();
    }



}