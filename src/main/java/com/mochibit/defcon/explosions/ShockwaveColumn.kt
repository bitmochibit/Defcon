package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.utils.Geometry
import org.bukkit.Bukkit
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import kotlin.math.abs
import kotlin.random.Random

class ShockwaveColumn(
    val location: Location,
    private val explosionPower: Float,
    private val radiusGroup: Int,
    private val shockwave: Shockwave,
    chunkSnapshot: ChunkSnapshot
) : Comparable<ShockwaveColumn> {

    companion object {

    }

    val minHeight: Location = Geometry.getMinYUsingSnapshot(
        location.clone().add(0.0, shockwave.shockwaveHeight, 0.0),
        shockwave.shockwaveHeight * 2,
        chunkSnapshot
    )

    fun explode() {

    }

    private fun placeAirBlocksAroundLocation(location: Location, radius: Double) {
        for (x in -radius.toInt()..radius.toInt()) {
            for (y in -radius.toInt()..radius.toInt()) {
                for (z in -radius.toInt()..radius.toInt()) {
                    if ((x * x + y * y + z * z).toDouble() <= radius*radius && Math.random() <= 0.5) {
                        val block = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block
                        if (block.type.isSolid) {
                            block.type = org.bukkit.Material.AIR
                        }
                    }

                }
            }
        }
    }

    override fun compareTo(other: ShockwaveColumn): Int {
        return this.radiusGroup.compareTo(other.radiusGroup)
    }
}
