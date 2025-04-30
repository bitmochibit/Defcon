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

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.teleport.RelativeFlag
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerPlayerPositionAndLook
import me.mochibit.defcon.threading.scheduling.interval
import org.bukkit.entity.Player
import java.io.Closeable
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random.Default.nextInt
import kotlin.time.Duration.Companion.milliseconds

data class CameraShakeOptions(
    val magnitude: Float,
    val decay: Float,
    val pitchPeriod: Float,
    val yawPeriod: Float,
)

class CameraShake(private val player: Player, options: CameraShakeOptions) : Closeable {
    private var time = 0
    private var magnitude = options.magnitude
    private var prevPitch = .0
    private var prevYaw = .0

    private val relativeFlag = RelativeFlag.X
        .or(RelativeFlag.Y)
        .or(RelativeFlag.Z)
        .or(RelativeFlag.DELTA_X)
        .or(RelativeFlag.DELTA_Y)
        .or(RelativeFlag.DELTA_Z)
        .or(RelativeFlag.PITCH)
        .or(RelativeFlag.YAW)
        .or(RelativeFlag.ROTATE_DELTA)


    private val repeat: Closeable = interval(50.milliseconds) {
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

        // Packet api since Paper relative TP stopped working
        val packet = WrapperPlayServerPlayerPositionAndLook(
            .0, .0, .0,
            relativeYaw.toFloat(),
            relativePitch.toFloat(),
            relativeFlag.mask,
            nextInt(),
            true
        )

        PacketEvents.getAPI().playerManager.sendPacket(player, packet)

    }

    override fun close() {
        repeat.close()
    }
}