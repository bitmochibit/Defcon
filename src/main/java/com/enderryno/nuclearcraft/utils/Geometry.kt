package com.enderryno.nuclearcraft.utils

import org.bukkit.Location
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
            while (hull.size > 0 && ccw(hull.peek(), top, sortedPoints[i]) <= 0) {
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

    fun rotateStructureXZ(points: List<Location>) : MutableList<Location> {
        val rotatedPoints : MutableList<Location> = ArrayList()
        for (point in points) {
            val rotatedX = point.z
            val rotatedZ = points.maxByOrNull { it.x }!!.x - point.x
            rotatedPoints.add(Location(point.world, rotatedX, point.y, rotatedZ))
        }
        return rotatedPoints
    }

    private fun ccw(p1: Location, p2: Location, p3: Location): Int {
        return (p2.blockX - p1.blockX) * (p3.blockZ - p1.blockZ) - (p2.blockZ - p1.blockZ) * (p3.blockX - p1.blockX)
    }
}
