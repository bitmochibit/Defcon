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

package com.mochibit.defcon.extensions

import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.MathFunctions
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location

fun Location.toVector3(): Vector3 {
    return Vector3(x, y, z)
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

fun Location.toChunkCoordinate(): Vector3 {
    return Vector3(chunk.x.toDouble(), .0, chunk.z.toDouble())
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