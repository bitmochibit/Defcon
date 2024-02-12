package com.mochibit.nuclearcraft.explosives

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.threading.jobs.ScheduledCompositionJob
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import com.mochibit.nuclearcraft.utils.Geometry
import com.mochibit.nuclearcraft.utils.Math
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.block.BlockFace

class ShockwaveColumn(val location: Location, maxDeltaHeight: Double, val radiusGroup: Int) {
    // Clamped to the world height limit
    private val maxHeight: Double = Math.clamp(
        location.y + maxDeltaHeight,
        location.world.minHeight.toDouble(),
        location.world.maxHeight.toDouble()
    );
    private val minHeight: Double = Geometry.getMinY(location.clone(), maxDeltaHeight).y;

    fun explode() {
        for (y in maxHeight.toInt() downTo minHeight.toInt()) {
            val block = Location(location.world, location.x, y.toDouble(), location.z).block;

            for (blockFace in BlockFace.entries) {
                val relative = block.getRelative(blockFace);
                if (relative.type == Material.AIR)
                    continue;

                relative.world.createExplosion(block.location, 4f, true, true)
            }
        }
    }

}