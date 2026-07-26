package me.mochibit.defcon.foundation.services

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.chunk.ChunkAccess

interface EventService {
    fun onChunkLoad(
        listener: (level: LevelAccessor, chunk: ChunkAccess, isNewChunk: Boolean) -> Unit
    )

    fun onLevelTick(
        listener: (level: LevelAccessor) -> Unit
    )

    fun onEntityTick(
        listener: (entity: Entity) -> Unit
    )
}

val eventService: EventService by lazy { loadService<EventService>()}
