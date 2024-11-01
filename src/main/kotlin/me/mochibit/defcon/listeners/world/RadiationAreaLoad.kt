/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.listeners.world

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.Defcon.Companion.Logger.info
import me.mochibit.defcon.math.Vector3
import me.mochibit.defcon.radiation.RadiationArea
import me.mochibit.defcon.save.savedata.RadiationAreaSave
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