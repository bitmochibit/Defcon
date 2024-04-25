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

inline fun <reified T> Location.getBlockData(key: BlockDataKey): T? {
    return MetaManager.getBlockData(this, key)
}