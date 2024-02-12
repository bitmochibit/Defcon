package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.explosives.NuclearComponent
import com.mochibit.nuclearcraft.explosives.ShockwaveColumn
import com.mochibit.nuclearcraft.interfaces.ExplodingStructure
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import com.mochibit.nuclearcraft.threading.tasks.ScheduledRunnable
import com.mochibit.nuclearcraft.utils.Geometry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import java.util.function.Consumer
import kotlin.math.ceil


class NuclearWarhead : AbstractStructureDefinition(), ExplodingStructure {
    override fun explode(center: Location, nuclearComponent: NuclearComponent) {
        /* The explosion is subdivided into some steps
        *
        *  The first one, assuming the bomb is a nuclear bomb, create a sphere of air blocks ( calculated with the explosive component ),
        *  since nuclear bombs obliterate everything in their radius
        *
        *  The second step is to give fire damage to all entities in the radius of the thermal radiation
        *
        *  The third step is to create an expanding shockwave, which will expand and move over structures, exploding them, and changing the terrain
        *  with some blocks that look like burnt or destroyed blocks.
        *
        *  The fourth step is to modify the biomes of the area, since the explosion will change the terrain and the environment
        *
        *  When all of this is happening, there will be a sound effect, and a particle effect, to simulate the explosion
        */

        // Create a sphere of air blocks
        val obliterationRadius = nuclearComponent.blastPower * 5;
        for (x in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
            for (y in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                for (z in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                    val distance = (x * x + y * y + z * z);
                    if (distance <= obliterationRadius * obliterationRadius) {
                        val block = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block;
                        block.type = Material.AIR;
                    }
                }
            }
        }

        // Give fire damage to all entities in the radius of the thermal radiation (unless they are protected)
        // We will use ray-casting to check if the entity is in the radius of the thermal radiation
        val thermalRadius = nuclearComponent.thermalPower * 5;
        // TODO: Implement thermal radiation


        val shockwaveRadius = nuclearComponent.blastPower * 30 * 2;
        val shockwaveHeight = nuclearComponent.blastPower * 100 * 2;

        NuclearCraft.Companion.Logger.info("Shockwave radius: $shockwaveRadius, Shockwave height: $shockwaveHeight");



        val columns: HashSet<ShockwaveColumn> = HashSet();
        for (radius in 0..shockwaveRadius.toInt()) {
            columns.addAll(shockwaveCyl(center, radius.toDouble(), shockwaveHeight.toDouble()));

        }

        for (column in columns.sortedBy { it.radiusGroup }) {
            column.explode()
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


                columns.add(ShockwaveColumn(center.clone().add(x.toDouble(), 0.0, z.toDouble()), maxHeight, radius.toInt()));
                columns.add(ShockwaveColumn(center.clone().add(-x.toDouble(), 0.0, z.toDouble()), maxHeight, radius.toInt()));
                columns.add(ShockwaveColumn(center.clone().add(x.toDouble(), 0.0, -z.toDouble()), maxHeight, radius.toInt()));
                columns.add(ShockwaveColumn(center.clone().add(-x.toDouble(), 0.0, -z.toDouble()), maxHeight, radius.toInt()));
            }
        }
        return columns;
    }
}
