package com.mochibit.nuclearcraft.utils

import com.mochibit.nuclearcraft.classes.StructureBlock
import org.bukkit.Location
import org.bukkit.Material
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
            while (hull.size > 0 && com.mochibit.nuclearcraft.utils.Geometry.ccw(
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
        val clonedPosition = position.clone();

        var depth = 0;
        while (clonedPosition.world.getBlockAt(clonedPosition).type == Material.AIR && (maxDepth == null || depth < maxDepth) && clonedPosition.y >= position.world.minHeight) {
            clonedPosition.subtract(0.0, 1.0, 0.0);
            depth++;
        }
        return clonedPosition;
    }
    fun getMaxY(position: Location, maxHeight: Double? = null): Location {
        val clonedPosition = position.clone();

        var height = 0;
        while (clonedPosition.world.getBlockAt(clonedPosition).type == Material.AIR && (maxHeight == null || height < maxHeight) && clonedPosition.y <= position.world.maxHeight) {
            clonedPosition.add(0.0, 1.0, 0.0);
            height++;
        }
        return clonedPosition;
    }

    fun lengthSq(x: Double, y: Double, z: Double): Double {
        return (x * x) + (y * y) + (z * z);
    }

    fun lengthSq(x: Double, z: Double): Double {
        return (x * x) + (z * z);
    }

    fun lengthSq(loc: Location): Double {
        return lengthSq(loc.x, loc.y, loc.z);
    }

    private fun ccw(p1: Location, p2: Location, p3: Location): Int {
        return (p2.blockX - p1.blockX) * (p3.blockZ - p1.blockZ) - (p2.blockZ - p1.blockZ) * (p3.blockX - p1.blockX)
    }
}
