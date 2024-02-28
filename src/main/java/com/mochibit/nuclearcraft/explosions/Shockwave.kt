package com.mochibit.nuclearcraft.explosions

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import com.mochibit.nuclearcraft.utils.Geometry
import org.bukkit.Location
import java.util.*
import kotlin.math.ceil

class Shockwave(val center: Location, val shockwaveRadius: Double, val shockwaveHeight: Double) {
    fun explode() {
        val columns: HashSet<ShockwaveColumn> = HashSet();
        for (radius in 0..shockwaveRadius.toInt()) {
            columns.addAll(shockwaveCyl(center, radius.toDouble(), shockwaveHeight.toDouble()));
        }

        // Convert columns to a thread-safe collection
        val clonedColumns = Collections.synchronizedList(ArrayList(columns));
        for (column in clonedColumns.sortedBy { it.radiusGroup }) {
            NuclearCraft.instance.scheduledRunnable.addWorkload(
                SimpleCompositionJob(column) {
                    it.explode();
                }
            );
        }
    }
    private fun shockwaveCyl(center: Location, radius: Double, maxHeight: Double): HashSet<ShockwaveColumn> {
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
                    maxHeight,
                    radius.toInt(),
                    this
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(-x.toDouble(), 0.0, z.toDouble()),
                    maxHeight,
                    radius.toInt(),
                    this
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(x.toDouble(), 0.0, -z.toDouble()),
                    maxHeight,
                    radius.toInt(),
                    this
                ));
                columns.add(ShockwaveColumn(
                    center.clone().add(-x.toDouble(), 0.0, -z.toDouble()),
                    maxHeight,
                    radius.toInt(),
                    this
                ));
            }
        }
        return columns;
    }
}