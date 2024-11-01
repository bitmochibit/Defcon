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

import me.mochibit.defcon.biomes.enums.GrassColorModifier

data class BiomeEffects(
    var skyColor: Int = 7972607,
    var fogColor: Int = 12638463,
    var waterColor: Int = 4159204,
    var waterFogColor: Int = 329011,

    var grassColor: Int? = null,
    var foliageColor: Int? = null,
    var grassColorModifier: GrassColorModifier = GrassColorModifier.UNSET,

    var ambientSound: String? = null,

    // Effects
    var moodSound: BiomeMoodSound? = null,
    var additionalSound : BiomeAdditionalSound? = null,
    var particle: BiomeParticle? = null,
    var music: BiomeMusic? = null,
)
