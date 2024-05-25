package com.mochibit.defcon.events.customitems

import com.mochibit.defcon.radiation.RadiationArea
import org.bukkit.entity.HumanEntity
import org.bukkit.event.Event
import org.bukkit.event.HandlerList

class GeigerDetectEvent(player: HumanEntity, radiationLevel: Double) : Event(){
    private var isCancelled = false

    var player : HumanEntity = player
        private set
    var radiationLevel : Double = radiationLevel
        private set

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
}