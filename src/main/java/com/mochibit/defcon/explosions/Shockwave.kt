package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.observer.Loadable
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.block.data.BlockData
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import org.joml.Vector3i
import java.time.Duration
import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.*

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Double,
    private val shockwaveSpeed: Long = 50L
) : Loadable<Unit> {
    override var isLoaded: Boolean = false
    override val observers: MutableList<(Unit) -> Unit> = mutableListOf()

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
        private val RUINED_BLOCKS = hashSetOf(Material.DEEPSLATE, Material.COBBLESTONE, Material.BLACKSTONE)
        private val RUINED_STAIRS_BLOCK =
            hashSetOf(Material.COBBLED_DEEPSLATE_STAIRS, Material.COBBLESTONE_STAIRS, Material.BLACKSTONE_STAIRS)
        private val RUINED_SLABS_BLOCK =
            hashSetOf(Material.COBBLED_DEEPSLATE_SLAB, Material.COBBLESTONE_SLAB, Material.BLACKSTONE_SLAB)
        private val RUINED_WALLS_BLOCK =
            hashSetOf(Material.COBBLED_DEEPSLATE_WALL, Material.COBBLESTONE_WALL, Material.BLACKSTONE_WALL)
        private val LOG_REPLACEMENTS =
            hashSetOf(Material.POLISHED_BASALT, Material.BASALT, Material.MANGROVE_ROOTS, Material.CRIMSON_STEM)
        private val DIRT_REPLACEMENTS = hashSetOf(Material.COARSE_DIRT, Material.PODZOL)
    }

    private val world = center.world
    private val explosionSchedule = ScheduledRunnable(999999).maxMillisPerTick(60.0)
    private val explosionColumns: ConcurrentLinkedQueue<Triple<Int, Float, Set<Vector3i>>> = ConcurrentLinkedQueue()
    //private val chunkSnapshotBuffer = ChunkSnapshotBuffer(world)
    private val processedBlockCoordinates = ConcurrentHashMap.newKeySet<Triple<Int, Int, Int>>()


    private val executor = Executors.newCachedThreadPool()
    val isExploding = AtomicBoolean(false)

    override fun load() {
        explosionColumns.clear()
        executor.submit {
            // Convert the range into a parallel stream for concurrent execution
            (shockwaveRadiusStart..shockwaveRadius).forEach { currentRadius ->
                val explosionPower = MathFunctions.lerp(4.0, 16.0, currentRadius / shockwaveRadius.toDouble())
                val columns = generateShockwaveColumns(currentRadius)
                // Use thread-safe operations for concurrent data access
                explosionColumns.offer(Triple(currentRadius, explosionPower.toFloat(), columns))
            }
            // Mark as loaded and notify observers
            isLoaded = true
            observers.forEach { it.invoke(Unit) }
        }
    }


    fun explode() {
        explosionSchedule.clear()
        executor.submit {
            val locationCursor = Location(center.world, .0, .0, .0)
            startExplosionProcessor()
            isExploding.set(true)
            explosionColumns.forEach { (_, explosionPower, columns) ->
                columns.forEach { location ->
                    locationCursor.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                    world.spawnParticle(Particle.EXPLOSION_HUGE, locationCursor, 1)
                    Bukkit.getScheduler().runTaskLater(Defcon.instance, { task ->
                        val temp = Location(center.world, locationCursor.x, locationCursor.y, locationCursor.z)
                        locationCursor.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                        world.getNearbyLivingEntities(locationCursor, 3.0, 2.0, 3.0)
                            .forEach { entity ->
                                applyExplosionEffects(entity, explosionPower)
                            }
                        locationCursor.set(temp.x, temp.y, temp.z)
                    }, 4L)
                }
                Thread.sleep(1/shockwaveSpeed)
            }
            isExploding.set(false)
        }
    }

    private fun startExplosionProcessor() {
        executor.submit {
            explosionSchedule.start()
            while (!explosionColumns.isEmpty() || isExploding.get()) {
                val rColumns = explosionColumns.poll() ?: continue
                val (currentRadius, explosionPower, locations) = rColumns

                locations.forEach { location ->
                    simulateExplosion(location, explosionPower, currentRadius)
                }
            }
        }
    }

    private fun simulateExplosion(location: Vector3i, explosionPower: Float, currentRadius: Int = 0) {
        val baseX = location.x
        val baseY = location.y
        val baseZ = location.z

        val radius = (explosionPower / 2).toInt()
        val worldMaxHeight = world.maxHeight
        val worldMinHeight = world.minHeight
        val radiusSquared = radius * radius

        // Cache the block positions and types to reduce world accesses
        val blockChanges = mutableListOf<Pair<Block, Material>>()

        // Process explosion destruction
        (-radius..radius).forEach { x ->
            (-radius..radius).forEach { y ->
                (-radius..radius).forEach zLoop@{ z ->
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared > radiusSquared) return@zLoop

                    val newX = baseX + x
                    val newY = (baseY + y).coerceIn(worldMinHeight, worldMaxHeight)
                    val newZ = baseZ + z

                    // Check if block has already been processed
                    if (!processedBlockCoordinates.add(Triple(newX, newY, newZ))) return@zLoop

                    val block = world.getBlockAt(newX, newY, newZ)
                    if (block.type == Material.AIR || BLOCK_TRANSFORMATION_BLACKLIST.contains(block.type) || LIQUID_MATERIALS.contains(
                            block.type
                        )
                    ) return@zLoop
                    // Uniform destruction (replace block with air)
                    blockChanges.add(block to Material.AIR)
                }
            }
        }

        // Apply shockwave effect (create flattened terrain transformation)
        val shockwaveRadius = radius + 1
        val shockwaveRadiusSquared = shockwaveRadius * shockwaveRadius

        (-shockwaveRadius..shockwaveRadius).forEach { x ->
            (-shockwaveRadius..shockwaveRadius).forEach { y ->
                (-shockwaveRadius..shockwaveRadius).forEach zLoop@{ z ->
                    val distanceSquared = x * x + y * y + z * z
                    if (distanceSquared > shockwaveRadiusSquared) return@zLoop

                    val newX = baseX + x
                    val newY = (baseY + y).coerceIn(worldMinHeight, worldMaxHeight)
                    val newZ = baseZ + z

                    // Process block only once for transformation
                    if (!processedBlockCoordinates.add(Triple(newX, newY, newZ))) return@zLoop
                    val block = world.getBlockAt(newX, newY, newZ)

                    if (block.type == Material.AIR || BLOCK_TRANSFORMATION_BLACKLIST.contains(block.type) || LIQUID_MATERIALS.contains(
                            block.type
                        )
                    ) return@zLoop

                    // Replace block based on its type for flattened shockwave effect
                    val replacementType = when {
                        block.type.name.endsWith("_WALL") -> RUINED_WALLS_BLOCK.random()
                        block.type.name.endsWith("_SLAB") -> RUINED_SLABS_BLOCK.random()
                        block.type.name.endsWith("_STAIRS") -> RUINED_STAIRS_BLOCK.random()
                        block.type.name.endsWith("_LOG") -> LOG_REPLACEMENTS.random()
                        block.type.name.endsWith("_LEAVES") -> Material.AIR
                        block.type.name.endsWith("_GLASS") || block.type.name.endsWith("_GLASS_PANE") -> Material.AIR
                        block.type == Material.DIRT || block.type == Material.GRASS_BLOCK -> DIRT_REPLACEMENTS.random()
                        else -> RUINED_BLOCKS.random()
                    }

                    blockChanges.add(block to replacementType)
                }
            }
        }

        // Batch process all block changes at once to improve performance
        explosionSchedule.addWorkload(SimpleSchedulable {
            blockChanges.forEach { (block, material) ->
                block.setType(material, false)
            }
        })
    }


    private fun applyExplosionEffects(entity: LivingEntity, explosionPower: Float) {
        entity.damage(explosionPower * 4.0)
        val knockback = Vector(entity.location.x - center.x, entity.location.y - center.y, entity.location.z - center.z)
            .normalize().multiply(explosionPower * 2.0)
        entity.velocity = knockback
    }

    private fun generateShockwaveColumns(radius: Int): Set<Vector3i> {
        val ringElements = mutableSetOf<Vector3i>()
        val steps = radius * 6
        val angleIncrement = 2 * Math.PI / steps
        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ
        val previousPosition = Vector3i(centerX, centerY, centerZ)
        val loc = Vector3i(0, 0, 0)
        var previousHeight: Int? = null

        for (i in 0 until steps) {
            val angleRad = i * angleIncrement
            val x = (centerX + cos(angleRad) * radius).toInt()
            val z = (centerZ + sin(angleRad) * radius).toInt()
            loc.set(x, centerY, z)

            val highestY = world.getHighestBlockYAt(x, z)
            previousHeight?.let { prevHeight ->
                val heightDiff = abs(prevHeight - highestY)
                if (heightDiff > 1) {
                    val dx = (x - previousPosition.x) / heightDiff.toDouble()
                    val dz = (z - previousPosition.z) / heightDiff.toDouble()

                    for (step in 0 until heightDiff) {
                        val interpolatedX = (previousPosition.x + dx * step).toInt()
                        val interpolatedY = minOf(prevHeight, highestY) + step
                        val interpolatedZ = (previousPosition.z + dz * step).toInt()
                        ringElements.add(Vector3i(interpolatedX, interpolatedY, interpolatedZ))
                    }
                }
            }

            ringElements.add(Vector3i(x, highestY, z))
            previousHeight = highestY
            previousPosition.set(x, highestY, z)
        }

        return ringElements
    }
}

class ChunkSnapshotBuffer(private val world: World) {
    companion object {
        private const val MAX_CACHE_SIZE = 10000
    }

    private val chunkCache: MutableMap<Pair<Int, Int>, ChunkSnapshot> = Collections.synchronizedMap(
        object : LinkedHashMap<Pair<Int, Int>, ChunkSnapshot>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ChunkSnapshot>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        })

    private val taskQueue = ConcurrentLinkedQueue<Runnable>()
    private val executor = Executors.newSingleThreadExecutor()

    init {
        executor.submit {
            while (true) {
                val task = taskQueue.poll()
                task?.run()
            }
        }
    }

    fun getChunkSnapshot(x: Int, z: Int): CompletableFuture<ChunkSnapshot> {
        val chunkCoords = Pair(x, z)
        synchronized(chunkCache) {
            chunkCache[chunkCoords]?.let {
                return CompletableFuture.completedFuture(it)
            }
        }

        val future = CompletableFuture<ChunkSnapshot>()
        taskQueue.add {
            val chunkSnapshot = world.getChunkAtAsync(x, z).thenApply { chunk ->
                chunk.getChunkSnapshot(true, false, false)
            }.join()

            synchronized(chunkCache) {
                chunkCache[chunkCoords] = chunkSnapshot
            }
            future.complete(chunkSnapshot)
        }

        return future
    }

    fun preloadChunkSnapshots(chunks: List<Pair<Int, Int>>) {
        chunks.forEach { (x, z) -> getChunkSnapshot(x, z) }
    }
}
