package com.mochibit.defcon.customassets.items.definitions

import com.mochibit.defcon.customassets.items.AbstractCustomItemModel
import com.mochibit.defcon.customassets.items.ModelData
import com.mochibit.defcon.customassets.sounds.SoundInfo
import org.bukkit.Material

class LeatherGasMask: AbstractCustomItemModel(
    ModelData(
        originalItem = Material.LEATHER_BOOTS,
        textures = mapOf(
            "layer0" to "item/leather_boots",
            "layer1" to "item/leather_boots_overlay"
        ),
        modelName = "leather_gas_mask"
    )
)
