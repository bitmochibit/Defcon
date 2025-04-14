package me.mochibit.defcon.particles.emitter

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import it.unimi.dsi.fastutil.objects.ObjectArrayList
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.mutators.AbstractShapeMutator
import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.threading.scheduling.intervalAsyncWithTask
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

/**
 * Optimized ParticleEmitter designed to handle 15,000+ particles efficiently
 * with improved LOD performance and parallel processing
 */
class ParticleEmitter(
    position: Location,
    range: Double,
    private val maxParticles: Int = 2500,
    var emitterShape: EmitterShape = PointShape,
    val transform: Matrix4f = Matrix4f(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null
) : Lifecycled {

    companion object {
        // LOD settings - inversed (higher = more frequent updates)
        private const val LOD_CLOSE = 4    // Update every frame at 4x speed
        private const val LOD_MEDIUM = 2   // Update every frame at 2x speed
        private const val LOD_FAR = 1      // Update every frame at normal speed

        private const val LOD_CLOSE_DISTANCE_SQ = 100.0
        private const val LOD_MEDIUM_DISTANCE_SQ = 400.0

        // Batch size for parallel processing
        private const val BATCH_SIZE = 1000
    }

    // Core position data
    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    // Object pooling for vector reuse with larger initial capacity
    private val vectorPool = ObjectPool(1000) { Vector3f() }

    // Reusable vectors for particle creation
    private val positionCursor: Vector3f = Vector3f()

    // Thread-safe collections for particles and player tracking
    private val particles = ConcurrentVectorList<ParticleInstance>(maxParticles)
    private val particlesToRemove = ObjectArrayList<ParticleInstance>(200)
    private val visiblePlayers = ConcurrentHashMap<Player, Int>(32) // Player -> LOD level

    // Batched updates with larger initial capacity
    private val batchedUpdates = ConcurrentHashMap<Player, ObjectArrayList<ClientSideParticleInstance>>(32)

    // State tracking
    private val activeCount = AtomicInteger(0)
    private val dyingOut = AtomicBoolean(false)
    private val updateCounter = AtomicInteger(0)
    private val lastUpdateTimes = ConcurrentHashMap<Int, Long>(maxParticles)

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)
    private var particlesPerFrame = 15 // Increased from 10
    private var spawnProbability = 1.0f
    var visible = true
        set(value) {
            if (field == value) return
            field = value

            if (!value) {
                // Hide all particles if visibility turned off
                val players = getPlayersInRange()
                particles.forEach {
                    if (it is ClientSideParticleInstance) {
                        players.forEach { player -> it.sendDespawnPacket(player) }
                    }
                }
            }
        }

    // Worker threads for parallel processing
    private val workThreads = Runtime.getRuntime().availableProcessors().coerceAtMost(4)

    /**
     * Thread-safe vector list implementation for concurrent access
     */
    private class ConcurrentVectorList<T>(initialCapacity: Int) {
        private val backingList = ArrayList<T>(initialCapacity)
        private val lock = Any()

        fun add(element: T) {
            synchronized(lock) {
                backingList.add(element)
            }
        }

        fun size(): Int {
            synchronized(lock) {
                return backingList.size
            }
        }

        fun removeAll(elements: Collection<T>): Boolean {
            synchronized(lock) {
                return backingList.removeAll(elements.toSet())
            }
        }

        fun forEach(action: (T) -> Unit) {
            val snapshot: List<T>
            synchronized(lock) {
                snapshot = ArrayList(backingList)
            }
            snapshot.forEach(action)
        }

        fun getRange(start: Int, end: Int): List<T> {
            synchronized(lock) {
                val actualEnd = backingList.size.coerceAtMost(end)
                if (start >= actualEnd) return emptyList()
                return ArrayList(backingList.subList(start, actualEnd))
            }
        }

        fun isEmpty(): Boolean {
            synchronized(lock) {
                return backingList.isEmpty()
            }
        }
    }

    /**
     * Spawn a new particle with optimized position and velocity calculations
     */
    private fun spawnParticle(particle: AbstractParticle) {
        if (activeCount.get() >= maxParticles || !visible || dyingOut.get()) return

        // Reuse positionCursor instead of creating new vectors
        positionCursor.set(origin)

        // Apply shape masking and transformation
        if (emitterShape != PointShape) {
            emitterShape.maskLoc(positionCursor)
            transform.transformPosition(positionCursor)
            shapeMutator?.mutateLoc(positionCursor)
        } else {
            transform.transformPosition(positionCursor)
        }

        // Create particle instance with more optimal allocation
        val newParticle = ParticleInstance.fromTemplate(particle, positionCursor)

        // Apply radial velocity if needed
        if (radialVelocity.lengthSquared() > 0) {
            val velocity = vectorPool.obtain()
            velocity.set(positionCursor)
                .sub(origin)
                .normalize()
                .mul(radialVelocity)

            newParticle.addVelocity(velocity)
            vectorPool.free(velocity)
        }

        // Add to particle collection
        particles.add(newParticle)
        activeCount.incrementAndGet()

        // Send spawn packets to players in range
        val playersInRange = getPlayersInRange()
        playersInRange.forEach { player ->
            newParticle.show(player)
        }
    }

    override fun start() {
        // Initialize counters
        updateCounter.set(0)
        activeCount.set(0)

        // Pre-spawn some particles if needed
        if (visible && spawnableParticles.isNotEmpty()) {
            val initialParticles = maxParticles / 10 // Start with 10% of capacity
            repeat(initialParticles.coerceAtMost(maxParticles)) {
                spawnParticle(spawnableParticles[ThreadLocalRandom.current().nextInt(spawnableParticles.size)])
            }
        }
    }

    /**
     * Update particles in a specific range (for parallel processing)
     * with improved LOD factor application
     */
    private fun updateParticleRange(particleRange: List<ParticleInstance>, delta: Double, players: List<Player>) {
        val currentFrame = updateCounter.get()

        for (particle in particleRange) {
            var needsPositionUpdate = false
            val particleId = System.identityHashCode(particle)

            // Determine update factor based on closest player's LOD
            var maxLodFactor = LOD_FAR // Default to far
            for (player in players) {
                val lod = visiblePlayers[player] ?: LOD_FAR
                maxLodFactor = maxOf(maxLodFactor, lod)
            }

            // Apply LOD factor - higher values mean faster/more frequent updates
            val currentTime = System.currentTimeMillis()
            val lastUpdateTime = lastUpdateTimes.getOrDefault(particleId, 0L)

            // Update at higher rate for closer particles
            if (currentTime - lastUpdateTime >= (1000 / 20) / maxLodFactor) { // 20 ticks per second
                // Apply multiple delta updates for close particles to make them move faster
                val scaledDelta = delta * maxLodFactor
                needsPositionUpdate = particle.update(scaledDelta)
                lastUpdateTimes[particleId] = currentTime
            }

            // Handle client-side particles position updates
            if (needsPositionUpdate && particle is ClientSideParticleInstance) {
                for (player in players) {
                    synchronized(batchedUpdates) {
                        batchedUpdates.computeIfAbsent(player) {
                            ObjectArrayList<ClientSideParticleInstance>()
                        }.add(particle)
                    }
                }
            }

            if (particle.isDead()) {
                synchronized(particlesToRemove) {
                    particlesToRemove.add(particle)
                }
            }
        }
    }

    /**
     * Update all particles with optimized parallel processing for large numbers
     */
    override fun update(delta: Float) {
        // Update visible players list with distance-based LOD
        updateVisiblePlayers()
        val players = visiblePlayers.keys.toList()

        // Spawn new particles if needed with more efficient batching
        if (activeCount.get() < maxParticles &&
            !dyingOut.get() &&
            visible &&
            spawnableParticles.isNotEmpty() &&
            ThreadLocalRandom.current().nextFloat() < spawnProbability
        ) {
            // Batch particle creation for better performance
            val availableCapacity = maxParticles - activeCount.get()
            if (availableCapacity > 0) {
                val particlesToCreate = minOf(particlesPerFrame, availableCapacity)
                repeat(particlesToCreate) {
                    spawnParticle(spawnableParticles[ThreadLocalRandom.current().nextInt(spawnableParticles.size)])
                }
            }
        }

        // Reset batched updates
        synchronized(batchedUpdates) {
            batchedUpdates.clear()
        }

        // Process particles in parallel batches for large collections
        val size = particles.size()
        if (size > BATCH_SIZE && workThreads > 1) {
            val batchSize = size / workThreads
            val futures = ArrayList<java.util.concurrent.Future<*>>(workThreads)

            val executor = java.util.concurrent.Executors.newFixedThreadPool(workThreads)
            for (i in 0 until workThreads) {
                val start = i * batchSize
                val end = if (i == workThreads - 1) size else (i + 1) * batchSize

                val future = executor.submit {
                    val particleBatch = particles.getRange(start, end)
                    updateParticleRange(particleBatch, delta.toDouble(), players)
                }
                futures.add(future)
            }

            // Wait for all tasks to complete
            for (future in futures) {
                future.get()
            }
            executor.shutdown()
        } else {
            // For smaller collections, process in a single batch
            val particleBatch = particles.getRange(0, size)
            updateParticleRange(particleBatch, delta.toDouble(), players)
        }

        // Send batched position updates more efficiently
        synchronized(batchedUpdates) {
            for ((player, particleList) in batchedUpdates) {
                // Process in smaller batches to avoid overwhelming the client
                val batchSize = 128
                for (i in 0 until particleList.size step batchSize) {
                    val end = minOf(i + batchSize, particleList.size)
                    for (j in i until end) {
                        particleList[j].updatePosition(player)
                    }
                }
            }
            batchedUpdates.clear()
        }

        // Remove dead particles
        synchronized(particlesToRemove) {
            if (particlesToRemove.isNotEmpty()) {
                val clientSideParticles = particlesToRemove.filterIsInstance<ClientSideParticleInstance>()
                if (clientSideParticles.isNotEmpty()) {
                    // Process in batches to avoid packet size limits
                    val batchSize = 100
                    for (i in clientSideParticles.indices step batchSize) {
                        val end = minOf(i + batchSize, clientSideParticles.size)
                        val batch = clientSideParticles.subList(i, end)

                        val destroyPacket = WrapperPlayServerDestroyEntities(
                            *(batch.map { it.particleID }.toIntArray())
                        )

                        for (player in getPlayersInRange()) {
                            PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
                        }
                    }

                    activeCount.addAndGet(-clientSideParticles.size)

                    // Remove from tracking map
                    clientSideParticles.forEach { particle ->
                        val particleId = System.identityHashCode(particle)
                        lastUpdateTimes.remove(particleId)
                    }
                }

                particles.removeAll(particlesToRemove)
                particlesToRemove.clear()
            }
        }

        updateCounter.incrementAndGet()
    }

    /**
     * Get LOD level based on player distance - inverted compared to original
     * Higher values now mean more updates/faster particles
     */
    private fun getLodLevel(playerLocation: Location): Int {
        val distSq = origin.distanceSquared(
            playerLocation.x.toFloat(),
            playerLocation.y.toFloat(),
            playerLocation.z.toFloat()
        )

        return when {
            distSq < LOD_CLOSE_DISTANCE_SQ -> LOD_CLOSE  // 4x speed
            distSq < LOD_MEDIUM_DISTANCE_SQ -> LOD_MEDIUM // 2x speed
            else -> LOD_FAR  // 1x speed (normal)
        }
    }

    /**
     * Update the list of players who can see particles
     */
    private fun updateVisiblePlayers() {
        // Only update player list every few frames for efficiency
        if (updateCounter.get() % 10 != 0) return

        // Get players in range
        val playersToUpdate = HashMap<Player, Int>()

        for (player in world.players) {
            val playerLocation = player.location
            val distSquared = origin.distanceSquared(
                playerLocation.x.toFloat(),
                playerLocation.y.toFloat(),
                playerLocation.z.toFloat()
            )

            if (distSquared < rangeSquared) {
                // Player is in range - determine LOD level
                val lodLevel = getLodLevel(playerLocation)
                playersToUpdate[player] = lodLevel

                // If this is a new player in range, send spawn packets
                if (!visiblePlayers.containsKey(player) && visible) {
                    particles.forEach {
                        if (it is ClientSideParticleInstance) {
                            it.sendSpawnPacket(player)
                        }
                    }
                }
            } else if (visiblePlayers.containsKey(player)) {
                // Player went out of range, despawn particles
                particles.forEach {
                    if (it is ClientSideParticleInstance) {
                        it.sendDespawnPacket(player)
                    }
                }
            }
        }

        // Update the visible players map
        visiblePlayers.clear()
        visiblePlayers.putAll(playersToUpdate)
    }

    /**
     * Get current players in range (used for one-off operations)
     */
    private fun getPlayersInRange(): List<Player> {
        return visiblePlayers.keys.toList()
    }

    /**
     * Gracefully stop emitter and clean up particles
     */
    override fun stop() {
        dyingOut.set(true)

        // Schedule gradual cleanup to avoid freezing the server
        intervalAsyncWithTask(0L, 1L) { task ->
            if (particles.isEmpty()) {
                task.cancel()
                return@intervalAsyncWithTask
            }
            update(0.05f)
        }
    }

    /**
     * Get current particle count
     */
    fun getParticleCount(): Int = activeCount.get()

    /**
     * Reset the emitter
     */
    fun reset() {
        // Despawn all particles
        val players = getPlayersInRange()
        particles.forEach {
            if (it is ClientSideParticleInstance) {
                players.forEach { player -> it.sendDespawnPacket(player) }
            }
        }

        // Clear collections
        synchronized(particlesToRemove) {
            particlesToRemove.clear()
        }

        // Reset counters
        activeCount.set(0)
        updateCounter.set(0)
        lastUpdateTimes.clear()
    }
}

/**
 * Object pool for reusing objects to reduce garbage collection
 */
class ObjectPool<T>(capacity: Int, private val factory: () -> T) {
    private val pool = ArrayList<T>(capacity)
    private val lock = Any()

    init {
        repeat(capacity) {
            pool.add(factory())
        }
    }

    fun obtain(): T {
        synchronized(lock) {
            return if (pool.isEmpty()) {
                factory()
            } else {
                pool.removeAt(pool.size - 1)
            }
        }
    }

    fun free(obj: T) {
        synchronized(lock) {
            pool.add(obj)
        }
    }
}