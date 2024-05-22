package com.mochibit.defcon.listeners.world

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getServer
import org.bukkit.Location
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent

class RadiationAreaLoad: Listener {
    @EventHandler
    fun loadRadiationArea(event : ChunkLoadEvent) {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            val correctSave = RadiationAreaSave.getSave(event.chunk.world)
            val radiationAreas = correctSave.getAll();
            for (radiationArea in radiationAreas) {
                if (radiationArea.affectedChunkCoordinates.isEmpty()) continue
                if (radiationArea.minVertex == null || radiationArea.maxVertex == null) continue

                val minVertexLocation = Location(getServer().getWorld(radiationArea.worldName),
                    radiationArea.minVertex.x, radiationArea.minVertex.y, radiationArea.minVertex.z
                )
                val maxVertexLocation = Location(getServer().getWorld(radiationArea.worldName),
                    radiationArea.maxVertex.x, radiationArea.maxVertex.y, radiationArea.maxVertex.z
                )


                // Check if chunk is in between the min and max vertex chunk
                if (minVertexLocation.chunk.x <= event.chunk.x && event.chunk.x <= maxVertexLocation.chunk.x &&
                    minVertexLocation.chunk.z <= event.chunk.z && event.chunk.z <= maxVertexLocation.chunk.z) {
                    RadiationArea.loadedRadiationAreas[radiationArea.id] = radiationArea
                }
            }
        })
    }
}