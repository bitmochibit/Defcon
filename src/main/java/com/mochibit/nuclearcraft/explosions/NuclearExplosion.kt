package com.mochibit.nuclearcraft.explosions

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.effects.NuclearMushroom
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import org.bukkit.Location
import org.bukkit.Material

class NuclearExplosion(private val center: Location, private val nuclearComponent: NuclearComponent) : Explosion() {

    override fun explode() {
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

        // Particle SFX
        NuclearMushroom(center).instantiate(true);

        // Create a sphere of air blocks
        val obliterationRadius = nuclearComponent.blastPower * 30;
        for (x in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
            for (y in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                for (z in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                    val distance = (x * x + y * y + z * z);
                    if (distance <= obliterationRadius * obliterationRadius) {
                        val block = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block;
                        if (block.type != Material.AIR)
                            block.type = Material.AIR;
                    }
                }
            }
        }

        // Give fire damage to all entities in the radius of the thermal radiation (unless they are protected)
        // We will use ray-casting to check if the entity is in the radius of the thermal radiation
        val thermalRadius = nuclearComponent.thermalPower * 5;
        // TODO: Implement thermal radiation


        val shockwaveRadius = nuclearComponent.blastPower * 30 * 5;
        val shockwaveHeight = nuclearComponent.blastPower * 100 * 2;

        NuclearCraft.Companion.Logger.info("Shockwave radius: $shockwaveRadius, Shockwave height: $shockwaveHeight");
        NuclearCraft.instance.scheduledRunnable.addWorkload(SimpleCompositionJob(shockwaveRadius) {
            //Shockwave(center, shockwaveRadius.toDouble(), shockwaveHeight.toDouble()).explode();
        });




    }

}