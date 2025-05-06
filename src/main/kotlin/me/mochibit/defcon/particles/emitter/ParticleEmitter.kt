package me.mochibit.defcon.particles.emitter

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.shynixn.mccoroutine.bukkit.SuspendingJavaPlugin
import com.github.shynixn.mccoroutine.bukkit.asyncDispatcher
import com.github.shynixn.mccoroutine.bukkit.launch
import com.github.shynixn.mccoroutine.bukkit.minecraftDispatcher
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.mutators.AbstractShapeMutator
import me.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.bukkit.plugin.java.JavaPlugin
import org.joml.Matrix4d
import org.joml.Matrix4f
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.Collections
import kotlin.collections.ArrayList
import kotlin.math.min
import kotlin.random.Random

/**
 * Coroutine-optimized ParticleEmitter designed to handle 15,000+ particles efficiently
 * with improved LOD performance and structured concurrency
 */
class ParticleEmitter(
    position: Location,
    range: Double,
    private val maxParticles: Int = 1500,
    var emitterShape: EmitterShape = PointShape,
    val transform: Matrix4d = Matrix4d(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null,
) : Lifecycled {

    companion object {
        // LOD settings - inverted (higher = more frequent updates)
        private const val LOD_CLOSE = 4    // Update every frame at 4x speed
        private const val LOD_MEDIUM = 2   // Update every frame at 2x speed
        private const val LOD_FAR = 1      // Update every frame at normal speed

        private const val LOD_CLOSE_DISTANCE_SQ = 100.0
        private const val LOD_MEDIUM_DISTANCE_SQ = 400.0

        // Batch processing constants
        private const val BATCH_SIZE = 1000
        private const val PARTICLE_SPAWN_BATCH = 32
        private const val PARTICLE_UPDATE_BATCH = 128
        private const val PLAYER_UPDATE_INTERVAL = 300L // Milliseconds between player updates

        // Batch processing interval constants
        private const val BATCH_PROCESS_INTERVAL = 50L // Process batches every 50ms
    }

    // Core position data
    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    // Position calculation vectors (single-thread access via mutex)
    private val positionMutex = Mutex()
    private val velocityVector = Vector3f()

    // Concurrent collections for particles and player tracking
    private val particles = ConcurrentHashMap.newKeySet<ParticleInstance>(maxParticles)
    private val visiblePlayers = ConcurrentHashMap<Player, PlayerLodInfo>(32)

    // Flows for async processing
    private val particleSpawnFlow = MutableSharedFlow<List<AbstractParticle>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val particleRemoveFlow = MutableSharedFlow<List<ParticleInstance>>(
        extraBufferCapacity = 64,
        onBufferOverflow = BufferOverflow.SUSPEND
    )
    private val positionUpdateFlow = MutableSharedFlow<Pair<Player, List<ClientSideParticleInstance>>>(
        extraBufferCapacity = 32,
        onBufferOverflow = BufferOverflow.SUSPEND
    )

    // State tracking with atomic safety
    private val activeCount = AtomicInteger(0)
    private val isRunning = AtomicBoolean(true)
    private val updateCounter = AtomicInteger(0)

    // Optimized update tracking
    private class ParticleUpdateInfo(var lastUpdate: Long = 0L)
    private val particleUpdateInfo = ConcurrentHashMap<ParticleInstance, ParticleUpdateInfo>()

    // LOD tracking
    private data class PlayerLodInfo(val lodLevel: Int, val lastUpdate: Long = System.currentTimeMillis())

    // Coroutine supervision
    private val emitterScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var playerUpdateJob: Job? = null

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)
    private var particlesPerFrame = 15
    private var spawnProbability = 1.0f

    // Batched operation tracking
    private val pendingRemovals = Collections.synchronizedList(ArrayList<ParticleInstance>())
    private val pendingSpawns = Collections.synchronizedList(ArrayList<AbstractParticle>())
    private val lastBatchProcessTime = AtomicLong(0L)

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
     * Process particle batches efficiently
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
            particleSpawnFlow.emit(spawnBatch)
        }

        // Process pending removals in batches
        val removalBatch = synchronized(pendingRemovals) {
            if (pendingRemovals.isEmpty()) return@synchronized emptyList()
            val batch = ArrayList(pendingRemovals)
            pendingRemovals.clear()
            batch
        }

        if (removalBatch.isNotEmpty()) {
            particleRemoveFlow.emit(removalBatch)
        }
    }

    /**
     * Spawn particles in batches for better performance
     */
    private suspend fun spawnParticleBatch(particleBatch: List<AbstractParticle>) {
        if (activeCount.get() >= maxParticles || !visible || !isRunning.get()) return

        val playersInRange = getPlayersInRange()
        val particlesToSpawn = arrayListOf<ParticleInstance>()

        coroutineScope {
            positionMutex.withLock {
                for (particle in particleBatch) {
                    if (activeCount.get() >= maxParticles) break

                    // Reuse positionCursor
                    val newParticle = ParticleInstance.fromTemplate(particle)

                    newParticle.position.set(origin)

                    // Apply shape and transform
                    if (emitterShape != PointShape) {
                        emitterShape.maskLoc(newParticle.position)
                        transform.transformPosition(newParticle.position)
                        shapeMutator?.mutateLoc(newParticle.position)
                    } else {
                        transform.transformPosition(newParticle.position)
                    }

                    // Apply radial velocity if needed
                    if (radialVelocity.lengthSquared() > 0) {
                        velocityVector.set(newParticle.position)
                            .sub(origin)
                            .normalize()
                            .mul(radialVelocity)

                        newParticle.addVelocity(velocityVector)
                    }

                    // Add to tracking collections
                    particles.add(newParticle)
                    particleUpdateInfo[newParticle] = ParticleUpdateInfo(System.currentTimeMillis())
                    activeCount.incrementAndGet()

                    particlesToSpawn.add(newParticle)
                }
            }
        }

        // Send spawn packets to players
        if (playersInRange.isNotEmpty() && particlesToSpawn.isNotEmpty()) {
            withContext(Dispatchers.IO) {
                for (player in playersInRange) {
                    for (particle in particlesToSpawn) {
                        particle.show(player)
                    }
                }
            }
        }
    }

    override fun start() {
        // Initialize state
        updateCounter.set(0)
        activeCount.set(0)
        isRunning.set(true)

        // Start particle processing infrastructure
        emitterScope.launch {
            startParticleProcessors()

            // Pre-spawn particles if needed
            if (visible && spawnableParticles.isNotEmpty()) {
                val initialParticles = min((maxParticles / 5), 1000) // Start with 20% of capacity, max 1000
                val batchSize = min(PARTICLE_SPAWN_BATCH, initialParticles)

                for (i in 0 until initialParticles step batchSize) {
                    val batchEnd = min(i + batchSize, initialParticles)
                    val batch = ArrayList<AbstractParticle>(batchEnd - i)

                    for (j in i until batchEnd) {
                        batch.add(spawnableParticles.random())
                    }

                    particleSpawnFlow.emit(batch)
                    delay(5) // Small delay between batches
                }
            }

            // Start periodic batch processor
            launch {
                while (isActive && isRunning.get()) {
                    processParticleBatches()
                    delay(BATCH_PROCESS_INTERVAL / 2) // Process at 2x the interval for smoother operations
                }
            }
        }
    }

    private suspend fun startParticleProcessors() {
        // Process particle spawn batches
        emitterScope.launch {
            particleSpawnFlow.collect { particleBatch ->
                try {
                    spawnParticleBatch(particleBatch)
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in particle spawn processor: ${e.message}")
                }
                yield() // Allow other coroutines to run
            }
        }

        // Process particle removal batches
        emitterScope.launch {
            particleRemoveFlow.collect { particleBatch ->
                try {
                    removeParticleBatch(particleBatch)
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in particle removal processor: ${e.message}")
                }
                yield() // Allow other coroutines to run
            }
        }

        // Process position updates
        emitterScope.launch {
            positionUpdateFlow.collect { (player, particleBatch) ->
                try {
                    // Process in optimized batches
                    val batchSize = PARTICLE_UPDATE_BATCH
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
                } catch (e: Exception) {
                    // Log error but don't crash the processor
                    Defcon.instance.logger.warning("Error in position update processor: ${e.message}")
                }
            }
        }

        // Start player update job with optimized interval
        playerUpdateJob = emitterScope.launch(Dispatchers.IO) {
            while (isActive && isRunning.get()) {
                try {
                    updateVisiblePlayers()
                } catch (e: Exception) {
                    Defcon.instance.logger.warning("Error in player update: ${e.message}")
                }
                delay(PLAYER_UPDATE_INTERVAL)
            }
        }
    }

    /**
     * Remove particles in batch for better performance
     */
    private suspend fun removeParticleBatch(particleBatch: List<ParticleInstance>) {
        val playersInRange = getPlayersInRange()

        // Collect client-side particles
        val clientParticles = particleBatch.filterIsInstance<ClientSideParticleInstance>()

        // Batch despawn for efficiency
        if (clientParticles.isNotEmpty() && playersInRange.isNotEmpty()) {
            for (player in playersInRange) {
                ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
            }
        }

        // Remove from tracking collections
        for (particle in particleBatch) {
            // No need to recycle vectors - let garbage collection handle them
            particles.remove(particle)
            particleUpdateInfo.remove(particle)
            activeCount.decrementAndGet()
        }
    }

    /**
     * Update particles with improved LOD factor application
     */
    private suspend fun updateParticleRange(particleRange: Collection<ParticleInstance>, delta: Double) {
        val currentTime = System.currentTimeMillis()
        val playersInRange = getPlayersInRange()

        if (playersInRange.isEmpty()) {
            // No players in range, do minimal updates
            for (particle in particleRange) {
                val info = particleUpdateInfo[particle] ?: continue

                // Update at reduced rate when no players are present
                if (currentTime - info.lastUpdate >= 500) { // 2 times per second
                    particle.update(delta)
                    info.lastUpdate = currentTime

                    if (particle.isDead()) {
                        synchronized(pendingRemovals) {
                            pendingRemovals.add(particle)
                        }
                    }
                }
            }
            return
        }

        // Process particles with optimized LOD
        val particlesToUpdate = ArrayList<ClientSideParticleInstance>()
        val particlesToRemove = ArrayList<ParticleInstance>()

        // Pre-calculate maximum LOD for each particle once
        for (particle in particleRange) {
            val info = particleUpdateInfo[particle] ?: continue

            // Find maximum LOD level from all players
            var maxLodFactor = LOD_FAR
            for (player in playersInRange) {
                val lodInfo = visiblePlayers[player]
                if (lodInfo != null && lodInfo.lodLevel > maxLodFactor) {
                    maxLodFactor = lodInfo.lodLevel
                }
            }

            // Apply LOD-based update schedule
            val updateInterval = (1000 / (20 * maxLodFactor)).toLong() // 20 ticks per second * LOD factor
            if (currentTime - info.lastUpdate >= updateInterval) {
                // Apply scaled delta based on LOD
                val scaledDelta = delta * maxLodFactor
                val positionChanged = particle.update(scaledDelta)
                info.lastUpdate = currentTime

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

        // Send position updates to players
        if (particlesToUpdate.isNotEmpty()) {
            // Group updates by player for better batching
            for (player in playersInRange) {
                positionUpdateFlow.emit(player to particlesToUpdate)
            }
        }

        // Queue particles for removal
        if (particlesToRemove.isNotEmpty()) {
            synchronized(pendingRemovals) {
                pendingRemovals.addAll(particlesToRemove)
            }
        }
    }

    /**
     * Update all particles with optimized coroutine-based processing
     */
    override fun update(delta: Float) {
        if (!isRunning.get()) return

        emitterScope.launch {
            // Spawn new particles if needed
            if (activeCount.get() < maxParticles &&
                isRunning.get() &&
                visible &&
                spawnableParticles.isNotEmpty() &&
                Random.nextFloat() < spawnProbability
            ) {
                val availableCapacity = maxParticles - activeCount.get()
                if (availableCapacity > 0) {
                    val particlesToCreate = min(particlesPerFrame, availableCapacity)

                    // Queue particles for batch spawning
                    synchronized(pendingSpawns) {
                        repeat(particlesToCreate) {
                            pendingSpawns.add(spawnableParticles.random())
                        }
                    }
                }
            }

            // Process particles in optimized batches
            val particlesList = particles.toList()
            val size = particlesList.size

            if (size > BATCH_SIZE) {
                // For large collections, process in parallel chunks
                val numCoroutines = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(2)
                val batchSize = size / numCoroutines
                val batches = particlesList.chunked(batchSize)

                coroutineScope {
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
        }
    }

    /**
     * Get LOD level based on player distance with optimization for frequent calculations
     */
    private fun getLodLevel(playerLocation: Location): Int {
        val distSq = origin.distanceSquared(
            playerLocation.x.toFloat(),
            playerLocation.y.toFloat(),
            playerLocation.z.toFloat()
        )

        return when {
            distSq < LOD_CLOSE_DISTANCE_SQ -> LOD_CLOSE
            distSq < LOD_MEDIUM_DISTANCE_SQ -> LOD_MEDIUM
            else -> LOD_FAR
        }
    }

    /**
     * Update visible players with improved efficiency
     */
    private suspend fun updateVisiblePlayers() {
        val currentTime = System.currentTimeMillis()

        // Use copy-on-write approach for thread safety
        val newVisiblePlayers = HashMap<Player, PlayerLodInfo>(visiblePlayers.size)
        val playersNoLongerVisible = HashSet<Player>()

        // First identify players to add or remove
        for (player in world.players) {
            val playerLocation = player.location
            val distSquared = origin.distanceSquared(
                playerLocation.x.toFloat(),
                playerLocation.y.toFloat(),
                playerLocation.z.toFloat()
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
                    // Send in optimized batches
                    val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                    for (batch in clientParticles.chunked(PARTICLE_UPDATE_BATCH)) {
                        for (particle in batch) {
                            particle.sendSpawnPacket(player)
                        }
                        yield() // Allow other coroutines to process between batches
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
                // Use batch despawn for efficiency
                ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
            }
        }

        // Update the visible players map
        visiblePlayers.clear()
        visiblePlayers.putAll(newVisiblePlayers)
    }

    /**
     * Get current players in range with optimized caching
     */
    private fun getPlayersInRange(): List<Player> {
        return ArrayList(visiblePlayers.keys)
    }

    /**
     * Gracefully stop emitter and clean up resources
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
                        ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                    }
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
                // Cancel all coroutines
                emitterScope.coroutineContext.cancelChildren()
            }
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
        emitterScope.launch {
            // Stop current operations
            isRunning.set(false)

            // Despawn all particles
            val players = getPlayersInRange()
            if (players.isNotEmpty()) {
                val clientParticles = particles.filterIsInstance<ClientSideParticleInstance>()
                for (player in players) {
                    ClientSideParticleInstance.destroyParticlesInBatch(player, clientParticles)
                }
            }

            // Cancel jobs
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

            // Restart
            isRunning.set(true)
            start()
        }
    }
}