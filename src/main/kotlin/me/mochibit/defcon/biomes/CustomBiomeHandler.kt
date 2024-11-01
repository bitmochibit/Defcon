package me.mochibit.defcon.biomes

import com.comphenix.protocol.PacketType
import com.comphenix.protocol.ProtocolLib
import com.comphenix.protocol.ProtocolLibrary
import com.comphenix.protocol.events.PacketContainer
import com.comphenix.protocol.wrappers.ChunkCoordIntPair
import com.comphenix.protocol.wrappers.WrappedLevelChunkData
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.threading.jobs.SimpleCompositionJob
import me.mochibit.defcon.threading.runnables.ScheduledRunnable
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.World
import org.bukkit.block.Biome
import org.bukkit.entity.Player

class CustomBiomeHandler {
    companion object {
        private val UNSAFE = Bukkit.getUnsafe()
        private const val CHUNK_SIZE = 16
        fun setCustomBiome(chunk: Chunk, biome: CustomBiome) {
            val syncRunnable = ScheduledRunnable().maxMillisPerTick(2.5)
            val task = Bukkit.getScheduler().runTaskTimerAsynchronously(Defcon.instance, syncRunnable, 0L, 20L)

            Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
                val minX = chunk.x shl 4
                val maxX = minX + CHUNK_SIZE

                val minZ = chunk.z shl 4
                val maxZ = minZ + CHUNK_SIZE

                val key = biome.biomeKey
                val maxHeight = chunk.world.maxHeight

                for (x in minX until maxX step 4) {
                    for (z in minZ until maxZ step 4) {
                        syncRunnable.addWorkload(SimpleCompositionJob(key) {
                            for (offsetX in 0 until 4) {
                                for (offsetZ in 0 until 4) {
                                    for (y in 55 until maxHeight) {
                                        // Set the biome of each block to the custom biome
                                        UNSAFE.setBiomeKey(chunk.world, x + offsetX, y, z + offsetZ, key)
                                    }
                                }
                            }
                        })
                    }
                }

                if (chunk.isLoaded) {
                    refreshChunkAsync(chunk)
                }
            })

            Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
                task.cancel()
            }, 20L * 120L)
        }

        private fun refreshChunkAsync(chunk: Chunk) {
            val players = Bukkit.getOnlinePlayers()
            val protocolManager = ProtocolLibrary.getProtocolManager()
            val chunkCoord = ChunkCoordIntPair(chunk.x, chunk.z)

            players.forEach { player ->
                Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {sendChunkDataPacket(protocolManager, player, chunkCoord)})
            }
        }
        private fun sendChunkDataPacket(
            protocolManager: com.comphenix.protocol.ProtocolManager,
            player: Player,
            chunkCoord: ChunkCoordIntPair
        ) {
            val packet = PacketContainer(PacketType.Play.Server.MAP_CHUNK)
            packet.integers.write(0, chunkCoord.chunkX)
            packet.integers.write(1, chunkCoord.chunkZ)

            try {
                protocolManager.sendServerPacket(player, packet)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

    }
}



