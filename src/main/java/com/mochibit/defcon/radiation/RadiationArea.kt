package com.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import com.mochibit.defcon.extensions.getRadiationAreaId
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Location
import java.util.concurrent.ConcurrentHashMap

data class RadiationArea(
    val center : Vector3,
    val worldName: String,
    val minVertex: Vector3? = null,
    val maxVertex: Vector3? = null,
    val affectedChunkCoordinates: HashSet<Vector3> = HashSet(),
    val id: Int = 0
) {
    companion object {
        @Expose(serialize = false, deserialize = false)
        val loadedRadiationAreas = ConcurrentHashMap<Int, RadiationArea>()
        fun shouldSuffocate(location: Location): Pair<Boolean, HashSet<RadiationArea>> {
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
            return Pair(results.isNotEmpty(), results)
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
