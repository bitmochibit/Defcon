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

class ShockwaveColumn(val location: Location, private val maxDeltaHeight: Double, val radiusGroup: Int) {
    // Clamped to the world height limit
    private val minHeight: Double = Geometry.getMinY(location.clone().add(0.0, maxDeltaHeight, 0.0), maxDeltaHeight*2).y;

    fun explode() {
        for (y in (location.y+maxDeltaHeight).toInt().coerceAtMost(location.world.maxHeight - 1) downTo  minHeight.toInt()) {
            val block = location.clone().set(location.x, y.toDouble(), location.z).block;

            faceExplosion@for (blockFace in BlockFace.entries) {
                if (blockFace == BlockFace.UP)
                    continue;

                val relative = block.getRelative(blockFace);
                if (relative.type == Material.AIR)
                    continue;

                relative.world.createExplosion(relative.location, 4f, true, true)
                break@faceExplosion;
            }
        }

    }

}