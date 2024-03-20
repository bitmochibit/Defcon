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