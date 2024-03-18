package com.mochibit.defcon.biomes

import com.mochibit.defcon.biomes.data.*
import com.mochibit.defcon.biomes.enums.PrecipitationType
import com.mochibit.defcon.biomes.enums.TemperatureModifier

class CustomBiomeBuilder {
    private var biome = CustomBiome()

    // Builder
    fun build() : CustomBiome {
        return biome
    }

    // Setters for the biome properties
    fun setTemperature(temperature: Float): CustomBiomeBuilder {
        biome.temperature = temperature
        return this
    }

    fun setDownfall(downfall: Float): CustomBiomeBuilder {
        biome.downfall = downfall
        return this
    }

    fun setPrecipitation(precipitation: PrecipitationType): CustomBiomeBuilder {
        biome.precipitation = precipitation
        return this
    }

    fun setTemperatureModifier(temperatureModifier: TemperatureModifier): CustomBiomeBuilder {
        biome.temperatureModifier = temperatureModifier
        return this
    }

    fun setHasPrecipitation(hasPrecipitation: Boolean): CustomBiomeBuilder {
        biome.hasPrecipitation = hasPrecipitation
        return this
    }

    fun setEffects(effects: BiomeEffects): CustomBiomeBuilder {
        biome.effects = effects
        return this
    }

    fun addMonsterSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.monsterSpawners.add(spawner)
        return this
    }

    fun addCreatureSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.creatureSpawners.add(spawner)
        return this
    }

    fun addAmbientSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.ambientSpawners.add(spawner)
        return this
    }

    fun addAxolotlSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.axolotlSpawners.add(spawner)
        return this
    }

    fun addUndergroundWaterCreatureSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.undergroundWaterCreatureSpawners.add(spawner)
        return this
    }

    fun addWaterCreatureSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.waterCreatureSpawners.add(spawner)
        return this
    }

    fun addWaterAmbientSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.waterAmbientSpawners.add(spawner)
        return this
    }

    fun addMiscSpawner(spawner: BiomeSpawner): CustomBiomeBuilder {
        biome.miscSpawners.add(spawner)
        return this
    }

    fun addSpawnCost(spawnCost: BiomeSpawnCost): CustomBiomeBuilder {
        biome.spawnCosts.add(spawnCost)
        return this
    }

    fun addFeature(feature: BiomeFeature): CustomBiomeBuilder {
        biome.features.add(feature)
        return this
    }

    fun addCarver(carver: BiomeCarver): CustomBiomeBuilder {
        biome.carvers.add(carver)
        return this
    }


}