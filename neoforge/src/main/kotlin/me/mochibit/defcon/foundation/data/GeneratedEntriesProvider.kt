package me.mochibit.defcon.foundation.data

import me.mochibit.defcon.DefconMod.MOD_ID
import net.minecraft.core.HolderLookup
import net.minecraft.data.PackOutput
import net.neoforged.neoforge.common.data.DatapackBuiltinEntriesProvider
import java.util.concurrent.CompletableFuture

class GeneratedEntriesProvider(output: PackOutput, registries: CompletableFuture<HolderLookup.Provider>) :
    DatapackBuiltinEntriesProvider(
        output, registries,
        ModRegistriesSet.registrySetBuilder, setOf(MOD_ID),
    ) {
    override fun getName(): String {
        return "Defcon's generated registry entries"
    }
}