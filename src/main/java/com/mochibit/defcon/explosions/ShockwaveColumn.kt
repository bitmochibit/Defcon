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

package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.utils.Geometry
import org.bukkit.Bukkit
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import kotlin.math.abs
import kotlin.random.Random

class ShockwaveColumn(
    val location: Location,
    private val explosionPower: Float,
    private val radiusGroup: Int,
    private val shockwave: Shockwave
) : Comparable<ShockwaveColumn> {
    // Clamped to the world height limit
    private val minHeight: Double =
        Geometry.getMinYUsingSnapshot(
            location.clone().add(0.0, shockwave.shockwaveHeight, 0.0),
            shockwave.shockwaveHeight * 2
        ).y

    // Make the power start from the maximum 8f and decrease evenly with the radius to a minimum of 6f


    fun explode() {
        var lastExplodedY = -1000
        val center = shockwave.center
        val maxDeltaHeight = shockwave.shockwaveHeight
        val direction = location.toVector().subtract(center.toVector()).normalize()

        val maxY = (location.y + maxDeltaHeight).toInt().coerceAtMost(location.world.maxHeight - 1)
        val minY = minHeight.toInt()

        for (y in maxY downTo minY) {
            if (abs(y - lastExplodedY) < 8) continue

            val currentYLocation = location.clone().set(location.x, y.toDouble(), location.z)
            val axis = if (abs(direction.x) > abs(direction.z)) BlockFace.EAST else BlockFace.SOUTH

            val forwardBlock = currentYLocation.clone().add(axis.direction).block
            val backwardBlock = currentYLocation.clone().add(axis.direction.clone().multiply(-1)).block

            if (forwardBlock.type == Material.AIR && backwardBlock.type == Material.AIR) continue

            Bukkit.getScheduler().runTask(Defcon.instance, Runnable {
                location.world.createExplosion(currentYLocation.clone().add(0.0, 5.0, 0.0), explosionPower, true, true)
            })
            replaceBlocks(currentYLocation, explosionPower.toInt() * 2)
            killNearbyEntities(currentYLocation)
            lastExplodedY = y
        }
    }

    // This function replaces the blocks around the explosion with deepslate blocks to simulate burnt blocks

    private fun replaceBlocks(location: Location, radius: Int) {
        val random = Random.Default
        val radiusSquared = radius * radius

        for (i in 0 until 1000) {
            val x = random.nextInt(-radius, radius + 1)
            val y = random.nextInt(-radius, radius + 1)
            val z = random.nextInt(-radius, radius + 1)

            if (x * x + y * y + z * z <= radiusSquared) {
                val block = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                if (block.type == Material.AIR || block.type == Material.WATER || block.type == Material.LAVA || block.type == Material.DEEPSLATE) continue
                Bukkit.getScheduler().runTask(Defcon.instance, Runnable {
                    if (random.nextDouble() < 0.6) block.type = Material.DEEPSLATE
                })
            }
        }
    }

    private fun killNearbyEntities(location: Location) {
        val power = explosionPower.toInt() * 3
        Bukkit.getScheduler().runTask(Defcon.instance, Runnable {
            val nearbyEntities =
                location.world.getNearbyEntities(location, power.toDouble(), power.toDouble(), power.toDouble())
            for (entity in nearbyEntities) {
                if (entity is LivingEntity) {
                    entity.damage(10000.0)
                }
            }
        })
    }

    override fun compareTo(other: ShockwaveColumn): Int {
        return this.radiusGroup.compareTo(other.radiusGroup)
    }

}