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

package com.mochibit.defcon.particles

import com.mochibit.defcon.particles.templates.DisplayParticleProperties
import com.mochibit.defcon.particles.templates.GenericParticleProperties

import org.bukkit.Location
import org.bukkit.entity.Player
import org.joml.Vector3f
import java.util.*
import kotlin.reflect.KClass

interface ParticleAdapter {
    fun summon(location: Vector3f, players: List<Player>, displayID: Int, displayUUID: UUID)
    fun summon(location: Vector3f, particleProperties: GenericParticleProperties, players: List<Player>, displayID: Int, displayUUID: UUID)
    fun remove(displayID: Int, players: List<Player>)
    fun updatePosition(displayID: Int, newLocation: Vector3f, players: List<Player>)

    fun setMotionTime(displayID: Int, time: Int, players: List<Player>)
}