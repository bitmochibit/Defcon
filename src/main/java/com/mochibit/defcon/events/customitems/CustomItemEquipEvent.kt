package com.mochibit.defcon.events.customitems

import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList
import org.bukkit.inventory.ItemStack

class CustomItemEquipEvent(private val equippedItem: ItemStack, private val player: HumanEntity) : Event() {
    private var isCancelled = false

    companion object {
        private val HANDLERS = HandlerList()
        @JvmStatic
        fun getHandlerList(): HandlerList {
            return HANDLERS
        }
    }
    override fun getHandlers(): HandlerList {
        return HANDLERS
    }

    fun setCancelled(cancel: Boolean) {
        isCancelled = cancel
    }

    fun isCancelled(): Boolean {
        return isCancelled
    }

    fun getPlayer(): HumanEntity {
        return player
    }

    fun getEquippedItem(): ItemStack {
        return equippedItem
    }

}