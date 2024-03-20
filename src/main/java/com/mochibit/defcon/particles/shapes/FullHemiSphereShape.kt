package com.mochibit.defcon.particles.shapes

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
class FullHemiSphereShape(
    private val particle: Particle, spawnPoint: Location,
    private val radiusY: Double, private val radiusXZ: Double,
    private val skipRadiusY: Double = 0.0, private val skipRadiusXZ: Double = 0.0,
    private val density: Double = 1.0, private val yStart: Double = 0.0,
    private val yEnd: Double = radiusY
): ParticleShape(particle, spawnPoint){

    // TODO: Add an option to make it hollow (like a shell)

    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>();
        val sphere = FullSphereShape(particle, spawnPoint, radiusY, radiusXZ, skipRadiusY, skipRadiusXZ, density).build();
        for (vertex in sphere) {
            if (vertex.point.y in yStart..yEnd)
                result.add(vertex);
        }
        return result.toTypedArray()
    }



}