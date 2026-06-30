package me.mochibit.defcon.effects

import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.emitter.EmitterShape
import org.bukkit.Color
import org.joml.Vector3d

typealias ColorSupply = () -> Color
typealias PositionColorSupply = (Vector3d, EmitterShape) -> Color


interface ColorSupplier {
    val colorSupplier: ColorSupply
    val shapeColorSupplier: PositionColorSupply
}

interface CycledColorSupplier : ColorSupplier, Lifecycled {}