package com.mochibit.defcon.particles

import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Display.Brightness
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.Matrix4f
import org.joml.Vector3f

interface ParticleEntityHandler {
    fun spawn(location: Location)
    fun remove()

    fun setItemStack(itemStack: ItemStack): ParticleEntityHandler
    fun setItemInSlot(slot: Int, itemStack: ItemStack): ParticleEntityHandler
    fun setBillboard(billboard: Display.Billboard): ParticleEntityHandler
    fun setBrightness(brightness: Display.Brightness): ParticleEntityHandler
    fun setShadowStrength(shadowStrength: Float): ParticleEntityHandler
    fun setInterpolationDuration(interpolationDuration: Int): ParticleEntityHandler
    fun setTransformation(transformation: Transformation): ParticleEntityHandler
    fun setTransformationMatrix(transformation: Matrix4f): ParticleEntityHandler
    fun setScale(scale: Vector3f): ParticleEntityHandler
    fun setTeleportDuration(teleportDuration: Int): ParticleEntityHandler
    fun setViewRange(viewRange: Float): ParticleEntityHandler
    fun setPersistent(persistent: Boolean): ParticleEntityHandler
}