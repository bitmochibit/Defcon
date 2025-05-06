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
package me.mochibit.defcon.registers.packformat

import me.mochibit.defcon.Defcon.Logger.info
import me.mochibit.defcon.Defcon.Logger.warn
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.enums.ConfigurationStorage
import me.mochibit.defcon.utils.compareVersions
import me.mochibit.defcon.utils.versionGreaterOrEqualThan
import org.bukkit.Bukkit

/**
 * Handles detection and management of Minecraft pack formats for different versions.
 * Uses version ranges to efficiently represent pack format data.
 */
object FormatReader {
    /**
     * Represents a version range with associated pack format
     */
    private data class VersionRange(
        val minVersion: String,
        val maxVersion: String,
        val packFormat: PackFormat
    )

    /**
     * Pack format data organized in version ranges
     * Ranges are defined from oldest to newest for proper traversal
     */
    private val PACK_FORMAT_RANGES = listOf(
        VersionRange("1.20.2", "1.20.2", PackFormat(18, 18)),
        VersionRange("1.20.3", "1.20.4", PackFormat(18, 22)),
        VersionRange("1.20.5", "1.20.6", PackFormat(18, 32)),
        VersionRange("1.21.0", "1.21.3", PackFormat(48, 34)),
        VersionRange("1.21.4", "1.21.4", PackFormat(61, 46)),
        VersionRange("1.21.5", "1.21.5", PackFormat(71, 46))
    )

    /**
     * The detected pack format for the current server version.
     * Lazily initialized on first access.
     */
    val packFormat: PackFormat by lazy { detectPackFormat() }

    /**
     * Gets the appropriate pack format for a specific version
     */
    fun getPackFormatForVersion(version: String): PackFormat? {
        val range = findVersionRange(version)
        return range?.packFormat
    }

    /**
     * Finds the appropriate version range for a given version
     */
    private fun findVersionRange(version: String): VersionRange? {
        return PACK_FORMAT_RANGES.find { range ->
            compareVersions(version, range.minVersion) >= 0 &&
            compareVersions(version, range.maxVersion) <= 0
        }
    }

    /**
     * Detects the appropriate pack format based on server version
     */
    private fun detectPackFormat(): PackFormat {
        val serverVersion = Bukkit.getBukkitVersion().split("-")[0]
        info("Detecting pack format for Minecraft version: $serverVersion")

        // Find the matching version range
        val matchingRange = findVersionRange(serverVersion)

        if (matchingRange != null) {
            info("Found pack format for version $serverVersion: ${matchingRange.packFormat} " +
                 "(range: ${matchingRange.minVersion} to ${matchingRange.maxVersion})")
            return matchingRange.packFormat
        }

        // If no range matches, check if we're beyond the latest known version
        val latestRange = PACK_FORMAT_RANGES.last()
        if (compareVersions(serverVersion, latestRange.maxVersion) > 0) {
            warn("Server version $serverVersion is newer than latest known version ${latestRange.maxVersion}. " +
                 "Using latest known pack format as best guess: ${latestRange.packFormat}")
            return latestRange.packFormat
        }

        // Last resort: use fallback values from config
        val config = PluginConfiguration.get(ConfigurationStorage.Config).config
        val resourcePackFallback = config.getInt("pack_format_fallback.resource_pack")
        val datapackFallback = config.getInt("pack_format_fallback.datapack")

        warn("No suitable pack format found for $serverVersion. Using fallback values: " +
             "datapack=$datapackFallback, resource_pack=$resourcePackFallback")

        return PackFormat(datapackFallback, resourcePackFallback)
    }


}

/**
 * Data class representing pack format versions for datapacks and resource packs
 */
data class PackFormat(
    val dataVersion: Int,
    val resourceVersion: Int
)