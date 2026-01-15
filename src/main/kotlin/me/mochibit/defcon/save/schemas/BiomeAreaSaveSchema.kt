/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

import kotlinx.serialization.Serializable
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.save.serializers.NamespacedKeySerializer
import org.bukkit.NamespacedKey
import kotlin.time.Duration
import kotlin.time.ExperimentalTime

@Serializable
data class BiomeAreaSaveSchema(
    val biomeAreas: HashSet<BoundarySaveSchema> = HashSet(),
) : SaveSchema {
    @Serializable
    data class BoundarySaveSchema(
        val id: Int = 0,
        val uuid: String = "",
        @Serializable(with = NamespacedKeySerializer::class)
        val biome: NamespacedKey,
        val worldName: String = "",
        val minX: Int,
        val maxX: Int,
        val minY: Int,
        val maxY: Int,
        val minZ: Int,
        val maxZ: Int,
        val priority: Int = 0,
        val transition: List<BiomeTransitionSaveSchema> = emptyList(),
    )

    @Serializable
    data class BiomeTransitionSaveSchema(
        val transitionDuration: String,
        @Serializable(with = NamespacedKeySerializer::class)
        val targetBiome: NamespacedKey,
        val transitionTime: Long,
        val completed: Boolean = false,
        val targetPriority: Int = 0,
    )

    override fun getMaxID(): Int = biomeAreas.maxOfOrNull { it.id } ?: 0

    override fun getSize(): Int = biomeAreas.size

    override fun getAllItems(): List<Any> = biomeAreas.toList()
}

@OptIn(ExperimentalTime::class)
fun CustomBiomeHandler.CustomBiomeBoundary.toSchema(): BiomeAreaSaveSchema.BoundarySaveSchema =
    BiomeAreaSaveSchema.BoundarySaveSchema(
        id = id,
        uuid = uuid.toString(),
        biome = biome,
        worldName = worldName,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        minZ = minZ,
        maxZ = maxZ,
        priority = priority,
        transition =
            transitions.map {
                BiomeAreaSaveSchema.BiomeTransitionSaveSchema(
                    transitionDuration = it.transitionDuration.toString(),
                    targetBiome = it.targetBiome,
                    transitionTime = it.transitionTime.toEpochMilliseconds(),
                    completed = it.completed,
                    targetPriority = it.targetPriority,
                )
            },
    )

@OptIn(ExperimentalTime::class)
fun BiomeAreaSaveSchema.BoundarySaveSchema.toCustomBiomeBoundary(): CustomBiomeHandler.CustomBiomeBoundary =
    CustomBiomeHandler.CustomBiomeBoundary(
        id = id,
        uuid = java.util.UUID.fromString(uuid),
        biome = biome,
        worldName = worldName,
        minX = minX,
        maxX = maxX,
        minY = minY,
        maxY = maxY,
        minZ = minZ,
        maxZ = maxZ,
        priority = priority,
        transitions =
            transition.map {
                CustomBiomeHandler.CustomBiomeBoundary.BiomeTransition(
                    transitionDuration = Duration.parse(it.transitionDuration),
                    targetBiome = it.targetBiome,
                    targetPriority = it.targetPriority,
                    transitionTime = kotlin.time.Instant.fromEpochMilliseconds(it.transitionTime),
                    completed = it.completed,
                )
            },
    )
