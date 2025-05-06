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

package me.mochibit.defcon.utils

import me.mochibit.defcon.classes.StructureBlock
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector3d
import java.util.*
import kotlin.math.atan2

object Geometry {
    fun getConvexHullXZ(points: List<Location>): MutableList<Location> {
        // Applying Graham Scan Algorithm
        var lowestPoint = points[0]
        // Get the lowest z-coordinate point (and the leftmost if there are multiple)
        for (point in points) {
            if (point.blockZ < lowestPoint.blockZ) {
                lowestPoint = point
            } else if (point.blockZ == lowestPoint.blockZ) {
                if (point.blockX < lowestPoint.blockX) {
                    lowestPoint = point
                }
            }
        }

        // Sort the points by their polar angle with the lowest point
        val sortedPoints: MutableList<Location> = ArrayList(points)
        sortedPoints.remove(lowestPoint)
        val finalLowestPoint = lowestPoint
        sortedPoints.sortWith { p1: Location, p2: Location ->
            val angle1 = atan2(
                (p1.blockZ - finalLowestPoint.blockZ).toDouble(),
                (p1.blockX - finalLowestPoint.blockX).toDouble()
            )
            val angle2 = atan2(
                (p2.blockZ - finalLowestPoint.blockZ).toDouble(),
                (p2.blockX - finalLowestPoint.blockX).toDouble()
            )
            angle1.compareTo(angle2)
        }
        sortedPoints.add(0, lowestPoint)

        // Build the convex hull using a stack
        val hull = Stack<Location>()
        if (sortedPoints.size < 3) {
            return sortedPoints
        }
        hull.push(sortedPoints[0])
        hull.push(sortedPoints[1])
        for (i in 2 until sortedPoints.size) {
            var top = hull.pop()
            while (hull.size > 0 && ccw(
                    hull.peek(),
                    top,
                    sortedPoints[i]
                ) <= 0
            ) {
                top = hull.pop()
            }
            hull.push(top)
            hull.push(sortedPoints[i])
        }
        val convexHull: MutableList<Location> = ArrayList()
        while (!hull.empty()) {
            convexHull.add(hull.pop())
        }
        return convexHull
    }

    // Todo - make this work for any angle of rotation
    fun rotateLocationPlaneXZ(points: List<Location>, angle: Float = 90f): MutableList<Location> {
        val rotatedPoints: MutableList<Location> = ArrayList()
        for (point in points) {
            val rotatedX = point.z
            val rotatedZ = points.maxByOrNull { it.x }!!.x - point.x
            rotatedPoints.add(Location(point.world, rotatedX, point.y, rotatedZ))
        }
        return rotatedPoints
    }

    fun rotateStructureBlockPlaneXZ(
        structureBlocks: List<StructureBlock>,
        angle: Float = 90f
    ): MutableList<StructureBlock> {
        val rotatedStructure: MutableList<StructureBlock> = ArrayList()
        for (structureBlock in structureBlocks) {
            val rotatedX = structureBlock.z
            val rotatedZ = structureBlocks.maxByOrNull { it.x }!!.x - structureBlock.x
            rotatedStructure.add(
                StructureBlock(
                    structureBlock.block,
                    rotatedX,
                    structureBlock.y,
                    rotatedZ,
                    structureBlock.isInterface
                )
            )
        }
        return rotatedStructure
    }

    fun getMinY(position: Location, maxDepth: Double? = null): Location {
        val clonedPosition = position.clone()
        // Coerce the Y coordinate to the world height limit
        clonedPosition.y = clonedPosition.y.coerceAtMost((clonedPosition.world.maxHeight - 1).toDouble())
        var depth = 0
        while (clonedPosition.world.getBlockAt(clonedPosition).type == Material.AIR && (maxDepth == null || depth < maxDepth) && clonedPosition.y >= position.world.minHeight) {
            clonedPosition.subtract(0.0, 1.0, 0.0)
            depth++
        }
        return clonedPosition
    }

    fun getMinYUsingSnapshotFromCache(
        world: World,
        position: Vector3d,
        maxDepth: Double?,
        chunkSnapshotCache: MutableMap<Pair<Int, Int>, ChunkSnapshot>
    ): Vector3d {
        position.y = getMinYUsingSnapshotFromCache(
            Location(world, position.x, position.y, position.z),
            maxDepth,
            chunkSnapshotCache
        ).y
        return position
    }

    fun getMinYUsingSnapshotFromCache(
        position: Location,
        maxDepth: Double?,
        chunkSnapshotCache: MutableMap<Pair<Int, Int>, ChunkSnapshot>
    ): Location {
        val blockX = position.blockX
        val blockZ = position.blockZ
        return if (chunkSnapshotCache.containsKey(Pair(blockX shr 4, blockZ shr 4)))
            getMinYUsingSnapshot(position, maxDepth, chunkSnapshotCache[Pair(blockX shr 4, blockZ shr 4)])
        else
            getMinYUsingSnapshot(position, maxDepth)
    }

    fun getMinYUsingSnapshot(
        position: Location,
        maxDepth: Double?,
        givenChunkSnapshot: ChunkSnapshot? = null
    ): Location {
        val clonedPosition = position.clone()
        val chunkSnapshot = givenChunkSnapshot ?: position.chunk.chunkSnapshot

        val localX = position.blockX and 15
        val localZ = position.blockZ and 15

        // Return the location where the material is not air or the maximum depth has been reached
        clonedPosition.y = chunkSnapshot.getHighestBlockYAt(localX, localZ).toDouble()
        return clonedPosition
    }


    fun lengthSq(x: Double, y: Double, z: Double): Double {
        return (x * x) + (y * y) + (z * z)
    }

    fun lengthSq(x: Double, z: Double): Double {
        return (x * x) + (z * z)
    }

    fun lengthSq(loc: Location): Double {
        return lengthSq(loc.x, loc.y, loc.z)
    }

    private fun ccw(p1: Location, p2: Location, p3: Location): Int {
        return (p2.blockX - p1.blockX) * (p3.blockZ - p1.blockZ) - (p2.blockZ - p1.blockZ) * (p3.blockX - p1.blockX)
    }

    fun packIntegerCoordinates(x: Int, y: Int, z: Int): Long {
        return (x.toLong() and 0x1FFFFF) or ((y.toLong() and 0x1FFFFF) shl 21) or ((z.toLong() and 0x1FFFFF) shl 42)
    }

    fun unpackIntegerCoordinates(packed: Long): Triple<Int, Int, Int> {
        val x = (packed and 0x1FFFFF).toInt()
        val y = ((packed shr 21) and 0x1FFFFF).toInt()
        val z = ((packed shr 42) and 0x1FFFFF).toInt()
        return Triple(x, y, z)
    }


    fun packCoordinates(x: Int, z: Int): Long {
        return (x.toLong() shl 32) or (z.toLong() and 0xFFFFFFFFL)
    }

    fun unpackX(packed: Long): Int {
        return (packed shr 32).toInt()
    }

    fun unpackZ(packed: Long): Int {
        return (packed and 0xFFFFFFFFL).toInt()
    }

    fun wangNoise(x: Int, y: Int, z: Int): Double {
        // Wang hash for better pseudo-random distribution
        var hash = x.toLong() * 0x1f1f1f1f
        hash = hash xor (y.toLong() * 0x27d4eb2d)
        hash = hash xor (z.toLong() * 0x85ebca77)
        hash = hash xor (hash ushr 15)
        hash *= 0xc2b2ae3d
        hash = hash xor (hash ushr 16)
        return (hash and 0x7FFFFFFF) / 2147483647.0
    }
}
