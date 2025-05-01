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

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.extensions.toChunkCoordinate
import me.mochibit.defcon.save.savedata.RadiationAreaSave
import me.mochibit.defcon.utils.FloodFill3D
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3i

/**
 * Factory for creating and manipulating radiation areas using coroutines.
 */
object RadiationAreaFactory {
    /**
     * Creates a radiation area from a center point.
     *
     * @param center The center point of the radiation area
     * @param world The world in which the radiation area exists
     * @param radLevel The radiation level of the area
     * @param maxFloodBlocks The maximum number of blocks to flood fill
     * @param maxUpperVertexRadius The maximum upper vertex radius
     * @param maxLowerVertexRadius The maximum lower vertex radius
     * @return The created radiation area
     */
    suspend fun fromCenter(
        center: Vector3i,
        world: World,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int = 20000,
        maxUpperVertexRadius: Vector3i = Vector3i(20000, 150, 20000),
        maxLowerVertexRadius: Vector3i = Vector3i(-20000, -10, -20000)
    ): RadiationArea = withContext(Dispatchers.IO) {
        generate(center, world, radLevel, maxFloodBlocks, maxUpperVertexRadius, maxLowerVertexRadius)
    }

    /**
     * Generates a radiation area using the provided parameters.
     */
    private suspend fun generate(
        center: Vector3i,
        world: World,
        radLevel: Double = 1.0,
        maxFloodBlocks: Int,
        maxVertexRadius: Vector3i,
        minVertexRadius: Vector3i
    ): RadiationArea = withContext(Dispatchers.IO) {
        val centerLocation = Location(world, center.x.toDouble(), center.y.toDouble(), center.z.toDouble())
        val locations = FloodFill3D.getFloodFillAsync(centerLocation, maxFloodBlocks + 1, true)
        val affectedChunkCoordinates = HashSet<Vector3i>()

        // Determine vertices based on flood fill size
        val (minVertex, maxVertex) = if (locations.size > maxFloodBlocks) {
            // Use bounding box if the area is too large
            val min = Vector3i(
                center.x + minVertexRadius.x,
                center.y + minVertexRadius.y,
                center.z + minVertexRadius.z
            )
            val max = Vector3i(
                center.x + maxVertexRadius.x,
                center.y + maxVertexRadius.y,
                center.z + maxVertexRadius.z
            )

            affectedChunkCoordinates.add(min.toChunkCoordinate())
            affectedChunkCoordinates.add(max.toChunkCoordinate())

            Pair(min, max)
        } else {
            // Use actual flood fill
            locations.forEach { location ->
                affectedChunkCoordinates.add(location.toChunkCoordinate())
            }
            Pair(null, null)
        }

        // Create and save radiation area
        val radiationArea = RadiationArea(
            center = center,
            world = world,
            minVertex = minVertex,
            maxVertex = maxVertex,
            affectedChunkCoordinates = affectedChunkCoordinates,
            radiationLevel = radLevel
        )

        val indexedRA = RadiationAreaSave.getSave(world.name).addRadiationArea(radiationArea)

        // Apply metadata to blocks if area size is manageable
        if (locations.size < maxFloodBlocks) {
            locations.forEach { location ->
                MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, radLevel)
                MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, indexedRA.id)
            }
        }

        radiationArea
    }

    /**
     * Expands an existing radiation area.
     *
     * @param radiationArea The radiation area to expand
     * @param maxFloodBlocks The maximum number of blocks to flood fill
     * @return The expanded radiation area, or null if expansion failed
     */
    suspend fun expand(radiationArea: RadiationArea, maxFloodBlocks: Int = 20000): RadiationArea =
        withContext(Dispatchers.IO) {
            val world = radiationArea.world
            val centerLoc = Location(
                world,
                radiationArea.center.x.toDouble(),
                radiationArea.center.y.toDouble(),
                radiationArea.center.z.toDouble()
            )

            val locations = FloodFill3D.getFloodFillAsync(centerLoc, maxFloodBlocks, true)

            // Process all new locations
            locations.forEach { location ->
                val chunkCoord = location.toChunkCoordinate()
                radiationArea.affectedChunkCoordinates.add(chunkCoord)
                MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, radiationArea.radiationLevel)
                MetaManager.setBlockData(location, BlockDataKey.RadiationAreaId, radiationArea.id)
            }

            radiationArea
        }
}