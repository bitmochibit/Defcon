package com.mochibit.defcon.biomes

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.SimpleCompositionJob
import org.bukkit.*
import org.bukkit.block.Block



class CustomBiomeHandler {
    companion object {
        private val UNSAFE = Bukkit.getUnsafe()
        fun setCustomBiome(chunk: Chunk, keyValue: String) {
            val key = NamespacedKey("defcon", keyValue)

            val minX = chunk.x shl 4
            val maxX = minX + 16

            val minZ = chunk.z shl 4
            val maxZ = minZ + 16

            for (x in minX until maxX) {
                for (y in 55 until chunk.world.maxHeight) {
                    for (z in minZ until maxZ) {
                        // Set the biome of each block to the custom biome
                        Defcon.instance.scheduledRunnable.addWorkload(
                            SimpleCompositionJob(key) {
                                UNSAFE.setBiomeKey(chunk.world, x, y, z, key)
                            }
                        );
                    }
                }
            }

            // Update the chunk to reflect the changes
            if (chunk.isLoaded)
                chunk.world.refreshChunk(chunk.x, chunk.z)

        }
    }
}



