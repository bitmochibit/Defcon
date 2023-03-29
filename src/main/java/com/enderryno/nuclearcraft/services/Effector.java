package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.*;

public class Effector {

    /**
     * <p> Generate a cuboid region which is the main source of radiation </p>
     * @param center The center of the radiation field
     * @param maxRange The maximum range of the radiation field
     *
     */
    public static void generateRadiationField(Location center, int maxRange) {
        List<Location> blockLocations = getFloodFill(center, maxRange);

        List<BlockVector2> points = new ArrayList<>();
        for (Location loc : blockLocations) {
            points.add(BlockVector2.at(loc.getBlockX(), loc.getBlockZ()));
        }

        // Get the max Y and min Y from the blockLocations
        int maxY = blockLocations.get(0).getBlockY();
        int minY = blockLocations.get(0).getBlockY();
        for (Location loc : blockLocations) {
            if (loc.getBlockY() > maxY) {
                maxY = loc.getBlockY();
            }
            if (loc.getBlockY() < minY) {
                minY = loc.getBlockY();
            }
        }

        NuclearCraft.instance.getLogger().info("Max Y: " + maxY);
        NuclearCraft.instance.getLogger().info("Min Y: " + minY);

        ProtectedRegion region = new ProtectedPolygonalRegion("radiation", points, minY, maxY);
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(center.getWorld()));
        regions.addRegion(region);


    }

    private static List<Location> getFloodFill(Location startLoc, int maxRange) {

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
