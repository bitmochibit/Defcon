package com.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class RadiationArea(val center: Vector3, val worldName: String) {


    var minVertex: CuboidVertex = CuboidVertex(0, 0, 0)
    var maxVertex: CuboidVertex = CuboidVertex(0, 0, 0)

    var affectedChunkCoordinates: HashSet<Vector3> = HashSet()

    companion object {
        @Expose(serialize = false, deserialize = false)
        private val loadedRadiationAreas = ConcurrentHashMap.newKeySet<RadiationArea>()
        fun fromCenter(
            center: Location,
            maxFloodBlocks: Int = 20000,
            maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
            maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
        ): CompletableFuture<RadiationArea> {
            val area = RadiationArea(Vector3.fromLocation(center), center.world.name)
            return area.generate(center, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
        }

        fun getAreaAt(location: Location): RadiationArea? {
            val chunkCoordinates = toChunkCoordinate(location)
            for (area in loadedRadiationAreas) {
                if (!area.affectedChunkCoordinates.contains(chunkCoordinates)) continue
                if (!area.checkIfInBounds(location)) continue
                if (area.blockRadiationLevel(location).equals(0.0)) continue

                return area
            }
            return null
        }

        private fun toChunkCoordinate(location: Location): Vector3 {
            return Vector3(location.chunk.x.toDouble(), .0, location.chunk.z.toDouble())
        }

        fun checkIfInBounds(location: Location): Boolean {
            for (area in loadedRadiationAreas) {
                if (area.checkIfInBounds(location))
                    return true
            }
            return false
        }

        fun loadRadiationArea(radiationArea: RadiationArea) {
            loadedRadiationAreas.add(radiationArea)
        }

        fun unloadRadiationArea(radiationArea: RadiationArea) {
            loadedRadiationAreas.remove(radiationArea)
        }
    }

    fun checkIfInBounds(location: Location): Boolean {
        if (worldName == "") return false
        if (location.world.name != worldName) return false

        return location.x >= minVertex.x && location.x <= maxVertex.x &&
                location.y >= minVertex.y && location.y <= maxVertex.y &&
                location.z >= minVertex.z && location.z <= maxVertex.z
    }

    fun blockRadiationLevel(location: Location): Double {
        val blockData = MetaManager.getBlockData<Double>(location, BlockDataKey.RadiationLevel)
        return blockData ?: 0.0
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
                    affectedChunkCoordinates.add(toChunkCoordinate(location))
                }
            }

            val radiationAreaSave = RadiationAreaSave()
            radiationAreaSave.saveData.radiationAreas.add(this)
            radiationAreaSave.save()

            return@result this;
        }
    }
}


