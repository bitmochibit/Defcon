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

import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.biomes.definitions.BurningAirBiome
import me.mochibit.defcon.biomes.definitions.NuclearFalloutBiome
import me.mochibit.defcon.effects.nuclear.CondensationCloudVFX
import me.mochibit.defcon.effects.nuclear.NuclearExplosionVFX
import me.mochibit.defcon.effects.nuclear.NuclearFogVFX
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.explosions.TransformationRule
import me.mochibit.defcon.explosions.effects.BlindFlashEffect
import me.mochibit.defcon.explosions.processor.Crater
import me.mochibit.defcon.explosions.processor.ExplosionSoundManager
import me.mochibit.defcon.explosions.processor.Shockwave
import me.mochibit.defcon.explosions.processor.ThermalRadiationBurn
import me.mochibit.defcon.extensions.toVector3i
import me.mochibit.defcon.radiation.RadiationAreaFactory
import me.mochibit.defcon.threading.scheduling.runLaterAsync
import org.bukkit.Location
import java.time.Instant
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class NuclearExplosion(center: Location, private val nuclearComponent: ExplosionComponent = ExplosionComponent()) :
    Explosion(center) {
    override fun explode() {
        Defcon.instance.launch {
            val shockwaveRadius = nuclearComponent.blastPower * 800
            val shockwaveHeight = (nuclearComponent.blastPower * 100 * 3).roundToInt()
            val craterRadius = (shockwaveRadius / 2).roundToInt().coerceIn(20, 180)

            val falloutRadius = (shockwaveRadius * 2).roundToInt()

            val flashReach = (nuclearComponent.thermalPower * 1000).roundToInt()

            // VFX
            val nuclearExplosion = NuclearExplosionVFX(nuclearComponent, center)
            val condensationCloud = CondensationCloudVFX(nuclearComponent, center)
            val nuclearFog = NuclearFogVFX(nuclearComponent, center)

            nuclearExplosion.instantiate(async = true)
            nuclearFog.instantiate(async = true)
            condensationCloud.instantiate(async = true)

            launch(Dispatchers.IO) {
                val duration = 10.seconds
                val blindEffect = BlindFlashEffect(center, flashReach, 200, duration)
                blindEffect.start()

                val thermalRadius = (nuclearComponent.thermalPower * 1000).roundToInt()
                val thermalRadiationBurn = ThermalRadiationBurn(center, thermalRadius, duration = 30.seconds)
                thermalRadiationBurn.start()
            }

            launch(Dispatchers.IO) {
                val players = center.world.players

                for (player in players) {
                    val playerDistance = player.location.distance(center)

                    if (playerDistance < shockwaveRadius) {
                        ExplosionSoundManager.startRepeatingSounds(
                            ExplosionSoundManager.DefaultSounds.LargeExplosionWindBackground,
                            player,
                            2.minutes,
                            6.seconds
                        )
                    }
                }

                ExplosionSoundManager.playSoundsWithDelay(
                    ExplosionSoundManager.DefaultSounds.DistantExplosion,
                    players,
                    center,
                    50f,
                )
            }

            launch(Dispatchers.Default) {
                val burningBiomeUUID = CustomBiomeHandler.createBiomeArea(
                    center,
                    BurningAirBiome,
                    lengthPositiveY = falloutRadius,
                    lengthNegativeY = craterRadius / 6,
                    lengthNegativeX = falloutRadius,
                    lengthNegativeZ =  falloutRadius,
                    lengthPositiveX = falloutRadius,
                    lengthPositiveZ = falloutRadius,
                    priority = 100,
                )

                CustomBiomeHandler.scheduleBiomeTransition(
                    burningBiomeUUID,
                    NuclearFalloutBiome.key,
                    Instant.now().plusSeconds(1.minutes.inWholeSeconds),
                    0
                )
            }

            runLaterAsync(1.minutes) {
                RadiationAreaFactory.fromCenter(center.toVector3i(), center.world, 5.0, 20000)
            }

            launch(Dispatchers.Default) {
                val players = center.world.players
                // Kill all the players within the crater radius instantly
                for (player in players) {
                    val playerDistance = player.location.distance(center)
                    if (playerDistance < craterRadius) {
                        withContext(Defcon.instance.minecraftDispatcher) {
                            player.damage(1000.0)
                        }
                    }
                }

                val effectiveRadius = Crater(
                    center,
                    craterRadius,
                    craterRadius / 6,
                    craterRadius,
                    TransformationRule(),
                    shockwaveHeight
                ).create()
                val shockwaveJob = Shockwave(
                    center,
                    effectiveRadius-1,
                    shockwaveRadius.toInt(),
                    shockwaveHeight,
                ).explode()
                shockwaveJob.join()
            }
        }

    }

}
