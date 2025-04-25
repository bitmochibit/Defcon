/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024-2025 mochibit.
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

package me.mochibit.defcon.explosions.types

import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.biomes.definitions.BurningAirBiome
import me.mochibit.defcon.effects.nuclear.CondensationCloudVFX
import me.mochibit.defcon.effects.nuclear.NuclearExplosionVFX
import me.mochibit.defcon.effects.nuclear.NuclearFogVFX
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.BlindFlashEffect
import me.mochibit.defcon.explosions.processor.Crater
import me.mochibit.defcon.explosions.processor.Shockwave
import org.bukkit.Location
import kotlin.math.roundToInt


class NuclearExplosion(center: Location, private val nuclearComponent: ExplosionComponent = ExplosionComponent()) :
    Explosion(center) {
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

        val shockwaveRadius = nuclearComponent.blastPower * 650
        val shockwaveHeight = (nuclearComponent.blastPower * 100 * 3).roundToInt()
        val craterRadius = (shockwaveRadius / 2.5).roundToInt().coerceIn(20, 150)

        val falloutRadius = (shockwaveRadius * 2).roundToInt()

        // VFX
//        val nuclearExplosion = NuclearExplosionVFX(nuclearComponent, center)
//        val condensationCloud = CondensationCloudVFX(nuclearComponent, center)
//        val nuclearFog = NuclearFogVFX(nuclearComponent, center)
//
//        nuclearExplosion.instantiate(async = true, useThreadPool = true)
//        nuclearFog.instantiate(async = true, useThreadPool = true)
//        condensationCloud.instantiate(async = true, useThreadPool = true)

        val duration = 60L * 20
        val blindEffect = BlindFlashEffect(center, 100, 200, duration)
        blindEffect.start(duration)

//        val shockwave = Shockwave(
//            center,
//            craterRadius / 2,
//            shockwaveRadius.toInt(),
//            shockwaveHeight,
//            radiusDestroyStart = craterRadius - 2
//        )
//
//        Crater(center, craterRadius, craterRadius / 6, craterRadius, TransformationRule(), shockwaveHeight).apply {
//            onComplete {
//                shockwave.explode()
//            }
//            create()
//        }
//
//
//        for (player in center.world.players) {
//            CustomBiomeHandler.setBiomeClientSide(
//                player.uniqueId,
//                center,
//                BurningAirBiome.asBukkitBiome,
//                falloutRadius,
//                20,
//                falloutRadius,
//                falloutRadius,
//                falloutRadius,
//                falloutRadius
//            )
//        }
    }

}
