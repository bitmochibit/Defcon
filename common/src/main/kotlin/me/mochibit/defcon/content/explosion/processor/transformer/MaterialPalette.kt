package me.mochibit.defcon.content.explosion.processor.transformer

import net.minecraft.world.level.block.state.BlockState
import org.joml.SimplexNoise

data class MaterialPalette(
    val materials: Set<MaterialPaletteEntry>,
    val noiseScale: Float = 0.1f,
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
        val noiseValue = SimplexNoise.noise(x * noiseScale, y * noiseScale, z * noiseScale)
        val normalizedNoise = noiseValue+ 1 / 2f

        val totalWeight = materials.sumOf { it.weight }
        val targetWeight = (normalizedNoise * totalWeight).toInt()

        var cumulativeWeight = 0
        for (entry in materials) {
            cumulativeWeight += entry.weight
            if (targetWeight < cumulativeWeight) {
                return entry.material
            }
        }

        return materials.first().material
    }
}

data class MaterialPaletteEntry(
    val material: BlockState,
    val weight: Int = 1,
)
