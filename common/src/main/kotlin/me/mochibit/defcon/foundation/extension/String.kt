package me.mochibit.defcon.foundation.extension

import me.mochibit.defcon.DefconMod.MOD_ID
import net.minecraft.resources.ResourceLocation

fun String.asResource(): ResourceLocation = ResourceLocation.fromNamespaceAndPath(MOD_ID, this)

infix fun String.resPath(other: String): ResourceLocation = ResourceLocation.fromNamespaceAndPath(this, other)