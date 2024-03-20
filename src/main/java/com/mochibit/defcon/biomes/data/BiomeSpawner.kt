package com.mochibit.defcon.biomes.data

import com.mochibit.defcon.biomes.enums.SpawnerCategory
import org.bukkit.entity.EntityType


data class BiomeSpawner (
    var spawnerCategory: SpawnerCategory = SpawnerCategory.CREATURE,
    var type: EntityType = EntityType.ZOMBIE,
    var weight: Int = 1,
    var minCount: Int = 0,
    var maxCount: Int = 0
)
