package com.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.extensions.getBlockData
import com.mochibit.defcon.extensions.getRadiationAreaId
import com.mochibit.defcon.extensions.toChunkCoordinate
import com.mochibit.defcon.extensions.toVector3
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.manager.RadiationAreaManager
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

class RadiationAreaFactory () {
    //TODO: Convert this whole class to a Factory, and store the data in a dataclass
    companion object {
        @Expose(serialize = false, deserialize = false)
        private val loadedRadiationAreas = ConcurrentHashMap<Int, RadiationArea>()
        fun fromCenter(
            center: Location,
            maxFloodBlocks: Int = 20000,
            maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
            maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
        ): CompletableFuture<RadiationArea> {
            return generate(center, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
        }

        fun fromCenter(
            center: Vector3,
            worldName : String,
            maxFloodBlocks: Int = 20000,
            maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
            maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
        ): CompletableFuture<RadiationArea> {
            val world = Bukkit.getWorld(worldName)
            if (world == null) {
                info("World $worldName not found")
                return CompletableFuture.completedFuture(null)
            }
            return fromCenter(center.toLocation(world), maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
        }

        private fun generate(
            center: Location,
            maxFloodBlocks: Int,
            maxVertexRadius: Vector3,
            minVertexRadius: Vector3
        ): CompletableFuture<RadiationArea> {
            return CompletableFuture.supplyAsync result@{
                var radiationArea: RadiationArea? = null
                var minVertex: CuboidVertex? = null;
                var maxVertex: CuboidVertex? = null;
                val locations = FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).join();
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
                    radiationArea = RadiationArea(center.toVector3(), center.world.name, minVertex, maxVertex)
                } else {
                    info("${locations.size} is less than $maxFloodBlocks, storing in chunks")
                    radiationArea = RadiationArea(center.toVector3(), center.world.name)
                    for (location in locations) {
                        radiationArea.affectedChunkCoordinates.add(location.toChunkCoordinate())
                        MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, 1.0)
                        MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, radiationArea.id)
                    }
                }
                RadiationAreaManager().save(radiationArea)
                return@result radiationArea;
            }
        }

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
            val radiationAreaSave = RadiationAreaSave().load()
            // TODO: CACHE SYSTEM

            val loadedRadiationAreas = ArrayList<RadiationArea>()
            val radiationAreaId = location.getRadiationAreaId()


            for (radiationArea in radiationAreaSave.saveData.radiationAreas) {
                if (radiationArea.checkIfInBounds(location)) {
                    loadedRadiationAreas.add(radiationArea)
                    loadRadiationArea(radiationArea)
                    continue
                }

                if (radiationAreaId != null && radiationAreaId == radiationArea.id) {
                    loadRadiationArea(radiationArea)
                    loadedRadiationAreas.add(radiationArea)
                }
            }

            return loadedRadiationAreas
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

    }






}


