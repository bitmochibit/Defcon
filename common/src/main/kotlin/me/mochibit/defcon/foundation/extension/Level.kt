package me.mochibit.defcon.foundation.extension

import kotlinx.coroutines.delay
import me.mochibit.defcon.mixin.ChunkAccessAccessor
import me.mochibit.defcon.mixin.LevelChunkAccessor
import net.minecraft.CrashReport
import net.minecraft.CrashReportCategory
import net.minecraft.ReportedException
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.util.profiling.ProfilerFiller
import net.minecraft.world.level.Level
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.EntityBlock
import net.minecraft.world.level.block.entity.BlockEntity
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.LevelChunkSection
import net.minecraft.world.level.levelgen.Heightmap
import net.minecraft.world.level.lighting.LightEngine
import kotlin.time.Duration.Companion.milliseconds

fun Level.getBlockState(x: Int, y: Int, z: Int): BlockState {
    if (this.isOutsideBuildHeight(y)) {
        return Blocks.VOID_AIR.defaultBlockState()
    } else {
        return this.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
            .getBlockState(x, y, z)
    }
}

fun LevelChunk.getBlockState(x: Int, y: Int, z: Int): BlockState {
    try {
        val l = this.getSectionIndex(y)
        if (l >= 0 && l < this.sections.size) {
            val section = this.sections[l]
            if (!section.hasOnlyAir()) {
                return section.getBlockState(x and 15, y and 15, z and 15)
            }
        }

        return Blocks.AIR.defaultBlockState()
    } catch (throwable: Throwable) {
        val crashReport = CrashReport.forThrowable(throwable, "Getting block state")
        val crashReportCategory = crashReport.addCategory("Block being got")
        crashReportCategory.setDetail(
            "Location"
        ) { CrashReportCategory.formatLocation(this, x, y, z) }
        throw ReportedException(crashReport)
    }
}


fun LevelChunk.setBlockState(pos: BlockPos, state: BlockState, isMoving: Boolean, locked: Boolean = false): BlockState? {
    val chunkAccessAccessor = this as ChunkAccessAccessor
    val levelChunkAccessor = this as LevelChunkAccessor

    val level = this.level ?: return null

    val i = pos.y
    val levelChunkSection: LevelChunkSection = this.getSection(this.getSectionIndex(i))
    val flag = levelChunkSection.hasOnlyAir()
    if (flag && state.isAir) {
        return null
    }

    val j = pos.x and 15
    val k = i and 15
    val l = pos.z and 15
    val blockState = levelChunkSection.setBlockState(j, k, l, state, locked)
    if (blockState === state) {
        return null
    }

    val block = state.block
    val heightmaps = chunkAccessAccessor.getHeightmaps()
    (heightmaps[Heightmap.Types.MOTION_BLOCKING] as Heightmap).update(j, i, l, state)
    (heightmaps[Heightmap.Types.MOTION_BLOCKING_NO_LEAVES] as Heightmap).update(j, i, l, state)
    (heightmaps[Heightmap.Types.OCEAN_FLOOR] as Heightmap).update(j, i, l, state)
    (heightmaps[Heightmap.Types.WORLD_SURFACE] as Heightmap).update(j, i, l, state)

    val flag1 = levelChunkSection.hasOnlyAir()
    if (flag != flag1) {
        level.chunkSource.lightEngine.updateSectionStatus(pos, flag1)
    }

    if (LightEngine.hasDifferentLightProperties(this, pos, blockState, state)) {
        val profilerFiller: ProfilerFiller = level.profiler
        profilerFiller.push("updateSkyLightSources")
        this.skyLightSources.update(this, j, i, l)
        profilerFiller.popPush("queueCheckLight")
        level.chunkSource.lightEngine.checkBlock(pos)
        profilerFiller.pop()
    }

    val flag2 = blockState.hasBlockEntity()
    if (!level.isClientSide) {
        blockState.onRemove(level, pos, state, isMoving)
    } else if (!blockState.`is`(block) && flag2) {
        this.removeBlockEntity(pos)
    }

    if (!levelChunkSection.getBlockState(j, k, l).`is`(block)) {
        return null
    }

    if (!level.isClientSide) {
        state.onPlace(level, pos, blockState, isMoving)
    }

    if (state.hasBlockEntity()) {
        var blockEntity: BlockEntity? = this.getBlockEntity(pos, LevelChunk.EntityCreationType.CHECK)
        if (blockEntity == null) {
            blockEntity = (block as EntityBlock).newBlockEntity(pos, state)
            if (blockEntity != null) {
                this.addAndRegisterBlockEntity(blockEntity)
            }
        } else {
            blockEntity.setBlockState(state)
            levelChunkAccessor.invokeUpdateBlockEntityTicker(blockEntity)
        }
    }

    chunkAccessAccessor.setUnsaved(true)
    return blockState
}

suspend fun ServerLevel.awaitUnpaused(pollIntervalMs: Long = 100) {
    while (tickRateManager().isFrozen) {
        delay(pollIntervalMs.milliseconds)
    }
}