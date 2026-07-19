package me.mochibit.defcon

import me.mochibit.defcon.DefconMod.MOD_ID
import me.mochibit.defcon.content.explosion.processor.PostApocalypticTerrain
import me.mochibit.defcon.foundation.data.DataGenerators.provideLang
import me.mochibit.defcon.foundation.eventbus.LogicalSide
import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.registry.CommonRegistry
import me.mochibit.defcon.foundation.registry.NeoforgeModPackets
import me.mochibit.defcon.foundation.registry.NeoforgeRegistry
import me.mochibit.defcon.foundation.registry.autoRegister
import net.neoforged.bus.api.IEventBus
import net.neoforged.fml.ModList
import net.neoforged.fml.ModLoadingContext
import net.neoforged.fml.common.Mod
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.registries.RegisterEvent
import org.objectweb.asm.Type
import thedarkcolour.kotlinforforge.neoforge.forge.MOD_BUS
import kotlin.jvm.java

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
        val scanData = ModList.get().getModFileById(MOD_ID)?.file

        val annotationTarget = Type.getDescriptor(CommonRegistry::class.java)
        val subtypes = scanData?.scanResult?.classes?.map {
            it.clazz.className
        }

        subtypes?.forEach {
//            it.info()
        }

        DefconMod.commonSetup {
//            registerEventListeners(this@NeoforgeModEntryPoint.modEventBus)
            // For now since using the default registrate do not register event listeners, in future if a new subtype is created this is required!
        }

        provideLang()

        autoRegister<NeoforgeRegistry>()
        ModEventBus.addListener(NeoforgeModPackets::registerPayloads)
    }
}

internal val ModEventBus: IEventBus = NeoforgeModEntryPoint.instance.modEventBus