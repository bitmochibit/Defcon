package me.mochibit.defcon.content.explosion.processor

import me.mochibit.defcon.content.explosion.BlastActor
import me.mochibit.defcon.content.explosion.BlastZoneSavedData
import me.mochibit.defcon.content.explosion.processor.carver.CraterCarver
import me.mochibit.defcon.content.explosion.processor.carver.RuntimeCraterCarveContext
import me.mochibit.defcon.foundation.async.processTickBudgeted
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.ChunkPos
import java.util.UUID
import kotlin.math.pow


class Crater(
    private val level: ServerLevel,
    private val center: BlockPos,
    val radiusX: Int,
    val radiusY: Int,
    val radiusZ: Int,
    val collapseHeight: Int = 200,
    private val zoneId: UUID,
    val debrisRimWidth: Int = (radiusX * 0.3).toInt().coerceAtLeast(5),
) {
    private val blockChanger by lazy { BlockChanger.getInstance(level) }

    suspend fun create() {
        val ctx = RuntimeCraterCarveContext(level, blockChanger)
        val params = toCraterParams()
        val maxRadius = radiusX + debrisRimWidth
        val savedData = BlastZoneSavedData.get(level)

        var currentChunkKey = Long.MIN_VALUE
        var currentChunkTouched = false

        collectColumns(maxRadius).processTickBudgeted { task ->
            val chunkKey = ChunkPos.asLong(task.cx, task.cz)

            if (chunkKey != currentChunkKey) {
                finalizeChunk(savedData, currentChunkKey, currentChunkTouched)
                currentChunkKey = chunkKey
                currentChunkTouched = false
            }

            if (savedData.isChunkWorldgenProcessedByAnyActor(zoneId, task.cx, task.cz)) return@processTickBudgeted
            if (!level.chunkSource.isPositionTicking(chunkKey)) return@processTickBudgeted

            CraterCarver.carveColumn(task.x - center.x, task.z - center.z, params, ctx)
            currentChunkTouched = true
        }
        finalizeChunk(savedData, currentChunkKey, currentChunkTouched)
    }

    private fun finalizeChunk(savedData: BlastZoneSavedData, chunkKey: Long, touched: Boolean) {
        if (chunkKey == Long.MIN_VALUE || !touched) return
        savedData.markChunkWorldgenProcessed(zoneId, BlastActor.CRATER, ChunkPos.getX(chunkKey), ChunkPos.getZ(chunkKey))
    }

    private fun collectColumns(maxRadius: Int): Sequence<ColumnTask> = sequence {
        val maxNormSq = (1.0 + debrisRimWidth.toDouble() / radiusX).pow(2)
        for (cx in (center.x - maxRadius shr 4)..(center.x + maxRadius shr 4)) {
            for (cz in (center.z - maxRadius shr 4)..(center.z + maxRadius shr 4)) {
                val xRange = maxOf(cx shl 4, center.x - maxRadius)..minOf((cx shl 4) + 15, center.x + maxRadius)
                val zRange = maxOf(cz shl 4, center.z - maxRadius)..minOf((cz shl 4) + 15, center.z + maxRadius)
                for (x in xRange) for (z in zRange) {
                    val dx = x - center.x
                    val dz = z - center.z
                    val norm = (dx.toDouble() / radiusX).pow(2) + (dz.toDouble() / radiusZ).pow(2)
                    if (norm <= maxNormSq) yield(ColumnTask(cx, cz, x, z))
                }
            }
        }
    }

    private fun Crater.toCraterParams() = CraterCarver.Params(
        centerX = center.x, centerY = center.y, centerZ = center.z,
        radiusX = radiusX, radiusY = radiusY, radiusZ = radiusZ,
        debrisRimWidth = debrisRimWidth, collapseHeight = collapseHeight,
        seaLevel = level.seaLevel, minBuildHeight = level.minBuildHeight, maxBuildHeight = level.maxBuildHeight,
    )
}

private data class ColumnTask(val cx: Int, val cz: Int, val x: Int, val z: Int)