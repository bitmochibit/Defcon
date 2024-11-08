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

package me.mochibit.defcon.threading.scheduling

import me.mochibit.defcon.Defcon
import java.io.Closeable

private val plugin = Defcon.instance

fun runLater(delay: Long, task: () -> Unit) : Closeable {
    val handler = plugin.server.scheduler.runTaskLater(plugin, task, delay)
    return Closeable { handler.cancel() }
}

fun interval(delay: Long, period: Long, task: () -> Unit) : Closeable {
    val handler = plugin.server.scheduler.runTaskTimer(plugin, task, delay, period)
    return Closeable { handler.cancel() }
}