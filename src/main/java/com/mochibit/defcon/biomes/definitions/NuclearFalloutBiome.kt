package com.mochibit.defcon.biomes.definitions

import com.mochibit.defcon.biomes.*
import com.mochibit.defcon.biomes.data.BiomeEffects
import com.mochibit.defcon.biomes.data.BiomeInfo
import com.mochibit.defcon.biomes.data.BiomeParticle
import com.mochibit.defcon.biomes.enums.PrecipitationType
import com.mochibit.defcon.biomes.enums.TemperatureModifier
import org.bukkit.Particle

@BiomeInfo("nuclear_fallout")
class NuclearFalloutBiome : CustomBiome() {
    override fun build(): CustomBiome {
        val builder = CustomBiomeBuilder()
            .setPrecipitation(PrecipitationType.RAIN)
            .setTemperature(0.8f)
            .setDownfall(0.4f)
            .setTemperatureModifier(TemperatureModifier.NONE)
            .setHasPrecipitation(true)
            .setEffects(
                BiomeEffects(
                    skyColor = 7175279,
                    fogColor = 9016715,
                    waterColor = 6187104,
                    waterFogColor = 4804682,
                    particle = BiomeParticle(
                        particle = Particle.WHITE_ASH,
                        probability = 0.05f
                    )
                )
            )

        return builder.build()
    }
}