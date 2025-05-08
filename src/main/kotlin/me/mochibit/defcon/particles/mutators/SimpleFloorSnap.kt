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

package me.mochibit.defcon.particles.mutators

import org.bukkit.World
import org.joml.Vector3d
import kotlin.math.roundToInt

class SimpleFloorSnap(private val world: World): AbstractShapeMutator() {
    override fun mutateLoc(location: Vector3d) {
        location.y = world.getHighestBlockYAt(location.x.roundToInt(), location.z.roundToInt()).toDouble()
    }
}