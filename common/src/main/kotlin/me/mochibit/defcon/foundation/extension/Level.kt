package me.mochibit.defcon.foundation.extension

import net.minecraft.CrashReport
import net.minecraft.CrashReportCategory
import net.minecraft.CrashReportDetail
import net.minecraft.ReportedException
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.world.level.Level
import net.minecraft.world.level.LightLayer
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.chunk.status.ChunkStatus
import net.minecraft.world.level.lighting.LightEngine

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
            "Location",
            CrashReportDetail { CrashReportCategory.formatLocation(this, x, y, z) })
        throw ReportedException(crashReport)
    }
}

