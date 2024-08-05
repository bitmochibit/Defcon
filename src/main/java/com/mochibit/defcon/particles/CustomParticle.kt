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

import org.bukkit.Bukkit
import org.bukkit.Location

abstract class CustomParticle(properties: DisplayParticleProperties) : AbstractParticle(properties) {
    override fun spawnParticle(location: Location) {
        DisplayItemAsyncHandler(location.clone(), Bukkit.getOnlinePlayers(), particleProperties as DisplayParticleProperties)
            .initialVelocity(initialVelocity)
            .damping(initialDamping)
            .acceleration(initialAcceleration)
            .accelerationTicks(initialAccelerationTicks)
            .summon()
    }
}