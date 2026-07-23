package me.mochibit.defcon.content.explosion

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import net.minecraft.core.HolderLookup
import net.minecraft.nbt.CompoundTag
import net.minecraft.nbt.ListTag
import net.minecraft.nbt.Tag
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.saveddata.SavedData
import java.util.UUID

data class BlastZone(
    val id: UUID,
    val centerX: Int,
    val centerY: Int,
    val centerZ: Int,
    val radius: Int,
    val radiusStart: Int = 0,
    val shockwaveHeight: Int = 200,
)
class BlastZoneSavedData : SavedData() {
    val zones = mutableListOf<BlastZone>()
    private val worldgenProcessedChunks = HashMap<UUID, LongOpenHashSet>()
    private val pendingWorldgenChunks = HashMap<UUID, LongOpenHashSet>()

    private val lock = Any()

    fun addZone(zone: BlastZone) {
        synchronized(lock) {
            zones.add(zone)
            worldgenProcessedChunks.getOrPut(zone.id) { LongOpenHashSet() }
            pendingWorldgenChunks.getOrPut(zone.id) { LongOpenHashSet() }
        }
        setDirty()
    }

    fun markChunkPending(zoneId: UUID, chunkX: Int, chunkZ: Int) {
        synchronized(lock) {
            if (!isChunkWorldgenProcessed(zoneId, chunkX, chunkZ)) {
                pendingWorldgenChunks.getOrPut(zoneId) { LongOpenHashSet() }
                    .add(ChunkPos.asLong(chunkX, chunkZ))
            }
        }
    }

    fun markChunkWorldgenProcessed(zoneId: UUID, chunkX: Int, chunkZ: Int) {
        val key = ChunkPos.asLong(chunkX, chunkZ)
        synchronized(lock) {
            pendingWorldgenChunks[zoneId]?.remove(key)
            worldgenProcessedChunks.getOrPut(zoneId) { LongOpenHashSet() }.add(key)
        }
        setDirty()
    }

    fun isChunkWorldgenProcessed(zoneId: UUID, chunkX: Int, chunkZ: Int): Boolean =
        synchronized(lock) {
            worldgenProcessedChunks[zoneId]?.contains(ChunkPos.asLong(chunkX, chunkZ)) == true
        }

    fun pendingChunkSnapshot(zoneId: UUID): LongArray =
        synchronized(lock) { pendingWorldgenChunks[zoneId]?.toLongArray() ?: LongArray(0) }

    fun removePending(zoneId: UUID, key: Long) {
        synchronized(lock) { pendingWorldgenChunks[zoneId]?.remove(key) }
    }

    fun zoneSnapshot(): List<BlastZone> = synchronized(lock) { zones.toList() }

    fun zonesForChunk(
        chunkX: Int,
        chunkZ: Int,
    ): List<BlastZone> {
        val minX = chunkX shl 4; val minZ = chunkZ shl 4
        val maxX = minX + 15; val maxZ = minZ + 15
        val snapshot = synchronized(lock) { zones.toList() }
        return snapshot.filter { zone ->
            val closestX = zone.centerX.coerceIn(minX, maxX)
            val closestZ = zone.centerZ.coerceIn(minZ, maxZ)
            val dx = (closestX - zone.centerX).toDouble()
            val dz = (closestZ - zone.centerZ).toDouble()
            val effectiveRadius = zone.radius - zone.radiusStart
            dx * dx + dz * dz <= effectiveRadius * effectiveRadius
        }
    }

    override fun save(tag: CompoundTag, registries: HolderLookup.Provider): CompoundTag {
        val zonesSnapshot = synchronized(lock) { zones.toList() }
        val processedSnapshot = synchronized(lock) { worldgenProcessedChunks.mapValues { it.value.toLongArray() } }
        val pendingSnapshot = synchronized(lock) { pendingWorldgenChunks.mapValues { it.value.toLongArray() } }

        val list = ListTag()
        zonesSnapshot.forEach { zone ->
            val zoneTag = CompoundTag()
            zoneTag.putUUID("id", zone.id)
            zoneTag.putInt("x", zone.centerX)
            zoneTag.putInt("y", zone.centerY)
            zoneTag.putInt("z", zone.centerZ)
            zoneTag.putInt("r", zone.radius)
            zoneTag.putInt("rs", zone.radiusStart)
            zoneTag.putInt("h", zone.shockwaveHeight)

            processedSnapshot[zone.id]?.let { zoneTag.putLongArray("processed_chunks", it) }
            pendingSnapshot[zone.id]?.let { zoneTag.putLongArray("pending_chunks", it) }

            list.add(zoneTag)
        }
        tag.put("zones", list)

        return tag
    }

    companion object {
        private const val ID = "defcon_blast_zones"

        private fun load(tag: CompoundTag, registries: HolderLookup.Provider): BlastZoneSavedData {
            val data = BlastZoneSavedData()
            val list = tag.getList("zones", Tag.TAG_COMPOUND.toInt())

            for (i in list.indices) {
                val zoneTag = list.getCompound(i)

                val id = if (zoneTag.hasUUID("id")) zoneTag.getUUID("id") else UUID.randomUUID()

                val zone = BlastZone(
                    id,
                    zoneTag.getInt("x"),
                    zoneTag.getInt("y"),
                    zoneTag.getInt("z"),
                    zoneTag.getInt("r"),
                    zoneTag.getInt("rs"),
                    zoneTag.getInt("h"),
                )
                data.zones.add(zone)

                val processed = LongOpenHashSet()
                if (zoneTag.contains("processed_chunks")) {
                    processed.addAll(zoneTag.getLongArray("processed_chunks").asList())
                }
                data.worldgenProcessedChunks[id] = processed

                val pending = LongOpenHashSet()
                if (zoneTag.contains("pending_chunks")) {
                    pending.addAll(zoneTag.getLongArray("pending_chunks").asList())
                }
                data.pendingWorldgenChunks[id] = pending
            }

            return data
        }

        private val factory = Factory(::BlastZoneSavedData, ::load, null)

        @JvmStatic
        fun get(level: ServerLevel): BlastZoneSavedData =
            level.dataStorage.computeIfAbsent(factory, ID)
    }
}