package me.mochibit.defcon.utils

import org.bukkit.ChunkSnapshot
import org.bukkit.Material
import org.bukkit.block.Biome
import org.bukkit.block.data.BlockData

class SmartChunkSnapshot(
    private val snapshot: ChunkSnapshot,
) : ChunkSnapshot {
    private val blockTypeOverrides = HashMap<Int, Material>()
    private val blockDataOverrides = HashMap<Int, BlockData>()

    fun updateBlockType(
        x: Int,
        y: Int,
        z: Int,
        type: Material,
    ) {
        val key = key(x, y, z)
        blockTypeOverrides[key] = type
        blockDataOverrides.remove(key)
    }

    fun updateBlockData(
        x: Int,
        y: Int,
        z: Int,
        data: BlockData,
    ) {
        val key = key(x, y, z)
        blockDataOverrides[key] = data
        blockTypeOverrides[key] = data.material
    }

    override fun getX(): Int = snapshot.x

    override fun getZ(): Int = snapshot.z

    override fun getWorldName(): String = snapshot.worldName

    override fun getBlockType(
        x: Int,
        y: Int,
        z: Int,
    ): Material =
        blockTypeOverrides[key(x, y, z)]
            ?: snapshot.getBlockType(x, y, z)

    override fun getBlockData(
        x: Int,
        y: Int,
        z: Int,
    ): BlockData =
        blockDataOverrides[key(x, y, z)]
            ?: snapshot.getBlockData(x, y, z)

    @Deprecated("Deprecated in Java")
    override fun getData(
        x: Int,
        y: Int,
        z: Int,
    ): Int = 0

    override fun getBlockSkyLight(
        x: Int,
        y: Int,
        z: Int,
    ): Int = snapshot.getBlockSkyLight(x, y, z)

    override fun getBlockEmittedLight(
        x: Int,
        y: Int,
        z: Int,
    ): Int = snapshot.getBlockEmittedLight(x, y, z)

    override fun getHighestBlockYAt(
        x: Int,
        z: Int,
    ): Int = snapshot.getHighestBlockYAt(x, z)

    @Deprecated("Deprecated in Java")
    override fun getBiome(
        x: Int,
        z: Int,
    ): Biome = snapshot.getBiome(x, z)

    override fun getBiome(
        x: Int,
        y: Int,
        z: Int,
    ): Biome = snapshot.getBiome(x, y, z)

    @Deprecated("Deprecated in Java")
    override fun getRawBiomeTemperature(
        x: Int,
        z: Int,
    ): Double = snapshot.getRawBiomeTemperature(x, z)

    override fun getRawBiomeTemperature(
        x: Int,
        y: Int,
        z: Int,
    ): Double = snapshot.getRawBiomeTemperature(x, y, z)

    override fun getCaptureFullTime(): Long = snapshot.captureFullTime

    override fun isSectionEmpty(sy: Int): Boolean = snapshot.isSectionEmpty(sy)

    override fun contains(block: BlockData): Boolean {
        if (blockDataOverrides.values.any { it == block }) return true
        return snapshot.contains(block)
    }

    override fun contains(biome: Biome): Boolean = snapshot.contains(biome)

    private fun key(
        x: Int,
        y: Int,
        z: Int,
    ): Int {
        // x: 0–15 (4 bit)
        // y: 0–511 (9 bit)
        // z: 0–15 (4 bit)
        return (x and 0xF) or
            ((y and 0x1FF) shl 4) or
            ((z and 0xF) shl 13)
    }
}
