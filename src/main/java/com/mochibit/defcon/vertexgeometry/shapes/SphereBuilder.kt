/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mochibit.defcon.vertexgeometry.shapes

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder

/**
 * This class represents a full sphere shape, which is a sphere that is filled with particles
 * @param particle The particle to use
 * @param spawnPoint The spawn point of the particle
 * @param radiusY The radius in the Y axis
 * @param radiusXZ The radius in the X and Z axis
 * @param skipRadius The radius to skip (useful for creating holes in the sphere)
 * @constructor
 */
class SphereBuilder() : VertexShapeBuilder {

    private var radiusY: Double = .0
    private var radiusXZ: Double = .0

    private var skipRadiusY: Double = .0
    private var skipRadiusXZ: Double = .0

    private var density: Double = 1.0

    private var yStart: Double? = null
    private var yEnd: Double? = null

    private var hollow: Boolean = false

    private var ignoreTopSurface: Boolean = false
    private var ignoreBottomSurface: Boolean = false


    override fun build(): Array<Vertex> {
        val sphere = HashSet<Vertex>();
        val incrementValue = 1.0 / density;

        var x = -radiusXZ
        while (x < radiusXZ) {
            var y = -radiusY

            while (y < radiusY) {
                if (!checkYBounds(y)) {
                    y += incrementValue
                    continue;
                }
                var z = -radiusXZ

                while (z < radiusXZ) {
                    if (checkInSkipRadius(x, y, z)) {
                        z += incrementValue
                        continue;
                    }

                    val radiusCheck = radiusCheck(x, y, z)

                    if (hollow && (radiusCheck !in 0.9..1.0 && !hollowValidSurfaceY(y))) {
                        z += incrementValue
                        continue;
                    }

                    if (radiusCheck <= 1) {
                        sphere.add(Vertex(Vector3(x, y, z)))
                    }
                    z += incrementValue
                }
                y += incrementValue
            }
            x += incrementValue
        }
        return sphere.toTypedArray()
    }

    private fun checkYBounds(y: Double): Boolean {
        if (yStart == null && yEnd == null)
            return true;

        return y in ((yStart ?: -radiusY)..(yEnd ?: radiusY))
    }

    private fun checkInSkipRadius(x: Double, y: Double, z: Double): Boolean {
        if (skipRadiusY <= 0 && skipRadiusXZ <= 0)
            return false;

        return (x * x) / (skipRadiusXZ * skipRadiusXZ) + (y * y) / (skipRadiusY * skipRadiusY) + (z * z) / (skipRadiusXZ * skipRadiusXZ) <= 1
    }

    private fun radiusCheck(x: Double, y: Double, z: Double): Double {
        return (x * x) / (radiusXZ * radiusXZ) + (y * y) / (radiusY * radiusY) + (z * z) / (radiusXZ * radiusXZ)
    }

    private fun hollowValidSurfaceY(y: Double): Boolean {
        if (ignoreTopSurface && y == yEnd) return false;
        if (ignoreBottomSurface && y == yStart) return false;

        if (y != yEnd && y != yStart) return false;

        return true;
    }

    // Builder methods
    fun withRadiusY(radiusY: Double): SphereBuilder {
        this.radiusY = radiusY
        return this
    }

    fun withRadiusXZ(radiusXZ: Double): SphereBuilder {
        this.radiusXZ = radiusXZ
        return this
    }

    fun withYStart(yStart: Double): SphereBuilder {
        this.yStart = yStart
        return this
    }

    fun withYEnd(yEnd: Double): SphereBuilder {
        this.yEnd = yEnd
        return this
    }

    fun skipRadiusY(skipRadiusY: Double): SphereBuilder {
        this.skipRadiusY = skipRadiusY
        return this
    }

    fun skipRadiusXZ(skipRadiusXZ: Double): SphereBuilder {
        this.skipRadiusXZ = skipRadiusXZ
        return this
    }

    fun withDensity(density: Double): SphereBuilder {
        this.density = density
        return this
    }

    fun hollow(hollow: Boolean): SphereBuilder {
        this.hollow = hollow
        return this
    }

    fun ignoreTopSurface(ignoreTopSurface: Boolean): SphereBuilder {
        this.ignoreTopSurface = ignoreTopSurface
        return this
    }

    fun ignoreBottomSurface(ignoreBottomSurface: Boolean): SphereBuilder {
        this.ignoreBottomSurface = ignoreBottomSurface
        return this
    }

    // Getters

    fun getRadiusY(): Double {
        return radiusY
    }

    fun getRadiusXZ(): Double {
        return radiusXZ
    }

    fun getYStart(): Double? {
        return yStart
    }

    fun getYEnd(): Double? {
        return yEnd
    }

    fun getSkipRadiusY(): Double {
        return skipRadiusY
    }

    fun getSkipRadiusXZ(): Double {
        return skipRadiusXZ
    }

    fun getDensity(): Double {
        return density
    }

    fun isHollow(): Boolean {
        return hollow
    }

    fun isIgnoreTopSurface(): Boolean {
        return ignoreTopSurface
    }

    fun isIgnoreBottomSurface(): Boolean {
        return ignoreBottomSurface
    }

}