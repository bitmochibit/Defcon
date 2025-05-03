/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package me.mochibit.defcon.customassets.items.definitions

import me.mochibit.defcon.customassets.items.*
import org.bukkit.Material

class LeatherGasMask: AbstractCustomItemModel(
    LegacyModelData(
        originalItem = Material.LEATHER_BOOTS,
        textures = mapOf(
            "layer0" to "item/leather_boots",
            "layer1" to "item/leather_boots_overlay"
        ),
        overrides = setOf(
            Override(
                model = "minecraft:item/leather_boots_quartz_trim",
                predicate = Predicate(
                    "trim_type",
                    0.1
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_iron_trim",
                predicate = Predicate(
                    "trim_type",
                    0.2
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_netherite_trim",
                predicate = Predicate(
                    "trim_type",
                    0.3
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_redstone_trim",
                predicate = Predicate(
                    "trim_type",
                    0.4
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_copper_trim",
                predicate = Predicate(
                    "trim_type",
                    0.5
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_gold_trim",
                predicate = Predicate(
                    "trim_type",
                    0.6
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_emerald_trim",
                predicate = Predicate(
                    "trim_type",
                    0.7
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_diamond_trim",
                predicate = Predicate(
                    "trim_type",
                    0.8
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_lapis_trim",
                predicate = Predicate(
                    "trim_type",
                    0.9
                )
            ),
            Override(
                model = "minecraft:item/leather_boots_amethyst_trim",
                predicate = Predicate(
                    "trim_type",
                    1.0
                )
            )
        ), // Trims
        modelName = "leather_gas_mask"
    ),
    ModelData(
        name = "leather_gas_mask",
    )
)
