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
import me.mochibit.defcon.extensions.getRadiationAreaId
import me.mochibit.defcon.save.savedata.RadiationAreaSave
import org.bukkit.Location
import org.bukkit.World
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap

/**
 * Represents an area affected by radiation in the world.
 *
 * @property center The center point of the radiation area
 * @property world The world in which this radiation area exists
 * @property minVertex The minimum vertex of the bounding box (nullable)
 * @property maxVertex The maximum vertex of the bounding box (nullable)
 * @property affectedChunkCoordinates Set of chunk coordinates affected by this radiation
 * @property radiationLevel The level of radiation in this area
 * @property id Unique identifier for this radiation area
 */
data class RadiationArea(
    val center: Vector3i,
    val world: World,
    val minVertex: Vector3i? = null,
    val maxVertex: Vector3i? = null,
    val affectedChunkCoordinates: MutableSet<Vector3i> = HashSet(),
    val radiationLevel: Double = 0.0,
    val id: Int = 0
) {
    /**
     * Checks if a location is within the bounds of this radiation area.
     *
     * @param location The location to check
     * @return true if the location is within bounds, false otherwise
     */
    fun checkIfInBounds(location: Location): Boolean {
        // If this radiation area doesn't have boundaries defined, it can't contain the location
        if (minVertex == null || maxVertex == null) {
            return false
        }

        // Check if the location is within the bounding box
        return location.x >= minVertex.x && location.x <= maxVertex.x &&
               location.y >= minVertex.y && location.y <= maxVertex.y &&
               location.z >= minVertex.z && location.z <= maxVertex.z
    }

    companion object {
        /**
         * Map of loaded radiation areas by their ID.
         */
        @Transient
        private val loadedRadiationAreas = ConcurrentHashMap<Int, RadiationArea>()

        /**
         * Get all radiation areas at a specific location.
         *
         * @param location The location to check
         * @return Set of radiation areas at the given location
         */
        suspend fun getAtLocation(location: Location): Set<RadiationArea> = withContext(Dispatchers.IO) {
            val results = HashSet<RadiationArea>()

            // First check if this block has a specific radiation area ID assigned
            location.getRadiationAreaId()?.let { areaId ->
                loadedRadiationAreas[areaId]?.let { area ->
                    results.add(area)
                }
            }

            // Then check if the location is within any radiation area's bounds
            loadedRadiationAreas.values
                .filter { it.checkIfInBounds(location) }
                .forEach { results.add(it) }

            // If no radiation areas were found, try to load them from storage
            if (results.isEmpty()) {
                val loadedAreas = tryLoadFromLocation(location)

                // Add the newly loaded areas to our cache and results
                loadedAreas.forEach { area ->
                    loadedRadiationAreas[area.id] = area
                    results.add(area)
                }
            }

            results
        }

         /**
         * Check if a location is within any radiation area.
         *
         * @param location The location to check
         * @return true if the location is within any radiation area, false otherwise
         */
        fun checkIfInBounds(location: Location): Boolean {
            return loadedRadiationAreas.values.any { it.checkIfInBounds(location) }
        }

        /**
         * Attempts to load radiation areas that might contain this location.
         *
         * @param location The location to check
         * @return List of radiation areas found for this location
         */
        private suspend fun tryLoadFromLocation(location: Location): List<RadiationArea> {
            // Try to load the radiation area save for this world
            val radiationAreaSave = RadiationAreaSave.getSave(location.world.name)

            val loadedAreas = ArrayList<RadiationArea>()
            val radiationAreaId = location.getRadiationAreaId()

            // Check all radiation areas in the save
            for (radiationArea in radiationAreaSave.getAll()) {
                // If the area contains this location, add it
                if (radiationArea.checkIfInBounds(location)) {
                    loadedAreas.add(radiationArea)
                    continue
                }

                // If this location has a radiation area ID that matches, add it
                if (radiationAreaId != null && radiationAreaId == radiationArea.id) {
                    loadedAreas.add(radiationArea)
                }
            }

            return loadedAreas
        }

        /**
         * Adds a radiation area to the cache.
         *
         * @param radiationArea The radiation area to add
         * @return The added radiation area
         */
        fun addToCache(radiationArea: RadiationArea): RadiationArea {
            loadedRadiationAreas[radiationArea.id] = radiationArea
            return radiationArea
        }

        /**
         * Removes a radiation area from the cache.
         *
         * @param id The ID of the radiation area to remove
         * @return The removed radiation area, or null if it wasn't in the cache
         */
        fun removeFromCache(id: Int): RadiationArea? {
            return loadedRadiationAreas.remove(id)
        }

        /**
         * Gets all currently loaded radiation areas.
         *
         * @return Collection of all loaded radiation areas
         */
        fun getAllLoaded(): Collection<RadiationArea> {
            return loadedRadiationAreas.values
        }

        /**
         * Clears all radiation areas from the cache.
         */
        fun clearCache() {
            loadedRadiationAreas.clear()
        }
    }
}