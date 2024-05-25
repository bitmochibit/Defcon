package com.mochibit.defcon.radiation

import com.google.gson.annotations.Expose
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.extensions.getRadiationAreaId
import com.mochibit.defcon.extensions.toChunkCoordinate
import com.mochibit.defcon.extensions.toVector3
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import it.unimi.dsi.fastutil.Hash
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.Vector
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

object RadiationAreaFactory {
    fun fromCenter(
        center: Location,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
        maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0),
        radLevel : Double = 1.0
    ): CompletableFuture<RadiationArea> {
        return generate(center, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius, radLevel)
    }

    fun fromCenter(
        center: Vector3,
        worldName: String,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
        maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0),
        radLevel : Double = 1.0
    ): CompletableFuture<RadiationArea> {
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            info("World $worldName not found")
            return CompletableFuture.completedFuture(null)
        }
        return fromCenter(center.toLocation(world), maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius, radLevel)
    }

    private fun generate(
        center: Location,
        maxFloodBlocks: Int,
        maxVertexRadius: Vector3,
        minVertexRadius: Vector3,
        radLevel : Double = 1.0
    ): CompletableFuture<RadiationArea> {
        return CompletableFuture.supplyAsync result@{
            var radiationArea: RadiationArea? = null
            var minVertex: Vector3? = null;
            var maxVertex: Vector3? = null;

            val locations = FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).join();
            val affectedChunkCoordinates = HashSet<Vector3>()

            if (locations.size > maxFloodBlocks) {
                minVertex = Vector3(
                    (center.x + minVertexRadius.x),
                    (center.y + minVertexRadius.y),
                    (center.z + minVertexRadius.z)
                )
                maxVertex = Vector3(
                    (center.x + maxVertexRadius.x),
                    (center.y + maxVertexRadius.y),
                    (center.z + maxVertexRadius.z)
                )

                affectedChunkCoordinates.add(minVertex.toLocation(center.world).toChunkCoordinate())
                affectedChunkCoordinates.add(maxVertex.toLocation(center.world).toChunkCoordinate())
            } else {
                for (location in locations) {
                    affectedChunkCoordinates.add(location.toChunkCoordinate())
                }
            }
            radiationArea = RadiationArea(
                center = center.toVector3(),
                worldName = center.world.name,
                minVertex = minVertex,
                maxVertex = maxVertex,
                affectedChunkCoordinates = affectedChunkCoordinates,
                radiationLevel = radLevel
            )
            val indexedRA = RadiationAreaSave.getSave(center.world).addRadiationArea(radiationArea)

            if (locations.size < maxFloodBlocks) {
                for (location in locations) {
                    MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, indexedRA.radiationLevel)
                    MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, indexedRA.id)
                }

            }
            return@result radiationArea;
        }
    }
}


