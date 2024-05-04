package com.mochibit.defcon.listeners.world

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.manager.RadiationAreaManager
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
            val radiationAreas = RadiationAreaManager().getAll();
            for (radiationArea in radiationAreas) {
                if (radiationArea.affectedChunkCoordinates.isNotEmpty()) continue

                val minVertexLocation = Location(getServer().getWorld(radiationArea.worldName),
                    radiationArea.minVertex.x.toDouble(), radiationArea.minVertex.y.toDouble(), radiationArea.minVertex.z.toDouble()
                )
                val maxVertexLocation = Location(getServer().getWorld(radiationArea.worldName),
                    radiationArea.maxVertex.x.toDouble(), radiationArea.maxVertex.y.toDouble(), radiationArea.maxVertex.z.toDouble()
                )


                // Check if chunk is in between the min and max vertex chunk
                if (minVertexLocation.chunk.x <= event.chunk.x && event.chunk.x <= maxVertexLocation.chunk.x &&
                    minVertexLocation.chunk.z <= event.chunk.z && event.chunk.z <= maxVertexLocation.chunk.z) {
                    RadiationArea.loadRadiationArea(radiationArea)
                }
            }
        })
    }
}