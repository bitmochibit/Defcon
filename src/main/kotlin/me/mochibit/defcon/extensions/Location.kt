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

package me.mochibit.defcon.extensions

import me.mochibit.defcon.enums.BlockDataKey
import me.mochibit.defcon.utils.MathFunctions
import me.mochibit.defcon.utils.MetaManager
import org.bukkit.Location
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.roundToInt

fun Location.toVector3i(): Vector3i {
    return Vector3i(x.roundToInt(), y.roundToInt(), z.roundToInt())
}

fun Location.distanceSquared(other: Vector3f) : Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return dx * dx + dy * dy + dz * dz
}

fun Location.toVector3f(): Vector3f {
    return Vector3f(x.toFloat(), y.toFloat(), z.toFloat())
}

fun Location.toVector3d(): Vector3d {
    return Vector3d(x, y, z)
}

fun Location.lerp(other: Location, t: Double): Location {
    return Location(
        world,
        MathFunctions.lerp(x, other.x, t),
        MathFunctions.lerp(y, other.y, t),
        MathFunctions.lerp(z, other.z, t),
        MathFunctions.lerp(yaw, other.yaw, t),
        MathFunctions.lerp(pitch, other.pitch, t)
    )
}

fun Location.toChunkCoordinate(): Vector3i {
    // Convert world coordinates to chunk coordinates
    return Vector3i((blockX shr 4), 0, (blockZ shr 4))
}

fun Location.toLocalChunkCoordinate(): Vector3i{
    // Convert world coordinates to local chunk coordinates (0-15 range)
    return Vector3i((blockX and 15), blockY, (blockZ and 15))
}


fun Location.getCustomBlockId(): String? {
    return getBlockData<String>(BlockDataKey.CustomBlockId)
}

fun Location.getItemId(): String? {
    return getBlockData<String>(BlockDataKey.ItemId)
}

fun Location.getStructureId(): String? {
    return getBlockData<String>(BlockDataKey.StructureId)
}

fun Location.getRadiationAreaId(): Int? {
    return getBlockData<Int>(BlockDataKey.RadiationAreaId)
}

fun Location.getRadiationLevel(): Double? {
    return getBlockData<Double>(BlockDataKey.RadiationLevel)
}



inline fun <reified T> Location.getBlockData(key: BlockDataKey): T? {
    return MetaManager.getBlockData(this, key)
}