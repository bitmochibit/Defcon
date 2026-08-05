package me.mochibit.defcon.content.explosion.processor

import me.mochibit.defcon.content.explosion.BlastZone
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.content.explosion.processor.carver.ColumnCarver
import me.mochibit.defcon.content.explosion.processor.carver.CraterCarver
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenCraterCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialTransformer
import me.mochibit.defcon.explosion.processor.Shockwave
import me.mochibit.defcon.foundation.eventbus.ModEventHandler
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
        val effectiveShockwaveRange = (zone.shockwaveRadiusOuter - zone.shockwaveRadiusInner).toFloat()
        val craterCtx = WorldGenCraterCarveContext(chunk)
        val craterParams = zone.toCraterParams(level)

        for (x in 0 until 16) {
            for (z in 0 until 16) {
                val worldX = chunkPos.minBlockX + x
                val worldZ = chunkPos.minBlockZ + z

                val dx = (worldX - zone.centerX).toDouble()
                val dz = (worldZ - zone.centerZ).toDouble()
                val dist = sqrt(dx * dx + dz * dz).toFloat()

                val inCrater = craterParams != null && dist <= craterParams.radiusX + craterParams.debrisRimWidth
                if (inCrater) {
                    CraterCarver.carveColumn(dx.toInt(), dz.toInt(), craterParams, craterCtx)
                } else {
                    val distanceFromCraterEdge = dist - zone.shockwaveRadiusInner
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

    fun seedZone(level: ServerLevel, zone: BlastZone) {
        val minChunkX = (zone.centerX - zone.shockwaveRadiusOuter) shr 4
        val maxChunkX = (zone.centerX + zone.shockwaveRadiusOuter) shr 4
        val minChunkZ = (zone.centerZ - zone.shockwaveRadiusOuter) shr 4
        val maxChunkZ = (zone.centerZ + zone.shockwaveRadiusOuter) shr 4

        for (cx in minChunkX..maxChunkX) {
            for (cz in minChunkZ..maxChunkZ) {
                val closestX = zone.centerX.coerceIn(cx shl 4, (cx shl 4) + 15)
                val closestZ = zone.centerZ.coerceIn(cz shl 4, (cz shl 4) + 15)
                val dx = (closestX - zone.centerX).toDouble()
                val dz = (closestZ - zone.centerZ).toDouble()
                if (dx * dx + dz * dz > zone.shockwaveRadiusOuter.toDouble() * zone.shockwaveRadiusOuter) continue

                if (level.chunkSource.hasChunk(cx, cz)) {
                    pending.add(ChunkPos(cx, cz) to level)
                }
            }
        }
    }

    private fun BlastZone.toCraterParams(level: ServerLevel): CraterCarver.Params? {
        if (craterRadiusX <= 0) return null
        return CraterCarver.Params(
            centerX = centerX, centerY = centerY, centerZ = centerZ,
            radiusX = craterRadiusX, radiusY = craterRadiusY, radiusZ = craterRadiusZ,
            debrisRimWidth = craterDebrisRimWidth, collapseHeight = craterCollapseHeight,
            seaLevel = level.seaLevel, minBuildHeight = level.minBuildHeight, maxBuildHeight = level.maxBuildHeight,
        )
    }
}


