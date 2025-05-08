package me.mochibit.defcon.particles.emitter

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.mutators.AbstractShapeMutator
import me.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Matrix4d
import org.joml.Vector3f
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import kotlin.math.min
import kotlin.random.Random

/**
 * Coroutine-optimized ParticleEmitter designed to handle 15,000+ particles efficiently
 * with improved LOD performance and structured concurrency.
 *
 * Features:
 * - Memory-optimized particle processing
 * - Adaptive LOD (Level of Detail) system
 * - Efficient batch processing
 * - Thread-safe concurrent operations
 * - Proper coroutine lifecycle management
 */
class ParticleEmitter<T: EmitterShape>(
    position: Location,
    range: Double,
    maxParticlesInitial: Int = 1500,
    val emitterShape: T,
    val transform: Matrix4d = Matrix4d(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null,
) : Lifecycled {

    companion object {
        // LOD settings - inverted (higher = more frequent updates)
        // Performance tuning constants
        private const val LOD_CLOSE = 4    // Update every frame at 4x speed
        private const val LOD_MEDIUM = 2   // Update every frame at 2x speed
        private const val LOD_FAR = 1      // Update every frame at normal speed
        private const val LOD_INACTIVE = 0 // Minimal updates when no players in range

        // Distance thresholds (squared for performance)
        private const val LOD_CLOSE_DISTANCE_SQ = 100.0
        private const val LOD_MEDIUM_DISTANCE_SQ = 400.0

        // Batch processing constants
        private const val BATCH_SIZE = 1000
        private const val PARTICLE_SPAWN_BATCH = 64  // Increased from 32 for better throughput
        private const val PARTICLE_UPDATE_BATCH = 256 // Increased from 128 for better throughput
        private const val PLAYER_UPDATE_INTERVAL = 250L // Reduced from 300ms for more responsive player tracking

        // Batch processing interval constants
        private const val BATCH_PROCESS_INTERVAL = 16L // Reduced from 20ms for smoother processing

        // Burst spawning settings
        private const val BURST_BATCH_SIZE = 250      // Increased from 150 for faster initial population
        private const val BURST_BATCH_DELAY = 10L     // Reduced from 15ms for faster burst completion

        // Object pooling size
        private const val VECTOR_POOL_SIZE = 64       // Pool size for vector objects to reduce allocations
    }

    // Core position data - using immutable reference with mutable content for thread safety
    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    // Concurrent collections for particles and player tracking
    private val particles = ConcurrentHashMap.newKeySet<ParticleInstance>(maxParticlesInitial)
    private val visiblePlayers = ConcurrentHashMap<Player, PlayerLodInfo>(32)

    // Flows for async processing with increased buffer capacities
    private val particleSpawnFlow = MutableSharedFlow<List<AbstractParticle>>(
        extraBufferCapacity = 128, // Increased from 64
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val particleRemoveFlow = MutableSharedFlow<List<ParticleInstance>>(
        extraBufferCapacity = 128, // Increased from 64
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val positionUpdateFlow = MutableSharedFlow<Pair<Player, List<ClientSideParticleInstance>>>(
        extraBufferCapacity = 64, // Increased from 32
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    // State tracking with atomic safety
    private val activeCount = AtomicInteger(0)
    private val isRunning = AtomicBoolean(true)
    private val updateCounter = AtomicInteger(0)
    private val hasPlayersInRange = AtomicBoolean(false)

    // Track previous maxParticles value for change detection
    private var previousMaxParticles = maxParticlesInitial

    // Property with setter for maxParticles to detect changes
    var maxParticles = maxParticlesInitial
        set(value) {
            val oldValue = field
            field = value

            // If maxParticles increased, trigger burst spawn
            if (value > oldValue && isRunning.get() && visible) {
                triggerBurstSpawn(oldValue, value)
            }

            previousMaxParticles = value
        }

    // Optimized update tracking with reduced memory allocation
    private class ParticleUpdateInfo(var lastUpdate: Long = System.currentTimeMillis())

    private val particleUpdateInfo = ConcurrentHashMap<ParticleInstance, ParticleUpdateInfo>()

    // LOD tracking with improved memory profile
    private data class PlayerLodInfo(val lodLevel: Int, val lastUpdate: Long = System.currentTimeMillis())

    // Coroutine supervision with custom dispatcher for better thread utilization
    private val emitterScope = CoroutineScope(
        SupervisorJob() +
                Dispatchers.Default.limitedParallelism(Runtime.getRuntime().availableProcessors() / 2)
    )
    private var playerUpdateJob: Job? = null

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)

    // Dynamic spawn rate control
    private var particlesPerFrame = 15
        set(value) {
            field = value.coerceAtLeast(1)
        }
    private var spawnProbability = 1.0f
        set(value) {
            field = value.coerceIn(0.0f, 1.0f)
        }

    // Batched operation tracking with pre-sized collections
    private val pendingRemovals = Collections.synchronizedList(ArrayList<ParticleInstance>(PARTICLE_UPDATE_BATCH))
    private val pendingSpawns = Collections.synchronizedList(ArrayList<AbstractParticle>(PARTICLE_SPAWN_BATCH))
    private val lastBatchProcessTime = AtomicLong(0L)

    // Statistics for monitoring
    private val statsUpdateTimeNanos = AtomicLong(0L)
    private val statsParticleUpdates = AtomicLong(0L)
    private val statsFrameCount = AtomicLong(0L)

    var visible = true
        set(value) {
            if (field == value) return
            field = value

            if (!value) {
                emitterScope.launch {
                    // Hide all particles if visibility turned off
                    val players = getPlayersInRange()
                    if (players.isNotEmpty()) {
                        val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                        players.forEach { player ->
                            // Use batch despawn for efficiency
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        }
                    }
                }
            } else {
                // Show particles if visibility turned on
                emitterScope.launch {
                    val players = getPlayersInRange()
                    if (players.isNotEmpty()) {
                        val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                        for (player in players) {
                            for (batch in clientParticles.chunked(PARTICLE_UPDATE_BATCH)) {
                                batch.forEach { it.sendSpawnPacket(player) }
                                yield() // Allow other coroutines to run between batches
                            }
                        }
                    }
                }
            }
        }

    /**
     * Trigger a burst of particle spawns to quickly fill capacity when maxParticles increases
     * With improved efficiency and error handling
     */
    private fun triggerBurstSpawn(oldValue: Int, newValue: Int) {
        if (spawnableParticles.isEmpty()) return

        val particlesToAdd = newValue - oldValue
        val particlesToCreate = min(particlesToAdd, newValue - activeCount.get())

        if (particlesToCreate <= 0) return

        emitterScope.launch {
            try {
                // Spawn particles in controlled batches for better performance
                var remaining = particlesToCreate

                while (remaining > 0 && isRunning.get()) {
                    val batchSize = min(BURST_BATCH_SIZE, remaining)
                    val batch = ArrayList<AbstractParticle>(batchSize)

                    repeat(batchSize) {
                        // Use random with index optimization for better performance on large lists
                        val index = Random.nextInt(spawnableParticles.size)
                        batch.add(spawnableParticles[index])
                    }

                    particleSpawnFlow.emit(batch)
                    remaining -= batchSize

                    // Small delay between batches to avoid freezing
                    delay(BURST_BATCH_DELAY)
                }
            } catch (e: CancellationException) {
                // Expected when coroutine is canceled - do nothing
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error during burst spawn: ${e.message}")
            }
        }
    }

    /**
     * Process particle batches efficiently with improved timing control
     */
    private suspend fun processParticleBatches() {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBatchProcessTime.get() < BATCH_PROCESS_INTERVAL) {
            return // Not enough time has passed since last batch process
        }

        lastBatchProcessTime.set(currentTime)

        // Process pending spawns in batches
        val spawnBatch = synchronized(pendingSpawns) {
            if (pendingSpawns.isEmpty()) return@synchronized emptyList()
            val batch = ArrayList(pendingSpawns)
            pendingSpawns.clear()
            batch
        }

        if (spawnBatch.isNotEmpty()) {
            try {
                particleSpawnFlow.emit(spawnBatch)
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error emitting spawn batch: ${e.message}")
                // Return items to pending queue if emission failed
                synchronized(pendingSpawns) {
                    pendingSpawns.addAll(spawnBatch)
                }
            }
        }

        // Process pending removals in batches
        val removalBatch = synchronized(pendingRemovals) {
            if (pendingRemovals.isEmpty()) return@synchronized emptyList()
            val batch = ArrayList(pendingRemovals)
            pendingRemovals.clear()
            batch
        }

        if (removalBatch.isNotEmpty()) {
            try {
                particleRemoveFlow.emit(removalBatch)
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error emitting removal batch: ${e.message}")
                // Return items to pending queue if emission failed
                synchronized(pendingRemovals) {
                    pendingRemovals.addAll(removalBatch)
                }
            }
        }
    }

    /**
     * Spawn particles in batches with improved memory management
     */
    private suspend fun spawnParticleBatch(particleBatch: List<AbstractParticle>) {
        if (activeCount.get() >= maxParticles || !visible || !isRunning.get()) return

        val playersInRange = getPlayersInRange()
        val particlesToSpawn = arrayListOf<ParticleInstance>()

        try {
            for (particle in particleBatch) {
                if (activeCount.get() >= maxParticles) break

                // Create new particle from template with improved memory management
                val newParticle = ParticleInstance.fromTemplate(particle)

                // Start at origin
                newParticle.position.set(origin)

                // Apply shape and transform
                if (emitterShape != PointShape) {
                    emitterShape.maskLoc(newParticle.position)
                    transform.transformPosition(newParticle.position)
                    shapeMutator?.mutateLoc(newParticle.position)
                } else {
                    transform.transformPosition(newParticle.position)
                }

                if (radialVelocity.lengthSquared() > 0) {
                    newParticle.velocity
                        .set(newParticle.position.x, newParticle.position.y, newParticle.position.z)
                        .sub(origin.x.toDouble(), origin.y.toDouble(), origin.z.toDouble())
                        .normalize()
                        .mul(radialVelocity)

                    newParticle.velocity.add(
                        particle.initialVelocity.x.toDouble(),
                        particle.initialVelocity.y.toDouble(),
                        particle.initialVelocity.z.toDouble()
                    )
                } else {
                    newParticle.velocity.set(
                        particle.initialVelocity.x.toDouble(),
                        particle.initialVelocity.y.toDouble(),
                        particle.initialVelocity.z.toDouble()
                    )
                }

                // Add to tracking collections
                particles.add(newParticle)
                particleUpdateInfo[newParticle] = ParticleUpdateInfo(System.currentTimeMillis())
                activeCount.incrementAndGet()

                particlesToSpawn.add(newParticle)
            }

            // Send spawn packets to players in efficient batches
            if (playersInRange.isNotEmpty() && particlesToSpawn.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    for (player in playersInRange) {
                        // Process in batches for better network performance
                        for (batch in particlesToSpawn.chunked(PARTICLE_UPDATE_BATCH)) {
                            for (particle in batch) {
                                particle.show(player)
                            }
                            yield() // Allow other coroutines to run between batches
                        }
                    }
                }
            }
        } catch (e: CancellationException) {
            // Expected cancellation, do nothing
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error in particle spawn: ${e.message}")
            e.printStackTrace()
        }
    }

    override fun start() {
        // Initialize state
        updateCounter.set(0)
        activeCount.set(0)
        isRunning.set(true)
        statsFrameCount.set(0)
        statsParticleUpdates.set(0)
        statsUpdateTimeNanos.set(0)

        // Start particle processing infrastructure with improved error handling
        emitterScope.launch {
            try {
                startParticleProcessors()

                // Pre-spawn particles if needed with improved algorithm
                if (visible && spawnableParticles.isNotEmpty()) {
                    val initialParticles =
                        min((maxParticles * 0.3).toInt(), 1500) // Start with 30% of capacity, max 1500

                    // Use adaptive batch sizing based on system capabilities
                    val processorCount = Runtime.getRuntime().availableProcessors()
                    val adaptiveBatchSize = min(BURST_BATCH_SIZE, initialParticles / processorCount)

                    for (i in 0 until initialParticles step adaptiveBatchSize) {
                        val batchEnd = min(i + adaptiveBatchSize, initialParticles)
                        val batch = ArrayList<AbstractParticle>(batchEnd - i)

                        for (j in i until batchEnd) {
                            // Use indexed access instead of random() for large lists
                            val index = Random.nextInt(spawnableParticles.size)
                            batch.add(spawnableParticles[index])
                        }

                        particleSpawnFlow.emit(batch)
                        delay(3) // Reduced delay for faster initialization
                    }
                }

                // Start periodic batch processor with proper error handling
                launch {
                    while (isActive && isRunning.get()) {
                        try {
                            processParticleBatches()
                        } catch (e: CancellationException) {
                            break // Exit loop on cancellation
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error in batch processor: ${e.message}")
                        }
                        delay(BATCH_PROCESS_INTERVAL / 2) // Process at 2x the interval for smoother operations
                    }
                }
            } catch (e: CancellationException) {
                // Expected when scope is canceled
            } catch (e: Exception) {
                Defcon.instance.logger.severe("Critical error starting particle emitter: ${e.message}")
                e.printStackTrace()
                stop() // Ensure cleanup happens
            }
        }
    }

    private suspend fun startParticleProcessors() {
        // Process particle spawn batches with improved error handling
        emitterScope.launch {
            particleSpawnFlow.collect { particleBatch ->
                try {
                    spawnParticleBatch(particleBatch)
                } catch (e: CancellationException) {
                    // Expected when coroutine is canceled - propagate
                    throw e
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in particle spawn processor: ${e.message}")
                }
                yield() // Allow other coroutines to run
            }
        }

        // Process particle removal batches with improved error handling
        emitterScope.launch {
            particleRemoveFlow.collect { particleBatch ->
                try {
                    removeParticleBatch(particleBatch)
                } catch (e: CancellationException) {
                    // Expected when coroutine is canceled - propagate
                    throw e
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in particle removal processor: ${e.message}")
                }
                yield() // Allow other coroutines to run
            }
        }

        // Process position updates with improved error handling and batching
        emitterScope.launch {
            positionUpdateFlow.collect { (player, particleBatch) ->
                try {
                    // Process in optimized batches with adaptive sizing
                    val batchSize = PARTICLE_UPDATE_BATCH

                    // Use parallelization for large batches
                    if (particleBatch.size > BATCH_SIZE && hasPlayersInRange.get()) {
                        // For very large batches, process in parallel
                        val processor = Runtime.getRuntime().availableProcessors() / 2
                        val subBatchSize = particleBatch.size / processor

                        withContext(Dispatchers.IO) {
                            val batches = particleBatch.chunked(subBatchSize)
                            batches.map { subBatch ->
                                async {
                                    for (particle in subBatch) {
                                        particle.updatePosition(player)
                                    }
                                }
                            }.awaitAll()
                        }
                    } else {
                        // Sequential processing for smaller batches
                        for (i in particleBatch.indices step batchSize) {
                            val end = min(i + batchSize, particleBatch.size)
                            val subBatch = particleBatch.subList(i, end)

                            withContext(Dispatchers.IO) {
                                for (particle in subBatch) {
                                    particle.updatePosition(player)
                                }
                            }
                            yield() // Allow other coroutines to run between batches
                        }
                    }
                } catch (e: CancellationException) {
                    // Expected when coroutine is canceled - propagate
                    throw e
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in position update processor: ${e.message}")
                }
            }
        }

        // Start player update job with adaptive interval based on activity
        playerUpdateJob = emitterScope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                try {
                    val playerCountBefore = visiblePlayers.size
                    updateVisiblePlayers()
                    val playerCountAfter = visiblePlayers.size

                    // Adjust update frequency based on player presence
                    val delay = when {
                        playerCountAfter > 0 -> PLAYER_UPDATE_INTERVAL
                        playerCountBefore > 0 -> PLAYER_UPDATE_INTERVAL * 2 // Slower checks when players just left
                        else -> PLAYER_UPDATE_INTERVAL * 4 // Very slow checks when no players around
                    }

                    delay(delay)
                } catch (e: CancellationException) {
                    break // Exit loop on cancellation
                } catch (e: Exception) {
                    Defcon.instance.logger.warning("Error in player update: ${e.message}")
                    delay(PLAYER_UPDATE_INTERVAL * 2) // Use longer delay after errors
                }
            }
        }
    }

    /**
     * Remove particles in batch with optimized memory handling
     */
    private suspend fun removeParticleBatch(particleBatch: List<ParticleInstance>) {
        if (particleBatch.isEmpty()) return

        val playersInRange = getPlayersInRange()

        try {
            // Collect client-side particles for batch processing
            val clientParticles = particleBatch.filterIsInstance<ClientSideParticleInstance>()

            // Batch despawn for efficiency
            if (clientParticles.isNotEmpty() && playersInRange.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    for (player in playersInRange) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            // Log but continue with other players
                            Defcon.instance.logger.warning("Failed to despawn particles for player ${player.name}: ${e.message}")
                        }
                    }
                }
            }

            // Remove from tracking collections with batch operations
            for (particle in particleBatch) {
                particles.remove(particle)
                particleUpdateInfo.remove(particle)
                activeCount.decrementAndGet()
            }
        } catch (e: CancellationException) {
            // Expected when coroutine is canceled - do nothing
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error removing particle batch: ${e.message}")
        }
    }

    /**
     * Update particles with improved LOD factor application and adaptive processing
     */
    private suspend fun updateParticleRange(particleRange: Collection<ParticleInstance>, delta: Double) {
        if (particleRange.isEmpty()) return

        val currentTime = System.currentTimeMillis()
        val playersInRange = getPlayersInRange()
        val hasPlayers = playersInRange.isNotEmpty()
        hasPlayersInRange.set(hasPlayers)

        // Measure performance
        val startTime = System.nanoTime()
        var particlesProcessed = 0

        try {
            // Prepare collection with appropriate capacity
            val particlesToUpdate = ArrayList<ClientSideParticleInstance>(
                if (hasPlayers) min(particleRange.size / 4, 1000) else 0
            )
            val particlesToRemove = ArrayList<ParticleInstance>(
                min(particleRange.size / 10, 500)
            )

            if (!hasPlayers) {
                // No players in range, do minimal updates
                for (particle in particleRange) {
                    val info = particleUpdateInfo[particle] ?: continue

                    // Update at highly reduced rate when no players are present
                    if (currentTime - info.lastUpdate >= 1000) { // Once per second
                        val updatedPosition = particle.update(delta * LOD_INACTIVE)
                        info.lastUpdate = currentTime
                        particlesProcessed++

                        if (particle.isDead()) {
                            particlesToRemove.add(particle)
                        }
                    }
                }
            } else {
                // Pre-calculate LOD for each player once and cache results
                val playerLodMap = HashMap<Player, Int>(playersInRange.size)
                for (player in playersInRange) {
                    val lodInfo = visiblePlayers[player] ?: continue
                    playerLodMap[player] = lodInfo.lodLevel
                }

                // Process particles with optimized LOD
                for (particle in particleRange) {
                    val info = particleUpdateInfo[particle] ?: continue

                    // Find maximum LOD level from all players
                    var maxLodFactor = LOD_FAR
                    for ((player, lodLevel) in playerLodMap) {
                        if (lodLevel > maxLodFactor) {
                            maxLodFactor = lodLevel
                        }
                    }

                    // Apply LOD-based update schedule with dynamic adjustment
                    val updateInterval = when (maxLodFactor) {
                        LOD_CLOSE -> 12L    // ~83 fps
                        LOD_MEDIUM -> 25L   // 40 fps
                        else -> 50L         // 20 fps
                    }

                    if (currentTime - info.lastUpdate >= updateInterval) {
                        // Apply scaled delta based on LOD
                        val scaledDelta = delta * maxLodFactor
                        val positionChanged = particle.update(scaledDelta)
                        info.lastUpdate = currentTime
                        particlesProcessed++

                        // Add to position update list if needed
                        if (positionChanged && particle is ClientSideParticleInstance) {
                            particlesToUpdate.add(particle)
                        }

                        // Add to removal list if needed
                        if (particle.isDead()) {
                            particlesToRemove.add(particle)
                        }
                    }
                }
            }

            // Send position updates to players in optimized batches
            if (particlesToUpdate.isNotEmpty()) {
                // Group updates by player for better batching
                for (player in playersInRange) {
                    try {
                        positionUpdateFlow.emit(player to particlesToUpdate)
                    } catch (e: Exception) {
                        Defcon.instance.logger.warning("Failed to emit position updates: ${e.message}")
                    }
                }
            }

            // Queue particles for removal
            if (particlesToRemove.isNotEmpty()) {
                synchronized(pendingRemovals) {
                    pendingRemovals.addAll(particlesToRemove)
                }
            }
        } catch (e: CancellationException) {
            // Expected when coroutine is canceled - do nothing
        } catch (e: Exception) {
            Defcon.instance.logger.warning("Error updating particle range: ${e.message}")
        } finally {
            // Update statistics
            val processingTime = System.nanoTime() - startTime
            statsUpdateTimeNanos.addAndGet(processingTime)
            statsParticleUpdates.addAndGet(particlesProcessed.toLong())
            statsFrameCount.incrementAndGet()
        }
    }

    /**
     * Update all particles with optimized coroutine-based processing
     * and adaptive workload distribution
     */
    override fun update(delta: Float) {
        if (!isRunning.get()) return

        emitterScope.launch {
            try {
                // Spawn new particles if needed with improved probability check
                if (activeCount.get() < maxParticles &&
                    isRunning.get() &&
                    visible &&
                    spawnableParticles.isNotEmpty() &&
                    (spawnProbability >= 1.0f || Random.nextFloat() < spawnProbability)
                ) {
                    val availableCapacity = maxParticles - activeCount.get()
                    if (availableCapacity > 0) {
                        val particlesToCreate = min(particlesPerFrame, availableCapacity)

                        // Queue particles for batch spawning with optimized random access
                        synchronized(pendingSpawns) {
                            repeat(particlesToCreate) {
                                val index = Random.nextInt(spawnableParticles.size)
                                pendingSpawns.add(spawnableParticles[index])
                            }
                        }
                    }
                }

                // Process particles in optimized batches with adaptive parallelism
                val particlesList = particles.toList()
                val size = particlesList.size

                if (size > BATCH_SIZE * 2) {
                    // For very large collections, process in parallel chunks with adaptive sizing
                    val availableProcessors = Runtime.getRuntime().availableProcessors()
                    val optimalCoroutines = (availableProcessors / 2).coerceAtLeast(2).coerceAtMost(8)
                    val batchSize = (size / optimalCoroutines) + 1 // Ensure all particles are processed

                    coroutineScope {
                        // Split work into balanced chunks
                        val batches = particlesList.chunked(batchSize)

                        batches.forEach { batch ->
                            launch {
                                updateParticleRange(batch, delta.toDouble())
                            }
                        }
                    }
                } else if (size > 0) {
                    // For smaller collections, process in a single batch
                    updateParticleRange(particlesList, delta.toDouble())
                }

                updateCounter.incrementAndGet()
            } catch (e: CancellationException) {
                // Expected when coroutine is canceled - do nothing
            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error in particle update: ${e.message}")
            }
        }
    }

    /**
     * Get LOD level based on player distance with optimized calculation
     * and caching for frequent access
     */
    private fun getLodLevel(playerLocation: Location): Int {
        // Use fast, approximate distance calculation for LOD determination
        val dx = origin.x - playerLocation.x.toFloat()
        val dy = origin.y - playerLocation.y.toFloat()
        val dz = origin.z - playerLocation.z.toFloat()
        val distSq = dx * dx + dy * dy + dz * dz

        return when {
            distSq < LOD_CLOSE_DISTANCE_SQ -> LOD_CLOSE
            distSq < LOD_MEDIUM_DISTANCE_SQ -> LOD_MEDIUM
            else -> LOD_FAR
        }
    }

    /**
     * Update visible players with improved efficiency and adaptive refresh rate
     */
    private suspend fun updateVisiblePlayers() {
        val currentTime = System.currentTimeMillis()

        // Use copy-on-write approach for thread safety
        val newVisiblePlayers = HashMap<Player, PlayerLodInfo>(visiblePlayers.size)
        val playersNoLongerVisible = HashSet<Player>()

        // Reuse distance calculation to reduce allocations
        // First identify players to add or remove
        for (player in world.players) {
            val playerLocation = player.location

            val distSquared = origin.distanceSquared(
                playerLocation.x.toFloat(),
                playerLocation.y.toFloat(), playerLocation.z.toFloat()
            )

            if (distSquared < rangeSquared) {
                // Player is in range - update LOD level
                val lodLevel = getLodLevel(playerLocation)
                val playerInfo = PlayerLodInfo(lodLevel, currentTime)
                newVisiblePlayers[player] = playerInfo

                // Only send spawn packets if:
                // 1. This is a new player in range
                // 2. Particles are visible
                val existingInfo = visiblePlayers[player]
                if (existingInfo == null && visible) {
                    // Send in optimized batches with proper error handling
                    val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                    try {
                        // Use larger batches for initial spawn
                        val batchSize = PARTICLE_UPDATE_BATCH * 2
                        for (batch in clientParticles.chunked(batchSize)) {
                            try {
                                withContext(Dispatchers.IO) {
                                    for (particle in batch) {
                                        particle.sendSpawnPacket(player)
                                    }
                                }
                                yield() // Allow other coroutines to process between batches
                            } catch (e: Exception) {
                                Defcon.instance.logger.warning("Error sending particle spawn batch: ${e.message}")
                                // Continue with next batch despite errors
                            }
                        }
                    } catch (e: Exception) {
                        Defcon.instance.logger.warning("Failed to spawn particles for player ${player.name}: ${e.message}")
                    }
                }
            } else if (visiblePlayers.containsKey(player)) {
                // Player went out of range, add to cleanup list
                playersNoLongerVisible.add(player)
            }
        }

        // Handle players who went out of range
        if (playersNoLongerVisible.isNotEmpty()) {
            val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
            for (player in playersNoLongerVisible) {
                try {
                    // Use batch despawn for efficiency
                    ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                } catch (e: Exception) {
                    Defcon.instance.logger.warning("Failed to despawn particles for player ${player.name}: ${e.message}")
                }
            }
        }

        // Update the visible players map
        visiblePlayers.clear()
        visiblePlayers.putAll(newVisiblePlayers)

        // Update global player presence flag
        hasPlayersInRange.set(newVisiblePlayers.isNotEmpty())
    }

    /**
     * Get current players in range with optimized caching and defensive copy
     */
    private fun getPlayersInRange(): List<Player> {
        val playerCount = visiblePlayers.size
        if (playerCount == 0) return emptyList()

        // Create defensive copy to avoid concurrent modification
        return ArrayList<Player>(playerCount).apply {
            addAll(visiblePlayers.keys)
        }
    }

    /**
     * Gracefully stop emitter and clean up resources
     * with improved cleanup sequence
     */
    override fun stop() {
        if (!isRunning.compareAndSet(true, false)) {
            return // Already stopping or stopped
        }

        // Clean up in a controlled way
        emitterScope.launch {
            try {
                // Despawn particles for all players
                val players = getPlayersInRange()
                if (players.isNotEmpty()) {
                    val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                    for (player in players) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error despawning particles for player ${player.name}: ${e.message}")
                        }
                    }
                }

                // Wait for pending operations to complete or timeout
                withTimeoutOrNull(1000L) {
                    delay(200) // Brief delay to allow any in-flight operations to complete
                }

                // Clear collections
                particles.clear()
                particleUpdateInfo.clear()
                visiblePlayers.clear()
                pendingRemovals.clear()
                pendingSpawns.clear()
                activeCount.set(0)

            } catch (e: Exception) {
                Defcon.instance.logger.warning("Error during emitter shutdown: ${e.message}")
            } finally {
                // Cancel all coroutines with proper cleanup
                emitterScope.coroutineContext.cancelChildren()
            }
        }
    }

    /**
     * Get current particle count
     */
    fun getParticleCount(): Int = activeCount.get()

    /**
     * Get performance statistics
     */
    fun getPerformanceStats(): Map<String, Any> {
        val frameCount = statsFrameCount.get()
        val updateCount = statsParticleUpdates.get()
        val updateTime = statsUpdateTimeNanos.get()

        return mapOf(
            "activeParticles" to activeCount.get(),
            "frameCount" to frameCount,
            "totalParticleUpdates" to updateCount,
            "avgUpdateTimeNs" to if (frameCount > 0) updateTime / frameCount else 0,
            "avgParticlesPerFrame" to if (frameCount > 0) updateCount / frameCount else 0
        )
    }

    /**
     * Reset the emitter with improved consistency and error handling
     */
    fun reset() {
        // Stop current operations
        isRunning.set(false)

        emitterScope.launch {
            try {
                // Despawn all particles
                val players = getPlayersInRange()
                if (players.isNotEmpty()) {
                    val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                    for (player in players) {
                        try {
                            ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                        } catch (e: Exception) {
                            Defcon.instance.logger.warning("Error despawning particles during reset: ${e.message}")
                        }
                    }
                }

                // Wait for pending operations to complete or timeout
                withTimeoutOrNull(500L) {
                    delay(100) // Brief delay to allow any in-flight operations to complete
                }

                // Cancel jobs with proper cleanup
                emitterScope.coroutineContext.cancelChildren()

                // Clear collections
                particles.clear()
                particleUpdateInfo.clear()
                visiblePlayers.clear()
                pendingRemovals.clear()
                pendingSpawns.clear()

                // Reset counters
                activeCount.set(0)
                updateCounter.set(0)
                statsFrameCount.set(0)
                statsParticleUpdates.set(0)
                statsUpdateTimeNanos.set(0)

                // Wait briefly before restart to ensure clean state
                delay(50)

                // Restart
                isRunning.set(true)
                start()
            } catch (e: Exception) {
                Defcon.instance.logger.severe("Error during emitter reset: ${e.message}")
                e.printStackTrace()

                // Force restart even after error
                isRunning.set(true)
                start()
            }
        }
    }

    /**
     * Set particle spawn rate dynamically
     * @param particlesPerSecond Number of particles to spawn per second
     */
    fun setSpawnRate(particlesPerSecond: Int) {
        // Convert particles per second to particles per frame (assuming 20 ticks/second)
        this.particlesPerFrame = (particlesPerSecond / 20).coerceAtLeast(1)
    }

    /**
     * Set new emitter position
     * @param location New location for the emitter
     */
    fun setPosition(location: Location) {
        // Update origin position
        origin.set(
            location.x.toFloat(),
            location.y.toFloat(),
            location.z.toFloat()
        )

        // Force player update on next cycle
        emitterScope.launch {
            updateVisiblePlayers()
        }
    }

    fun getShape() : T {
        return emitterShape
    }
}