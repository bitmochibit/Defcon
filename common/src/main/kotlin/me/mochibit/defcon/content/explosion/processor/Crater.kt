package me.mochibit.defcon.content.explosion.processor

import it.unimi.dsi.fastutil.longs.LongOpenHashSet
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.suspendCancellableCoroutine
import me.mochibit.defcon.content.explosion.BlastActor
import me.mochibit.defcon.content.explosion.BlastZone
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.content.explosion.processor.carver.CraterCarveContext
import me.mochibit.defcon.content.explosion.processor.carver.CraterCarver
import me.mochibit.defcon.content.explosion.processor.carver.RuntimeCraterCarveContext
import me.mochibit.defcon.content.explosion.processor.transformer.MaterialCategories
import me.mochibit.defcon.foundation.async.withMainContext
import me.mochibit.defcon.foundation.extension.awaitUnpaused
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.info
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.server.level.TicketType
import net.minecraft.world.level.ChunkPos
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.levelgen.Heightmap
import java.util.UUID
import java.util.concurrent.CompletableFuture
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sqrt

class Crater(
    private val level: ServerLevel,
    private val center: BlockPos,
    val radiusX: Int,
    val radiusY: Int,
    val radiusZ: Int,
    val collapseHeight: Int = 200,
    private val zoneId: UUID,
    val debrisRimWidth: Int = (radiusX * 0.3).toInt().coerceAtLeast(5)
) {
    private val touchedChunks = LongOpenHashSet()
    private val blockChanger by lazy { BlockChanger.getInstance(level) }

    suspend fun create() {
        val ctx = RuntimeCraterCarveContext(level, blockChanger)
        val params = toCraterParams()

        val maxRadius = radiusX + debrisRimWidth
        withMainContext {
            for (cx in (center.x - maxRadius shr 4)..(center.x + maxRadius shr 4)) {
                for (cz in (center.z - maxRadius shr 4)..(center.z + maxRadius shr 4)) {
                    if (!level.chunkSource.isPositionTicking(ChunkPos.asLong(cx, cz))) continue
                    processChunkColumns(cx, cz, maxRadius, params, ctx)
                    touchedChunks.add(ChunkPos.asLong(cx, cz))
                }
            }
        }
        blockChanger.flush()
        markProcessedChunks()
    }

    private fun processChunkColumns(
        cx: Int, cz: Int,
        maxRadius: Int,
        params: CraterCarver.Params,
        ctx: CraterCarveContext,
    ) {
        val xRange = maxOf(cx shl 4, center.x - maxRadius)..minOf((cx shl 4) + 15, center.x + maxRadius)
        val zRange = maxOf(cz shl 4, center.z - maxRadius)..minOf((cz shl 4) + 15, center.z + maxRadius)

        val maxNormSq = (1.0 + debrisRimWidth.toDouble() / radiusX).pow(2)

        for (x in xRange) for (z in zRange) {
            val dx = x - center.x
            val dz = z - center.z
            val normalizedDistance = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)
            if (normalizedDistance > maxNormSq) continue

            CraterCarver.carveColumn(dx, dz, params, ctx)
        }
    }

    private fun markProcessedChunks() {
        val savedData = BlastZoneSavedData.get(level)
        val it = touchedChunks.iterator()
        while (it.hasNext()) {
            val key = it.nextLong()
            savedData.markChunkWorldgenProcessed(zoneId, BlastActor.CRATER, ChunkPos.getX(key), ChunkPos.getZ(key))
        }
    }

    private fun Crater.toCraterParams() = CraterCarver.Params(
        centerX = center.x, centerY = center.y, centerZ = center.z,
        radiusX = radiusX, radiusY = radiusY, radiusZ = radiusZ,
        debrisRimWidth = debrisRimWidth, collapseHeight = collapseHeight,
        seaLevel = level.seaLevel, minBuildHeight = level.minBuildHeight, maxBuildHeight = level.maxBuildHeight,
    )


}

private fun wangNoise(x: Int, y: Int, z: Int): Double {
    var hash = x.toLong() * 0x1f1f1f1f
    hash = hash xor (y.toLong() * 0x27d4eb2d)
    hash = hash xor (z.toLong() * 0x85ebca77)
    hash = hash xor (hash ushr 15)
    hash *= 0xc2b2ae3d
    hash = hash xor (hash ushr 16)
    return (hash and 0x7FFFFFFF) / 2147483647.0
}