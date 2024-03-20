package com.mochibit.defcon.biomes.data

import org.bukkit.entity.EntityType

data class BiomeSpawnCost (
    // Used as a key to the map, not an array
    var entityKey: EntityType = EntityType.ZOMBIE,

    var energyBudget: Float = 0.0f,
    var charge : Float = 0.0f
)
