package com.mochibit.nuclearcraft.classes.structures

import com.mochibit.nuclearcraft.explosives.NuclearComponent
import com.mochibit.nuclearcraft.interfaces.ExplodingStructure
import com.mochibit.nuclearcraft.utils.Geometry
import org.bukkit.Location
import org.bukkit.Material

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


        // Create an expanding shockwave (first approach)
        /* Get a maximum height for the shockwave starting from the center of the explosion, let it be parametric.
        *  For every step so the radius is 0, 1 ... radius, generate a column that will check from the top to the bottom if there is a block
        *  in front of it or under it, if there is, it will explode it, and if there is not, it will move to the next block.
        *  The column will start with a single one in the center, and for example at radius 1, it will have 4 columns, one for each direction
        *  for the next radius 8, for the next one 16, and so on.
        *
        *
        * */
        val shockwaveRadius = nuclearComponent.blastPower * 10;
        val shockwaveHeight = nuclearComponent.blastPower * 15;

        val locations = HashSet<Location>();

        MainLoop@ for (radius in 0..shockwaveRadius.toInt()) {
            if (radius == 0) {
                locations.add(center.clone());
                continue@MainLoop;
            }

            ZLoop@ for (z in -radius..radius) {
                XLoop@ for (x in -radius..radius) {
                    // Add location to the set if the distance from the center is the same as the radius
                    if (Geometry.lengthSq(x.toDouble(), z.toDouble()) == (radius * radius).toDouble()) {
                        locations.add(center.clone().add(x.toDouble(), 0.0, z.toDouble()));
                    }
                }
            }
        }

        // From every location, get the deepest block from the current location and save it to current location y
        for (location in locations) {
            val block = location.block;
            YCheck@for (y in 0 downTo -shockwaveHeight.toInt()) {
                val currentBlock = block.getRelative(0, y, 0);
                if (currentBlock.type != Material.AIR) {
                    location.y = currentBlock.y.toDouble();
                    break@YCheck;
                }
            }
        }

        // From every location, start from the maximum height and go down to the lowest block, if there is a block around it, explode
        for (location in locations) {
            for (y in shockwaveHeight.toInt() downTo location.y.toInt()) {
                val currentBlock = location.block.getRelative(0, y, 0);
                // Check in every direction if there is a block
                var solidBlock: Boolean = false;
                if (currentBlock.getRelative(1, 0, 0).type != Material.AIR) {
                    solidBlock = true;
                }
                if (currentBlock.getRelative(-1, 0, 0).type != Material.AIR) {
                    solidBlock = true;
                }
                if (currentBlock.getRelative(0, 0, 1).type != Material.AIR) {
                    solidBlock = true;
                }
                if (currentBlock.getRelative(0, 0, -1).type != Material.AIR) {
                    solidBlock = true;
                }
                if (currentBlock.getRelative(0, -1, 0).type != Material.AIR) {
                    solidBlock = true;
                }

                if (solidBlock) {
                    // Create an explosion at the current location
                    currentBlock.world.createExplosion(currentBlock.location, 8.0f, true, true);
                }


            }
        }



    }
}
