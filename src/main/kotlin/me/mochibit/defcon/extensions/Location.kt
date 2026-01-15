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

import com.jeff_media.customblockdata.CustomBlockData
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.pluginNamespacedKey
import me.mochibit.defcon.utils.lerp
import org.bukkit.Location
import org.joml.Vector3d
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.roundToInt

object PluginLocationPropertyKeys {
    val customBlockId = StringProperty(pluginNamespacedKey("custom-block-id"))
    val itemId = StringProperty(pluginNamespacedKey("item-id"))
    val structureId = StringProperty(pluginNamespacedKey("structure-id"))
    val radiationAreaId = IntProperty(pluginNamespacedKey("radiation-area-id"))
    val radiationLevel = DoubleProperty(pluginNamespacedKey("radiation-level"))
}

fun Location.toVector3i(): Vector3i = Vector3i(x.roundToInt(), y.roundToInt(), z.roundToInt())

fun Location.distanceSquared(other: Vector3f): Double {
    val dx = x - other.x
    val dy = y - other.y
    val dz = z - other.z
    return dx * dx + dy * dy + dz * dz
}

fun Location.toVector3f(): Vector3f = Vector3f(x.toFloat(), y.toFloat(), z.toFloat())

fun Location.toVector3d(): Vector3d = Vector3d(x, y, z)

fun Location.lerp(
    other: Location,
    t: Double,
): Location =
    Location(
        world,
        lerp(x, other.x, t),
        lerp(y, other.y, t),
        lerp(z, other.z, t),
        lerp(yaw, other.yaw, t),
        lerp(pitch, other.pitch, t),
    )

fun Location.toChunkCoordinate(): Vector3i {
    // Convert world coordinates to chunk coordinates
    return Vector3i((blockX shr 4), 0, (blockZ shr 4))
}

fun Location.toLocalChunkCoordinate(): Vector3i {
    // Convert world coordinates to local chunk coordinates (0-15 range)
    return Vector3i((blockX and 15), blockY, (blockZ and 15))
}

// Extension functions for setting/getting data on locations via CustomBlockData
fun <T : Any> Location.setData(
    property: DataProperty<T>,
    value: T,
) {
    val customBlockData = CustomBlockData(this.block, Defcon)
    customBlockData.setData(property, value)
}

fun <T : Any> Location.getData(property: DataProperty<T>): T? {
    val customBlockData = CustomBlockData(this.block, Defcon)
    return customBlockData.getData(property)
}

fun <T : Any> Location.hasData(property: DataProperty<T>): Boolean {
    val customBlockData = CustomBlockData(this.block, Defcon)
    return customBlockData.hasData(property)
}

fun <T : Any> Location.removeData(property: DataProperty<T>) {
    val customBlockData = CustomBlockData(this.block, Defcon)
    customBlockData.removeData(property)
}

// Convenience functions for specific properties
fun Location.getCustomBlockId(): String? = getData(PluginLocationPropertyKeys.customBlockId)

fun Location.getItemId(): String? = getData(PluginLocationPropertyKeys.itemId)

fun Location.getStructureId(): String? = getData(PluginLocationPropertyKeys.structureId)

fun Location.getRadiationAreaId(): Int? = getData(PluginLocationPropertyKeys.radiationAreaId)

fun Location.getRadiationLevel(): Double? = getData(PluginLocationPropertyKeys.radiationLevel)
