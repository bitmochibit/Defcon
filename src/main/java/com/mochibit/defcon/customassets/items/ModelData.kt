package com.mochibit.defcon.customassets.items

data class ModelData(
    val parent: String = "minecraft:item/generated",
    val textures: Map<String, String> = mapOf("layer0" to "item/air"),
    val overrides : List<Overrides> = listOf()
    )
