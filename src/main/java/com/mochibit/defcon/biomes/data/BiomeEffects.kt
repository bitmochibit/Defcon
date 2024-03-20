package com.mochibit.defcon.biomes.data

import com.mochibit.defcon.biomes.enums.GrassColorModifier

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
