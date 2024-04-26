package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.biomes.CustomBiomeHandler
import com.mochibit.defcon.biomes.definitions.BurningAirBiome
import com.mochibit.defcon.biomes.definitions.NuclearFalloutBiome
import com.mochibit.defcon.effects.NuclearExplosion
import com.mochibit.defcon.threading.jobs.SimpleCompositionJob
import com.mochibit.defcon.utils.FloodFill3D
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.Title.Times
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import java.time.Duration


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
        NuclearExplosion(center).instantiate(true);

        // Send to a nearby player the flash of the explosion (radius)
        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
            // Custom font shows a flash screen
            val title = Title.title(
                Component.text("\uE000"),
                Component.empty(),
                Times.times(Duration.ZERO, Duration.ofSeconds(2), Duration.ofSeconds(1))
            );

            player.showTitle(title);
        }

        // Send definitions explosion sounds to all players in the radius
        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
            // Play sound delayed to the distance
            val distance = player.location.distance(center);
            val soundSpeed = 50 // blocks per second
            val delayInSeconds = (distance / soundSpeed).toLong();
            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
                player.playSound(center, "minecraft:nuke.set_near", 1.0f, 1.0f);
                player.playSound(center, "minecraft:nuke.set_near_outer_rumble", 1.0f, 1.0f);
                player.playSound(center, "minecraft:nuke.set_near_outer_wind", 1.0f, 1.0f);
            }, delayInSeconds * 20);

            player.playSound(center, "minecraft:nuke.ground_rumble", 1.0f, 1.0f);
        }

        center.world.getNearbyPlayers(center,600.0).forEach { player ->
            val distance = player.location.distance(center);
            val soundSpeed = 50 // blocks per second
            val delayInSeconds = (distance / soundSpeed).toLong();
            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
                player.playSound(center, "minecraft:nuke.set_distant_outer", 1.0f, 1.0f);
            }, delayInSeconds * 20);
        }

        // Give fire damage to all entities in the radius of the thermal radiation (unless they are protected)
        // We will use ray-casting to check if the entity is in the radius of the thermal radiation
        val thermalRadius = nuclearComponent.thermalPower * 5;
        // TODO: Implement thermal radiation

        val shockwaveRadius = nuclearComponent.blastPower * 30 * 5;
        val shockwaveHeight = nuclearComponent.blastPower * 100 * 2;

        val falloutRadius = shockwaveRadius / 16


        // Get area of 10 chunks around the center
        for (x in -falloutRadius.toInt()..falloutRadius.toInt()) {
            for (z in -falloutRadius.toInt()..falloutRadius.toInt()) {
                val chunk = center.world.getChunkAt(center.chunk.x + x, center.chunk.z + z);
                CustomBiomeHandler.setCustomBiome(chunk, BurningAirBiome());
            }
        }

        // After 30 seconds, set the biomes to "nuclear_fallout"

        Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
            for (x in -falloutRadius.toInt()..falloutRadius.toInt()) {
                for (z in -falloutRadius.toInt()..falloutRadius.toInt()) {
                    val chunk = center.world.getChunkAt(center.chunk.x + x, center.chunk.z + z);
                    CustomBiomeHandler.setCustomBiome(chunk, NuclearFalloutBiome());
                }
            }
        }, 20 * 30);


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

        Defcon.Companion.Logger.info("Shockwave radius: $shockwaveRadius, Shockwave height: $shockwaveHeight");
        Shockwave(center, 0.0, shockwaveRadius.toDouble(), shockwaveHeight.toDouble()).explode();

    }

}