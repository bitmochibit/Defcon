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

package me.mochibit.defcon.save.schemas

import me.mochibit.defcon.radiation.RadiationArea
import org.bukkit.Bukkit
import org.joml.Vector3i


data class RadiationSaveSchema(
    var radiationAreas: HashSet<AreaSchema> = HashSet()
) : SaveSchema {
    override fun getMaxID(): Int =
        radiationAreas.maxOfOrNull { it.id } ?: 0


    override fun getSize(): Int =
        radiationAreas.size


    override fun getAllItems(): List<Any> =
        radiationAreas.toList()


    data class AreaSchema(
        val id : Int = 0,
        val center: Triple<Int, Int, Int> = Triple(0, 0, 0),
        val minVertex : Triple<Int, Int, Int>? = null,
        val maxVertex : Triple<Int, Int, Int>? = null,
        val affectedChunkCoordinates: List<Triple<Int, Int, Int>> = emptyList(),
        val radiationLevel: Double = 0.0,
        val worldName: String = "",
    )
}

fun RadiationArea.toSchema(): RadiationSaveSchema.AreaSchema {
    return RadiationSaveSchema.AreaSchema(
        id = id,
        center = Triple(center.x, center.y, center.z),
        minVertex = minVertex?.let { Triple(it.x, it.y, it.z) },
        maxVertex = maxVertex?.let { Triple(it.x, it.y, it.z) },
        affectedChunkCoordinates = affectedChunkCoordinates.map { Triple(it.x, it.y, it.z) },
        radiationLevel = radiationLevel,
        worldName = world.name
    )
}

fun RadiationSaveSchema.AreaSchema.toRadiationArea(): RadiationArea {
    return RadiationArea(
        id = id,
        center = Vector3i(center.first, center.second, center.third),
        minVertex = minVertex?.let { Vector3i(it.first, it.second, it.third) },
        maxVertex = maxVertex?.let { Vector3i(it.first, it.second, it.third) },
        affectedChunkCoordinates = affectedChunkCoordinates.map {
            Vector3i(it.first, it.second, it.third)
        }.toMutableSet(),
        radiationLevel = radiationLevel,
        world = Bukkit.getWorld(worldName) ?: throw IllegalArgumentException("World $worldName not found")
    )
}

