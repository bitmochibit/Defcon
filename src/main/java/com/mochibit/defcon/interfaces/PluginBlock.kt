package com.mochibit.defcon.interfaces

import com.mochibit.defcon.enums.BlockBehaviour
import org.bukkit.Location

interface PluginBlock {
    val id: String
    val customModelId: Int
    val minecraftId: String
    fun placeBlock(item: PluginItem, location: Location)
    fun removeBlock(location: Location)

    /*Behaviour type*/
    val behaviour: BlockBehaviour
}
