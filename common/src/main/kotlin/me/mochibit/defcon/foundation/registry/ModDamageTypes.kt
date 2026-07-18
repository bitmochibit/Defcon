package me.mochibit.defcon.foundation.registry

import me.mochibit.defcon.foundation.damageTypes.DamageTypeBuilder
import me.mochibit.defcon.foundation.extension.asResource
import me.mochibit.defcon.foundation.info
import net.minecraft.core.registries.Registries
import net.minecraft.data.worldgen.BootstrapContext
import net.minecraft.resources.ResourceKey
import net.minecraft.world.damagesource.DamageSource
import net.minecraft.world.damagesource.DamageType
import net.minecraft.world.level.LevelReader
import java.util.logging.Level

object ModDamageTypes {
    val VAPORIZED = key("vaporized")
    val BLASTED = key("blasted")

    private fun key(name: String): ResourceKey<DamageType> =
        ResourceKey.create(Registries.DAMAGE_TYPE, name.asResource())

    fun bootstrap(ctx: BootstrapContext<DamageType>) {
        "Bootstrapping damage types".info()
        DamageTypeBuilder(VAPORIZED).register(ctx)
        DamageTypeBuilder(BLASTED).register(ctx)

    }
}

object ModDamageSources {
    fun vaporized(level: LevelReader) = source(ModDamageTypes.VAPORIZED, level)


    private fun source(key: ResourceKey<DamageType>, level: LevelReader): DamageSource {
        val registry = level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE)
        return DamageSource(registry.getHolderOrThrow(key))
    }
}
