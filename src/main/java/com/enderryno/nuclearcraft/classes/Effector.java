package com.enderryno.nuclearcraft.classes;

import com.enderryno.nuclearcraft.NuclearCraft;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import org.bukkit.Location;

import java.util.ArrayList;
import java.util.List;

public class Effector {

    /**
     * <p> Generate a cuboid region which is the main source of radiation </p>
     * @param center The center of the radiation field
     * @param maxRange The maximum range of the radiation field
     *
     */
    public static void generateRadiationField(Location center, int maxRange) {
        BlockVector3 corner1 = BlockVector3.at(center.getBlockX(), center.getBlockY(), center.getBlockZ());
        BlockVector3 corner2 = BlockVector3.at(center.getBlockX(), center.getBlockY(), center.getBlockZ());
        // Corner 1 calculation
        // Get the max depths in each positive direction of the axis (corner 1); The max is determined by the maxRange or if the block is solid
        // The loop must stop when the block is solid, otherwise if the maxRange is too big, it will go out of bounds

        int xMax = 0;
        int yMax = 0;
        int zMax = 0;

        for (int y = 0; y < maxRange; y++) {
            for (int z = 0; z < maxRange; z++) {
                for (int x = 0; x < maxRange; x++) {
                    if (center.getBlock().getRelative(x, y, z).getType().isSolid()) {
                        if (x > xMax) {
                            xMax = x;
                        }
                        break;
                    }
                }
            }
        }


        for (int x = 0; x < maxRange; x++) {
            for (int z = 0; z < maxRange; z++) {
                for (int y = 0; y < maxRange; y++) {
                    if (center.getBlock().getRelative(x, y, z).getType().isSolid()) {
                        if (y > yMax) {
                            yMax = y;
                        }
                        break;
                    }
                }
            }
        }

        for (int x = 0; x < maxRange; x++) {
            for (int y = 0; y < maxRange; y++) {
                for (int z = 0; z < maxRange; z++) {
                    if (center.getBlock().getRelative(x, y, z).getType().isSolid()) {
                        if (z > zMax) {
                            zMax = z;
                        }
                        break;
                    }
                }
            }
        }

        // Set the corner 1 (excluding the solid blocks)
        corner1 = corner1.add(xMax-1, yMax-1, zMax-1);

        // Corner 2 calculation
        // Get the max depths in each negative direction of the axis (corner 2); The max is determined by the maxRange or if the block is solid
        // The loop must stop when the block is solid, otherwise if the maxRange is too big, it will go out of bounds
        xMax = 0;
        for (int y = 0; y < maxRange; y++) {
            for (int z = 0; z < maxRange; z++) {
                for (int x = 0; x < maxRange; x++) {

                    if (center.getBlock().getRelative(-x, -y, -z).getType().isSolid()) {
                        if (x > xMax) {
                            xMax = x;
                        }
                        break;
                    }
                }
            }
        }
        NuclearCraft.instance.getLogger().info("xMax " + xMax);

        yMax = 0;
        for (int x = 0; x < maxRange; x++) {
            for (int z = 0; z < maxRange; z++) {
                for (int y = 0; y < maxRange; y++) {
                    if (center.getBlock().getRelative(-x, -y, -z).getType().isSolid()) {
                        if (y > yMax) {
                            yMax = y;
                        }
                        break;
                    }
                }
            }
        }

        zMax = 0;
        for (int x = 0; x < maxRange; x++) {
            for (int y = 0; y < maxRange; y++) {
                for (int z = 0; z < maxRange; z++) {
                    if (center.getBlock().getRelative(-x, -y, -z).getType().isSolid()) {
                        if (z > zMax) {
                            zMax = z;
                        }
                        break;
                    }
                }
            }
        }

        // Set the corner 2 (excluding the solid blocks)
        corner2 = corner2.add(-(xMax-1), -(yMax-1), -(zMax-1));



        // Generate the cuboid region
        ProtectedCuboidRegion region = new ProtectedCuboidRegion("radiation", corner1, corner2);


        RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        RegionManager regions = container.get(BukkitAdapter.adapt(center.getWorld()));
        regions.addRegion(region);



    }
}
