/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.particles.mutators

import me.mochibit.defcon.utils.ChunkCache
import org.bukkit.Location
import org.joml.Vector3d
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

class FloorSnapper(
    private val center: Location,
    private val easeFromPoint: Vector3f? = null,
    private val peakHeight : Float = easeFromPoint?.y() ?: 0.0f,
    private val maxDistance: Float = 80.0f
) : AbstractShapeMutator() {
    private val cachedMinY: ConcurrentMap<Pair<Int, Int>, Float> = ConcurrentHashMap()
    private val chunkCache = ChunkCache.getInstance(center.world)

    override fun mutateLoc(location: Vector3d) {
        val x = location.x().toInt()
        val z = location.z().toInt()

        // Cache the minimum Y values based on (x, z)
        val minY = cachedMinY.computeIfAbsent(x to z) {
            val baseY = chunkCache.highestBlockYAt(x, z).toFloat()
            if (easeFromPoint != null) {
                // Calculate distance from easing point in the X-Z plane
                val dx = x.toFloat() - easeFromPoint.x()
                val dz = z.toFloat() - easeFromPoint.z()
                val distance = sqrt(dx * dx + dz * dz)

                // Normalize distance and compute bell factor
                val normalizedDistance = (distance / maxDistance).coerceIn(0.0f, 1.0f)
                val bellFactor = exp(-4 * normalizedDistance.pow(2)) // Gaussian-like curve

                // Compute final Y value with bell effect
                baseY + bellFactor * peakHeight
            } else {
                baseY
            }
        }

        // Adjust the Y value relative to the center
        location.y = minY + (location.y - center.y.toFloat())
    }
}