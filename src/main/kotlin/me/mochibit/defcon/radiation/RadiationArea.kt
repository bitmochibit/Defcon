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

package me.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import me.mochibit.defcon.extensions.getRadiationAreaId
import me.mochibit.defcon.math.Vector3
import me.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Location
import java.util.concurrent.ConcurrentHashMap

data class RadiationArea(
    val center : Vector3,
    val worldName: String,
    val minVertex: Vector3? = null,
    val maxVertex: Vector3? = null,
    val affectedChunkCoordinates: HashSet<Vector3> = HashSet(),
    val radiationLevel: Double = 0.0,
    val id: Int = 0
) {
    companion object {
        @Expose(serialize = false, deserialize = false)
        val loadedRadiationAreas = ConcurrentHashMap<Int, RadiationArea>()
        fun getAtLocation(location: Location): HashSet<RadiationArea> {
            val results = HashSet<RadiationArea>()
            val currBlockRadId = location.getRadiationAreaId()
            if (currBlockRadId != null) {
                val radiationArea = loadedRadiationAreas[currBlockRadId]
                if (radiationArea != null) {
                    results.add(radiationArea)
                }
            }

            for (area in loadedRadiationAreas.values) {
                if (area.checkIfInBounds(location)) results.add(area)
            }
            return results
        }

        fun checkIfInBounds(location: Location): Boolean {
            for (area in loadedRadiationAreas.values) {
                if (area.checkIfInBounds(location))
                    return true
            }
            return false
        }

        private fun tryLoadFromLocation(location: Location): List<RadiationArea> {
            val radiationAreaSave = RadiationAreaSave.getSave(location.world).load() ?: return ArrayList()

            val loadedRadiationAreas = ArrayList<RadiationArea>()
            val radiationAreaId = location.getRadiationAreaId()


            for (radiationArea in radiationAreaSave.radiationAreas) {
                if (radiationArea.checkIfInBounds(location)) {
                    loadedRadiationAreas.add(radiationArea)
                    continue
                }

                if (radiationAreaId != null && radiationAreaId == radiationArea.id) {
                    loadedRadiationAreas.add(radiationArea)
                }
            }

            return loadedRadiationAreas
        }
    }
    fun checkIfInBounds(location: Location): Boolean {
        if (minVertex == null || maxVertex == null) {
            return false
        }

        return location.x >= minVertex.x && location.x <= maxVertex.x &&
                location.y >= minVertex.y && location.y <= maxVertex.y &&
                location.z >= minVertex.z && location.z <= maxVertex.z
    }
}
