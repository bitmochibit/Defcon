package me.mochibit.defcon.content.explosion.processor.worldgen

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.saveddata.SavedData
import kotlin.math.pow

data class BlastZone(val centerX: Int, val centerZ: Int, val radius: Int)

class BlastZoneSavedData : SavedData() {
    val zones = mutableListOf<BlastZone>()

    fun addZone(zone: BlastZone) {
        zones.add(zone)
        setDirty()
    }

    private val worldgenProcessedChunks = LongOpenHashSet()

    fun markChunkWorldgenProcessed(chunkX: Int, chunkZ: Int) {
        worldgenProcessedChunks.add(ChunkPos.asLong(chunkX, chunkZ))
        setDirty()
    }

    fun isChunkWorldgenProcessed(chunkX: Int, chunkZ: Int): Boolean =
        worldgenProcessedChunks.contains(ChunkPos.asLong(chunkX, chunkZ))


    fun zoneForChunk(
        chunkX: Int,
        chunkZ: Int,
        marginFor: (BlastZone) -> Double = { 0.0 },
    ): BlastZone? {
        val minX = chunkX shl 4
        val minZ = chunkZ shl 4
        val maxX = minX + 15
        val maxZ = minZ + 15
        return zones.firstOrNull { zone ->
            val closestX = zone.centerX.coerceIn(minX, maxX)
            val closestZ = zone.centerZ.coerceIn(minZ, maxZ)
            val dx = (closestX - zone.centerX).toDouble()
            val dz = (closestZ - zone.centerZ).toDouble()
            val effectiveRadius = zone.radius + marginFor(zone)
            dx * dx + dz * dz <= effectiveRadius * effectiveRadius
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val list = ListTag()
        zones.forEach { zone ->
            val zoneTag = CompoundTag()
            zoneTag.putInt("x", zone.centerX)
            zoneTag.putInt("z", zone.centerZ)
            zoneTag.putInt("r", zone.radius)
            list.add(zoneTag)
        }
        tag.put("zones", list)

        tag.putLongArray("worldgen_processed_chunks", worldgenProcessedChunks.toLongArray())

        return tag
    }

    companion object {
        private const val ID = "defcon_blast_zones"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): BlastZoneSavedData {
            val data = BlastZoneSavedData()
            val list = tag.getList("zones", Tag.TAG_COMPOUND.toInt())
            for (i in 0 until list.size) {
                val zoneTag = list.getCompound(i)
                data.zones.add(BlastZone(zoneTag.getInt("x"), zoneTag.getInt("z"), zoneTag.getInt("r")))
            }

            if (tag.contains("worldgen_processed_chunks")) {
                data.worldgenProcessedChunks.addAll(tag.getLongArray("worldgen_processed_chunks").asList())
            }

            return data
        }

        private val factory = Factory(::BlastZoneSavedData, ::load, null)

        @JvmStatic
        fun get(level: ServerLevel): BlastZoneSavedData =
            level.dataStorage.computeIfAbsent(factory, ID)
    }
}