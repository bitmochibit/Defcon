/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.transformer.material

import me.mochibit.defcon.palette.MaterialPalette
import org.bukkit.Material
import kotlin.random.Random

sealed class TransformationOutcome {
    abstract fun transform(
        material: Material,
        explosionPower: Float,
        random: Random,
        x: Int = 0,
        z: Int = 0,
        y: Int = 0,
    ): Material

    data class ToMaterial(
        val material: Material,
    ) : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material = this.material
    }

    data class ToRandomMaterial(
        val materials: Set<Material>,
    ) : TransformationOutcome() {
        private val materialList: List<Material> by lazy { materials.toList() }

        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material = materialList.random(random)
    }

    data class ToPalette(
        val palette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material = palette.getRandom()
    }

    data class ToPaletteWithNoise(
        val palette: MaterialPalette,
    ) : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material = palette.getWithNoise(x, z, y)
    }

    data class ChanceOutcome(
        val chance: Float, // 0.0 to 1.0
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material =
            if (random.nextFloat() < chance) {
                trueOutcome.transform(material, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(material, explosionPower, random, x, z, y)
            }
    }

    data class ConditionalOutcome(
        val condition: (Material, Float) -> Boolean,
        val trueOutcome: TransformationOutcome,
        val falseOutcome: TransformationOutcome,
    ) : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material =
            if (condition(material, explosionPower)) {
                trueOutcome.transform(material, explosionPower, random, x, z, y)
            } else {
                falseOutcome.transform(material, explosionPower, random, x, z, y)
            }
    }

    data object NoTransformation : TransformationOutcome() {
        override fun transform(
            material: Material,
            explosionPower: Float,
            random: Random,
            x: Int,
            z: Int,
            y: Int,
        ): Material = material
    }
}
