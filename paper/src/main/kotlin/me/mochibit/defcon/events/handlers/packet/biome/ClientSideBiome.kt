/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.events.handlers.packet.biome

import com.github.retrooper.packetevents.event.PacketListener
import com.github.retrooper.packetevents.event.PacketSendEvent
import com.github.retrooper.packetevents.protocol.packettype.PacketType
import com.github.retrooper.packetevents.protocol.world.chunk.impl.v_1_18.Chunk_v1_18
import com.github.retrooper.packetevents.resources.ResourceLocation
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerChunkData
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.biomes.CustomBiomeHandler
import me.mochibit.defcon.events.AutoRegisterHandler

@AutoRegisterHandler
class ClientSideBiome : PacketListener {
    override fun onPacketSend(event: PacketSendEvent) {
        try {
            if (event.packetType != PacketType.Play.Server.CHUNK_DATA) return

            // Check if the plugin is still enabled to avoid "zip file closed" errors
            if (!Defcon.isEnabled) return

            val user = event.user
            val player = Defcon.server.getPlayer(user.uuid)
            if (player == null || !player.isOnline) {
                // Player is offline, skip processing
                return
            }
            val playerBiomes = CustomBiomeHandler.getPlayerVisibleBiomes(user.uuid, player.world.name)
            if (playerBiomes.isEmpty()) return

            // Get the biome boundary for the player (with the highest priority)
            val biomeBoundary = playerBiomes.maxByOrNull { it.priority } ?: return

            val chunkData = WrapperPlayServerChunkData(event)
            val chunkX = chunkData.column.x
            val chunkZ = chunkData.column.z

            if (!biomeBoundary.intersectsChunk(chunkX, chunkZ)) return

            // Check if this chunk is even within the biome boundary area
            // Each chunk is 16x16 blocks, so calculate chunk boundaries
            val chunkMinX = chunkX * 16
            val chunkMinZ = chunkZ * 16

            // Get the biome element from registry
            val element =
                user
                    .getRegistry(ResourceLocation.minecraft("worldgen/biome"))
                    ?.getByName(biomeBoundary.biome.toString()) ?: return
            val biomeId = element.getId(user.clientVersion)

            val minSection = user.minWorldHeight / 16 // Minimum section Y index

            // Set biome data for each chunk section within boundaries
            for ((sectionIndex, chunk) in chunkData.column.chunks.withIndex()) {
                if (chunk !is Chunk_v1_18) {
                    continue // Skip incompatible chunks
                }

                // Calculate the actual Y coordinate for this section
                val sectionY = minSection + sectionIndex
                val sectionMinY = sectionY * 16
                val sectionMaxY = sectionMinY + 15

                // Skip if outside vertical boundaries
                if (sectionMaxY < biomeBoundary.minY || sectionMinY > biomeBoundary.maxY) {
                    continue
                }

                // For biome data, each index represents a 4x4x4 cube of blocks
                for (x in 0 until 4) {
                    val worldX = chunkMinX + (x * 4)
                    if (worldX + 3 < biomeBoundary.minX || worldX > biomeBoundary.maxX) continue

                    for (z in 0 until 4) {
                        val worldZ = chunkMinZ + (z * 4)
                        if (worldZ + 3 < biomeBoundary.minZ || worldZ > biomeBoundary.maxZ) continue

                        for (y in 0 until 4) {
                            val worldY = sectionMinY + (y * 4)
                            if (worldY + 3 < biomeBoundary.minY || worldY > biomeBoundary.maxY) continue

                            // This 4x4x4 cube intersects with our biome area, set it
                            chunk.biomeData.set(x, y, z, biomeId)
                        }
                    }
                }
            }

            // Mark the packet for re-encoding since we modified it
            event.markForReEncode(true)
        } catch (e: IllegalStateException) {
            // Handle "zip file closed" or other state errors silently during plugin shutdown
            if (e.message?.contains("zip file closed") == true) {
                // Plugin is being disabled, ignore this packet
                return
            }
            throw e
        } catch (e: Exception) {
            // Log unexpected errors but don't crash the packet handler
            me.mochibit.defcon.utils.Logger
                .err("Error processing biome packet: ${e.message}")
        }
    }
}
