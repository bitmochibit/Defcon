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
    val maxAliveTick = 20 * 60 * 3;

    override fun drawRate(): Int {
        return 1;
    }
    private val nuclearMushroom = NuclearMushroom(nuclearComponent, center)
    override fun draw() {
        nuclearMushroom.emit()
    }
    override fun animate(delta: Double) {
        nuclearMushroom.processEffects(delta, tickAlive)
        if (tickAlive > maxAliveTick)
            this.destroy();
    }

//    private fun stretchCondensationCloud(delta: Double) {
//        if (tickAlive % 40 != 0) return
//        val condensationCloudBuilder = (condensationCloud.shapeBuilder as SphereBuilder)
//
//        val currentRadiusXZ = condensationCloudBuilder.getRadiusXZ()
//        if (currentRadiusXZ > 150) return
//
//        val currentRadiusY = condensationCloudBuilder.getRadiusY()
//
//        condensationCloudBuilder.withRadiusXZ(currentRadiusXZ + 15.0)
//        condensationCloudBuilder.withRadiusY(currentRadiusY + 20.0)
//        condensationCloud.buildAndAssign()
//    }



    override fun start() {
        nuclearMushroom.buildShape()
    }

    override fun stop() {
        // Nothing to do here
    }

    fun stripesHeight(value: Double): Boolean {
        // Every 10 blocks show 20 blocks of stripes
        return value % 20 < 10;
    }

    fun stripesWidth(value: Double): Boolean {
        // Every 5 blocks show 1 blocks of stripes
        return floor(value) % 5 < 1;
    }




}