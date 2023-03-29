package com.enderryno.nuclearcraft.utils;

import com.enderryno.nuclearcraft.NuclearCraft;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

public class Geometry {
    public static List<Location> getConvexHullXZ(List<Location> points) {
        // Applying Graham Scan Algorithm
        Location lowestPoint = points.get(0);
        // Get the lowest z-coordinate point (and the leftmost if there are multiple)
        for (Location point : points) {
            if (point.getBlockZ() < lowestPoint.getBlockZ()) {
                lowestPoint = point;
            } else if (point.getBlockZ() == lowestPoint.getBlockZ()) {
                if (point.getBlockX() < lowestPoint.getBlockX()) {
                    lowestPoint = point;
                }
            }
        }

        // Sort the points by their polar angle with the lowest point
        List<Location> sortedPoints = new ArrayList<>(points);
        sortedPoints.remove(lowestPoint);
        Location finalLowestPoint = lowestPoint;
        sortedPoints.sort((p1, p2) -> {
            double angle1 = Math.atan2(p1.getBlockZ() - finalLowestPoint.getBlockZ(), p1.getBlockX() - finalLowestPoint.getBlockX());
            double angle2 = Math.atan2(p2.getBlockZ() - finalLowestPoint.getBlockZ(), p2.getBlockX() - finalLowestPoint.getBlockX());
            return Double.compare(angle1, angle2);
        });
        sortedPoints.add(0, lowestPoint);

        // Build the convex hull using a stack
        Stack<Location> hull = new Stack<>();
        if (sortedPoints.size() < 3) {
            return sortedPoints;
        }
        hull.push(sortedPoints.get(0));
        hull.push(sortedPoints.get(1));
        for (int i = 2; i < sortedPoints.size(); i++) {
            Location top = hull.pop();
            while (hull.size() > 0 && ccw(hull.peek(), top, sortedPoints.get(i)) <= 0) {
                top = hull.pop();
            }
            hull.push(top);
            hull.push(sortedPoints.get(i));
        }
        List<Location> convexHull = new ArrayList<>();
        while (!hull.empty()) {
            convexHull.add(hull.pop());
        }

        return convexHull;
    }
    private static int ccw(Location p1, Location p2, Location p3) {
        return (p2.getBlockX() - p1.getBlockX()) * (p3.getBlockZ() - p1.getBlockZ()) - (p2.getBlockZ() - p1.getBlockZ()) * (p3.getBlockX() - p1.getBlockX());
    }
}
