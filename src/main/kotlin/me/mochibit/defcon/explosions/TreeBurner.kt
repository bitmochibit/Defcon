package me.mochibit.defcon.explosions

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.toVector3f
import me.mochibit.defcon.utils.FloodFill3D.getFloodFillBlock
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.World
import org.bukkit.block.Block
import org.bukkit.metadata.FixedMetadataValue
import org.joml.Vector3f
import org.joml.Vector3i
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class TreeBurner(
    private val world: World,
    private val center: Vector3i,
    private val maxTreeBlocks: Int = 500,
    private val transformationRule: TransformationRule
) {
    companion object {
        private const val LEAF_SUFFIX = "_LEAVES"
        private const val LOG_SUFFIX = "_LOG"
        private val TERRAIN_TYPES = setOf(Material.GRASS_BLOCK, Material.DIRT, Material.PODZOL)

        // New properties for the random tree falling feature
        private const val TREE_FALL_CHANCE = 0.35 // 35% chance for a tree to fall
        private const val MIN_POWER_FOR_AUTOMATIC_DESTRUCTION = 0.7
    }

    fun processTreeBurn(initialBlock: Block, normalizedExplosionPower: Double) {
        // Early exit if block is not part of a tree
        if (!isTreeBlock(initialBlock)) {
            return
        }

        val treeBlocks = getFloodFillBlock(initialBlock, maxTreeBlocks, ignoreEmpty = true) { block ->
            isTreeBlock(block)
        }

        if (treeBlocks.isEmpty()) {
            return
        }

        // Decide if this tree should completely fall over
        val shouldTreeFall = normalizedExplosionPower < MIN_POWER_FOR_AUTOMATIC_DESTRUCTION &&
                             Random.nextDouble() < TREE_FALL_CHANCE

        // Organize blocks by type with a single pass
        val leaves = mutableListOf<Location>()
        val logs = mutableListOf<Location>()
        val terrain = mutableListOf<Location>()

        for ((material, locations) in treeBlocks.entries) {
            val materialName = material.name
            when {
                materialName.endsWith(LEAF_SUFFIX) -> leaves.addAll(locations)
                materialName.endsWith(LOG_SUFFIX) -> logs.addAll(locations)
                material in TERRAIN_TYPES -> terrain.addAll(locations)
            }
        }

        // Process the tree depending on its fate
        if (shouldTreeFall) {
            processFallingTree(logs, leaves, terrain, normalizedExplosionPower)
        } else {
            processStandardTreeBurn(logs, leaves, terrain, normalizedExplosionPower)
        }
    }

    private fun isTreeBlock(block: Block): Boolean {
        val materialName = block.type.name
        return materialName.endsWith(LOG_SUFFIX) ||
               materialName.endsWith(LEAF_SUFFIX) ||
               block.type in TERRAIN_TYPES
    }

    private fun processStandardTreeBurn(
        logs: List<Location>,
        leaves: List<Location>,
        terrain: List<Location>,
        normalizedExplosionPower: Double
    ) {
        // Process leaves - always remove them
        leaves.forEach { leafLocation ->
            BlockChanger.addBlockChange(
                leafLocation.block,
                Material.AIR,
            )
        }

        // Calculate log properties once
        if (logs.isNotEmpty()) {
            val treeMinHeight = logs.minOf { it.y }
            val treeMaxHeight = logs.maxOf { it.y }
            val heightRange = (treeMaxHeight - treeMinHeight).coerceAtLeast(1.0)

            // Calculate shockwave direction once
            val initialLogLocation = logs.firstOrNull() ?: return
            val shockwaveDirection = initialLogLocation.subtract(
                center.x.toDouble(), center.y.toDouble(), center.z.toDouble()
            ).toVector3f().normalize()

            // Process logs with consistent tilt
            logs.forEach { logLocation ->
                processLogBlock(
                    logLocation,
                    treeMinHeight,
                    heightRange,
                    shockwaveDirection,
                    normalizedExplosionPower
                )
            }
        }

        // Process terrain - convert to scorched earth
        terrain.forEach { terrainLocation ->
            BlockChanger.addBlockChange(
                terrainLocation.block,
                Material.COARSE_DIRT,
            )
        }
    }

    private fun processFallingTree(
        logs: List<Location>,
        leaves: List<Location>,
        terrain: List<Location>,
        normalizedExplosionPower: Double
    ) {
        if (logs.isEmpty()) return

        // Determine tree properties
        val treeMinHeight = logs.minOf { it.y }
        val treeBase = logs.filter { it.y == treeMinHeight }
            .minByOrNull { it.distanceSquared(Location(world, center.x.toDouble(), center.y.toDouble(), center.z.toDouble())) }
            ?: logs.first()

        // Determine fall direction - away from explosion
        val fallDirection = treeBase.subtract(
            center.x.toDouble(), center.y.toDouble(), center.z.toDouble()
        ).toVector3f().normalize()

        // Randomize direction slightly for realism
        val randomAngle = Random.nextDouble(-Math.PI/6, Math.PI/6) // ±30 degrees
        val randomizedDirection = Vector3f(
            (fallDirection.x * cos(randomAngle) - fallDirection.z * sin(randomAngle)).toFloat(),
            fallDirection.y,
            (fallDirection.x * sin(randomAngle) + fallDirection.z * cos(randomAngle)).toFloat()
        ).normalize()

        // Process the fallen tree logs
        val treeHeight = logs.maxOf { it.y } - treeMinHeight
        logs.forEach { logLocation ->
            val heightFromBase = logLocation.y - treeMinHeight
            val fallDistance = heightFromBase * 1.5 * normalizedExplosionPower.coerceAtLeast(0.2)
            val newPosition = logLocation.clone().add(
                randomizedDirection.x * fallDistance,
                -heightFromBase * 0.8, // Logs closer to top fall more toward ground
                randomizedDirection.z * fallDistance
            )

            // The closer to the ground, the more likely to remain as wood
            val woodChance = 1.0 - (heightFromBase / treeHeight.coerceAtLeast(1.0)) * 0.7
            val newMaterial = if (Random.nextDouble() < woodChance) {
                // Find the original log type
                val blockData = logLocation.block.blockData
                logLocation.block.type // Preserve the original log type
            } else {
                Material.POLISHED_BASALT // Burned wood
            }

            // Move the log block to its fallen position
            BlockChanger.addBlockChange(
                newPosition.block,
                newMaterial,
            )

            // Remove the original log block
            if (newPosition.block.location != logLocation.block.location) {
                BlockChanger.addBlockChange(logLocation.block, Material.AIR)
            }
        }

        // Always remove leaves
        leaves.forEach { leafLocation ->
            BlockChanger.addBlockChange(
                leafLocation.block,
                Material.AIR,
            )
        }

        // Process terrain - convert to scorched earth
        terrain.forEach { terrainLocation ->
            BlockChanger.addBlockChange(
                terrainLocation.block,
                Material.COARSE_DIRT,
            )
        }
    }

    private fun processLogBlock(
        logLocation: Location,
        treeMinHeight: Double,
        heightRange: Double,
        shockwaveDirection: Vector3f,
        normalizedExplosionPower: Double
    ) {
        val logBlock = logLocation.block

        // Completely destroy logs if explosion power is strong enough
        if (normalizedExplosionPower > MIN_POWER_FOR_AUTOMATIC_DESTRUCTION) {
            BlockChanger.addBlockChange(logBlock, Material.AIR)
            return
        }

        // Calculate tilt based on height
        val blockHeight = logLocation.y - treeMinHeight
        val heightFactor = blockHeight / heightRange
        val tiltFactor = if (logLocation.y.toInt() == treeMinHeight.toInt()) {
            0.0 // Base of tree doesn't move
        } else {
            heightFactor * normalizedExplosionPower * 6 // Smooth gradient tilt
        }

        val newPosition = logLocation.clone().add(
            shockwaveDirection.x * tiltFactor,
            0.0,
            shockwaveDirection.z * tiltFactor
        )

        // Change the block and mark it as processed
        BlockChanger.addBlockChange(
            newPosition.block,
            Material.POLISHED_BASALT,
        )

        // Remove the original block if it moved
        if (newPosition.block.location != logBlock.location) {
            BlockChanger.addBlockChange(logBlock, Material.AIR)
        }
    }
}