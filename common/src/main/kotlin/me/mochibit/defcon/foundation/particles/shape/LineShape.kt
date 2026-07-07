package me.mochibit.defcon.foundation.particles.shape

import org.joml.Vector3d

/**
 * Line-shaped emitter that distributes particles along a straight line.
 */
class LineShape(
    var length: Float,
    var direction: Direction = Direction.Y,
    density: Float = 1.0f
) : EmitterShape(density) {

    /**
     * Direction enum represents the axis along which the line extends
     */
    enum class Direction { X, Y, Z }

    init {
        require(length > 0f) { "Length must be positive" }
    }

    override val minHeight: Double
        get() = if (direction == Direction.Y) 0.0 else -0.5
    override val maxHeight: Double
        get() = if (direction == Direction.Y) length.toDouble() else 0.5

    override val minWidth: Double
        get() = if (direction == Direction.X) 0.0 else -0.5
    override val maxWidth: Double
        get() = if (direction == Direction.X) length.toDouble() else 0.5

    override val minDepth: Double
        get() = if (direction == Direction.Z) 0.0 else -0.5
    override val maxDepth: Double
        get() = if (direction == Direction.Z) length.toDouble() else 0.5

    override fun maskLoc(location: Vector3d) {
        // Apply density to control distribution along the line
        val pos = applyDensity(nextDouble()) * length

        // Apply position based on direction
        when (direction) {
            Direction.X -> location.add(pos, 0.0, 0.0)
            Direction.Y -> location.add(0.0, pos, 0.0)
            Direction.Z -> location.add(0.0, 0.0, pos)
        }
    }
}