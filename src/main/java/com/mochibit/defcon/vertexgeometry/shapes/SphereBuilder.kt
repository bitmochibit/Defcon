package com.mochibit.defcon.vertexgeometry.shapes

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder

/**
 * This class represents a full sphere shape, which is a sphere that is filled with particles
 */
class SphereBuilder : VertexShapeBuilder {
    var radiusY: Double = .0; private set
    var radiusXZ: Double = .0; private set
    var skipRadiusY: Double = .0; private set
    var skipRadiusXZ: Double = .0; private set
    var density: Double = 1.0; private set
    var yStart: Double? = null; private set
    var yEnd: Double? = null; private set
    var hollow: Boolean = false; private set
    var ignoreTopSurface: Boolean = false; private set
    var ignoreBottomSurface: Boolean = false; private set

    override fun build(): Array<Vertex> {
        val sphere = mutableListOf<Vertex>()
        val incrementValue = 1.0 / density
        val startY = yStart ?: -radiusY
        val endY = yEnd ?: radiusY

        var x = -radiusXZ
        while (x < radiusXZ) {
            var y = startY
            while (y < endY) {
                if (!checkYBounds(y)) {
                    y += incrementValue
                    continue
                }
                var z = -radiusXZ
                while (z < radiusXZ) {
                    if (checkInSkipRadius(x, y, z)) {
                        z += incrementValue
                        continue
                    }

                    val radiusCheck = radiusCheck(x, y, z)

                    if (hollow && (radiusCheck !in 0.9..1.0 && !hollowValidSurfaceY(y))) {
                        z += incrementValue
                        continue
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
        return yStart == null && yEnd == null || y in ((yStart ?: -radiusY)..(yEnd ?: radiusY))
    }

    private fun checkInSkipRadius(x: Double, y: Double, z: Double): Boolean {
        if (skipRadiusY <= 0 && skipRadiusXZ <= 0) return false

        val xzComponent = if (skipRadiusXZ > 0) {
            (x * x) / (skipRadiusXZ * skipRadiusXZ) + (z * z) / (skipRadiusXZ * skipRadiusXZ)
        } else 0.0

        val yComponent = if (skipRadiusY > 0) {
            (y * y) / (skipRadiusY * skipRadiusY)
        } else 0.0

        return xzComponent + yComponent <= 1
    }

    private fun radiusCheck(x: Double, y: Double, z: Double): Double {
        return (x * x) / (radiusXZ * radiusXZ) + (y * y) / (radiusY * radiusY) + (z * z) / (radiusXZ * radiusXZ)
    }

    private fun hollowValidSurfaceY(y: Double): Boolean {
        return when {
            ignoreTopSurface && y == yEnd -> false
            ignoreBottomSurface && y == yStart -> false
            y != yEnd && y != yStart -> false
            else -> true
        }
    }

    fun withRadiusY(radiusY: Double) = apply { this.radiusY = radiusY }
    fun withRadiusXZ(radiusXZ: Double) = apply { this.radiusXZ = radiusXZ }

    fun withYStart(yStart: Double) = apply { this.yStart = yStart }

    fun withYEnd(yEnd: Double) = apply { this.yEnd = yEnd }

    fun skipRadiusY(skipRadiusY: Double) = apply { this.skipRadiusY = skipRadiusY }

    fun skipRadiusXZ(skipRadiusXZ: Double) = apply { this.skipRadiusXZ = skipRadiusXZ }

    fun withDensity(density: Double) = apply { this.density = density }

    fun hollow(hollow: Boolean) = apply { this.hollow = hollow }

    fun ignoreTopSurface(ignoreTopSurface: Boolean) = apply { this.ignoreTopSurface = ignoreTopSurface }

    fun ignoreBottomSurface(ignoreBottomSurface: Boolean) = apply { this.ignoreBottomSurface = ignoreBottomSurface }

}
