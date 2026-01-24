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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.biomes.definitions.BurningAirBiome
import me.mochibit.defcon.biomes.definitions.NuclearFalloutBiome
import me.mochibit.defcon.config.MainConfiguration
import me.mochibit.defcon.config.PluginConfiguration
import me.mochibit.defcon.effects.explosion.generic.ShockwaveEffect
import me.mochibit.defcon.effects.explosion.nuclear.CondensationCloudVFX
import me.mochibit.defcon.effects.explosion.nuclear.NuclearExplosionVFX
import me.mochibit.defcon.effects.explosion.nuclear.NuclearFogVFX
import me.mochibit.defcon.explosions.ExplosionComponent
import me.mochibit.defcon.explosions.effects.BlindFlashEffect
import me.mochibit.defcon.explosions.processor.Crater
import me.mochibit.defcon.explosions.processor.EntityShockwave
import me.mochibit.defcon.explosions.processor.ExplosionSoundManager
import me.mochibit.defcon.explosions.processor.Shockwave
import me.mochibit.defcon.explosions.processor.ThermalRadiationBurn
import me.mochibit.defcon.threading.scheduling.runLater
import org.bukkit.Location
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

class NuclearExplosion(
    center: Location,
    private val nuclearComponent: ExplosionComponent = ExplosionComponent(),
) : Explosion(center) {
    @OptIn(ExperimentalTime::class)
    override fun explode() {
        Defcon.launch {
            val pluginConfiguration = MainConfiguration.getSchema()
            // VFX
            val nuclearExplosion = NuclearExplosionVFX(nuclearComponent, center)
            val condensationCloud = CondensationCloudVFX(nuclearComponent, center)
            val nuclearFog = NuclearFogVFX(nuclearComponent, center)
            val shockwaveEffect =
                ShockwaveEffect(
                    center,
                    pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseRadius,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius,
                    50f,
                )

            nuclearExplosion.instantiate()
            nuclearFog.instantiate()
            condensationCloud.instantiate()
            shockwaveEffect.instantiate()

            launch(Dispatchers.IO) {
                val duration = 10.seconds
                val blindEffect =
                    BlindFlashEffect(
                        center,
                        pluginConfiguration.nuclearExplosionConfig.flashConfig.baseRadius,
                        200,
                        duration,
                    )
                blindEffect.start()

                val thermalRadiationBurn =
                    ThermalRadiationBurn(
                        center,
                        pluginConfiguration.nuclearExplosionConfig.thermalConfig.baseRadius,
                        duration = 30.seconds,
                    )
                thermalRadiationBurn.start()
            }

            launch(Dispatchers.IO) {
                val players = center.world.players

                for (player in players) {
                    val playerDistance = player.location.distance(center)

                    if (playerDistance < pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseRadius) {
                        ExplosionSoundManager.startRepeatingSounds(
                            ExplosionSoundManager.DefaultSounds.LargeExplosionWindBackground,
                            player,
                            2.minutes,
                            6.seconds,
                        )
                    }
                }

                ExplosionSoundManager.playSoundsWithDelay(
                    ExplosionSoundManager.DefaultSounds.DistantExplosion,
                    players,
                    center,
                    pluginConfiguration.nuclearExplosionConfig.soundConfig.speed
                        .toFloat(),
                )
            }

            if (pluginConfiguration.nuclearExplosionConfig.biomeHandling) {
                launch(Dispatchers.Default) {
                    val falloutRadius = pluginConfiguration.nuclearExplosionConfig.falloutConfig.baseRadius
                    val craterRadius = pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius
                    CustomBiomeHandler.createBiomeArea(
                        center,
                        BurningAirBiome,
                        lengthPositiveY = falloutRadius,
                        lengthNegativeY = craterRadius / 6 + 10,
                        lengthNegativeX = falloutRadius,
                        lengthNegativeZ = falloutRadius,
                        lengthPositiveX = falloutRadius,
                        lengthPositiveZ = falloutRadius,
                        priority = 100,
                        transitions =
                            listOf(
                                CustomBiomeHandler.CustomBiomeBoundary.BiomeTransition(
                                    1.minutes,
                                    NuclearFalloutBiome.key,
                                ),
                            ),
                    )
                }
            }
//
//            runLater(1.minutes, Dispatchers.Default) {
//                RadiationAreaFactory.fromCenter(
//                    center.toVector3i(), center.world, 5.0, 20000,
//                    Vector3i(
//                        falloutRadius,
//                        falloutSpreadAir,
//                        falloutRadius
//                    ),
//                    Vector3i(
//                        -falloutRadius,
//                        -falloutSpreadUnderground,
//                        -falloutRadius
//                    ),
//                )
//            }
//
            launch(Dispatchers.Default) {
                EntityShockwave(
                    center,
                    pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseHeight,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius / 6,
                    pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseRadius,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius / 6,
                    50f,
                ).process()
            }

            launch(Dispatchers.Default) {
                val players = center.world.players
                // Kill all the players within the crater radius instantly
                for (player in players) {
                    val playerDistance = player.location.distance(center)
                    if (playerDistance < pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius) {
                        withContext(Defcon.minecraftDispatcher) {
                            player.damage(1000.0)
                        }
                    }
                }

                Crater(
                    center,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseDepth,
                    pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius,
                ).create()

                val shockwaveJob =
                    Shockwave(
                        center,
                        pluginConfiguration.nuclearExplosionConfig.craterConfig.baseRadius,
                        pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseRadius,
                        pluginConfiguration.nuclearExplosionConfig.shockwaveConfig.baseHeight,
                    ).explode()

                shockwaveJob.join()
            }
        }
    }
}
