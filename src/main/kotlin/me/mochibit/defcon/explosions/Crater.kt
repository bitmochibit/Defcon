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

package me.mochibit.defcon.explosions

import org.bukkit.Location
import org.bukkit.Material
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Crater(val center: Location, private val radius: Int) {
    fun create() {
        /*TODO:
         * Add noise to the crater
         * Add burnt blocks to the perimeter
         * Increase the sphere destruction height dynamically (based on the floor, force and center)
         */
        val world = center.world
        val centerX = center.x.roundToInt()
        val centerY = center.y.roundToInt()
        val centerZ = center.z.roundToInt()

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared <= radius*radius) {
                        BlockChanger.addBlockChange(
                            world.getBlockAt(centerX + x, centerY + y, centerZ + z),
                            Material.AIR
                        )
                    }
                }
            }
        }
    }
}