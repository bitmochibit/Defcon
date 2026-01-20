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
import me.mochibit.defcon.palette.MaterialPaletteEntry
import org.bukkit.Material
import kotlin.random.Random

class MaterialTransformer(
    rules: List<TransformationRule> = defaultRules(),
    private val random: Random = Random.Default,
) {
    private val sortedRules: List<TransformationRule> by lazy {
        rules.sortedByDescending { it.priority }
    }

    fun transformMaterial(
        currentMaterial: Material,
        explosionPower: Float,
        x: Int = 0,
        z: Int = 0,
        y: Int = 0,
    ): Material =
        sortedRules
            .find { it.matches(currentMaterial, explosionPower) }
            ?.transform(currentMaterial, explosionPower, random, x, z, y)
            ?: currentMaterial

    companion object {
        // Pre-computed material sets for better performance
        private val BRICK_MATERIALS =
            setOf(
                Material.BRICKS,
                Material.BRICK_WALL,
                Material.BRICK_STAIRS,
                Material.BRICK_SLAB,
            )

        private val CONCRETE_MATERIALS =
            setOf(
                Material.WHITE_CONCRETE,
                Material.RED_CONCRETE,
                Material.BLUE_CONCRETE,
            )

        private val DESTROYED_SLAB_MATERIALS =
            setOf(
                Material.COBBLED_DEEPSLATE_SLAB,
                Material.COBBLESTONE_SLAB,
            )

        private val DESTROYED_DOUBLE_SLAB_MATERIALS =
            setOf(
                Material.COBBLED_DEEPSLATE,
                Material.COBBLESTONE,
            )

        private val DESTROYED_WALL_MATERIALS =
            setOf(
                Material.COBBLED_DEEPSLATE_WALL,
                Material.COBBLESTONE_WALL,
            )

        private val DESTROYED_STAIRS_MATERIALS =
            setOf(
                Material.COBBLED_DEEPSLATE_STAIRS,
                Material.COBBLESTONE_STAIRS,
            )

        private val DIRT_GRASS_MATERIALS =
            setOf(
                Material.DIRT,
                Material.GRASS_BLOCK,
            )

        private val DESTROYED_DIRT_MATERIALS =
            setOf(
                Material.COARSE_DIRT,
                Material.MUD,
                Material.MUDDY_MANGROVE_ROOTS,
            )

        private val DOUBLE_SLAB_BLOCKS =
            setOf(
                Material.STONE,
                Material.SMOOTH_STONE,
                Material.SANDSTONE,
                Material.PETRIFIED_OAK_SLAB,
                Material.OAK_PLANKS,
                Material.SPRUCE_PLANKS,
                Material.BIRCH_PLANKS,
                Material.JUNGLE_PLANKS,
                Material.ACACIA_PLANKS,
                Material.DARK_OAK_PLANKS,
                Material.CRIMSON_PLANKS,
                Material.WARPED_PLANKS,
                Material.COBBLESTONE,
                Material.BRICKS,
                Material.STONE_BRICKS,
                Material.NETHER_BRICKS,
                Material.QUARTZ_BLOCK,
                Material.RED_SANDSTONE,
                Material.PURPUR_BLOCK,
                Material.PRISMARINE,
                Material.PRISMARINE_BRICKS,
                Material.DARK_PRISMARINE,
                Material.POLISHED_GRANITE,
                Material.POLISHED_DIORITE,
                Material.POLISHED_ANDESITE,
                Material.RED_NETHER_BRICKS,
                Material.POLISHED_BLACKSTONE,
                Material.POLISHED_BLACKSTONE_BRICKS,
                Material.END_STONE_BRICKS,
                Material.BLACKSTONE,
                Material.MOSSY_COBBLESTONE,
                Material.MOSSY_STONE_BRICKS,
                Material.DEEPSLATE,
                Material.COBBLED_DEEPSLATE,
                Material.POLISHED_DEEPSLATE,
                Material.DEEPSLATE_BRICKS,
                Material.DEEPSLATE_TILES,
                Material.CUT_COPPER,
                Material.EXPOSED_CUT_COPPER,
                Material.WEATHERED_CUT_COPPER,
                Material.OXIDIZED_CUT_COPPER,
                Material.WAXED_CUT_COPPER,
                Material.WAXED_EXPOSED_CUT_COPPER,
                Material.WAXED_WEATHERED_CUT_COPPER,
                Material.WAXED_OXIDIZED_CUT_COPPER,
                Material.MUD_BRICKS,
            )

        fun defaultRules(): List<TransformationRule> =
            buildList {
                // Indestructible blocks (highest priority)
                add(
                    TransformationRule(
                        name = "Blacklisted Blocks",
                        priority = 100,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.INDESTRUCTIBLE_BLOCKS),
                        outcome = TransformationOutcome.NoTransformation,
                    ),
                )

                // Glass destruction
                add(
                    TransformationRule(
                        name = "Glass Destruction",
                        priority = 90,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isGlass),
                        outcome = TransformationOutcome.ToMaterial(Material.AIR),
                    ),
                )

                // Brick to iron bars
                add(
                    TransformationRule(
                        name = "Brick to Iron Bars",
                        priority = 85,
                        condition = TransformationCondition.MaterialSet(BRICK_MATERIALS),
                        outcome =
                            TransformationOutcome.ChanceOutcome(
                                chance = 0.2f,
                                trueOutcome = TransformationOutcome.ToMaterial(Material.IRON_BARS),
                                falseOutcome = TransformationOutcome.NoTransformation,
                            ),
                    ),
                )

                // Concrete to terracotta palette
                add(
                    TransformationRule(
                        name = "Concrete to Terracotta Palette",
                        priority = 82,
                        condition = TransformationCondition.MaterialSet(CONCRETE_MATERIALS),
                        outcome =
                            TransformationOutcome.ToPalette(
                                MaterialPalette(
                                    materials =
                                        setOf(
                                            MaterialPaletteEntry(Material.WHITE_TERRACOTTA, 3),
                                            MaterialPaletteEntry(Material.RED_TERRACOTTA, 3),
                                            MaterialPaletteEntry(Material.BLUE_TERRACOTTA, 3),
                                            MaterialPaletteEntry(Material.ANDESITE, 1),
                                        ),
                                ),
                            ),
                    ),
                )

                // Double slab destruction (higher priority than single slabs)
                add(
                    TransformationRule(
                        name = "Double Slab Destruction",
                        priority = 81,
                        condition = TransformationCondition.MaterialSet(DOUBLE_SLAB_BLOCKS),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_DOUBLE_SLAB_MATERIALS),
                    ),
                )

                // Slab destruction
                add(
                    TransformationRule(
                        name = "Slab Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isSlab),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_SLAB_MATERIALS),
                    ),
                )

                // Wall destruction
                add(
                    TransformationRule(
                        name = "Wall Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isWall),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_WALL_MATERIALS),
                    ),
                )

                // Stairs destruction
                add(
                    TransformationRule(
                        name = "Stairs Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isStairs),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_STAIRS_MATERIALS),
                    ),
                )

                // Light weight blocks
                add(
                    TransformationRule(
                        name = "Light Weight Block Destruction",
                        priority = 70,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.LIGHT_WEIGHT_BLOCKS),
                        outcome = TransformationOutcome.ToMaterial(Material.AIR),
                    ),
                )

                // Plant destruction - high power
                add(
                    TransformationRule(
                        name = "Plant Destruction - High Power",
                        priority = 60,
                        condition =
                            TransformationCondition.PowerThreshold(
                                TransformationCondition.MaterialSet(MaterialCategories.PLANTS),
                                minPower = 0.5f,
                            ),
                        outcome = TransformationOutcome.ToMaterial(Material.AIR),
                    ),
                )

                // Plant destruction - low power
                add(
                    TransformationRule(
                        name = "Plant Destruction - Low Power",
                        priority = 59,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.PLANTS),
                        outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DEAD_PLANTS),
                    ),
                )

                // Terrain transformation with noise
                add(
                    TransformationRule(
                        name = "Terrain Transformation with Noise",
                        priority = 55,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.TERRAIN_BLOCKS),
                        outcome =
                            TransformationOutcome.ToPaletteWithNoise(
                                MaterialPalette(
                                    materials =
                                        setOf(
                                            MaterialPaletteEntry(Material.COARSE_DIRT, 4),
                                            MaterialPaletteEntry(Material.MUD, 2),
                                            MaterialPaletteEntry(Material.MUDDY_MANGROVE_ROOTS, 1),
                                            MaterialPaletteEntry(Material.GRAVEL, 3),
                                        ),
                                    noiseScale = 0.05f,
                                ),
                            ),
                    ),
                )

                // Dirt/grass transformation
                add(
                    TransformationRule(
                        name = "Dirt/Grass Transformation",
                        priority = 50,
                        condition = TransformationCondition.MaterialSet(DIRT_GRASS_MATERIALS),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_DIRT_MATERIALS),
                    ),
                )

                // Default block destruction (lowest priority)
                add(
                    TransformationRule(
                        name = "Default Block Destruction",
                        priority = 0,
                        condition = TransformationCondition.MaterialCategory { true },
                        outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DESTROYED_BLOCK),
                    ),
                )
            }
    }
}
