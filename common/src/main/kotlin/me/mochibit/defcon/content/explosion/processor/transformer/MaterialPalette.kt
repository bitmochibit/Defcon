package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import org.joml.SimplexNoise
import kotlin.math.abs

data class MaterialPalette(
    val materials: Set<MaterialPaletteEntry>,
    val noiseScale: Float = 0.1f,
    val chaosWeight: Float = 0.6f,
) {

    private val sortedMaterials = materials.sortedBy { it.weight }
    private val totalWeight = sortedMaterials.sumOf { it.weight }

    fun getRandom(): BlockState {
        val randomValue = (0 until totalWeight).random()

        var cumulativeWeight = 0
        for (entry in sortedMaterials) {
            cumulativeWeight += entry.weight
            if (randomValue < cumulativeWeight) {
                return entry.material
            }
        }
        return sortedMaterials.first().material
    }

    fun getWithNoise(x: Int, z: Int, y: Int = 0): BlockState {
        if (sortedMaterials.isEmpty()) return net.minecraft.world.level.block.Blocks.AIR.defaultBlockState()
        if (sortedMaterials.size == 1) return sortedMaterials.first().material


        val coherent = (SimplexNoise.noise(x * noiseScale, y * noiseScale, z * noiseScale) + 1f) / 2f

        val chaotic = getCoordinateRandom(x, y, z)


        val blended = (coherent * (1f - chaosWeight) + chaotic * chaosWeight).coerceIn(0f, 1f)


        val targetWeight = (blended * (totalWeight - 1)).toInt()

        var cumulativeWeight = 0
        for (entry in sortedMaterials) {
            cumulativeWeight += entry.weight
            if (targetWeight < cumulativeWeight) {
                return entry.material
            }
        }
        return sortedMaterials.last().material
    }

    private fun getCoordinateRandom(x: Int, y: Int, z: Int): Float {
        var h = x * 1274126177 + y * 374761393 + z * 668265263
        h = (h xor (h ushr 16)) * -2128831035
        h = (h xor (h ushr 15)) * -1472204593
        h = h xor (h ushr 13)
        return abs(h % 100000) / 100000f
    }
}

data class MaterialPaletteEntry(
    val material: BlockState,
    val weight: Int = 1,
)
