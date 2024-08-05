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

package com.mochibit.defcon.radiation

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.extensions.toChunkCoordinate
import com.mochibit.defcon.extensions.toVector3
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.save.savedata.RadiationAreaSave
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Bukkit
import org.bukkit.Location
import java.util.concurrent.CompletableFuture

object RadiationAreaFactory {
    fun fromCenter(
        center: Location,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
        maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
    ): CompletableFuture<RadiationArea> {
        return generate(center, radLevel, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
    }

    fun fromCenter(
        center: Vector3,
        worldName: String,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3 = Vector3(20000.0, 150.0, 20000.0),
        maxLowerVertexRadius: Vector3 = Vector3(-20000.0, -10.0, -20000.0)
    ): CompletableFuture<RadiationArea> {
        val world = Bukkit.getWorld(worldName)
        if (world == null) {
            info("World $worldName not found")
            return CompletableFuture.completedFuture(null)
        }
        return fromCenter(
            center.toLocation(world),
            radLevel,
            maxFloodBlocks,
            maxUpperVertexRadius,
            maxLowerVertexRadius
        )
    }

    private fun generate(
        center: Location,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int,
        maxVertexRadius: Vector3,
        minVertexRadius: Vector3
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
            //TODO: Probably needs a refactor
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
                    MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, radLevel)
                    MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, indexedRA.id)
                }

            }
            return@result radiationArea;
        }
    }

    fun expand(radiationArea: RadiationArea, maxFloodBlocks: Int = 20000): CompletableFuture<RadiationArea?> {
        return CompletableFuture.supplyAsync result@{
            val world = Bukkit.getWorld(radiationArea.worldName) ?: return@result null

            val locations = FloodFill3D.getFloodFillAsync(radiationArea.center.toLocation(world), maxFloodBlocks, true).join();
            val affectedChunkCoordinates = HashSet<Vector3>()

            for (location in locations) {
                affectedChunkCoordinates.add(location.toChunkCoordinate())
                MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, radiationArea.radiationLevel)
                MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, radiationArea.id)
            }

            radiationArea.affectedChunkCoordinates.addAll(affectedChunkCoordinates)

            return@result radiationArea;
        }
    }
}


