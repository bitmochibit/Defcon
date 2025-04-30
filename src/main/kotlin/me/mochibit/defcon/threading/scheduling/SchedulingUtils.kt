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

import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.mochibit.defcon.Defcon
import org.bukkit.scheduler.BukkitTask
import java.io.Closeable
import java.util.concurrent.Future
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

private val plugin = Defcon.instance

fun <T> runSyncMethod(task: () -> T): Future<T> {
    return plugin.server.scheduler.callSyncMethod(plugin, task)
}

fun intervalWithTask(delay: Long, period: Long, task: (BukkitTask) -> Unit) {
    plugin.server.scheduler.runTaskTimer(plugin, task, delay, period)
}

fun intervalAsyncWithTask(delay: Long, period: Long, task: (BukkitTask) -> Unit) {
    plugin.server.scheduler.runTaskTimerAsynchronously(plugin, task, delay, period)
}

fun interval(period: Duration, delay: Duration = 0.seconds, task: suspend () -> Unit): Closeable {
    val job = plugin.launch {
        delay(delay)
        while (isActive) {
            task()
            delay(period)
        }
    }
    return Closeable { job.cancel() }
}

fun intervalAsync(
    period: Duration, delay: Duration = 0.seconds ,task: suspend () -> Unit,
): Closeable {
    val job = plugin.launch(plugin.asyncDispatcher) {
        delay(delay)
        while (isActive) {
            task()
            delay(period)
        }
    }
    return Closeable { job.cancel() }
}

fun runLater (
    delay: Duration, task: suspend () -> Unit,
): Closeable {
    val job = plugin.launch {
        delay(delay)
        task()
    }
    return Closeable { job.cancel() }
}

fun runLaterAsync(
    delay: Duration, task: suspend () -> Unit,
): Closeable {
    val job = plugin.launch(plugin.asyncDispatcher) {
        delay(delay)
        task()
    }
    return Closeable { job.cancel() }
}
