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

package me.mochibit.defcon.biomes.data

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack

data class BiomeParticle (
    var particle: Particle = Particle.BLOCK_CRUMBLE,
    var probability: Float = 0.5f,

    // Per particle data
    // Dust (color, size), Mu
    var color: Int? = null,
    var size: Float? = null,
    // Block / Item (material, data)
    var material: Material? = null,
    // Transition color
    var fromColor: Int? = null,
    var toColor: Int? = null
)
