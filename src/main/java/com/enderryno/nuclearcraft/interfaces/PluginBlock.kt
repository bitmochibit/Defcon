package com.enderryno.nuclearcraft.interfaces

import com.enderryno.nuclearcraft.enums.BlockBehaviour
import org.bukkit.Location

interface PluginBlock {
    fun setID(id: String?): PluginBlock
    val id: String?
    fun setCustomModelId(customModelId: Int): PluginBlock
    val customModelId: Int
    fun setMinecraftId(minecraftId: String?): PluginBlock
    val minecraftId: String?
    fun placeBlock(item: PluginItem?, location: Location)
    fun getBlock(location: Location): PluginBlock?
    fun removeBlock(location: Location)

    /*Behaviour type*/
    fun setBehaviour(behaviour: BlockBehaviour?): PluginBlock
    val behaviour: BlockBehaviour?
}
