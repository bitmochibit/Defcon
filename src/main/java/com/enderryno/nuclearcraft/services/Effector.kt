package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.utils.FloodFiller
import com.enderryno.nuclearcraft.utils.Geometry
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Location

object Effector {
    /**
     *
     *  Generate a polygonal region from a Flood-Fill from a center, to a maxRange
     *
     *  **Warning** this can be intensive on hardware if not handled correctly
     * @param center The center of the radiation field
     * @param maxRange The maximum range of the radiation field
     */
    fun generateRadiationField(center: Location, maxRange: Int) {
        var blockLocations: List<Location>? = FloodFiller.getFloodFill(center, maxRange)

        if (blockLocations.isNullOrEmpty()) {
            NuclearCraft.instance!!.getLogger().info("No blocks found in range")
            return
        }

        // Get the max Y and min Y from the blockLocations
        var maxY = blockLocations[0].blockY
        var minY = blockLocations[0].blockY
        for (loc in blockLocations) {
            if (loc.blockY > maxY) {
                maxY = loc.blockY
            }
            if (loc.blockY < minY) {
                minY = loc.blockY
            }
        }

        // Get convex hull of the blockLocations
        blockLocations = Geometry.getConvexHullXZ(blockLocations)
        val points: MutableList<BlockVector2> = ArrayList()
        for (loc in blockLocations) {
            points.add(BlockVector2.at(loc.blockX, loc.blockZ))
        }
        val region: ProtectedRegion = ProtectedPolygonalRegion("radiation", points, minY, maxY)
        val container = WorldGuard.getInstance().platform.regionContainer
        val regions = container[BukkitAdapter.adapt(center.world)]
        regions!!.addRegion(region)
    }
}
