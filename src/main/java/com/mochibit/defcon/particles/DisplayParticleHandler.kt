package com.mochibit.defcon.particles

import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.entity.Display
import org.bukkit.entity.ItemDisplay
import org.bukkit.inventory.ItemStack
import org.bukkit.util.Transformation
import org.joml.AxisAngle4f
import org.joml.Matrix4f
import org.joml.Vector3f

class DisplayParticleHandler : ParticleEntityHandler {
    private var billboard: Display.Billboard? = null
    private var brightness: Display.Brightness? = null
    private var shadowStrength: Float = 0.0f
    private var interpolationDuration: Int = 0
    private var transformation: Transformation? = null
    private var transformationMatrix: Matrix4f? = null
    private var scale: Vector3f? = null
    private var teleportDuration: Int = 0
    private var viewRange: Float = 0.0f
    private var persistent: Boolean = false
    private var itemStack: ItemStack? = null

    private var item: ItemDisplay? = null

    override fun spawn(location: Location) {
        item = location.world.spawn(location, ItemDisplay::class.java) {
            it.billboard = this.billboard ?: Display.Billboard.FIXED;
            it.brightness = this.brightness ?: Display.Brightness(15, 15);
            it.shadowStrength = this.shadowStrength;
            // Apply velocity and scale with transformation matrix
            it.interpolationDuration = this.interpolationDuration;
            // Assign transformation only if it is not null
            this.transformation?.let { transformation ->
                it.transformation = transformation
            }
            this.transformationMatrix?.let { transformationMatrix ->
                it.setTransformationMatrix(transformationMatrix)
            }
            this.scale?.let { s ->
                it.transformation.apply {
                    scale.set(s)
                }
            }

            it.teleportDuration = this.teleportDuration.coerceIn(0,59)
            it.viewRange = this.viewRange;
            it.isPersistent = this.persistent;

            it.itemStack = this.itemStack ?: ItemStack(Material.DIAMOND);
        };
    }

    override fun remove() {
        if (item == null) return
        item!!.remove()
        item = null
    }

    override fun setItemStack(itemStack: ItemStack): ParticleEntityHandler {
        this.itemStack = itemStack
        return this
    }

    override fun setItemInSlot(slot: Int, itemStack: ItemStack): ParticleEntityHandler {
        return setItemStack(itemStack)
    }

    override fun setBillboard(billboard: Display.Billboard): ParticleEntityHandler {
        this.billboard = billboard
        return this
    }

    override fun setBrightness(brightness: Display.Brightness): ParticleEntityHandler {
        this.brightness = brightness
        return this
    }

    override fun setShadowStrength(shadowStrength: Float): ParticleEntityHandler {
        this.shadowStrength = shadowStrength
        return this
    }

    override fun setInterpolationDuration(interpolationDuration: Int): ParticleEntityHandler {
        this.interpolationDuration = interpolationDuration
        return this
    }

    override fun setTransformation(transformation: Transformation): ParticleEntityHandler {
        this.transformation = transformation
        return this
    }

    override fun setTransformationMatrix(transformation: Matrix4f): ParticleEntityHandler {
        this.transformationMatrix = transformation
        return this
    }

    override fun setScale(scale: Vector3f): ParticleEntityHandler {
        this.scale = scale
        return this
    }

    override fun setTeleportDuration(teleportDuration: Int): ParticleEntityHandler {
        this.teleportDuration = teleportDuration
        return this
    }

    override fun setViewRange(viewRange: Float): ParticleEntityHandler {
        this.viewRange = viewRange
        return this
    }

    override fun setPersistent(persistent: Boolean): ParticleEntityHandler {
        this.persistent = persistent
        return this
    }
}