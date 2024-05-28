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

package com.mochibit.defcon.biomes

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.threading.jobs.SimpleCompositionJob
import org.bukkit.*



class CustomBiomeHandler {
    companion object {
        private val UNSAFE = Bukkit.getUnsafe()
        fun setCustomBiome(chunk: Chunk, biome: CustomBiome) {

            val minX = chunk.x shl 4
            val maxX = minX + 16

            val minZ = chunk.z shl 4
            val maxZ = minZ + 16

            val key = biome.biomeKey

            Defcon.instance.scheduledRunnable.addWorkload(
                SimpleCompositionJob(key) {
                    for (x in minX until maxX) {
                        for (y in 55 until chunk.world.maxHeight) {
                            for (z in minZ until maxZ) {
                                // Set the biome of each block to the definitions biome
                                UNSAFE.setBiomeKey(chunk.world, x, y, z, key)
                            }
                        }
                    }

                    // Update the chunk to reflect the changes
                    if (chunk.isLoaded)
                        chunk.world.refreshChunk(chunk.x, chunk.z)
                }
            );


        }
    }
}



