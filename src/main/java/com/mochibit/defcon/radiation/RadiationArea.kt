package com.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.extensions.getBlockData
import com.mochibit.defcon.extensions.toChunkCoordinate
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.manager.RadiationAreaManager
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import java.util.UUID
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class RadiationArea (val center: Vector3, val worldName: String) {
    //TODO: Convert this whole class to a Factory, and store the data in a dataclass
    var id: Int = -1
        set(value) {
            if (field > -1) return
            field = value
        }
        get() {
            if (field == -1) {
                throw IllegalStateException("Radiation area id is not set")
            }
            return field
        }

    var minVertex: CuboidVertex = CuboidVertex(0, 0, 0)
    var maxVertex: CuboidVertex = CuboidVertex(0, 0, 0)

    var affectedChunkCoordinates: HashSet<Vector3> = HashSet()

    companion object {
        @Expose(serialize = false, deserialize = false)
        private val loadedRadiationAreas = ConcurrentHashMap<Int, RadiationArea>()
        fun fromCenter(
            center: Location,
            maxFloodBlocks: Int = 20000,
            maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
            maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
        ): CompletableFuture<RadiationArea> {
            val area = RadiationArea(Vector3.fromLocation(center), center.world.name)
            return area.generate(center, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
        }

        fun getAreaAt(location: Location): List<RadiationArea> {
            val result = HashSet<RadiationArea>()
            val chunkCoordinates = location.toChunkCoordinate()
            tryLoadFromLocation(location);

            val blockRadiationAreaId = blockRadiationAreaId(location)
            if (blockRadiationAreaId != null) {
                val radiationArea = loadedRadiationAreas[blockRadiationAreaId]
                if (radiationArea != null) {
                    result.add(radiationArea)
                }
            }

            for (area in loadedRadiationAreas.values) {
                if (area.checkIfInBounds(location)) {
                    result.add(area)
                }
            }

            return result.toList();
        }
        fun checkIfInBounds(location: Location): Boolean {
            for (area in loadedRadiationAreas.values) {
                if (area.checkIfInBounds(location))
                    return true
            }
            return false
        }
        private fun tryLoadFromLocation(location: Location) {
            val radiationAreaSave = RadiationAreaSave().load()
            for (radiationArea in radiationAreaSave.saveData.radiationAreas) {
                if (radiationArea.checkIfInBounds(location)) {
                    loadRadiationArea(radiationArea)
                    continue
                }

                if (blockRadiationAreaId(location) == radiationArea.id) {
                    loadRadiationArea(radiationArea)
                }
            }
        }

        fun loadRadiationArea(radiationArea: RadiationArea) {
            loadedRadiationAreas[radiationArea.id] = radiationArea
        }

        fun unloadRadiationArea(radiationArea: RadiationArea) {
            loadedRadiationAreas.remove(radiationArea.id)
        }

        fun blockRadiationLevel(location: Location): Double {
            return location.getBlockData<Double>(BlockDataKey.RadiationLevel) ?: 0.0
        }

        fun blockRadiationAreaId(location: Location): Int? {
            return location.getBlockData<Int>(BlockDataKey.RadiationAreaId)
        }

    }

    fun checkIfInBounds(location: Location): Boolean {
        if (worldName == "") return false
        if (location.world.name != worldName) return false

        return location.x >= minVertex.x && location.x <= maxVertex.x &&
                location.y >= minVertex.y && location.y <= maxVertex.y &&
                location.z >= minVertex.z && location.z <= maxVertex.z
    }


    /**
     * Flood fills the radiation area.
     * If the area is bigger than a threshold, it will be a simple cuboid.
     * The area will be stored inside the chunkData block per block, or the vertexes if it's a cuboid.
     * For caching purposes, the area will be stored in a HashSet and removed when the chunk containing the area is unloaded.
     * For keeping track of the areas, they will be stored in a file, containing the affected chunks.
     */

    fun generate(
        center: Location,
        maxFloodBlocks: Int,
        maxVertexRadius: Vector3,
        minVertexRadius: Vector3
    ): CompletableFuture<RadiationArea> {
        return CompletableFuture.supplyAsync result@{

            val locations = FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).join();
            affectedChunkCoordinates = HashSet()
            info("Flood fill completed with ${locations.size} blocks")
            if (locations.size > maxFloodBlocks) {
                minVertex = CuboidVertex(
                    (center.x + minVertexRadius.x).toInt(),
                    (center.y + minVertexRadius.y).toInt(),
                    (center.z + minVertexRadius.z).toInt()
                )
                maxVertex = CuboidVertex(
                    (center.x + maxVertexRadius.x).toInt(),
                    (center.y + maxVertexRadius.y).toInt(),
                    (center.z + maxVertexRadius.z).toInt()
                )
            } else {
                info("${locations.size} is less than $maxFloodBlocks, storing in chunks")
                for (location in locations) {
                    MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, 1.0 + blockRadiationLevel(location))
                    MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, id)
                    affectedChunkCoordinates.add(location.toChunkCoordinate())
                }
            }

            RadiationAreaManager().save(this)

            return@result this;
        }
    }
}


