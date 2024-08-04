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
import com.mochibit.defcon.effects.nuclear.CondensationCloudVFX
import com.mochibit.defcon.effects.nuclear.NuclearExplosionVFX
import com.mochibit.defcon.effects.nuclear.NuclearFogVFX
import com.mochibit.defcon.radiation.RadiationAreaFactory
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.Title.Times
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.BlockIterator
import java.time.Duration


class NuclearExplosion(private val center: Location, private val nuclearComponent: NuclearComponent) : Explosion() {

    override fun explode() {
        //TODO: Make this async to precalculate stuff and make a more appealing explosion

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

        NuclearExplosionVFX(nuclearComponent, center).instantiate(true)
        NuclearFogVFX(nuclearComponent, center).instantiate(true)
        CondensationCloudVFX(nuclearComponent, center).instantiate(true)


        // Send to a nearby player the flash of the explosion (radius)
        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
            val playerLocation = player.location.add(0.0, 1.0, 0.0)
            val direction = playerLocation.clone().subtract(center).toVector().normalize()
            val blockIterator =
                BlockIterator(center.world, center.toVector(), direction, 0.0, 180)

            // check if block iterator reaches the entity, if it does, apply fire damage
            while (blockIterator.hasNext()) {
                // if the block isn't transparent, isn't passable and it is solid, then break the loop
                val block = blockIterator.next()
                if (block.type.isOccluding && block.type.isSolid) {
                    break
                }

                if (block.location.distanceSquared(player.location) < 1.0) {
                    val title = Title.title(
                        Component.text("\uE000"),
                        Component.empty(),
                        Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ofSeconds(10))
                    )
                    val angle = player.eyeLocation.direction.angle(direction.multiply(-1.0))
                    info("Angle: $angle")
                    if (angle < 1.74) {
                        player.showTitle(title)
                    }
                    break
                }
            }
        }

        // Send definitions explosion sounds to all players in the radius
        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
            // Play sound delayed to the distance
            val distance = player.location.distance(center)
            val soundSpeed = 50 // blocks per second
            val delayInSeconds = (distance / soundSpeed).toLong()
            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
                player.playSound(center, "minecraft:nuke.set_near", 1.0f, 1.0f)
                player.playSound(center, "minecraft:nuke.set_near_outer_rumble", 1.0f, 1.0f)
                player.playSound(center, "minecraft:nuke.set_near_outer_wind", 1.0f, 1.0f)
            }, delayInSeconds * 20)

            player.playSound(center, "minecraft:nuke.ground_rumble", 1.0f, 1.0f)
        }

        center.world.getNearbyPlayers(center, 600.0).forEach { player ->
            val distance = player.location.distance(center)
            val soundSpeed = 50 // blocks per second
            val delayInSeconds = (distance / soundSpeed).toLong()
            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
                player.playSound(center, "minecraft:nuke.set_distant_outer", 1.0f, 1.0f)
            }, delayInSeconds * 20)
        }

        // Give fire damage to all entities in the radius of the thermal radiation (unless they are protected)
        // We will use ray-casting to check if the entity is in the radius of the thermal radiation
        val thermalRadius = nuclearComponent.thermalPower * 30 * 10

        // For 10 seconds, send the thermal radiation damage
        // TODO: REFACTOR
        var secondsElapsed = 0
        Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, { task ->
            secondsElapsed++
            if (secondsElapsed >= 10) {
                task.cancel()
                return@runTaskTimerAsynchronously
            }
            Bukkit.getScheduler().runTask(Defcon.instance, Runnable {
                center.world.getNearbyEntities(
                    center, thermalRadius.toDouble(),
                    thermalRadius.toDouble(), thermalRadius.toDouble()
                ).forEach { entity ->
                    if (entity is org.bukkit.entity.LivingEntity) {

                        val direction = entity.location.subtract(center).toVector().normalize()
                        val blockIterator =
                            BlockIterator(center.world, center.toVector(), direction, 0.0, thermalRadius.toInt())

                        // check if block iterator reaches the entity, if it does, apply fire damage
                        while (blockIterator.hasNext()) {
                            // if the block isn't transparent, isn't passable and it is solid, then break the loop
                            val block = blockIterator.next()
                            if (block.type.isOccluding && block.type.isSolid) {
                                break
                            }

                            if (block.location.distanceSquared(entity.location) < 1.0) {
                                entity.setFireTicks(20 * 30)
                                break
                            }
                        }
                    }
                }
            })
            // After 10 seconds, cancel the task

        }, 0, 20)

        val shockwaveRadius = nuclearComponent.blastPower * 60 * 10
        val shockwaveHeight = nuclearComponent.blastPower * 100 * 2

        val falloutRadius = shockwaveRadius / 16


         //Get area of 10 chunks around the center
        //Set the biomes to "burning_air" (a custom biome that will be used to simulate the thermal radiation

        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
            RadiationAreaFactory.fromCenter(
                center,
                radLevel = 3.0,
                20000
            ).join()
        }, 40 * 20)

        // Create a sphere of air blocks
        val obliterationRadius = nuclearComponent.blastPower * 30
        for (x in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
            for (y in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                for (z in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
                    val distance = (x * x + y * y + z * z)
                    if (distance <= obliterationRadius * obliterationRadius) {
                        val block = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                        if (block.type != Material.AIR)
                            block.type = Material.AIR
                    }
                }
            }
        }


        Shockwave(center, 0.0, shockwaveRadius.toDouble(), shockwaveHeight.toDouble()).explode()
    }

}