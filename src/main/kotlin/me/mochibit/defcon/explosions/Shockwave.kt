package me.mochibit.defcon.explosions

import me.mochibit.defcon.threading.scheduling.runLater
import me.mochibit.defcon.threading.scheduling.runSyncMethod
import me.mochibit.defcon.utils.FloodFill3D.getFloodFillBlock
import me.mochibit.defcon.utils.MathFunctions
import me.mochibit.defcon.utils.MathFunctions.remap
import org.bukkit.*
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
import kotlin.math.round
import kotlin.math.sin
import kotlin.random.Random

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Int,
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
        private val DEAD_PLANTS = hashSetOf(
            Material.DEAD_BUSH,
            Material.WITHER_ROSE
        )
        private val BURNT_BLOCK = hashSetOf(
            Material.COBBLED_DEEPSLATE,
            Material.BLACK_CONCRETE_POWDER,
            Material.OBSIDIAN,
        )
        private val PLANTS = hashSetOf(
            Material.GRASS,
            Material.TALL_GRASS,

            Material.FERN,
            Material.LARGE_FERN,

            Material.OAK_SAPLING,
            Material.BIRCH_SAPLING,
            Material.JUNGLE_SAPLING,
            Material.ACACIA_SAPLING,
            Material.DARK_OAK_SAPLING,
            Material.SPRUCE_SAPLING,
            Material.CHERRY_SAPLING,
            Material.BAMBOO_SAPLING,

            Material.POPPY,
            Material.DANDELION,
            Material.BLUE_ORCHID,
            Material.ALLIUM,
            Material.AZURE_BLUET,
            Material.OXEYE_DAISY,
            Material.CORNFLOWER,
            Material.LILY_OF_THE_VALLEY,
            Material.PINK_PETALS,
            Material.LILAC,
            Material.PEONY,
            Material.SUNFLOWER,
            Material.RED_TULIP,
            Material.ORANGE_TULIP,
            Material.WHITE_TULIP,
            Material.PINK_TULIP,
        )
    }

    private val transformationMap = mapOf(
        Material.GRASS_BLOCK to ::transformGrassBlock,
        Material.DIRT to ::transformDirt,
        Material.STONE to { Material.COBBLED_DEEPSLATE },
        Material.COBBLESTONE to { Material.COBBLED_DEEPSLATE },
        Material.ICE to { Material.AIR },
        Material.PACKED_ICE to { Material.AIR },
        Material.BLUE_ICE to { Material.AIR },
        Material.FROSTED_ICE to { Material.AIR },
        Material.SNOW to { Material.AIR },
        Material.SNOW_BLOCK to { Material.AIR },
        Material.POWDER_SNOW to { Material.AIR },
    )

    private val regexTransformationMap: Map<Regex, (Material, Double) -> Material> = mapOf(
        Regex(".*sapling.*", RegexOption.IGNORE_CASE) to { _, normalizedExplosionPower ->
            transformToDeadPlantOrAir(normalizedExplosionPower)
        },

        Regex(".*_(SLAB|WALL|STAIRS)") to { material, _ ->
            when {
                material.name.endsWith("_SLAB") -> Material.COBBLED_DEEPSLATE_SLAB
                material.name.endsWith("_WALL") -> Material.COBBLED_DEEPSLATE_WALL
                material.name.endsWith("_STAIRS") -> Material.COBBLED_DEEPSLATE_STAIRS
                else -> Material.AIR // Fallback
            }
        }

    )

    private fun transformToDeadPlantOrAir(normalizedExplosionPower: Double): Material {
        return if (normalizedExplosionPower > 0.5) Material.AIR else DEAD_PLANTS.random()
    }


    // Custom rules for materials based on name suffix
    private fun customTransformation(currentMaterial: Material, normalizedExplosionPower: Double): Material {
        if (normalizedExplosionPower > 0.8)
            return BURNT_BLOCK.random()

        return when (currentMaterial) {
            in transformationMap -> transformationMap[currentMaterial]?.invoke() ?: Material.AIR
            in PLANTS -> transformToDeadPlantOrAir(normalizedExplosionPower)
            // Check if the block type matches any of the regex patterns
            else -> regexTransformationMap.entries.firstOrNull { (regex, _) -> regex.matches(currentMaterial.name) }
                ?.value?.invoke(currentMaterial, normalizedExplosionPower) ?: Material.AIR
        }
    }

    private fun transformGrassBlock(): Material = if (Random.nextBoolean()) Material.COARSE_DIRT else Material.DIRT
    private fun transformDirt(): Material =
        if (Random.nextBoolean()) Material.COARSE_DIRT else Material.COBBLED_DEEPSLATE

    private val maximumDistanceForAction = 4.0
    private val maxTreeBlocks = 500
    private val maxDestructionPower = 5.0
    private val minDestructionPower = 2.0

    private val world = center.world
    private val centerVector = Vector3i(center.blockX, center.blockY, center.blockZ)
    private val explosionColumns: ConcurrentLinkedQueue<Pair<Double, List<Vector3i>>> = ConcurrentLinkedQueue()

    private val executorService = ForkJoinPool.commonPool()

    private val completedExplosion = AtomicBoolean(false)

    private val blockChanger = BlockChanger()

    private val chunkCache = ChunkCache(world)

    fun explode() {
        val locationCursor = Location(center.world, -1.0, 0.0, 0.0)
        val entities = world.getNearbyEntities(
            center,
            shockwaveRadius.toDouble(),
            shockwaveRadius.toDouble(),
            shockwaveRadius.toDouble()
        )
        val visitedEntities: MutableSet<Entity> = ConcurrentHashMap.newKeySet()
        startExplosionProcessor()

        val startTime = System.nanoTime()
        executorService.submit {
            var lastProcessedRadius = shockwaveRadiusStart

            while (lastProcessedRadius <= shockwaveRadius) {
                // Calculate elapsed time in seconds
                val elapsedSeconds = (System.nanoTime() - startTime) / 1_000_000_000.0

                // Determine the current radius based on shockwaveSpeed (blocks per second)
                val currentRadius = (shockwaveRadiusStart + elapsedSeconds * shockwaveSpeed).toInt()

                // Skip if the radius hasn't advanced yet
                if (currentRadius <= lastProcessedRadius) {
                    Thread.yield() // Let other tasks proceed
                    continue
                }

                // Process new radii
                (lastProcessedRadius + 1..currentRadius).forEach { radius ->
                    val explosionPower = MathFunctions.lerp(
                        maxDestructionPower,
                        minDestructionPower,
                        radius / shockwaveRadius.toDouble()
                    )
                    val columns = generateShockwaveColumns(radius)
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
                }

                lastProcessedRadius = currentRadius
            }
            completedExplosion.set(true)
        }
    }



    private fun startExplosionProcessor() {
        blockChanger.start()
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

    private fun processTreeBurn(location: Vector3i, normalizedExplosionPower: Double) {
        val treeBlocks =
            getFloodFillBlock(world.getBlockAt(location.x, location.y, location.z), maxTreeBlocks, ignoreEmpty = true) {
                it.type.name.endsWith("_LOG") || it.type.name.endsWith("_LEAVES") || it.type == Material.GRASS_BLOCK || it.type == Material.DIRT || it.type == Material.PODZOL
            }

        // Match the treeBlocks keys which ends with LEAVES, and process them
        treeBlocks.filter { it.key.name.endsWith("_LEAVES") }.forEach { (_, blocks) ->
            for (leafBlockLocation in blocks) {
                val leafBlock = leafBlockLocation.block
                if (normalizedExplosionPower > 0.7) {
                    blockChanger.addBlockChange(leafBlock, Material.AIR)
                    continue
                }
                blockChanger.addBlockChange(
                    leafBlock,
                    if (Random.nextDouble() > normalizedExplosionPower * 0.4) Material.MANGROVE_ROOTS else Material.AIR
                )
            }
        }

        // Match the treeBlocks keys which ends with LOG, and process them
        treeBlocks.filter { it.key.name.endsWith("_LOG") }.forEach { (_, blocks) ->
            for (logBlockLocation in blocks) {
                val logBlock = logBlockLocation.block
                if (normalizedExplosionPower > 0.8) {
                    blockChanger.addBlockChange(logBlock, Material.AIR)
                    continue
                }
                blockChanger.addBlockChange(logBlock, Material.POLISHED_BASALT)
            }
        }

        treeBlocks.filter { it.key == Material.GRASS_BLOCK || it.key == Material.DIRT || it.key == Material.PODZOL }
            .forEach { (_, terrainBlocks) ->
                for (blockLocation in terrainBlocks) {
                    val block = blockLocation.block
                    blockChanger.addBlockChange(block, customTransformation(block.type, normalizedExplosionPower))
                }
            }
    }


    private fun simulateExplosion(location: Vector3i, explosionPower: Double) {
        val baseX = location.x
        val baseY = location.y
        val baseZ = location.z
        val normalizedExplosionPower = remap(explosionPower, minDestructionPower, maxDestructionPower, 0.0, 1.0)
//        val skipChance = 1.0 - normalizedExplosionPower
//        if (Random.nextDouble() < skipChance) return

        val block = world.getBlockAt(baseX, baseY, baseZ)
        val blockType = block.type

        if (blockType == Material.AIR || blockType in BLOCK_TRANSFORMATION_BLACKLIST || blockType in LIQUID_MATERIALS)
            return

        if (blockType.name.endsWith("_LOG") || blockType.name.endsWith("_LEAVES")) {
            processTreeBurn(location, normalizedExplosionPower)
            return
        }

        val newMaterial = customTransformation(blockType, normalizedExplosionPower)
        blockChanger.addBlockChange(block, newMaterial, true)

    }

    private fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        val knockback =
            Vector(entity.location.x - center.x, entity.location.y - center.y, entity.location.z - center.z)
                .normalize().multiply(explosionPower * 2.0)
        runLater(1L) {
            entity.velocity = knockback
        }

        if (entity !is LivingEntity) return

        runLater(1L) {
            entity.damage(explosionPower * 4.0)
        }
        if (entity !is Player) return

        val inv = 2 / explosionPower
        try {
            CameraShake(entity, CameraShakeOptions(1.6f, 0.04f, 3.7f * inv, 3.0f * inv))
        } catch (e: Exception) {
            println("Error applying CameraShake: ${e.message}")
        }

    }

    private fun generateShockwaveColumns(radius: Int): List<Vector3i> {
        val ringElements = calculateCircle(radius)

        var previousHeight: Int? = null
        val previousPosition = Vector3i(0, 0, 0)

        val wallPoints = mutableListOf<Vector3i>()

        // Process each point and interpolate walls
        for (pos in ringElements) {
            val highestY = chunkCache.highestBlockYAt(pos.x, pos.z)
            pos.y = highestY

            if (previousHeight == null) {
                previousHeight = highestY
                previousPosition.set(pos.x, highestY, pos.z)
                continue
            }

            val heightDiff = abs(previousHeight - highestY)

            if (heightDiff > 0) {
                // Include all wall points between previousHeight and highestY
                val minY = minOf(previousHeight, highestY)
                val maxY = maxOf(previousHeight, highestY)

                for (y in minY..maxY) {
                    wallPoints.add(Vector3i(pos.x, y, pos.z))
                }
            }

            previousHeight = highestY
            previousPosition.set(pos.x, highestY, pos.z)
        }

        return ringElements
    }

    private fun calculateCircle(radius: Int): MutableList<Vector3i> {
        val offsets = mutableListOf<Vector3i>()
        val centerX = center.blockX
        val centerZ = center.blockZ

        val steps = (2 * Math.PI * radius).toInt() * 4 // Adjust multiplier for smoothness

        for (i in 0 until steps) {
            val angle = 2 * Math.PI * i / steps
            val x = round(centerX + radius * cos(angle)).toInt()
            val z = round(centerZ + radius * sin(angle)).toInt()

            val point = Vector3i(x, 0, z)
            if (!offsets.contains(point)) {
                offsets.add(point)
            }
        }

        return offsets
    }
}


class ChunkCache(private val world: World) {
    private val chunkSnapshots = mutableMapOf<Pair<Int, Int>, ChunkSnapshot>()
    private val accessCounts = mutableMapOf<Pair<Int, Int>, Int>()
    private val maxAccessCount = 20 // Define the access limit before cleanup

    private fun getChunkSnapshot(x: Int, z: Int): ChunkSnapshot {
        val chunkX = x shr 4
        val chunkZ = z shr 4
        val key = chunkX to chunkZ

        accessCounts[key] = accessCounts.getOrDefault(key, 0) + 1
        cleanupUnusedChunks()

        return chunkSnapshots.getOrPut(key) {
            world.getChunkAt(chunkX, chunkZ).chunkSnapshot
        }
    }

    private fun cleanupUnusedChunks() {
        val iterator = accessCounts.iterator()
        while (iterator.hasNext()) {
            val (key, count) = iterator.next()
            if (count > maxAccessCount) {
                chunkSnapshots.remove(key)
                iterator.remove()
            }
        }
    }

    fun highestBlockYAt(x: Int, z: Int): Int {
        return getChunkSnapshot(x, z).getHighestBlockYAt(x and 15, z and 15)
    }

    fun getBlockMaterial(x: Int, y: Int, z: Int): Material {
        return getChunkSnapshot(x, z).getBlockType(x and 15, y, z and 15)
    }
}

