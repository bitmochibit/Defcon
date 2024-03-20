package com.mochibit.defcon.biomes.data

import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.inventory.ItemStack

data class BiomeParticle (
    var particle: Particle = Particle.BLOCK_DUST,
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
