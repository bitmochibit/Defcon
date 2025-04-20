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

import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.extensions.toChunkCoordinate
import me.mochibit.defcon.save.savedata.RadiationAreaSave
import me.mochibit.defcon.utils.FloodFill3D
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3i
import java.util.concurrent.CompletableFuture

object RadiationAreaFactory {
    fun fromCenter(
        center: Vector3i,
        world: World,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3i = Vector3i(20000, 150, 20000),
        maxLowerVertexRadius: Vector3i = Vector3i(-20000, -10, -20000)
    ): CompletableFuture<RadiationArea> {
        return generate(center, world, radLevel, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
    }

    private fun generate(
        center: Vector3i,
        world: World,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int,
        maxVertexRadius: Vector3i,
        minVertexRadius: Vector3i
    ): CompletableFuture<RadiationArea> {
        return CompletableFuture.supplyAsync result@{
            var radiationArea: RadiationArea? = null
            var minVertex: Vector3i? = null;
            var maxVertex: Vector3i? = null;

            val centerLocation = Location(world, center.x.toDouble(), center.y.toDouble(), center.z.toDouble())

            val locations = FloodFill3D.getFloodFillAsync(centerLocation, maxFloodBlocks + 1, true).join();
            val affectedChunkCoordinates = HashSet<Vector3i>()

            if (locations.size > maxFloodBlocks) {
                minVertex = Vector3i(
                    (center.x + minVertexRadius.x),
                    (center.y + minVertexRadius.y),
                    (center.z + minVertexRadius.z)
                )
                maxVertex = Vector3i(
                    (center.x + maxVertexRadius.x),
                    (center.y + maxVertexRadius.y),
                    (center.z + maxVertexRadius.z)
                )

                affectedChunkCoordinates.add(minVertex.toChunkCoordinate())
                affectedChunkCoordinates.add(maxVertex.toChunkCoordinate())
            } else {
                for (location in locations) {
                    affectedChunkCoordinates.add(location.toChunkCoordinate())
                }
            }
            //TODO: Probably needs a refactor
            radiationArea = RadiationArea(
                center = center,
                world = world,
                minVertex = minVertex,
                maxVertex = maxVertex,
                affectedChunkCoordinates = affectedChunkCoordinates,
                radiationLevel = radLevel
            )
            val indexedRA = RadiationAreaSave.getSave(world).addRadiationArea(radiationArea)

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
            val world = radiationArea.world

            val centerLoc = Location(world, radiationArea.center.x.toDouble(), radiationArea.center.y.toDouble(), radiationArea.center.z.toDouble())

            val locations = FloodFill3D.getFloodFillAsync(centerLoc, maxFloodBlocks, true).join();
            val affectedChunkCoordinates = HashSet<Vector3i>()

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


