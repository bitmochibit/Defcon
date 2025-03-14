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

package me.mochibit.defcon.utils

import org.bukkit.Material
import org.bukkit.World
import org.joml.Vector3i

class RayCaster(world: World) {
    private val chunkCache by lazy { ChunkCache.getInstance(world) }

    fun cachedRayTrace(location: Vector3i, maxDistance: Double = 200.0): Int {
        var currentDepth = 0
        var currentY = location.y
        while (currentDepth < maxDistance) {
            val blockType = chunkCache.getBlockMaterial(location.x, currentY, location.z)

            if (blockType != Material.AIR) {
                return currentY
            } else {
                currentY--
            }

            currentDepth++
        }
        return currentY
    }
}