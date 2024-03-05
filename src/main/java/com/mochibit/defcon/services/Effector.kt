package com.mochibit.defcon.services

import com.mochibit.defcon.utils.FloodFiller
import com.sk89q.worldedit.bukkit.BukkitAdapter
import com.sk89q.worldedit.math.BlockVector2
import com.sk89q.worldguard.WorldGuard
import com.sk89q.worldguard.protection.regions.ProtectedPolygonalRegion
import com.sk89q.worldguard.protection.regions.ProtectedRegion
import org.bukkit.Location
import com.mochibit.defcon.Defcon.Companion.Logger

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
        var blockLocations: List<Location>? = FloodFiller.getFloodFill(center, maxRange, true)

        if (blockLocations.isNullOrEmpty()) {
            Logger.warning("No blocks found in radiation field")
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
        blockLocations = com.mochibit.defcon.utils.Geometry.getConvexHullXZ(blockLocations)
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
