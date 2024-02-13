package com.mochibit.nuclearcraft.explosives

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.NuclearCraft.Companion.Logger
import com.mochibit.nuclearcraft.threading.jobs.ScheduledCompositionJob
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import com.mochibit.nuclearcraft.utils.Geometry
import com.mochibit.nuclearcraft.utils.Math
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace

class ShockwaveColumn(val location: Location, val maxDeltaHeight: Double, val radiusGroup: Int) {
    // Clamped to the world height limit
    private val maxHeight: Double = Math.clamp(
        location.y + maxDeltaHeight,
        location.world.minHeight.toDouble(),
        location.world.maxHeight.toDouble()-1
    );
    private val minHeight: Double = Geometry.getMinY(location, maxDeltaHeight).y;

    fun explode() {
        Logger.info("Exploding column at $location, maxHeight: $maxHeight, minHeight: $minHeight");
        for (y in maxHeight.toInt() downTo minHeight.toInt()) {
            val block = location.clone().set(location.x, y.toDouble(), location.z).block;

            for (blockFace in BlockFace.entries) {
                if (blockFace == BlockFace.UP)
                    continue;

                val relative = block.getRelative(blockFace);
                if (relative.type == Material.AIR)
                    continue;

                relative.world.createExplosion(block.location, 4f, true, true)
            }
        }
    }

}