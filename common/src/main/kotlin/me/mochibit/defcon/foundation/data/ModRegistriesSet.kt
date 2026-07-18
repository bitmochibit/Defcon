package me.mochibit.defcon.foundation.data

import me.mochibit.defcon.foundation.registry.ModDamageTypes
import net.minecraft.core.RegistrySetBuilder
import net.minecraft.core.registries.Registries

object ModRegistriesSet {
    val registrySetBuilder: RegistrySetBuilder = RegistrySetBuilder()
        .add(Registries.DAMAGE_TYPE, ModDamageTypes::bootstrap)
}