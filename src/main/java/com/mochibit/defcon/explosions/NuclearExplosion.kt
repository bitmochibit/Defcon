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
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import net.kyori.adventure.text.Component
import net.kyori.adventure.title.Title
import net.kyori.adventure.title.Title.Times
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.util.BlockIterator
import java.time.Duration
import kotlin.concurrent.thread


class NuclearExplosion(private val center: Location, private val nuclearComponent: NuclearComponent) : Explosion() {

    override fun explode() {
//        // Send to a nearby player the flash of the explosion (radius)
//        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
//            val playerLocation = player.location.add(0.0, 1.0, 0.0)
//            val direction = playerLocation.clone().subtract(center).toVector().normalize()
//            val blockIterator =
//                BlockIterator(center.world, center.toVector(), direction, 0.0, 180)
//
//            // check if block iterator reaches the entity, if it does, apply fire damage
//            while (blockIterator.hasNext()) {
//                // if the block isn't transparent, isn't passable and it is solid, then break the loop
//                val block = blockIterator.next()
//                if (block.type.isOccluding && block.type.isSolid) {
//                    break
//                }
//
//                if (block.location.distanceSquared(player.location) < 1.0) {
//                    val title = Title.title(
//                        Component.text("\uE000"),
//                        Component.empty(),
//                        Times.times(Duration.ZERO, Duration.ofSeconds(6), Duration.ofSeconds(10))
//                    )
//                    val angle = player.eyeLocation.direction.angle(direction.multiply(-1.0))
//                    info("Angle: $angle")
//                    if (angle < 1.74) {
//                        player.showTitle(title)
//                    }
//                    break
//                }
//            }
//        }
//
//        // Send definitions explosion sounds to all players in the radius
//        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
//            // Play sound delayed to the distance
//            val distance = player.location.distance(center)
//            val soundSpeed = 50 // blocks per second
//            val delayInSeconds = (distance / soundSpeed).toLong()
//            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
//                player.playSound(center, "minecraft:nuke.set_near", 1.0f, 1.0f)
//                player.playSound(center, "minecraft:nuke.set_near_outer_rumble", 1.0f, 1.0f)
//                player.playSound(center, "minecraft:nuke.set_near_outer_wind", 1.0f, 1.0f)
//            }, delayInSeconds * 20)
//
//            player.playSound(center, "minecraft:nuke.ground_rumble", 1.0f, 1.0f)
//        }
//
//        center.world.getNearbyPlayers(center, 600.0).forEach { player ->
//            val distance = player.location.distance(center)
//            val soundSpeed = 50 // blocks per second
//            val delayInSeconds = (distance / soundSpeed).toLong()
//            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {
//                player.playSound(center, "minecraft:nuke.set_distant_outer", 1.0f, 1.0f)
//            }, delayInSeconds * 20)
//        }
//
//        // Give fire damage to all entities in the radius of the thermal radiation (unless they are protected)
//        // We will use ray-casting to check if the entity is in the radius of the thermal radiation
//        val thermalRadius = nuclearComponent.thermalPower * 30 * 10
//
//        // For 10 seconds, send the thermal radiation damage
//        // TODO: REFACTOR
//        var secondsElapsed = 0
//        Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, { task ->
//            secondsElapsed++
//            if (secondsElapsed >= 10) {
//                task.cancel()
//                return@runTaskTimerAsynchronously
//            }
//            Bukkit.getScheduler().runTask(Defcon.instance, Runnable {
//                center.world.getNearbyEntities(
//                    center, thermalRadius.toDouble(),
//                    thermalRadius.toDouble(), thermalRadius.toDouble()
//                ).forEach { entity ->
//                    if (entity is org.bukkit.entity.LivingEntity) {
//
//                        val direction = entity.location.subtract(center).toVector().normalize()
//                        val blockIterator =
//                            BlockIterator(center.world, center.toVector(), direction, 0.0, thermalRadius.toInt())
//
//                        // check if block iterator reaches the entity, if it does, apply fire damage
//                        while (blockIterator.hasNext()) {
//                            // if the block isn't transparent, isn't passable and it is solid, then break the loop
//                            val block = blockIterator.next()
//                            if (block.type.isOccluding && block.type.isSolid) {
//                                break
//                            }
//
//                            if (block.location.distanceSquared(entity.location) < 1.0) {
//                                entity.setFireTicks(20 * 30)
//                                break
//                            }
//                        }
//                    }
//                }
//            })
//            // After 10 seconds, cancel the task
//
//        }, 0, 20)
//
        val shockwaveRadius = nuclearComponent.blastPower * 600
        val shockwaveHeight = nuclearComponent.blastPower * 100 * 2

        val falloutRadius = shockwaveRadius / 16

        thread(name = "Nuclear Explosion Thread") {
            val shockwave = Shockwave(center, 0.0, shockwaveRadius.toDouble(), shockwaveHeight.toDouble())
            val nuclearExplosion = NuclearExplosionVFX(nuclearComponent, center)
            val condensationCloud = CondensationCloudVFX(nuclearComponent, center)
            val nuclearFog = NuclearFogVFX(nuclearComponent, center)

            shockwave.loadPromise()
                .thenCompose {
                    nuclearFog.loadPromise()
                }
                .thenCompose {
                    condensationCloud.loadPromise()
                }
                .thenCompose {
                    nuclearExplosion.loadPromise()
                }
                .thenAccept {
                    nuclearFog.instantiate(true)
                    condensationCloud.instantiate(true)
                    nuclearExplosion.instantiate(true)
                    shockwave.explode()
                }
                .exceptionally { ex ->
                    println("Error loading effects: ${ex.message}")
                    null
                }
        }

//        center.world.getNearbyPlayers(center, 300.0).forEach { player ->
//            val task = Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, Runnable
//            {
//                val playerEyeLocation = player.eyeLocation.clone()
//                // Get the direction of the player face
//                //val playerEyesDirection = player.facing.direction
//                val playerNukeDirection = center.clone().subtract(playerEyeLocation).toVector().normalize()
//                // Get 3 blocks away from the player in the direction of the nuke
//                val directionBlock = playerEyeLocation.add(playerNukeDirection.multiply(1.1))
//                val angle = playerEyeLocation.direction.angle(playerNukeDirection)
//                if (!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION)) {
//                    Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable{
//                        player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.NIGHT_VISION, 20*20, 255))
//                    }, 1L)
//                }
//
//
//                if (angle < 0.5) {
//                    if (!player.hasPotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS)) {
//                        Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable{
//                            player.addPotionEffect(org.bukkit.potion.PotionEffect(org.bukkit.potion.PotionEffectType.BLINDNESS, 20*5, 255))
//                        }, 1L)
//                    }
//
//                }
//
//                // Spawn the particle flash
//                player.spawnParticle(
//                    org.bukkit.Particle.FLASH,
//                    directionBlock,
//                    60,
//                    0.0,
//                    0.0,
//                    0.0,
//                    0.0
//                )
//            }, 0, 2L)
//            Bukkit.getScheduler().runTaskLater(Defcon.instance, Runnable {task.cancel() }, 20*20)
//
//        }
//
//
//         //Get area of 10 chunks around the center
//        //Set the biomes to "burning_air" (a custom biome that will be used to simulate the thermal radiation
//
//        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
//            RadiationAreaFactory.fromCenter(
//                center,
//                radLevel = 3.0,
//                20000
//            ).join()
//        }, 40 * 20)

//        val scheduledRunnable = ScheduledRunnable().maxMillisPerTick(2.5)
//        Bukkit.getScheduler().runTaskTimer(Defcon.instance, scheduledRunnable, 0L, 1L)
//
//        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
//            // Create a sphere of air blocks
//            val obliterationRadius = nuclearComponent.blastPower * 50
//            for (x in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
//                for (y in -obliterationRadius.toInt()/2..obliterationRadius.toInt()/2) {
//                    for (z in -obliterationRadius.toInt()..obliterationRadius.toInt()) {
//                        val distance = (x * x + y * y + z * z)
//                        if (distance <= obliterationRadius * obliterationRadius) {
//                            scheduledRunnable.addWorkload(SimpleSchedulable{
//                                val block = center.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
//                                if (block.type != Material.AIR)
//                                    block.type = Material.AIR
//                            })
//
//                        }
//                    }
//                }
//            }
//        })


    }

}