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
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.effects.nuclear.CondensationCloudVFX
import me.mochibit.defcon.effects.nuclear.NuclearExplosionVFX
import me.mochibit.defcon.effects.nuclear.NuclearFogVFX
import me.mochibit.defcon.enums.ConfigurationStorage
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
import org.joml.Vector3i
import java.time.Instant
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds


class NuclearExplosion(center: Location, private val nuclearComponent: ExplosionComponent = ExplosionComponent()) :
    Explosion(center) {
    override fun explode() {
        Defcon.instance.launch {
            val pluginConfiguration = PluginConfiguration.get(ConfigurationStorage.Config).config

            val configShockwave = pluginConfiguration.getInt("nuke_shockwave_radius")
            val configShockwaveHeight = pluginConfiguration.getInt("nuke_shockwave_height")
            val configCrater = pluginConfiguration.getInt("nuke_crater_radius")
            val configFallout = pluginConfiguration.getInt("nuke_fallout_radius")
            val configFlashReach = pluginConfiguration.getInt("nuke_flash_radius")
            val configThermalRadius = pluginConfiguration.getInt("nuke_thermal_radius")
            val configSoundSpeed = pluginConfiguration.getInt("nuke_sound_speed")

            val configFalloutSpreadAir = pluginConfiguration.getInt("nuke_fallout_spread_air")
            val configFalloutSpreadUnderground = pluginConfiguration.getInt("nuke_fallout_spread_underground")

            val shockwaveRadius = if (configShockwave <= 0) 800 else configShockwave
            val shockwaveHeight = if (configShockwave <= 0) 300 else configShockwaveHeight
            val craterRadius = if (configCrater <= 0) 180 else configCrater
            val falloutRadius = if (configFallout <= 0) 1600 else configFallout
            val flashReach = if (configFlashReach <= 0) 1000 else configFlashReach
            val thermalRadius = if (configThermalRadius <= 0) 1000 else configThermalRadius
            val soundSpeed = if (configSoundSpeed <= 0) 50 else configSoundSpeed

            val falloutSpreadAir = if (configFalloutSpreadAir <= 0) 150 else configFalloutSpreadAir
            val falloutSpreadUnderground =
                if (configFalloutSpreadUnderground <= 0) 30 else configFalloutSpreadUnderground

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
                    soundSpeed.toFloat(),
                )
            }

            launch(Dispatchers.Default) {
                val burningBiomeUUID = CustomBiomeHandler.createBiomeArea(
                    center,
                    BurningAirBiome,
                    lengthPositiveY = falloutRadius,
                    lengthNegativeY = craterRadius / 6,
                    lengthNegativeX = falloutRadius,
                    lengthNegativeZ = falloutRadius,
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
                RadiationAreaFactory.fromCenter(
                    center.toVector3i(), center.world, 5.0, 20000,
                    Vector3i(
                        falloutRadius,
                        falloutSpreadAir,
                        falloutRadius
                    ),
                    Vector3i(
                        -falloutRadius,
                        -falloutSpreadUnderground,
                        -falloutRadius
                    ),
                )
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
                    effectiveRadius - 1,
                    shockwaveRadius,
                    shockwaveHeight,
                ).explode()
                shockwaveJob.join()
            }
        }

    }

}
