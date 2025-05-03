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

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.classes.PluginConfiguration
import me.mochibit.defcon.enums.ConfigurationStorage
import org.bukkit.Bukkit

object FormatReader {
    private val DEFAULT_PACK_FORMATS = mapOf(
        "1.20.2" to PackFormat(18, 18),
        "1.20.3" to PackFormat(18, 22),
        "1.20.4" to PackFormat(18, 22),
        "1.20.5" to PackFormat(18, 32),
        "1.21.0" to PackFormat(48, 34),
        "1.21.4" to PackFormat(61, 46),
        "1.21.5" to PackFormat(71, 46),
    )

    val packFormat: PackFormat by lazy { readPackFormat() }


    private fun readPackFormat(): PackFormat {
        val serverVersion = Bukkit.getBukkitVersion().split("-")[0]
        if (DEFAULT_PACK_FORMATS.containsKey(serverVersion)) {
            return DEFAULT_PACK_FORMATS[serverVersion] ?: PackFormat(0, 0)
        }

        val pluginConfiguration = PluginConfiguration.get(ConfigurationStorage.Config).config

        val resourcePackFallback = pluginConfiguration.getInt("pack_format_fallback.resource_pack")
        val datapackFallback = pluginConfiguration.getInt("pack_format_fallback.resource_pack")
        return PackFormat(datapackFallback, resourcePackFallback)
    }
}

data class PackFormat(
    val dataVersion: Int,
    val resourceVersion: Int
)