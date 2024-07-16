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

package com.mochibit.defcon.effects.nuclear

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.effects.AnimatedEffect
import com.mochibit.defcon.explosions.NuclearComponent
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import kotlin.math.floor

class NuclearExplosionVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect() {
    val maxAliveTick = 20 * 60 * 3

    override fun drawRate(): Double {
        return .5
    }
    private val nuclearMushroom = NuclearMushroom(nuclearComponent, center)
    private val condensationCloudVFX = CondensationCloudVFX(nuclearComponent, center)
    override fun draw() {
        nuclearMushroom.emit()
    }
    override fun animate(delta: Double) {
        nuclearMushroom.processEffects(delta, tickAlive)
        if (tickAlive > maxAliveTick)
            this.destroy()
    }

    override fun start() {
        nuclearMushroom.buildShape()
        condensationCloudVFX.instantiate(true)
    }

    override fun stop() {}
}