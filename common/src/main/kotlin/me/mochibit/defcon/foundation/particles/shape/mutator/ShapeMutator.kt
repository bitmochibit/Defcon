package me.mochibit.defcon.foundation.particles.shape.mutator

import net.minecraft.core.BlockPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.levelgen.Heightmap
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

interface ShapeMutator {
    abstract fun mutateLoc(location: Vector3d)
}

class FloorSnapper(
    val level: Level,
    private val center: BlockPos,
    private val easeFromPoint: Vector3f? = null,
    private val peakHeight: Float = easeFromPoint?.y() ?: 0.0f,
    private val maxDistance: Float = 80.0f
) : ShapeMutator {

    override fun mutateLoc(location: Vector3d) {
        val x = (center.x + location.x()).toInt()
        val z = (center.z + location.z()).toInt()

        val baseY = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z)
        val minY =
        if (easeFromPoint != null) {
            // Calculate distance from easing point in the X-Z plane
            val dx = x.toFloat() - easeFromPoint.x()
            val dz = z.toFloat() - easeFromPoint.z()
            val distance = sqrt(dx * dx + dz * dz)

            // Normalize distance and compute bell factor
            val normalizedDistance = (distance / maxDistance).coerceIn(0.0f, 1.0f)
            val bellFactor = exp(-4 * normalizedDistance.pow(2)) // Gaussian-like curve

            // Compute final Y value with bell effect
            (baseY + bellFactor * peakHeight).roundToInt()
        } else {
            baseY
        }

        // Adjust the Y value relative to the center
        location.y = minY + (location.y - center.y.toFloat())
    }
}