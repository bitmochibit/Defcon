package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import org.joml.SimplexNoise

data class MaterialPalette(
    val materials: Set<MaterialPaletteEntry>,
    val noiseScale: Float = 0.1f,
    val chaosWeight: Float = 0.6f,
) {
    fun getRandom(): BlockState {
        val totalWeight = materials.sumOf { it.weight }
        val randomValue = (0 until totalWeight).random()

        var cumulativeWeight = 0
        for (entry in materials) {
            cumulativeWeight += entry.weight
            if (randomValue < cumulativeWeight) {
                return entry.material
            }
        }
        return materials.first().material
    }

    fun getWithNoise(x: Int, z: Int, y: Int = 0): BlockState {
        val coherent = (SimplexNoise.noise(x * noiseScale, y * noiseScale, z * noiseScale) + 1f) / 2f
        val chaotic = hashNoise(x, y, z)
        val blended = (coherent * (1f - chaosWeight) + chaotic * chaosWeight).coerceIn(0f, 1f)

        val totalWeight = materials.sumOf { it.weight }
        val targetWeight = (blended * totalWeight).toInt().coerceIn(0, totalWeight - 1)

        var cumulativeWeight = 0
        for (entry in materials) {
            cumulativeWeight += entry.weight
            if (targetWeight < cumulativeWeight) return entry.material
        }
        return materials.first().material
    }

    private fun hashNoise(x: Int, y: Int, z: Int): Float {
        var h = x * 374761393 + y * 668265263 + z * 2147483647
        h = (h xor (h ushr 13)) * 1274126177
        h = h xor (h ushr 16)
        return (h and 0xFFFFFF).toFloat() / 0xFFFFFF.toFloat()
    }
}

data class MaterialPaletteEntry(
    val material: BlockState,
    val weight: Int = 1,
)
