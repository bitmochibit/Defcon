package com.mochibit.nuclearcraft.events.blocks

import com.mochibit.nuclearcraft.interfaces.PluginBlock
import com.mochibit.nuclearcraft.services.BlockRegister
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockBreakEvent

class CustomBlockBreakEvent : Listener {
    @EventHandler
    fun onBlockBreak(event: BlockBreakEvent) {
        // Get the item in the player's hand
        val customBlock: PluginBlock
        val block = event.block

        customBlock = BlockRegister.getBlock(block.location)?: return
        customBlock.removeBlock(block.location)
    }

}