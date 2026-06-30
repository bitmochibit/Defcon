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

package me.mochibit.defcon.utils

import me.mochibit.defcon.Defcon
import net.kyori.adventure.text.minimessage.MiniMessage

object Logger {
    fun interface PluginLogger {
        fun log(message: String)
    }

    private fun PluginLogger.withPrefix(level: LogLevel) = PluginLogger {
        val prefix = level.getPrefix()
        log("$prefix $it")
    }

    enum class LogLevel {
        INFO,
        WARNING,
        ERROR,
        DEBUG;

        fun getPrefix(): String {
            return when (this) {
                INFO -> "<blue><b>INFO</b></blue> "
                WARNING -> "<yellow><b>WARN</b></yellow> "
                ERROR -> "<red><b>ERROR</b></red> "
                DEBUG -> "<light_purple><b>DEBUG</b></light_purple> "
            }
        }
    }

    private val miniMessage = MiniMessage.miniMessage()

    private val pluginLogger = PluginLogger {
        Defcon.componentLogger.info(miniMessage.deserialize(it))
    }

    fun info(message: String) {
        pluginLogger.withPrefix(LogLevel.INFO).log(message)
    }

    fun warn(message: String) {
        pluginLogger.withPrefix(LogLevel.WARNING).log(message)
    }

    fun err(message: String) {
        pluginLogger.withPrefix(LogLevel.ERROR).log(message)
    }

    fun debug(message: String) {
        pluginLogger.withPrefix(LogLevel.DEBUG).log(message)
    }
}