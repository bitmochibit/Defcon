package com.mochibit.defcon.events.blocks

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.interfaces.PluginItem
import com.mochibit.defcon.services.BlockRegister
import com.mochibit.defcon.services.ItemRegister
import org.bukkit.NamespacedKey
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPlaceEvent
import org.bukkit.persistence.PersistentDataType
import org.bukkit.plugin.java.JavaPlugin

class CustomBlockPlaceEvent : Listener {
    @EventHandler
    fun onBlockPlace(event: BlockPlaceEvent) {
        // Get the item in the player's hand
        val item = event.itemInHand
        val customItem: PluginItem?
        val block = event.block

        // Get persistent data container
        val container = item.itemMeta.persistentDataContainer

        // Get key and check if namespace exists
        val blockIdKey = NamespacedKey(JavaPlugin.getPlugin(Defcon::class.java), "custom-block-id")
        val itemIdKey = NamespacedKey(JavaPlugin.getPlugin(Defcon::class.java), "item-id")
        if (!container.has(blockIdKey, PersistentDataType.STRING)) return
        if (!container.has(itemIdKey, PersistentDataType.STRING)) return

        // Get the custom block id and set it to the block
        val customBlockId = container.get(blockIdKey, PersistentDataType.STRING)?: return
        val customItemId = container.get(itemIdKey, PersistentDataType.STRING)?: return


        customItem = ItemRegister.registeredItems[customItemId] ?: throw Exception("Item not registered")

        try {
            BlockRegister.getBlock(customBlockId)
                    ?.placeBlock(customItem, block.location)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
