package me.mochibit.defcon.foundation.services

import net.minecraft.world.entity.Entity
import net.minecraft.world.level.LevelAccessor
import net.minecraft.world.level.chunk.ChunkAccess
import net.neoforged.neoforge.common.NeoForge
import net.neoforged.neoforge.event.level.ChunkEvent
import net.neoforged.neoforge.event.tick.EntityTickEvent
import net.neoforged.neoforge.event.tick.LevelTickEvent

class NeoforgeEventService: EventService {
    override fun onChunkLoad(listener: (level: LevelAccessor, chunk: ChunkAccess, isNewChunk: Boolean) -> Unit) {
        NeoForge.EVENT_BUS.addListener<ChunkEvent.Load> { e ->
            listener(e.level, e.chunk, e.isNewChunk)
        }
    }

    override fun onLevelTick(listener: (level: LevelAccessor) -> Unit) {
        NeoForge.EVENT_BUS.addListener<LevelTickEvent.Post> { e ->
            listener(e.level)
        }
    }

    override fun onEntityTick(listener: (entity: Entity) -> Unit) {
        NeoForge.EVENT_BUS.addListener<EntityTickEvent.Post> { event ->
            listener(event.entity)
        }
    }
}

