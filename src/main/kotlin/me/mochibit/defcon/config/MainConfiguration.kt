/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.config

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

object MainConfiguration : PluginConfiguration<MainConfiguration.BaseConfiguration>("config") {
    @Serializable
    data class BaseConfiguration(
        @SerialName("nuclear-explosion-settings")
        val nuclearExplosionConfig: NuclearExplosionConfig,
        @SerialName("pack-generator")
        val packGenerator: PackGenerator? = null,
    ) {
        // Computed property to ensure resourcePackConfig is always available
        val resourcePackConfig: ResourcePackConfig
            get() =
                packGenerator?.let { packGen ->
                    ResourcePackConfig(
                        enabled = packGen.resourcePack.enabled,
                        serverPort = packGen.resourcePack.serverPort,
                        fallbackResourceInteger =
                            ResourcePackConfig.FallbackResourceInteger(
                                packGen.packFormatFallback.resourcePack,
                            ),
                        fallbackDatapackInteger =
                            ResourcePackConfig.FallbackDatapackInteger(
                                packGen.packFormatFallback.dataPack,
                            ),
                    )
                } ?: ResourcePackConfig(
                    enabled = true,
                    serverPort = 8000,
                    fallbackResourceInteger = ResourcePackConfig.FallbackResourceInteger(46),
                    fallbackDatapackInteger = ResourcePackConfig.FallbackDatapackInteger(71),
                )

        @Serializable
        data class PackGenerator(
            @SerialName("resource-pack")
            val resourcePack: ResourcePackSettings,
            @SerialName("pack-format-fallback")
            val packFormatFallback: PackFormatFallback,
        ) {
            @Serializable
            data class ResourcePackSettings(
                @SerialName("automatic-generation")
                val enabled: Boolean = true,
                @SerialName("resource-server-port")
                val serverPort: Int = 8000,
            )

            @Serializable
            data class PackFormatFallback(
                @SerialName("resource-pack")
                val resourcePack: Int = 46,
                @SerialName("data-pack")
                val dataPack: Int = 71,
            )
        }

        @Serializable
        data class ResourcePackConfig(
            val enabled: Boolean,
            val serverPort: Int,
            val fallbackResourceInteger: FallbackResourceInteger,
            val fallbackDatapackInteger: FallbackDatapackInteger,
        ) {
            @Serializable
            @JvmInline
            value class FallbackResourceInteger(
                val value: Int,
            )

            @Serializable
            @JvmInline
            value class FallbackDatapackInteger(
                val value: Int,
            )
        }

        @Serializable
        data class NuclearExplosionConfig(
            @SerialName("biome-handling")
            val biomeHandling: Boolean = true,
            @SerialName("shockwave-config")
            val shockwaveConfig: ShockwaveConfig,
            @SerialName("crater-config")
            val craterConfig: CraterConfig,
            @SerialName("fallout-config")
            val falloutConfig: FalloutConfig,
            @SerialName("flash-config")
            val flashConfig: FlashConfig,
            @SerialName("thermal-config")
            val thermalConfig: ThermalConfig,
            @SerialName("sound-config")
            val soundConfig: SoundConfig,
        ) {
            @Serializable
            data class ShockwaveConfig(
                @SerialName("base-radius")
                val baseRadius: Int,
                @SerialName("base-height")
                val baseHeight: Int,
            )

            @Serializable
            data class CraterConfig(
                @SerialName("base-radius")
                val baseRadius: Int,
            )

            @Serializable
            data class FalloutConfig(
                @SerialName("base-radius")
                val baseRadius: Int,
                @SerialName("base-spread-height")
                val baseSpreadHeight: Int,
                @SerialName("base-underground-spread-depth")
                val baseSpreadDepth: Int,
            )

            @Serializable
            data class FlashConfig(
                @SerialName("base-radius")
                val baseRadius: Int,
            )

            @Serializable
            data class ThermalConfig(
                @SerialName("base-radius")
                val baseRadius: Int,
            )

            @Serializable
            data class SoundConfig(
                @SerialName("sound-speed")
                val speed: Int,
            )
        }
    }

    override suspend fun loadSchema(): BaseConfiguration {
        val configText = readConfigFile()
        return json.decodeFromString<BaseConfiguration>(configText)
    }

    override suspend fun cleanupSchema() {}
}
