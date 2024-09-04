package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.observer.Loadable
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.*
import org.bukkit.block.Block
import org.bukkit.entity.LivingEntity
import org.bukkit.util.Vector
import org.joml.Vector3i
import java.util.LinkedList
import java.util.Queue
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class Shockwave(
    val center: Location,
    val shockwaveRadiusStart: Double,
    private val shockwaveRadius: Double,
    val shockwaveHeight: Double,
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

        private val RUINED_BLOCKS = hashSetOf(
            Material.DEEPSLATE,
            Material.COBBLESTONE,
            Material.BLACKSTONE,
        )

        private val RUINED_STAIRS_BLOCK = hashSetOf(
            Material.COBBLED_DEEPSLATE_STAIRS,
            Material.COBBLESTONE_STAIRS,
            Material.BLACKSTONE_STAIRS
        )

        private val RUINED_SLABS_BLOCK = hashSetOf(
            Material.COBBLED_DEEPSLATE_SLAB,
            Material.COBBLESTONE_SLAB,
            Material.BLACKSTONE_SLAB
        )

        private val RUINED_WALLS_BLOCK = hashSetOf(
            Material.COBBLED_DEEPSLATE_WALL,
            Material.COBBLESTONE_WALL,
            Material.BLACKSTONE_WALL
        )

        private val LOG_REPLACEMENTS = hashSetOf(
            Material.POLISHED_BASALT,
            Material.BASALT,
            Material.MANGROVE_ROOTS,
            Material.CRIMSON_STEM
        )

        private val DIRT_REPLACEMENTS = hashSetOf(
            Material.COARSE_DIRT,
            Material.PODZOL
        )
    }

    private val explosionSchedule = ScheduledRunnable(999999).maxMillisPerTick(60.0)

    private val world = center.world

    // BlockingQueue for thread-safe communication between shockwave generator and explosion processor
    private val explosionColumns: Queue<Triple<Int, Int, Set<Vector3i>>> = LinkedList()

    // Initialize the chunk snapshot buffer with a specified cache size
    private val chunkSnapshotBuffer = ChunkSnapshotBuffer(world)

    // Store three int values for the block coordinates in a hash set, this hash set is a cache
    private val processedBlockCoordinates = HashSet<Triple<Int, Int, Int>>()

    val isExploding = AtomicBoolean(false)
    fun explode() {
        explosionSchedule.clear()
        val shockwaveSpeed = 125.0 // Blocks per second

        isExploding.set(true)
        thread(
            name = "Shockwave Thread - World: ${center.world.name} Center: ${center.blockX + center.blockY + center.blockZ}",
            priority = Thread.MAX_PRIORITY
        ) {
            val bLoc = center.clone()
            val kLoc = center.clone()
            var currentRadius = shockwaveRadiusStart
            while (currentRadius < shockwaveRadius) {
                val explosionPower = MathFunctions.lerp(4.0, 8.0, currentRadius / shockwaveRadius)
                val columns = generateShockwaveColumns(currentRadius)
                explosionColumns.add(Triple(currentRadius.toInt(), explosionPower.toInt(), columns))
                for (location in columns) {
                    bLoc.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                    world.spawnParticle(Particle.EXPLOSION_HUGE, bLoc, 1, 0.0, 0.0, 0.0, 0.0)
                    Bukkit.getScheduler().runTaskLater(Defcon.instance, { task ->
                        kLoc.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                        world.getNearbyLivingEntities(kLoc, 3.0, 2.0, 3.0).forEach { entity ->
                            entity.damage(explosionPower * 4.0)
                            // Add knockback directed radially from the shockwave column
                            val knockback = Vector(
                                entity.location.x - kLoc.x,
                                entity.location.y - kLoc.y,
                                entity.location.z - kLoc.z
                            ).normalize().multiply(4)
                            entity.velocity = knockback
                        }
                    }, 20L)
                }
                Thread.sleep((shockwaveRadius / shockwaveSpeed).toLong())
                currentRadius += 1
            }
            isExploding.set(false)
            startExplosionProcessor()

        }
    }

    private fun startExplosionProcessor() {
        // Start a new thread to process the explosion
        thread(name = "Explosion Processor") {
            explosionSchedule.start()
            while (!explosionColumns.isEmpty() || isExploding.get()) {
                val rColumns = explosionColumns.poll() ?: continue
                val (cRadius, explPwr, locations) = rColumns
                locations.parallelStream().forEach { location ->
                    simulateExplosion(location, explPwr.toDouble(), cRadius)
                }
            }
        }
    }

    private fun simulateExplosion(location: Vector3i, explosionPower: Double, currentRadius: Int = 0) {
        val radius = explosionPower.toInt()
        val baseX = location.x
        val baseY = location.y
        val baseZ = location.z

        val worldMaxHeight = world.maxHeight
        val worldMinHeight = world.minHeight

        val radiusX = radius
        //val scaleFactor = 1.0 - (currentRadius.toDouble() / radius.toDouble() * 10)
        // val radiusY = (radius * scaleFactor).toInt().coerceAtLeast(1)
        val radiusY = radius
        val radiusZ = radius

        val radiusSquared = radiusX * radiusX + radiusY * radiusY + radiusZ * radiusZ
        val innerRadiusSquared =
            (radiusX - 1) * (radiusX - 1) + (radiusY - 1) * (radiusY - 1) + (radiusZ - 1) * (radiusZ - 1)

        for (x in -radiusX..radiusX) {
            for (y in -radiusY..radiusY) {
                for (z in -radiusZ..radiusZ) {
                    val distanceSquared = (x * x + y * y + z * z)
                    if (distanceSquared > radiusSquared) continue

                    val newX = baseX + x
                    val newY = (baseY + y).coerceIn(worldMinHeight, worldMaxHeight)
                    val newZ = baseZ + z

                    if (!processedBlockCoordinates.add(Triple(newX, newY, newZ))) continue
                    val block = world.getBlockAt(newX, newY, newZ)
                    if (distanceSquared <= innerRadiusSquared) {
                        if (currentRadius >= shockwaveRadius / 3 && Random.nextDouble() < 0.6) continue;
                        explosionSchedule.addWorkload(SimpleSchedulable { block.type = Material.AIR })
                        continue
                    }


                    val chunkX = newX shr 4
                    val chunkZ = newZ shr 4
                    val chunkSnapshot = chunkSnapshotBuffer.getChunkSnapshot(chunkX, chunkZ).join()
                    val blockType = run {
                        val localX = newX and 15
                        val localZ = newZ and 15
                        chunkSnapshot.getBlockType(localX, newY, localZ)
                    }

                    if (blockType == Material.AIR || BLOCK_TRANSFORMATION_BLACKLIST.contains(blockType) || LIQUID_MATERIALS.contains(
                            blockType
                        )
                    ) continue

                    val blockTypeName = blockType.name
                    val replacementType = when {
                        blockTypeName.endsWith("_WALL") && currentRadius > shockwaveRadius / 3 -> {
                            RUINED_WALLS_BLOCK.random()
                        }

                        blockTypeName.endsWith("_SLAB") && currentRadius > shockwaveRadius / 3 -> {
                            RUINED_SLABS_BLOCK.random()
                        }

                        blockTypeName.endsWith("_STAIRS") && currentRadius > shockwaveRadius / 3 -> {
                            RUINED_STAIRS_BLOCK.random()
                        }

                        blockTypeName.endsWith("_LOG") && currentRadius > shockwaveRadius / 3 -> {
                            LOG_REPLACEMENTS.random()
                        }

                        blockTypeName.endsWith("_LEAVES") -> {
                            Material.AIR
                        }

                        blockType == Material.DIRT || blockType == Material.GRASS_BLOCK && currentRadius > shockwaveRadius / 2 -> {
                            DIRT_REPLACEMENTS.random()
                        }

                        else -> {
                            RUINED_BLOCKS.random()
                        }
                    }

                    explosionSchedule.addWorkload(SimpleSchedulable {
//                        val e = world.spawnFallingBlock(block.location, replacementType, block.data)
//                        e.dropItem = false
                        block.type = replacementType
                    })
                }
            }
        }
    }


    private fun generateShockwaveColumns(radius: Double): Set<Vector3i> {
        val ringElements = mutableSetOf<Vector3i>()

        // Adjust the number of steps based on the radius
        val steps = (radius * 6).toInt() // You can tweak the multiplier (6) for more or less precision
        val angleIncrement = 2 * Math.PI / steps

        val centerX = center.blockX
        val centerY = center.blockY
        val centerZ = center.blockZ

        val previousPosition = Vector3i(centerX, centerY, centerZ)
        val loc = Vector3i(0, 0, 0)
        val floorHeight = Vector3i(0, 0, 0)

        var previousHeight: Int? = null

        for (i in 0 until steps) {
            val angleRad = i * angleIncrement
            val dirX = cos(angleRad) * radius
            val dirZ = sin(angleRad) * radius

            val x = (centerX + dirX).toInt()
            val z = (centerZ + dirZ).toInt()

            loc.set(x, centerY, z)

            // Get the height of the highest block at the location
            floorHeight.set(x, world.getHighestBlockYAt(x, z), z)

            // Connect with the previous point if height difference is significant
            previousHeight?.let { prevHeight ->
                val heightDiff = abs(prevHeight - floorHeight.y)
                if (heightDiff > 1) {
                    val stepsToInterpolate = heightDiff
                    val dx = (x - previousPosition.x) / stepsToInterpolate.toDouble()
                    val dz = (z - previousPosition.z) / stepsToInterpolate.toDouble()

                    for (step in 0 until stepsToInterpolate) {
                        val interpolatedX = (previousPosition.x + dx * step).toInt()
                        val interpolatedY = minOf(prevHeight, floorHeight.y) + step
                        val interpolatedZ = (previousPosition.z + dz * step).toInt()
                        ringElements.add(Vector3i(interpolatedX, interpolatedY, interpolatedZ))
                    }
                }
            }

            // Add the current point
            ringElements.add(Vector3i(floorHeight.x, floorHeight.y, floorHeight.z))

            // Update the previous position and height
            previousHeight = floorHeight.y
            previousPosition.set(x, floorHeight.y, z)
        }

        return ringElements
    }


}

class ChunkSnapshotBuffer(private val world: World) {

    companion object {
        private const val MAX_CACHE_SIZE = 10000
    }

    // Cache with LRU eviction policy
    private val chunkCache: MutableMap<Pair<Int, Int>, ChunkSnapshot> =
        object : LinkedHashMap<Pair<Int, Int>, ChunkSnapshot>(MAX_CACHE_SIZE, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Pair<Int, Int>, ChunkSnapshot>?): Boolean {
                return size > MAX_CACHE_SIZE
            }
        }

    // Queue to handle asynchronous tasks
    private val taskQueue = ConcurrentLinkedQueue<Runnable>()

    // Get available threads
    private val executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors())

    init {
        // Start a single background thread to process tasks asynchronously
        executor.submit {
            while (true) {
                val task = taskQueue.poll()
                task?.run()
            }
        }
    }

    // Retrieves the chunk snapshot, either from cache or by loading it asynchronously
    fun getChunkSnapshot(x: Int, z: Int): CompletableFuture<ChunkSnapshot> {
        val chunkCoords = Pair(x, z)
        synchronized(chunkCache) {
            chunkCache[chunkCoords]?.let {
                return CompletableFuture.completedFuture(it)
            }
        }

        // If not in cache, load asynchronously
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
        chunks.forEach { (x, z) ->
            getChunkSnapshot(x, z) // Preload the snapshot into the cache
        }
    }
}
