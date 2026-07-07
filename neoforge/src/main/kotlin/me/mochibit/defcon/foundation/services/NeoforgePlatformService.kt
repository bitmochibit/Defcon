package me.mochibit.defcon.foundation.services

import me.mochibit.defcon.DefconMod.MOD_ID
import me.mochibit.defcon.foundation.eventbus.NeoforgeClientEventBridge
import me.mochibit.defcon.foundation.eventbus.NeoforgeEventBridge
import net.neoforged.fml.ModList
import net.neoforged.fml.loading.FMLLoader
import net.neoforged.fml.util.thread.EffectiveSide
import java.nio.file.Path

class NeoforgePlatformService : PlatformService {
    override val currentPlatform: PlatformService.Platform = PlatformService.Platform.NEOFORGE
    override val environment: PlatformService.Environment
        get() =
            when {
                FMLLoader.getDist().isClient -> PlatformService.Environment.CLIENT
                FMLLoader.getDist().isDedicatedServer -> PlatformService.Environment.SERVER
                else -> throw IllegalStateException("Unknown environment")
            }
    override val currentThreadSide: PlatformService.Environment
        get() = if (EffectiveSide.get().isServer) PlatformService.Environment.SERVER else PlatformService.Environment.CLIENT

    override fun isModLoaded(modId: String): Boolean = ModList.get().isLoaded(modId)

    override fun setupEventBridge() {
        NeoforgeEventBridge.setup()
    }

    override fun setupClientEventBridge() {
        NeoforgeClientEventBridge.setup()
    }

    override fun getModRootPaths(): List<Path> {
        val modFileInfo = ModList.get().getModFileById(MOD_ID)
            ?: throw IllegalArgumentException("No mod file found for id '$MOD_ID'")

        val secureJar = modFileInfo.file.secureJar
        return listOf(modFileInfo.file.secureJar.primaryPath)
    }
}