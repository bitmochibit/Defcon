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

package com.mochibit.defcon.biomes

import com.mochibit.defcon.biomes.data.*
import com.mochibit.defcon.biomes.enums.PrecipitationType
import com.mochibit.defcon.biomes.enums.TemperatureModifier
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.NamespacedKey


open class CustomBiome {
    // Get key from annotation
    val biomeKey: NamespacedKey =
        MetaManager.convertStringToNamespacedKey(this::class.java.getAnnotation(BiomeInfo::class.java)?.key ?: "minecraft:forest")

    // Default values from FOREST biome
    var temperature = 0.7f
    var downfall = 0.8f
    var precipitation = PrecipitationType.RAIN
    var temperatureModifier = TemperatureModifier.NONE
    var hasPrecipitation = true

    var effects: BiomeEffects = BiomeEffects(
        skyColor = 7972607,
        fogColor = 12638463,
        waterColor = 4159204,
        waterFogColor = 329011,
        moodSound = BiomeMoodSound(
            sound = "minecraft:ambient.cave",
            tickDelay = 6000,
            blockSearchExtent = 8,
            offset = 2.0f
        ),
    )

    // Spawners
    var monsterSpawners: HashSet<BiomeSpawner> = HashSet()
    var creatureSpawners: HashSet<BiomeSpawner> = HashSet()
    var ambientSpawners: HashSet<BiomeSpawner> = HashSet()
    var axolotlSpawners: HashSet<BiomeSpawner> = HashSet()
    var undergroundWaterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    var waterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    var waterAmbientSpawners: HashSet<BiomeSpawner> = HashSet()
    var miscSpawners: HashSet<BiomeSpawner> = HashSet()

    // Spawn costs (this is not an array but an object)
    var spawnCosts: HashSet<BiomeSpawnCost> = HashSet()

    // Features
    var features: HashSet<BiomeFeature> = HashSet()

    // Carvers
    var carvers: HashSet<BiomeCarver> = HashSet()

    open fun build() : CustomBiome {
        return this;
    }
}