package me.mochibit.defcon.content.explosion.processor

import kotlinx.coroutines.delay
import me.mochibit.defcon.content.explosion.BlastZone
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.carver.RuntimeColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.explosion.processor.Shockwave
import me.mochibit.defcon.foundation.async.launchOnServer
import me.mochibit.defcon.foundation.async.withMainContext
import me.mochibit.defcon.foundation.err
import me.mochibit.defcon.foundation.eventbus.ModEventHandler
import me.mochibit.defcon.foundation.extension.ticks
import me.mochibit.defcon.foundation.services.eventService
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import kotlin.math.sqrt
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object PostApocalypticTerrain : ModEventHandler {

    fun apply(savedData: BlastZoneSavedData, level: ServerLevel, chunk: ChunkAccess, zone: BlastZone) {
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

                val surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, worldX, worldZ)

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
        savedData.markChunkWorldgenProcessed(zone.id, chunkPos.x, chunkPos.z)
        chunkCtx.finalizeLighting()
        chunkCtx.flushClientUpdates()
    }

    override fun setupEvents() {
        eventService.onChunkLoad { level, chunk, isNewChunk ->
            launchOnServer {
                val serverLevel = level as? ServerLevel ?: return@launchOnServer
                val chunkPos = chunk.pos
                val savedData = BlastZoneSavedData.get(serverLevel)

                val zonesHere = savedData.zonesForChunk(chunkPos.x, chunkPos.z)
                if (zonesHere.isEmpty()) return@launchOnServer

                val unprocessed = zonesHere.filter { zone ->
                    !savedData.isChunkWorldgenProcessed(zone.id, chunkPos.x, chunkPos.z)
                }
                if (unprocessed.isEmpty()) return@launchOnServer

                while (!serverLevel.chunkSource.isPositionTicking(chunkPos.toLong())) {
                    if (!serverLevel.chunkSource.hasChunk(chunkPos.x, chunkPos.z)) {
                        return@launchOnServer
                    }
                    delay(50.milliseconds)
                }
                withMainContext {
                    unprocessed.forEach { zone -> apply(savedData, serverLevel, chunk, zone) }
                }
            }
        }
    }
}


