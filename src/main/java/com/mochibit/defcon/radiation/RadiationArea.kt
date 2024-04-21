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

class RadiationArea(val center: Vector3) {

    var cuboidVertexes: HashSet<CuboidVertex> = HashSet()

    var affectedChunkCoordinates: HashSet<Vector3> = HashSet()

    var maxUpperVertexRadius = Vector3(20000.0, 150.0, 20000.0)

    var maxLowerVertexRadius = Vector3(-20000.0, -10.0, -20000.0)

    companion object {
        @Expose(serialize = false, deserialize = false)
        private val loadedRadiationAreas = ConcurrentHashMap.newKeySet<RadiationArea>()
        fun fromCenter(center: Location, maxFloodBlocks: Int = 20000): CompletableFuture<RadiationArea> {
            val area = RadiationArea(Vector3.fromLocation(center))
            return area.generate(center, maxFloodBlocks)
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
        if (cuboidVertexes.isEmpty()) {
            return false
        }

        // Get the min coordinates and the max coordinates of the vertexes and check if the location is inside the cuboid
        val min = cuboidVertexes.first()
        val max = cuboidVertexes.last()

        if (location.x >= min.x && location.x <= max.x &&
            location.y >= min.y && location.y <= max.y &&
            location.z >= min.z && location.z <= max.z
        ) {
            return true
        }

        return false
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

    fun generate(center: Location, maxFloodBlocks: Int): CompletableFuture<RadiationArea> {
        return CompletableFuture.supplyAsync result@{
            val locations = FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).join();
            affectedChunkCoordinates = HashSet()
            info("Flood fill completed with ${locations.size} blocks")
            if (locations.size > maxFloodBlocks) {
                synchronized(this.cuboidVertexes) {
                    cuboidVertexes = HashSet(
                        listOf(
                            CuboidVertex(
                                center.x + maxUpperVertexRadius.x,
                                center.y + maxUpperVertexRadius.y,
                                center.z + maxUpperVertexRadius.z
                            ),
                            CuboidVertex(
                                center.x + maxLowerVertexRadius.x,
                                center.y + maxLowerVertexRadius.y,
                                center.z + maxLowerVertexRadius.z
                            )
                        )
                    )
                }
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


