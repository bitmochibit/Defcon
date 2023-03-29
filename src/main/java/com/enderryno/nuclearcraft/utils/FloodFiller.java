package com.enderryno.nuclearcraft.utils;

import org.bukkit.Location;
import org.bukkit.block.Block;

import java.util.*;

public class FloodFiller {
    public static List<Location> getFloodFill(Location startLoc, int maxRange) {

        Set<Location> visited = new HashSet<>();
        Queue<Location> queue = new LinkedList<>();
        queue.add(startLoc);
        visited.add(startLoc);

        List<Location> locations = new ArrayList<>();

        int range = 0;
        while (!queue.isEmpty() && range <= maxRange) {
            int levelSize = queue.size();
            for (int i = 0; i < levelSize; i++) {
                Location loc = queue.poll();
                Block block = loc.getBlock();
                if (!block.isSolid()) {
                    locations.add(loc);
                    addNeighbors(loc, queue, visited);
                }
            }
            range++;
        }
        return locations;
    }
    private static void addNeighbors(Location loc, Queue<Location> queue, Set<Location> visited) {
        int x = loc.getBlockX();
        int y = loc.getBlockY();
        int z = loc.getBlockZ();

        Location neighbor = new Location(loc.getWorld(), x + 1, y, z);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }

        neighbor = new Location(loc.getWorld(), x - 1, y, z);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }

        neighbor = new Location(loc.getWorld(), x, y + 1, z);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }

        neighbor = new Location(loc.getWorld(), x, y - 1, z);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }

        neighbor = new Location(loc.getWorld(), x, y, z + 1);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }

        neighbor = new Location(loc.getWorld(), x, y, z - 1);
        if (!visited.contains(neighbor)) {
            queue.add(neighbor);
            visited.add(neighbor);
        }
    }
}
