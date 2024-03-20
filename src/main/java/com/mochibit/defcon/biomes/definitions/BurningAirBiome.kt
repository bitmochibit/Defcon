package com.mochibit.defcon.biomes.definitions

import com.mochibit.defcon.biomes.*
import com.mochibit.defcon.biomes.data.BiomeEffects
import com.mochibit.defcon.biomes.data.BiomeInfo
import com.mochibit.defcon.biomes.data.BiomeParticle
import com.mochibit.defcon.biomes.enums.PrecipitationType
import com.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.Particle

@BiomeInfo("burning_air")
class BurningAirBiome : CustomBiome() {
    override fun build(): CustomBiome {
        val builder = CustomBiomeBuilder()
            .setPrecipitation(PrecipitationType.NONE)
            .setTemperature(2.0f)
            .setDownfall(0.1f)
            .setTemperatureModifier(TemperatureModifier.NONE)
            .setHasPrecipitation(false)
            .setEffects(
                BiomeEffects(
                    skyColor = 16557138,
                    fogColor = 16739888,
                    waterColor = 4159204,
                    waterFogColor = 329011,
                    particle = BiomeParticle(
                        particle = Particle.LAVA,
                        probability = 0.005f
                    )
                )
            )

        return builder.build()
    }
}