package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.foundation.eventbus.PlatformEventBridge
import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.services.PlatformService
import me.mochibit.defcon.foundation.services.platformService
import net.minecraft.core.Registry

object ModEventProxy : CommonRegistry {
    override fun register(registry: Registry<*>?) {
        "Registering mod event proxies...".info()
        val isClient = platformService isEnvironment PlatformService.Environment.CLIENT
        platformService.setupEventBridge()
        if (isClient) {
            platformService.setupClientEventBridge()
        }
        PlatformEventBridge.validateAll(isClient)
    }
}