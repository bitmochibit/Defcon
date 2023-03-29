package com.enderryno.nuclearcraft.services;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.enderryno.nuclearcraft.utils.FloodFiller;
import com.enderryno.nuclearcraft.utils.Geometry;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector2;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;

import java.util.*;

public class Effector {

    /**
     * <p> Generate a polygonal region from a Flood-Fill from a center, to a maxRange </p>
     * <p> <strong>Warning</strong> this can be intensive on hardware if not handled correctly</p>
     * @param center The center of the radiation field
     * @param maxRange The maximum range of the radiation field
     */
    public static void generateRadiationField(Location center, int maxRange) {
        List<Location> blockLocations = FloodFiller.getFloodFill(center, maxRange);


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

        // Get convex hull of the blockLocations
        blockLocations = Geometry.getConvexHullXZ(blockLocations);

        List<BlockVector2> points = new ArrayList<>();
        for (Location loc : blockLocations) {
            points.add(BlockVector2.at(loc.getBlockX(), loc.getBlockZ()));
        }

        NuclearCraft.instance.getLogger().info("Max Y: " + maxY);
        NuclearCraft.instance.getLogger().info("Min Y: " + minY);

        ProtectedRegion region = new ProtectedPolygonalRegion("radiation", points, minY, maxY);
        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(center.getWorld()));
        regions.addRegion(region);


    }



}
