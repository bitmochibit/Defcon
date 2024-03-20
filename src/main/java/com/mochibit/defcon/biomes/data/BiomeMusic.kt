package com.mochibit.defcon.biomes.data

data class BiomeMusic (
    var sound: String? = null,
    var minDelay: Int = 0,
    var maxDelay: Int = 0,
    var replaceCurrentMusic : Boolean = false
)
