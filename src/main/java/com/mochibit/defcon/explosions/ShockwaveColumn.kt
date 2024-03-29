package com.mochibit.defcon.explosions

import com.mochibit.defcon.utils.Geometry
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace
import kotlin.math.abs

class ShockwaveColumn(
    val location: Location,
    private val explosionPower: Float,
    private val radiusGroup: Int,
    private val shockwave: Shockwave
) {
    // Clamped to the world height limit
    private val minHeight: Double =
        Geometry.getMinY(location.clone().add(0.0, shockwave.shockwaveHeight, 0.0), shockwave.shockwaveHeight * 2).y;

    // Make the power start from the maximum 8f and decrease evenly with the radius to a minimum of 6f


    fun explode() {
        var lastExplodedY = -1000;
        val center = shockwave.center;
        val maxDeltaHeight = shockwave.shockwaveHeight;

        val direction = location.toVector().subtract(center.toVector()).normalize();

        for (y in (location.y + maxDeltaHeight).toInt()
            .coerceAtMost(location.world.maxHeight - 1) downTo minHeight.toInt()) {

            val currentYLocation = location.clone().set(location.x, y.toDouble(), location.z);
            // Keep the distance between the explosions of this column by 8 blocks
            if (abs(y - lastExplodedY) < 8)
                continue;

            // Get the closest axis to the direction vector
            val axis = if (abs(direction.x) > abs(direction.z)) BlockFace.EAST else BlockFace.SOUTH;

            // Get the blocks facing the axis and backwards
            val forwardBlock = currentYLocation.clone().add(axis.direction).block
            val backwardBlock = currentYLocation.clone().add(axis.direction.clone().multiply(-1)).block

            if (forwardBlock.type == Material.AIR &&
                backwardBlock.type == Material.AIR)
                continue;


            location.world.createExplosion(currentYLocation, explosionPower, true, true);
            replaceBlocks(currentYLocation, explosionPower.toInt() * 2);
            lastExplodedY = y;
        }

    }

    // This function replaces the blocks around the explosion with deepslate blocks to simulate burnt blocks
    private fun replaceBlocks(location: Location, radius: Int) {
        // Fast random sphere of radius for replacing blocks with 60% chance of replacing the block with deepslate
        for (i in 0 until 1000) {
            val x = (Math.random() * radius * 2 - radius).toInt();
            val y = (Math.random() * radius * 2 - radius).toInt();
            val z = (Math.random() * radius * 2 - radius).toInt();

            val distance = (x * x + y * y + z * z);
            if (distance <= radius * radius) {
                val block = location.clone().add(x.toDouble(), y.toDouble(), z.toDouble()).block;
                if (block.type == Material.AIR || block.type == Material.WATER || block.type == Material.LAVA || block.type == Material.DEEPSLATE)
                    continue;

                if (Math.random() < 0.6)
                    block.type = Material.DEEPSLATE;

            }
        }


    }

}