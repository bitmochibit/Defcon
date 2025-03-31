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

package me.mochibit.defcon.explosions.effects

import io.papermc.paper.entity.TeleportFlag
import me.mochibit.defcon.threading.scheduling.interval
import org.bukkit.entity.Player
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin

data class CameraShakeOptions(
    val magnitude: Float,
    val decay: Float,
    val pitchPeriod: Float,
    val yawPeriod: Float,
)

class CameraShake(player: Player, options: CameraShakeOptions) : Closeable {
    private var time = 0
    private var prevPitch = .0
    private var prevYaw = .0

    private var magnitude = options.magnitude

    private val repeat: Closeable = interval(1, 1) {
        time += 1

        magnitude -= options.decay

        if (magnitude < 0) {
            close()
            return@interval
        }


        val pitch = sin(time.toDouble() / options.pitchPeriod * 2 * Math.PI) * magnitude
        val yaw = cos(time.toDouble() / options.yawPeriod * 2 * Math.PI) * magnitude

        val relativePitch = pitch - prevPitch
        val relativeYaw = yaw - prevYaw

        prevPitch = pitch
        prevYaw = yaw

        player.teleport(player.location.apply {
            setPitch(player.location.pitch + relativePitch.toFloat())
            setYaw(player.location.yaw + relativeYaw.toFloat())
        }, TeleportFlag.Relative.X, TeleportFlag.Relative.Y, TeleportFlag.Relative.Z, TeleportFlag.Relative.PITCH, TeleportFlag.Relative.YAW)
    }

    override fun close() {
        repeat.close()
    }
}