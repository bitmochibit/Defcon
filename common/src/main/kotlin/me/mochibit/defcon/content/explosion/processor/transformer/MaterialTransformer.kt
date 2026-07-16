package me.mochibit.defcon.content.explosion.processor.transformer

import me.mochibit.defcon.foundation.util.copyPropertiesTo
import net.minecraft.world.level.block.Blocks
import net.minecraft.world.level.block.state.BlockState
import kotlin.random.Random


class MaterialTransformer(
    rules: List<TransformationRule> = defaultRules(),
    val random: Random = Random.Default,
) {
    private val sortedRules: List<TransformationRule> by lazy {
        rules.sortedByDescending { it.priority }
    }

    fun transformMaterial(
        currentState: BlockState,
        explosionPower: Float,
        x: Int = 0, z: Int = 0, y: Int = 0,
    ): BlockState {
        val rule = sortedRules.find { it.matches(currentState, explosionPower) } ?: return currentState
        val transformed = rule.transform(currentState, explosionPower, random, x, z, y)
        return currentState.copyPropertiesTo(transformed)
    }

    companion object {
        private val BRICK_MATERIALS =
            setOf(
                Blocks.BRICKS.defaultBlockState(),
                Blocks.BRICK_WALL.defaultBlockState(),
                Blocks.BRICK_STAIRS.defaultBlockState(),
                Blocks.BRICK_SLAB.defaultBlockState(),
            )

        private val CONCRETE_MATERIALS =
            setOf(
                Blocks.WHITE_CONCRETE.defaultBlockState(),
                Blocks.RED_CONCRETE.defaultBlockState(),
                Blocks.BLUE_CONCRETE.defaultBlockState(),
            )

        private val DESTROYED_SLAB_MATERIALS =
            setOf(
                Blocks.ANDESITE_SLAB.defaultBlockState(),
                Blocks.GRANITE_SLAB.defaultBlockState(),
                Blocks.COBBLESTONE_SLAB.defaultBlockState(),
            )

        private val DESTROYED_WALL_MATERIALS =
            setOf(
                Blocks.ANDESITE_WALL.defaultBlockState(),
                Blocks.GRANITE_WALL.defaultBlockState(),
                Blocks.COBBLESTONE_WALL.defaultBlockState(),
            )

        private val DESTROYED_STAIRS_MATERIALS =
            setOf(
                Blocks.ANDESITE_STAIRS.defaultBlockState(),
                Blocks.GRANITE_STAIRS.defaultBlockState(),
                Blocks.COBBLESTONE_STAIRS.defaultBlockState(),
            )

        private val DIRT_GRASS_MATERIALS =
            setOf(
                Blocks.DIRT.defaultBlockState(),
                Blocks.GRASS_BLOCK.defaultBlockState(),
            )

        private val DESTROYED_DIRT_MATERIALS =
            setOf(
                Blocks.COARSE_DIRT.defaultBlockState(),
                Blocks.MUD.defaultBlockState(),
                Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(),
            )

        fun defaultRules(): List<TransformationRule> =
            buildList {
                add(
                    TransformationRule(
                        name = "Blacklisted Blocks",
                        priority = 100,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.INDESTRUCTIBLE_BLOCKS),
                        outcome = TransformationOutcome.NoTransformation,
                    ),
                )

                add(
                    TransformationRule(
                        name = "Carbonized trees destruction",
                        priority = 100,
                        condition = TransformationCondition.SpecificMaterial(Blocks.POLISHED_BASALT.defaultBlockState()),
                        outcome = TransformationOutcome.ToMaterial(Blocks.AIR.defaultBlockState()),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Glass Destruction",
                        priority = 90,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isGlass),
                        outcome = TransformationOutcome.ToMaterial(Blocks.AIR.defaultBlockState()),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Light Block Destruction",
                        priority = 90,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.LIGHT_BLOCKS),
                        outcome = TransformationOutcome.ToMaterial(Blocks.AIR.defaultBlockState()),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Brick to Iron Bars",
                        priority = 85,
                        condition = TransformationCondition.MaterialSet(BRICK_MATERIALS),
                        outcome =
                            TransformationOutcome.ChanceOutcome(
                                chance = 0.2f,
                                trueOutcome = TransformationOutcome.ToMaterial(Blocks.IRON_BARS.defaultBlockState()),
                                falseOutcome = TransformationOutcome.NoTransformation,
                            ),
                    ),
                )

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
                                            MaterialPaletteEntry(Blocks.WHITE_TERRACOTTA.defaultBlockState(), 3),
                                            MaterialPaletteEntry(Blocks.RED_TERRACOTTA.defaultBlockState(), 3),
                                            MaterialPaletteEntry(Blocks.BLUE_TERRACOTTA.defaultBlockState(), 3),
                                            MaterialPaletteEntry(Blocks.ANDESITE.defaultBlockState(), 1),
                                        ),
                                ),
                            ),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Slab Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isSlab),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_SLAB_MATERIALS),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Wall Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isWall),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_WALL_MATERIALS),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Stairs Destruction",
                        priority = 80,
                        condition = TransformationCondition.MaterialCategory(MaterialCategories::isStairs),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_STAIRS_MATERIALS),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Light Weight Block Destruction",
                        priority = 70,
                        condition = TransformationCondition.MaterialSet(MaterialCategories.LIGHT_WEIGHT_BLOCKS),
                        outcome = TransformationOutcome.ToMaterial(Blocks.AIR.defaultBlockState()),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Plant Destruction - High Power",
                        priority = 60,
                        condition = TransformationCondition.PowerThreshold(
                            TransformationCondition.BlockSet.fromStates(MaterialCategories.PLANTS),
                            minPower = 0.5f,
                        ),
                        outcome = TransformationOutcome.ToMaterial(Blocks.AIR.defaultBlockState()),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Plant Destruction - Low Power",
                        priority = 59,
                        condition = TransformationCondition.BlockSet.fromStates(MaterialCategories.PLANTS),
                        outcome = TransformationOutcome.ToRandomMaterial(MaterialCategories.DEAD_PLANTS),
                    ),
                )

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
                                            MaterialPaletteEntry(Blocks.COARSE_DIRT.defaultBlockState(), 4),
                                            MaterialPaletteEntry(Blocks.MUD.defaultBlockState(), 2),
                                            MaterialPaletteEntry(Blocks.MUDDY_MANGROVE_ROOTS.defaultBlockState(), 1),
                                            MaterialPaletteEntry(Blocks.GRAVEL.defaultBlockState(), 3),
                                        ),
                                    noiseScale = 0.05f,
                                ),
                            ),
                    ),
                )

                add(
                    TransformationRule(
                        name = "Dirt/Grass Transformation",
                        priority = 50,
                        condition = TransformationCondition.MaterialSet(DIRT_GRASS_MATERIALS),
                        outcome = TransformationOutcome.ToRandomMaterial(DESTROYED_DIRT_MATERIALS),
                    ),
                )

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


