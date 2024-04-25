package com.mochibit.defcon.customassets.items

import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import java.util.*

data class ModelData(
    val originalItem : Material = Material.FLINT,
    val originalItemName: String = originalItem.name.lowercase(Locale.getDefault()),
    val modelName: String,
    val parent: ParentType = ParentType.ITEM_GENERATED,
    val textures: Map<String, String> = mapOf("layer0" to "${if (originalItem.isBlock) "block" else "item"}/${originalItemName}"),
    val customModelData: Int = 1,
    val model: String = "${if (originalItem.isBlock) "block" else "item"}/$modelName/$modelName"
    )
