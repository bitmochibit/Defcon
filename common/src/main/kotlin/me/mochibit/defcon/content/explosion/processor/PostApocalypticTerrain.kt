package me.mochibit.defcon.content.explosion.processor

import me.mochibit.defcon.content.explosion.BlastActor
import me.mochibit.defcon.content.explosion.BlastZone
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.explosion.processor.Shockwave
import me.mochibit.defcon.foundation.async.modLaunch
import me.mochibit.defcon.foundation.eventbus.ModEventHandler
import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.services.eventService
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.sqrt
import kotlin.random.Random

object PostApocalypticTerrain : ModEventHandler {
    private val pending = ArrayDeque<Pair<ChunkPos, ServerLevel>>()

    fun apply(savedData: BlastZoneSavedData, level: ServerLevel, chunk: LevelChunk, zone: BlastZone) {

        val chunkPos = chunk.pos
        val chunkSeed = chunkPos.toLong() xor zone.id.leastSignificantBits xor 0x5DEECE66DL
        val transformer = MaterialTransformer(random = Random(chunkSeed))
        val seaLevelMinus3 = level.seaLevel - 3
        val worldMinHeight = chunk.minBuildHeight

        val chunkCtx = WorldGenColumnCarveContext(level, chunk, BlockPos(zone.centerX, zone.centerY, zone.centerZ))
        val effectiveShockwaveRange = (zone.radius - zone.radiusStart).toFloat()

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkPos.minBlockX + x
                val worldZ = chunkPos.minBlockZ + z

                val dx = (worldX - zone.centerX).toDouble()
                val dz = (worldZ - zone.centerZ).toDouble()
                val dist = sqrt(dx * dx + dz * dz).toFloat()

                val distanceFromCraterEdge = dist - zone.radiusStart
                val radiusProgress = distanceFromCraterEdge / effectiveShockwaveRange
                val explosionPower = Shockwave.calculateShockwavePower(radiusProgress)

                val surfaceY = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ)

                val firstState = chunk.getBlockState(BlockPos(worldX, surfaceY, worldZ))

                val randomOffset = transformer.random.nextInt(1, 6)
                val convertToAirMinY = (level.seaLevel + randomOffset) +
                        (zone.shockwaveHeight * 0.5f * (1.0f - explosionPower)).toInt()

                ColumnCarver.carveColumn(
                    x = worldX, topY = surfaceY, z = worldZ,
                    power = explosionPower,
                    convertToAirMinY = convertToAirMinY,
                    worldMinHeight = worldMinHeight,
                    seaLevelMinus3 = seaLevelMinus3,
                    materialTransformer = transformer,
                    firstBlockState = firstState,
                    ctx = chunkCtx,
                )
            }
        }
        chunkCtx.markChunkProcessedWhenFlushed(zone.id, chunkPos.x, chunkPos.z)
        chunkCtx.flushClientUpdates()
    }

    override fun setupEvents() {
        eventService.onChunkLoad { level, chunk, isNewChunk ->
            val serverLevel = level as? ServerLevel ?: return@onChunkLoad
            val savedData = BlastZoneSavedData.get(serverLevel)
            if (savedData.zonesForChunk(chunk.pos.x, chunk.pos.z).isEmpty()) return@onChunkLoad
            pending.add(chunk.pos to serverLevel)
        }

        eventService.onLevelTick { level ->
            if (level !is ServerLevel) return@onLevelTick
            var i = 0
            while (i < pending.size) {
                val (chunkPos, serverLevel) = pending[i]
                if (!serverLevel.chunkSource.hasChunk(chunkPos.x, chunkPos.z)) {
                    i++
                    continue
                }
                if (!serverLevel.chunkSource.isPositionTicking(chunkPos.toLong())) {
                    i++
                    continue
                }
                val chunk = serverLevel.getChunk(chunkPos.x, chunkPos.z)
                val savedData = BlastZoneSavedData.get(serverLevel)
                savedData.zonesForChunk(chunkPos.x, chunkPos.z)
                    .filterNot { savedData.isChunkWorldgenProcessedByAnyActor(it.id, chunkPos.x, chunkPos.z) }
                    .forEach { zone -> apply(savedData, serverLevel, chunk, zone) }
                pending.removeAt(i)
            }

        }
    }
}


