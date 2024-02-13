package com.mochibit.nuclearcraft.explosives

import com.mochibit.nuclearcraft.utils.Geometry
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace

class ShockwaveColumn(
    val location: Location,
    private val maxDeltaHeight: Double,
    val radiusGroup: Int,
    private val shockwave: Shockwave
) {
    // Clamped to the world height limit
    private val minHeight: Double =
        Geometry.getMinY(location.clone().add(0.0, maxDeltaHeight, 0.0), maxDeltaHeight * 2).y;

    // Make the power start from the maximum 8f and decrease evenly with the radius to a minimum of 4f
    private val explosionPower = 8f - (radiusGroup * 4f / shockwave.shockwaveRadius).toFloat();

    fun explode() {
;
        for (y in (location.y + maxDeltaHeight).toInt()
            .coerceAtMost(location.world.maxHeight - 1) downTo minHeight.toInt()) {
            val clonedLocation = location.clone().set(location.x, y.toDouble(), location.z);
            val block = clonedLocation.block;

            faceExplosion@ for (blockFace in BlockFace.entries) {
                if (blockFace == BlockFace.UP)
                    continue;

                val relative = block.getRelative(blockFace);
                if (relative.type == Material.AIR)
                    continue;

                replaceBlocks(block.location, explosionPower.toInt())

                relative.world.createExplosion(relative.location, explosionPower, true, true)
                break@faceExplosion;
            }

        }

    }

    // This function replaces the blocks around the explosion with deepslate blocks to simulate burnt blocks
    private fun replaceBlocks(location: Location, radius: Int) {

        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val distance = (x * x + y*y + z * z);
                    if (distance <= radius * radius) {
                        val block = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block;
                        if (block.type == Material.AIR || block.type == Material.WATER || block.type == Material.LAVA || block.type == Material.DEEPSLATE)
                            continue;

                        block.type = Material.DEEPSLATE;
                    }
                }
            }

        }
    }

}