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
class FullHemiSphereShape(
    private val particle: Particle, spawnPoint: Location,
    var radiusY: Double, public var radiusXZ: Double,
    private val skipRadiusY: Double = 0.0, private val skipRadiusXZ: Double = 0.0,
    private val density: Double = 1.0, private val yStart: Double = 0.0,
    private val yEnd: Double = radiusY, private val hollow : Boolean = false,
    private val ignoreTopSurface: Boolean = false, private val ignoreBottomSurface: Boolean = false
): ParticleShape(particle, spawnPoint){

    //TODO Convert this stuff to a builder pattern

    override fun build(): Array<ParticleVertex> {
        val result = HashSet<ParticleVertex>();
        val sphere = SphereShape(particle, spawnPoint, radiusY, radiusXZ, skipRadiusY, skipRadiusXZ, density, hollow).build();
        for (vertex in sphere) {
            if (vertex.point.y !in yStart..yEnd) continue;
            result.add(vertex);
        }

        if (hollow) {
            // Fill the hemisphere surface at yStart and yEnd
            if (!ignoreTopSurface)
                result.addAll(getRadiusAreaAtY(yEnd));

            if (!ignoreBottomSurface)
                result.addAll(getRadiusAreaAtY(yStart));
        }



        return result.toTypedArray()
    }

    fun getRadiusAreaAtY(yRadius: Double) : HashSet<ParticleVertex> {
        val result = HashSet<ParticleVertex>();
        val incrementValue = 1.0/ density;
        var x = -radiusXZ
        while (x < radiusXZ) {
            var z = -radiusXZ
            while (z < radiusXZ) {

                if (skipRadiusXZ > 0.0) {
                    if (x * x + z * z <= skipRadiusXZ * skipRadiusXZ) {
                        z += incrementValue
                        continue;
                    }
                }

                val radiusCheck = (x * x) / (radiusXZ * radiusXZ) + (yRadius * yRadius) / (radiusY * radiusY) + (z * z) / (radiusXZ * radiusXZ)
                if (radiusCheck <= 1) {
                    result.add(ParticleVertex(Vector3(x, yRadius, z)));
                }
                z += incrementValue
            }
            x += incrementValue
        }
        return result;
    }



}