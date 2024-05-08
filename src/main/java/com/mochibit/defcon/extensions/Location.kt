package com.mochibit.defcon.extensions

import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location

fun Location.toVector3(): Vector3 {
    return Vector3(x, y, z)
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