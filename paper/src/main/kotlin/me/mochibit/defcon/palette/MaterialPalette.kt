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

package me.mochibit.defcon.palette

import org.bukkit.Material
import org.joml.SimplexNoise

data class MaterialPalette(
    val materials: Set<MaterialPaletteEntry>,
    val noiseScale: Float = 0.1f,
) {
    fun getRandom(): Material {
        val totalWeight = materials.sumOf { it.weight }
        val randomValue = (0 until totalWeight).random()

        var cumulativeWeight = 0
        for (entry in materials) {
            cumulativeWeight += entry.weight
            if (randomValue < cumulativeWeight) {
                return entry.material
            }
        }
        return materials.first().material
    }

    fun getWithNoise(x: Int, z: Int, y: Int = 0): Material {
        val noiseValue = SimplexNoise.noise(x * noiseScale, y * noiseScale, z * noiseScale)
        val normalizedNoise = noiseValue+ 1 / 2f

        val totalWeight = materials.sumOf { it.weight }
        val targetWeight = (normalizedNoise * totalWeight).toInt()

        var cumulativeWeight = 0
        for (entry in materials) {
            cumulativeWeight += entry.weight
            if (targetWeight < cumulativeWeight) {
                return entry.material
            }
        }

        return materials.first().material
    }
}

data class MaterialPaletteEntry(
    val material: Material,
    val weight: Int = 1,
)
