package com.mochibit.defcon.events.world

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.radiation.RadiationArea
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.world.ChunkLoadEvent
import org.bukkit.event.world.ChunkUnloadEvent

class RadiationAreaLoad: Listener {
    @EventHandler
    fun chunkLoadEvent(event : ChunkLoadEvent) {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            val radiationAreaSave = RadiationAreaSave().load()
            radiationAreaSave.saveData.radiationAreas.forEach { radiationArea ->
                radiationArea.affectedChunkCoordinates.forEach { chunkCoordinate ->
                    if (chunkCoordinate.x.toInt() == event.chunk.x && chunkCoordinate.z.toInt() == event.chunk.z) {
                        RadiationArea.loadRadiationArea(radiationArea)
                    }
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