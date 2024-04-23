package com.mochibit.defcon.events.world

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Bukkit
import org.bukkit.Bukkit.getServer
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent

class RadiationAreaLoad: Listener {
    @EventHandler
    fun loadRadiationArea(event : ChunkLoadEvent) {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            val radiationAreaSave = RadiationAreaSave().load()
            for (radiationArea in radiationAreaSave.saveData.radiationAreas) {
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

    @EventHandler
    fun chunkUnloadEvent(event : ChunkUnloadEvent) {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            val radiationAreaSave = RadiationAreaSave().load()
            radiationAreaSave.saveData.radiationAreas.forEach { radiationArea ->
                radiationArea.affectedChunkCoordinates.forEach { chunkCoordinate ->
                    if (chunkCoordinate.x.toInt() == event.chunk.x && chunkCoordinate.z.toInt() == event.chunk.z) {
                        RadiationArea.unloadRadiationArea(radiationArea)
                    }
                }
            }
        })
    }
}