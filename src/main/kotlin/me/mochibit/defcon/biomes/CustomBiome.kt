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

@file:Suppress("UnstableApiUsage", "removal")

package me.mochibit.defcon.biomes

import io.papermc.paper.registry.RegistryAccess
import io.papermc.paper.registry.RegistryKey
import me.mochibit.defcon.Defcon.Logger.warn
import me.mochibit.defcon.biomes.data.*
import me.mochibit.defcon.biomes.enums.PrecipitationType
import me.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.NamespacedKey


abstract class CustomBiome {
    // Get key from annotation
    private val biomeKey: String = this::class.java.getAnnotation(BiomeInfo::class.java)?.let {
        "defcon:" + it.key
    }
        ?: "minecraft:forest"

    val key: NamespacedKey
        get() {
            return NamespacedKey.fromString(biomeKey) ?: NamespacedKey("minecraft", "forest")
        }

    val asBukkitBiome by lazy {
        try {
            RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(key)
        } catch (e: Exception) {
            warn("Biome $biomeKey not found in registry. Defaulting to minecraft:forest (probably you didn't restart the server for datapack installation)")
            RegistryAccess.registryAccess().getRegistry(RegistryKey.BIOME).getOrThrow(NamespacedKey.fromString("minecraft:forest")!!)
        }
    }

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
    val monsterSpawners: HashSet<BiomeSpawner> = HashSet()
    val creatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val ambientSpawners: HashSet<BiomeSpawner> = HashSet()
    val axolotlSpawners: HashSet<BiomeSpawner> = HashSet()
    val undergroundWaterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val waterCreatureSpawners: HashSet<BiomeSpawner> = HashSet()
    val waterAmbientSpawners: HashSet<BiomeSpawner> = HashSet()
    val miscSpawners: HashSet<BiomeSpawner> = HashSet()

    // Spawn costs (this is not an array but an object)
    val spawnCosts: HashSet<BiomeSpawnCost> = HashSet()

    // Features
    val features: HashSet<BiomeFeature> = HashSet()

    // Carvers
    val carvers: HashSet<BiomeCarver> = HashSet()


}