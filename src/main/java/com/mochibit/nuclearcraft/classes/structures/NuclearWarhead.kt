package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.explosives.NuclearComponent
import com.mochibit.nuclearcraft.interfaces.ExplodingStructure
import com.mochibit.nuclearcraft.utils.Geometry
import org.bukkit.Location
import org.bukkit.Material
import javax.swing.Spring.height
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


        val shockwaveRadius = nuclearComponent.blastPower * 30;
        val shockwaveHeight = nuclearComponent.blastPower * 30;

        NuclearCraft.Companion.Logger.info("Shockwave radius: $shockwaveRadius, Shockwave height: $shockwaveHeight");

        for (radius in 0..shockwaveRadius.toInt()) {
            val locations = shockwaveCyl(center, radius.toDouble(), shockwaveHeight);
            NuclearCraft.Companion.Logger.info("Shockwave locations: ${locations.size}");
            for (location in locations) {
                if (location.world.getBlockAt(location).type == Material.AIR)
                    continue;

                center.world.createExplosion(location, 8.0f, true, true);
            }
        }
    }

    private fun shockwaveCyl(center: Location, radius: Double, maxHeight: Float, filled: Boolean = false) : HashSet<Location> {
        val locations = HashSet<Location>();

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

                if (!filled) {
                    if (Geometry.lengthSq(nextXn, zn) <= 1 && Geometry.lengthSq(xn, nextZn) <= 1) {
                        continue
                    }
                }

                val locationYClone = center.clone();
                locationYClone.y = center.y + maxHeight;
                val location1 = getMinY(locationYClone.clone().add(x.toDouble(), 0.0, z.toDouble()));
                val location2 = getMinY(locationYClone.clone().add(-x.toDouble(), 0.0, z.toDouble()));
                val location3 = getMinY(locationYClone.clone().add(x.toDouble(), 0.0, -z.toDouble()));
                val location4 = getMinY(locationYClone.clone().add(-x.toDouble(), 0.0, -z.toDouble()));

                locations.add(location1);
                locations.add(location2);
                locations.add(location3);
                locations.add(location4);
            }
        }
        return locations;
    }

    private fun getMinY(position: Location) : Location {
        var y = position.y;
        while (position.world.getBlockAt(position).type == Material.AIR) {
            y--;
            position.y = y;
        }
        return position;
    }

}
