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
import com.mochibit.defcon.threading.jobs.SimpleCompositionJob
import com.mochibit.defcon.utils.Geometry
import org.bukkit.Location
import java.util.*
import kotlin.math.ceil
import kotlin.math.exp
import kotlin.math.roundToInt

class Shockwave(val center: Location, val shockwaveRadiusStart: Double, val shockwaveRadius: Double, val shockwaveHeight: Double) {
    fun explode() {
        for (radius in shockwaveRadiusStart.toInt()..shockwaveRadius.toInt()) {
            val explosionPower = 10f - (radius * 6f / shockwaveRadius)

            // From a radius to another, skip 3 radius
            if (radius % (1.5*ceil(explosionPower/6)).roundToInt() != 0)
                continue;

            Defcon.instance.scheduledRunnable.addWorkload(SimpleCompositionJob(radius) {
                for (column in shockwaveCyl(radius.toDouble(), explosionPower.toFloat())) {
                    Defcon.instance.scheduledRunnable.addWorkload(SimpleCompositionJob(column) {
                        column.explode()
                    })
                }
            });
        }
    }
    private fun shockwaveCyl(radius: Double, explosionPower: Float): HashSet<ShockwaveColumn> {
        val columns = HashSet<ShockwaveColumn>();

        val radiusX: Double = radius
        val radiusZ: Double = radius

        val invRadiusX: Double = 1 / radiusX
        val invRadiusZ: Double = 1 / radiusZ

        val ceilRadiusX = ceil(radiusX).toInt()
        val ceilRadiusZ = ceil(radiusZ).toInt()

        var nextXn = 0.0
        forX@ for (x in 0..ceilRadiusX) {
            val xn = nextXn
            nextXn = (x + 1) * invRadiusX
            var nextZn = 0.0
            forZ@ for (z in 0..ceilRadiusZ) {
                val zn = nextZn
                nextZn = (z + 1) * invRadiusZ

                val distanceSq: Double = Geometry.lengthSq(xn, zn)
                if (distanceSq > 1) {
                    if (z == 0) {
                        break@forX
                    }
                    break@forZ
                }

                if (Geometry.lengthSq(nextXn, zn) <= 1 && Geometry.lengthSq(xn, nextZn) <= 1) {
                    continue
                }


                columns.add(ShockwaveColumn(
                    center.clone().add(x.toDouble(), 0.0, z.toDouble()),
                    explosionPower,
                    radius.toInt(),
                    this,
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(-x.toDouble(), 0.0, z.toDouble()),
                    explosionPower,
                    radius.toInt(),
                    this
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(x.toDouble(), 0.0, -z.toDouble()),
                    explosionPower,
                    radius.toInt(),
                    this
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(-x.toDouble(), 0.0, -z.toDouble()),
                    explosionPower,
                    radius.toInt(),
                    this
                ));
            }
        }
        return columns;
    }
}