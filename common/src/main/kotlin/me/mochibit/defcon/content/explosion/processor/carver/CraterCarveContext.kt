package me.mochibit.defcon.content.explosion.processor.carver

import it.unimi.dsi.fastutil.shorts.ShortOpenHashSet
import it.unimi.dsi.fastutil.shorts.ShortSet
import me.mochibit.defcon.foundation.extension.getBlockState
import me.mochibit.defcon.foundation.extension.setBlockState
import me.mochibit.defcon.foundation.util.BlockChanger
import net.minecraft.core.BlockPos
import net.minecraft.core.SectionPos
import net.minecraft.network.protocol.game.ClientboundLightUpdatePacket
import net.minecraft.network.protocol.game.ClientboundSectionBlocksUpdatePacket
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.level.block.state.BlockState
import net.minecraft.world.level.chunk.ChunkAccess
import net.minecraft.world.level.chunk.LevelChunk
import net.minecraft.world.level.levelgen.Heightmap
import java.util.concurrent.ConcurrentHashMap

interface CraterCarveContext {
    fun getState(x: Int, y: Int, z: Int): BlockState
    fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean = false)
    fun heightAt(x: Int, z: Int, type: Heightmap.Types): Int
}

class RuntimeCraterCarveContext(
    private val level: ServerLevel,
    private val blockChanger: BlockChanger,
) : CraterCarveContext {
    override fun getState(x: Int, y: Int, z: Int) = level.getBlockState(x, y, z)
    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        blockChanger.addBlockChange(x, y, z, state, updateBlock)
    }
    override fun heightAt(x: Int, z: Int, type: Heightmap.Types) = level.getHeight(type, x, z)
}

//todo extract the packet sending logic
class WorldGenCraterCarveContext(
    private val level: ServerLevel,
    private val chunk: LevelChunk,
) : CraterCarveContext {
    private val mutablePos = BlockPos.MutableBlockPos()
    private val dirtySections = ConcurrentHashMap<Int, ShortSet>()

    override fun getState(x: Int, y: Int, z: Int): BlockState = chunk.getBlockState(x, y, z)

    override fun setState(x: Int, y: Int, z: Int, state: BlockState, updateBlock: Boolean) {
        val old = chunk.getBlockState(x, y, z)
        if (old == state) return
        mutablePos.set(x, y, z)
        chunk.setBlockState(mutablePos, state, false, triggerOnPlace = updateBlock)

        val sectionIndex = chunk.getSectionIndex(y)
        if (sectionIndex < 0 || sectionIndex >= chunk.sections.size) return
        val packed = (((x and 15) shl 8) or ((z and 15) shl 4) or (y and 15)).toShort()
        dirtySections.getOrPut(sectionIndex) { ShortOpenHashSet() }.add(packed)
    }

    override fun heightAt(x: Int, z: Int, type: Heightmap.Types) = chunk.getHeight(type, x, z)

    fun flushClientUpdates() {
        if (dirtySections.isEmpty()) return
        val lightEngine = level.chunkSource.lightEngine
        lightEngine.tryScheduleUpdate()
        lightEngine.waitForPendingTasks(chunk.pos.x, chunk.pos.z).thenRun {
            level.server.execute {
                val trackingPlayers = level.chunkSource.chunkMap.getPlayers(chunk.pos, false)
                if (trackingPlayers.isNotEmpty()) {
                    for ((sectionIndex, positions) in dirtySections) {
                        if (positions.isEmpty()) continue
                        val sectionY = chunk.minSection + sectionIndex
                        val sectionPos = SectionPos.of(chunk.pos.x, sectionY, chunk.pos.z)
                        val packet =
                            ClientboundSectionBlocksUpdatePacket(sectionPos, positions, chunk.sections[sectionIndex])
                        trackingPlayers.forEach { it.connection.send(packet) }
                    }
                    trackingPlayers.forEach {
                        it.connection.send(ClientboundLightUpdatePacket(chunk.pos, lightEngine, null, null))
                    }
                }
                dirtySections.clear()
            }
        }
    }
}