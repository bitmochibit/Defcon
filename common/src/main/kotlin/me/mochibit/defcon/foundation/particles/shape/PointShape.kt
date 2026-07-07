package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d

data object PointShape : EmitterShape() {
    override val minHeight: Double
        get() = 1.0
    override val maxHeight: Double
        get() = 1.0
    override val minWidth: Double
        get() = 1.0
    override val maxWidth: Double
        get() = 1.0
    override val minDepth: Double
        get() = 1.0
    override val maxDepth: Double
        get() = 1.0

    override fun maskLoc(location: Vector3d) {}

}