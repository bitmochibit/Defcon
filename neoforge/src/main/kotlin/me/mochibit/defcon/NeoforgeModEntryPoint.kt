package me.mochibit.defcon

import me.mochibit.defcon.DefconMod.MOD_ID
import me.mochibit.defcon.foundation.registry.NeoforgeModPackets
import me.mochibit.defcon.foundation.registry.NeoforgeRegistry
import me.mochibit.defcon.foundation.registry.autoRegister
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.registries.RegisterEvent

@Mod(MOD_ID)
class NeoforgeModEntryPoint(
    val modEventBus: IEventBus,
) {
    companion object {
        @JvmStatic
        lateinit var instance: NeoforgeModEntryPoint
            private set

        fun onRegister(event: RegisterEvent) {
            DefconMod.commonPreFreezeSetup(event.registry)
        }
    }

    init {
        instance = this
        modEventBus.addListener(NeoforgeModEntryPoint::onRegister)
        initialize()
    }

    private fun initialize() {
        DefconMod.commonSetup {
            registerEventListeners(this@NeoforgeModEntryPoint.modEventBus)
        }

        autoRegister<NeoforgeRegistry>()
        ModEventBus.addListener(NeoforgeModPackets::registerPayloads)
    }
}

internal val ModEventBus: IEventBus = NeoforgeModEntryPoint.instance.modEventBus