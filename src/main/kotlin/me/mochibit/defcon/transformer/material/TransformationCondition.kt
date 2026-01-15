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

import org.bukkit.Material

sealed class TransformationCondition {
    abstract fun matches(
        material: Material,
        explosionPower: Float,
    ): Boolean

    data class MaterialSet(
        val materials: Set<Material>,
    ) : TransformationCondition() {
        override fun matches(
            material: Material,
            explosionPower: Float,
        ): Boolean = material in materials
    }

    data class MaterialCategory(
        val predicate: (Material) -> Boolean,
    ) : TransformationCondition() {
        override fun matches(
            material: Material,
            explosionPower: Float,
        ): Boolean = predicate(material)
    }

    data class SpecificMaterial(
        val material: Material,
    ) : TransformationCondition() {
        override fun matches(
            material: Material,
            explosionPower: Float,
        ): Boolean = this.material == material
    }

    data class PowerThreshold(
        val condition: TransformationCondition,
        val minPower: Float? = null,
        val maxPower: Float? = null,
    ) : TransformationCondition() {
        override fun matches(
            material: Material,
            explosionPower: Float,
        ): Boolean {
            val powerMatches =
                when {
                    minPower != null && explosionPower < minPower -> false
                    maxPower != null && explosionPower > maxPower -> false
                    else -> true
                }
            return powerMatches && condition.matches(material, explosionPower)
        }
    }

    data class Combined(
        val conditions: List<TransformationCondition>,
        val operator: LogicalOperator = LogicalOperator.AND,
    ) : TransformationCondition() {
        override fun matches(
            material: Material,
            explosionPower: Float,
        ): Boolean =
            when (operator) {
                LogicalOperator.AND -> conditions.all { it.matches(material, explosionPower) }
                LogicalOperator.OR -> conditions.any { it.matches(material, explosionPower) }
            }
    }

    enum class LogicalOperator { AND, OR }
}
