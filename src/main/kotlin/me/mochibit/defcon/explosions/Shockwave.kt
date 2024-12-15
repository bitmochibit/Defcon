package me.mochibit.defcon.explosions

import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.utils.MathFunctions
import org.bukkit.Location
import org.bukkit.Material
import org.bukkit.Particle
import org.bukkit.block.Block
import org.bukkit.entity.Entity
import org.bukkit.entity.LivingEntity
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import org.joml.Vector3i
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.ForkJoinPool
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Double,
    private val shockwaveSpeed: Long = 200L
) {

    companion object {
        private val BLOCK_TRANSFORMATION_BLACKLIST = hashSetOf(
            Material.BEDROCK,
            Material.BARRIER,
            Material.COMMAND_BLOCK,
            Material.COMMAND_BLOCK_MINECART,
            Material.END_PORTAL_FRAME,
            Material.END_PORTAL
        )
        private val LIQUID_MATERIALS = hashSetOf(Material.WATER, Material.LAVA)
        val transformationMap = mapOf(
            Material.GRASS_BLOCK to { listOf(Material.COARSE_DIRT, Material.DIRT, Material.GRAVEL).random() },
            Material.DIRT to { listOf(Material.COARSE_DIRT, Material.GRAVEL).random() },
            Material.STONE to { Material.COBBLED_DEEPSLATE },
            Material.COBBLESTONE to { Material.COBBLED_DEEPSLATE }
        )

        // Custom rules for materials based on name suffix
        val customTransformation: (Material) -> Material? = { blockType ->
            when {
                blockType.name.endsWith("_SLAB") -> Material.COBBLED_DEEPSLATE_SLAB
                blockType.name.endsWith("_WALL") -> Material.COBBLED_DEEPSLATE_WALL
                blockType.name.endsWith("_STAIRS") -> Material.COBBLED_DEEPSLATE_STAIRS
                else -> null
            }
        }
    }


    private val maximumDistanceForAction = 4.0
    private val maxTreeBlocks = 400
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0

    private val navigatedTreeBlocks = ConcurrentHashMap.newKeySet<Vector3i>()

    private val world = center.world
    private val explosionColumns: ConcurrentLinkedQueue<Pair<Double, Set<Vector3i>>> = ConcurrentLinkedQueue()

    private val executorService = ForkJoinPool.commonPool()

    private val completedExplosion = AtomicBoolean(false)

    fun explode() {
        val locationCursor = Location(center.world, -1.0, 0.0, 0.0)
        val entities = world.getNearbyEntities(
            center,
            shockwaveRadius.toDouble(),
            shockwaveRadius.toDouble(),
            shockwaveRadius.toDouble()
        )
        val visitedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
        executorService.submit {
            (shockwaveRadiusStart..shockwaveRadius).forEach { currentRadius ->
                val explosionPower = MathFunctions.lerp(
                    minDestructionPower,
                    maxDestructionPower,
                    currentRadius / shockwaveRadius.toDouble()
                )
                val columns = generateShockwaveColumns(currentRadius)
                explosionColumns.add(explosionPower to columns)
                columns.parallelStream().forEach { location ->
                    locationCursor.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                    world.spawnParticle(Particle.EXPLOSION_HUGE, locationCursor, 0)
                    entities.parallelStream().forEach entityLoop@{ entity ->
                        if (visitedEntities.contains(entity)) return@entityLoop
                        val entityLocation = entity.location
                        val dx = entityLocation.x - locationCursor.x
                        val dz = entityLocation.z - locationCursor.z

                        val hDistanceSquared = dx * dx + dz * dz

                        // Check if the entity is within range and height bounds
                        if (hDistanceSquared <= maximumDistanceForAction * maximumDistanceForAction &&
                            entityLocation.y in locationCursor.y - maximumDistanceForAction..(locationCursor.y + shockwaveHeight + maximumDistanceForAction)
                        ) {
                            applyExplosionEffects(entity, explosionPower.toFloat())
                            visitedEntities.add(entity)
                        }

                    }
                }
                // Wait for shockwaveSpeed (blocks per second) and convert it to a MILLIS wait time (how much it waits for the next radius)
                TimeUnit.MILLISECONDS.sleep((1000 / shockwaveSpeed))
            }
            startExplosionProcessor()
            completedExplosion.set(true)
        }
    }

    private fun startExplosionProcessor() {
        executorService.submit {
            while (explosionColumns.isNotEmpty() || !completedExplosion.get()) {
                val rColumns = explosionColumns.poll() ?: continue
                val (explosionPower, locations) = rColumns
                locations.parallelStream().forEach { location ->
                    simulateExplosion(location, explosionPower)
                }
            }
        }
    }

    private val directions = listOf(
        Triple(1, 0, 0), Triple(-1, 0, 0),
        Triple(0, 1, 0), Triple(0, -1, 0),
        Triple(0, 0, 1), Triple(0, 0, -1)
    )

    private fun processTreeBurn(location: Vector3i, explosionPower: Double) {
        var navigatedBlocks = 0
        if (navigatedTreeBlocks.size >= maxTreeBlocks*2)
            navigatedTreeBlocks.clear()

        // Helper function to process leaves
        fun processLeaves(leafBlock: Block) {
            val chance = explosionPower / maxDestructionPower
            runLater(1L) {
                // Destroy leaves and very rarely set them to MANGROOVE_ROOTS, otherwise set them to AIR
                leafBlock.type =  if (Random.nextDouble() < chance * 0.1) Material.MANGROVE_ROOTS else Material.AIR
            }
        }

        // Helper function to recursively process the tree
        fun burnTree(x: Int, y: Int, z: Int) {
            if (navigatedBlocks >= maxTreeBlocks || !navigatedTreeBlocks.add(Vector3i(x,y,z))) return
            navigatedBlocks++

            val currentBlock = world.getBlockAt(x, y, z)
            val blockType = currentBlock.type

            runLater(1L) {
                when {
                    blockType.name.endsWith("_LOG") -> currentBlock.type = Material.POLISHED_BASALT
                    blockType.name.endsWith("_LEAVES") -> processLeaves(currentBlock)
                    else -> return@runLater // Exit recursion if block is neither log nor leaves
                }
            }
            // Explore neighbors (6 directions: up, down, north, south, east, west)

            for ((dx, dy, dz) in directions) {
                val neighborX = x + dx
                val neighborY = y + dy
                val neighborZ = z + dz

                if (neighborY in world.minHeight..world.maxHeight) {
                    val neighborBlock = world.getBlockAt(neighborX, neighborY, neighborZ)
                    if (neighborBlock.type.name.endsWith("_LOG") || neighborBlock.type.name.endsWith("_LEAVES")) {
                        burnTree(neighborX, neighborY, neighborZ)
                    }
                }
            }
        }

        // Start processing the tree from the initial location
        burnTree(location.x, location.y, location.z)
    }


    private fun simulateExplosion(location: Vector3i, explosionPower: Double) {
        val baseX = location.x
        val baseY = location.y
        val baseZ = location.z
        val radius = (explosionPower / 2).toInt()
        val worldMaxHeight = world.maxHeight
        val worldMinHeight = world.minHeight
        val radiusSquared = radius * radius

        val blockChanges = mutableListOf<Pair<Block, Material>>()

        // Iterate only within the sphere's bounds
        for (x in -radius..radius) {
            val xSquared = x * x
            for (y in -radius..radius) {
                val ySquared = y * y
                for (z in -radius..radius) {
                    val zSquared = z * z
                    val distanceSquared = xSquared + ySquared + zSquared

                    if (distanceSquared > radiusSquared) continue

                    val newX = baseX + x
                    val newY = (baseY + y).coerceIn(worldMinHeight, worldMaxHeight)
                    val newZ = baseZ + z

                    val block = world.getBlockAt(newX, newY, newZ)
                    val blockType = block.type
                    if (blockType == Material.AIR || blockType in BLOCK_TRANSFORMATION_BLACKLIST || blockType in LIQUID_MATERIALS)
                        continue

                    if (blockType.name.endsWith("_LOG") || blockType.name.endsWith("_LEAVES")) {
                        processTreeBurn(Vector3i(newX, newY, newZ), explosionPower)
                        continue
                    }

                    val newMaterial = customTransformation(blockType) ?: transformationMap[blockType]?.invoke()
                    if (newMaterial != null) {
                        blockChanges.add(block to newMaterial)
                    } else {
                        blockChanges.add(block to Material.AIR)
                    }
                }
            }
        }

        // Apply block changes in bulk
        blockChanges.parallelStream().forEach { (block, material) ->
            runLater(1L) {
                block.setType(material, false)
            }
        }
    }


    private fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        runLater(1L) {
            val knockback =
                Vector(entity.location.x - center.x, entity.location.y - center.y, entity.location.z - center.z)
                    .normalize().multiply(explosionPower * 2.0)
            entity.velocity = knockback

            if (entity !is LivingEntity) return@runLater

            entity.damage(explosionPower * 4.0)
            if (entity !is Player) return@runLater

            val inv = 2 / explosionPower
            try {
                CameraShake(entity, CameraShakeOptions(1.6f, 0.04f, 3.7f * inv, 3.0f * inv))
            } catch (e: Exception) {
                println("Error applying CameraShake: ${e.message}")
            }
        }
    }


    private fun generateShockwaveColumns(radius: Int): Set<Vector3i> {
        val ringElements = mutableSetOf<Vector3i>()
        val steps = radius * 6
        val angleIncrement = 2 * Math.PI / steps

        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ

        var previousHeight: Int? = null
        var previousPosition = Vector3i(centerX, centerY, centerZ)

        for (i in 0 until steps) {
            val angleRad = i * angleIncrement
            val cosAngle = cos(angleRad)
            val sinAngle = sin(angleRad)

            val x = (centerX + cosAngle * radius).toInt()
            val z = (centerZ + sinAngle * radius).toInt()
            val highestY = world.getHighestBlockYAt(x, z)

            if (previousHeight != null) {
                val heightDiff = abs(previousHeight - highestY)
                if (heightDiff > 1) {
                    // Calculate interpolated positions to include walls
                    val dx = x - previousPosition.x
                    val dz = z - previousPosition.z
                    val dy = highestY - previousHeight

                    for (step in 1..heightDiff) {
                        val interpX = previousPosition.x + (dx * step) / heightDiff
                        val interpY = previousHeight + (dy * step) / heightDiff
                        val interpZ = previousPosition.z + (dz * step) / heightDiff

                        ringElements.add(Vector3i(interpX, interpY, interpZ))
                    }
                }
            }

            ringElements.add(Vector3i(x, highestY, z))
            previousHeight = highestY
            previousPosition = Vector3i(x, highestY, z)
        }

        return ringElements
    }

}
