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

package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.biomes.CustomBiomeHandler
import com.mochibit.defcon.biomes.definitions.NuclearFalloutBiome
import com.mochibit.defcon.threading.jobs.SchedulableWorkload
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import com.mochibit.defcon.utils.Geometry
import javassist.Loader.Simple
import org.bukkit.Location
import kotlin.math.ceil
import kotlin.math.roundToInt
import org.bukkit.Bukkit
import java.util.concurrent.*
import kotlin.concurrent.thread
import kotlin.math.cos
import kotlin.math.sin

class Shockwave(val center: Location, val shockwaveRadiusStart: Double, val shockwaveRadius: Double, val shockwaveHeight: Double) {
    private val biome = NuclearFalloutBiome()
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors()) as ThreadPoolExecutor
    private val cylinderQueue = PriorityBlockingQueue<ShockwaveColumn>()

    fun explode() {
        info("Exploding..")
        thread(true) {
            info("Calculating cylinders")
            precalculateCylinders()
            executor.shutdown()
            info("Finished calculating cylinders")
        }
        startExploding()
    }

    private fun precalculateCylinders() {
        for (radius in shockwaveRadiusStart.toInt()..shockwaveRadius.toInt()) {
            val explosionPower = 6f - (radius * 3f / shockwaveRadius)

            // From a radius to another, skip 3 radius
            if (radius % (1.5 * ceil(explosionPower / 6)).roundToInt() != 0)
                continue

            executor.submit {
                val columns = shockwaveCyl(radius.toDouble(), explosionPower.toFloat())
                cylinderQueue.addAll(columns)
                info("Calculating columns for radius $radius")
            }
        }
    }

    private fun startExploding() {
        val scheduledRunnable = ScheduledRunnable().maxMillisPerTick(30.0)
        val taskTimer = Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, scheduledRunnable, 0L, 1L)
        Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, { task ->
            while (cylinderQueue.isNotEmpty()) {
                val column = cylinderQueue.poll()
                column?.let {
                    scheduledRunnable.addWorkload(SimpleSchedulable {
                        column.explode()
                    })
                }
            }
            if (executor.isTerminated && cylinderQueue.isEmpty() && scheduledRunnable.workloadDeque.isEmpty()) {
                task.cancel()
                taskTimer.cancel()
            }
        }, 0L, 2L )
    }

    private fun shockwaveCyl(radius: Double, explosionPower: Float): List<ShockwaveColumn> {
        val columns = mutableListOf<ShockwaveColumn>()

        // Increase the step size for less precision
        val angleStep = (1.0 / radius).coerceAtLeast(0.2) // Larger step size for less precision

        // Use a larger step size for generating angles
        for (angle in 0.0..2 * Math.PI step angleStep) {
            val x = radius * cos(angle)
            val z = radius * sin(angle)

            // Add fewer columns for less precision
            columns.add(ShockwaveColumn(
                center.clone().add(x, 0.0, z),
                explosionPower,
                radius.toInt(),
                this
            ))
            columns.add(ShockwaveColumn(
                center.clone().add(-x, 0.0, z),
                explosionPower,
                radius.toInt(),
                this
            ))
            columns.add(ShockwaveColumn(
                center.clone().add(x, 0.0, -z),
                explosionPower,
                radius.toInt(),
                this
            ))
            columns.add(ShockwaveColumn(
                center.clone().add(-x, 0.0, -z),
                explosionPower,
                radius.toInt(),
                this
            ))
        }
        return columns
    }

    private infix fun ClosedRange<Double>.step(step: Double) =
        generateSequence(start) { previous ->
            if (previous + step <= endInclusive) previous + step else null
        }
}
