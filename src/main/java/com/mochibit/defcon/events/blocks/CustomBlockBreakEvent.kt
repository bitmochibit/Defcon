package com.mochibit.defcon.events.blocks

import com.mochibit.defcon.interfaces.PluginBlock
import com.mochibit.defcon.registers.BlockRegister
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