package me.mochibit.defcon

import net.neoforged.bus.api.IEventBus
import net.neoforged.bus.api.SubscribeEvent
import net.neoforged.fml.common.EventBusSubscriber
import net.neoforged.fml.common.Mod
import net.neoforged.neoforge.capabilities.RegisterCapabilitiesEvent


@Mod(MOD_ID)
class NeoforgeModEntryPoint(
    val modEventBus: IEventBus,
) {
    companion object {
        @JvmStatic
        lateinit var instance: NeoforgeModEntryPoint
            private set

        fun onRegister(event: RegisterEvent) {
            CreateHarmonicsMod.commonPreFreezeSetup(event.registry)
        }
    }

    init {
        instance = this
        initialize()
    }

    @EventBusSubscriber(modid = MOD_ID)
    object ModSetup {
        @JvmStatic
        @SubscribeEvent
        fun registerCapabilities(event: RegisterCapabilitiesEvent) {
            // Register platform specific capabilities here
        }
    }

    private fun initialize() {
        ModEventBus.addListener(NeoforgeModEntryPoint::onRegister)

        CreateHarmonicsMod.commonSetup {
            registerEventListeners(this@NeoforgeModEntryPoint.modEventBus)
        }

        provideLang()

        autoRegister<NeoforgeRegistry>()
        ModEventBus.addListener(NeoforgeModPackets::registerPayloads)
    }
}

internal val ModEventBus: IEventBus = NeoforgeModEntryPoint.instance.modEventBus