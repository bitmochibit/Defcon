package com.mochibit.defcon.radiation

import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Location

data class RadiationArea(
    val center : Vector3,
    val worldName: String,
    val minVertex: Vector3? = null,
    val maxVertex: Vector3? = null,
    val affectedChunkCoordinates: HashSet<Vector3> = HashSet(),
    val id: Int = 0
) {
    fun checkIfInBounds(location: Location): Boolean {
        if (minVertex == null || maxVertex == null) {
            return false
        }

        return location.x >= minVertex.x && location.x <= maxVertex.x &&
                location.y >= minVertex.y && location.y <= maxVertex.y &&
                location.z >= minVertex.z && location.z <= maxVertex.z
    }
}
