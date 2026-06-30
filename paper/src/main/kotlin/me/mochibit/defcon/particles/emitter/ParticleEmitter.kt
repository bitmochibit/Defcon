package me.mochibit.defcon.particles.emitter

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.*
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.effects.ColorSupply
import me.mochibit.defcon.effects.PositionColorSupply
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.particles.mutators.AbstractShapeMutator
import me.mochibit.defcon.particles.templates.AbstractParticle
import org.bukkit.Location
import org.bukkit.World
import org.bukkit.entity.Player
import org.joml.Matrix4d
import org.joml.Vector3f
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.min
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

class ParticleEmitter<T : EmitterShape>(
    position: Location,
    private val range: Double,
    maxParticlesInitial: Int = 5000,
    val emitterShape: T,
    val transform: Matrix4d = Matrix4d(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null,
    var colorSupply: ColorSupply? = null,
    var positionColorSupply: PositionColorSupply? = null
) : Lifecycled {

    companion object {
        // Anti-flood settings
        private const val MAX_SPAWN_PER_TICK = 500
        private const val MAX_UPDATE_PER_TICK = 100
        private const val MAX_DESTROY_PER_TICK = 200

        // Timing controls
        private const val TICK_INTERVAL = 50L
        private const val PLAYER_CHECK_INTERVAL = 200L
        private const val CLEANUP_INTERVAL = 2000L

        // Distance-based LOD
        private const val CLOSE_DISTANCE_SQ = 64.0
        private const val MEDIUM_DISTANCE_SQ = 256.0

        // Player packet limits
        private const val MAX_PACKETS_PER_PLAYER = 800
    }

    // Core state
    private val origin = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    private val isRunning = AtomicBoolean(false)
    private val activeCount = AtomicInteger(0)
    private var mainJob: Job? = null

    // Collections - simplified
    private val particles = ConcurrentHashMap<Int, ParticleInstance>()
    private val visiblePlayers = ConcurrentHashMap<Player, Long>() // Player -> last update time

    // Packet batching - single queue per player
    private val playerPackets = ConcurrentHashMap<Player, PlayerPacketBatch>()

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)
    private var spawnRate = 5 // particles per tick
    var maxParticles = maxParticlesInitial

    var visible = true
        set(value) {
            if (field != value) {
                field = value
                if (value) showAllParticles() else hideAllParticles()
            }
        }

    private data class PlayerPacketBatch(
        val toSpawn: MutableList<ClientSideParticleInstance> = mutableListOf(),
        val toUpdate: MutableList<ClientSideParticleInstance> = mutableListOf(),
        val toDestroy: MutableList<ClientSideParticleInstance> = mutableListOf(),
        var lastSent: Long = 0L
    )

    override fun start() {
        if (!isRunning.compareAndSet(false, true)) return

        // Clear state
        particles.clear()
        visiblePlayers.clear()
        playerPackets.clear()
        activeCount.set(0)

        // Single main loop
        mainJob = Defcon.launch(Dispatchers.Default) {
            try {
                var lastPlayerCheck = 0L
                var lastCleanup = 0L

                while (isRunning.get()) {
                    val currentTime = System.currentTimeMillis()

                    // Core particle logic every tick
                    spawnParticles()
                    updateParticles()

                    // Player management less frequently
                    if (currentTime - lastPlayerCheck >= PLAYER_CHECK_INTERVAL) {
                        updateVisiblePlayers()
                        lastPlayerCheck = currentTime
                    }

                    // Send packets with flood protection
                    sendPacketBatches()

                    // Cleanup occasionally
                    if (currentTime - lastCleanup >= CLEANUP_INTERVAL) {
                        cleanupDeadParticles()
                        lastCleanup = currentTime
                    }

                    delay(TICK_INTERVAL.milliseconds)
                }
            } catch (e: CancellationException) {
                // Expected
            } catch (e: Exception) {
                Defcon.logger.severe("Particle emitter error: ${e.message}")
            } finally {
                cleanup()
            }
        }
    }

    override fun update(delta: Float) {
        // Self-managed
    }

    override fun stop() {
        if (!isRunning.compareAndSet(true, false)) return
        mainJob?.cancel()
        cleanup()
    }

    private fun spawnParticles() {
        if (!visible || spawnableParticles.isEmpty()) return

        val available = maxParticles - activeCount.get()
        if (available <= 0) return

        val toSpawn = min(min(spawnRate, available), MAX_SPAWN_PER_TICK)
        val newParticles = mutableListOf<ClientSideParticleInstance>()

        repeat(toSpawn) {
            try {
                val template = spawnableParticles[Random.nextInt(spawnableParticles.size)]
                val particle = createParticleFromTemplate(template)

                particles[particle.particleID] = particle
                activeCount.incrementAndGet()

                if (particle is ClientSideParticleInstance) {
                    newParticles.add(particle)
                }
            } catch (e: Exception) {
                Defcon.logger.warning("Spawn error: ${e.message}")
            }
        }

        // Queue for visible players
        if (newParticles.isNotEmpty()) {
            for (player in visiblePlayers.keys) {
                val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
                batch.toSpawn.addAll(newParticles)
            }
        }
    }

    private fun updateParticles() {
        val toRemove = mutableListOf<ParticleInstance>()
        val toUpdate = mutableListOf<ClientSideParticleInstance>()

        // Update physics for all particles
        for (particle in particles.values) {
            try {
                particle.update(0.05)

                if (particle.isDead()) {
                    toRemove.add(particle)
                } else if (particle is ClientSideParticleInstance) {
                    toUpdate.add(particle)
                }
            } catch (e: Exception) {
                toRemove.add(particle)
            }
        }

        // Handle removals
        if (toRemove.isNotEmpty()) {
            removeParticles(toRemove)
        }

        // Queue position updates (with limit)
        if (toUpdate.isNotEmpty()) {
            val updateLimit = min(toUpdate.size, MAX_UPDATE_PER_TICK)
            val particlesToUpdate = toUpdate.take(updateLimit)

            for (player in visiblePlayers.keys) {
                val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
                batch.toUpdate.addAll(particlesToUpdate)
            }
        }
    }

    private fun removeParticles(toRemove: List<ParticleInstance>) {
        val clientParticles = mutableListOf<ClientSideParticleInstance>()

        for (particle in toRemove) {
            particles.remove(particle.particleID)
            if (particle is ClientSideParticleInstance) {
                clientParticles.add(particle)
            }
        }

        activeCount.addAndGet(-toRemove.size)

        // Queue destroy packets
        if (clientParticles.isNotEmpty()) {
            for (player in visiblePlayers.keys) {
                val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
                batch.toDestroy.addAll(clientParticles)
            }
        }
    }

    private fun sendPacketBatches() {
        val currentTime = System.currentTimeMillis()

        for ((player, batch) in playerPackets) {
            if (!player.isValid || player.isDead) {
                playerPackets.remove(player)
                continue
            }

            // Rate limiting per player
            if (currentTime - batch.lastSent < 100) continue // Max 10 packets/sec per player

            try {
                var packetsSent = 0

                // Send spawns (priority)
                if (batch.toSpawn.isNotEmpty()) {
                    val toSend = batch.toSpawn.take(MAX_SPAWN_PER_TICK)
                    for (particle in toSend) {
                        particle.sendSpawnPacket(player)
                        packetsSent++
                    }
                    batch.toSpawn.removeAll(toSend.toSet())
                }

                // Send destroys (priority)
                if (batch.toDestroy.isNotEmpty() && packetsSent < MAX_PACKETS_PER_PLAYER) {
                    val toSend = batch.toDestroy.take(MAX_DESTROY_PER_TICK)
                    ClientSideParticleInstance.destroyParticlesInBatch(player, toSend)
                    batch.toDestroy.removeAll(toSend.toSet())
                    packetsSent++
                }

                // Send updates (lower priority)
                if (batch.toUpdate.isNotEmpty() && packetsSent < MAX_PACKETS_PER_PLAYER) {
                    val toSend = batch.toUpdate.take(MAX_UPDATE_PER_TICK)
                    for (particle in toSend) {
                        particle.updatePosition(player)
                    }
                    batch.toUpdate.removeAll(toSend.toSet())
                    packetsSent++
                }

                if (packetsSent > 0) {
                    batch.lastSent = currentTime
                }

                // Clean up empty batches
                if (batch.toSpawn.isEmpty() && batch.toUpdate.isEmpty() && batch.toDestroy.isEmpty()) {
                    playerPackets.remove(player)
                }

            } catch (e: Exception) {
                Defcon.logger.warning("Packet error for ${player.name}: ${e.message}")
                playerPackets.remove(player)
            }
        }
    }

    private fun updateVisiblePlayers() {
        val newVisible = mutableSetOf<Player>()
        val playersLeft = mutableSetOf<Player>()

        // Find current visible players
        for (player in world.players) {
            if (!player.isValid || player.isDead) continue

            val playerLoc = player.location
            if (playerLoc.world.name != world.name) continue

            val distSq = origin.distanceSquared(
                playerLoc.x.toFloat(),
                playerLoc.y.toFloat(),
                playerLoc.z.toFloat()
            )

            if (distSq < rangeSquared) {
                newVisible.add(player)

                // New player - show existing particles
                if (!visiblePlayers.containsKey(player) && visible) {
                    val existing = particles.values.filterIsInstance<ClientSideParticleInstance>()
                    if (existing.isNotEmpty()) {
                        val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
                        batch.toSpawn.addAll(existing)
                    }
                }
            }
        }

        // Find players who left
        for (player in visiblePlayers.keys) {
            if (!newVisible.contains(player)) {
                playersLeft.add(player)
            }
        }

        // Handle players leaving range
        if (playersLeft.isNotEmpty()) {
            val existing = particles.values.filterIsInstance<ClientSideParticleInstance>()
            for (player in playersLeft) {
                if (existing.isNotEmpty()) {
                    val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
                    batch.toDestroy.addAll(existing)
                }
                visiblePlayers.remove(player)
            }
        }

        // Update visible players
        val currentTime = System.currentTimeMillis()
        for (player in newVisible) {
            visiblePlayers[player] = currentTime
        }
    }

    private fun cleanupDeadParticles() {
        val currentTime = System.currentTimeMillis()
        val stale = particles.values.filter {
            it.isDead() || (currentTime - it.lastUpdateTime) > 10000
        }

        if (stale.isNotEmpty()) {
            removeParticles(stale)
        }
    }

    private fun showAllParticles() {
        val existing = particles.values.filterIsInstance<ClientSideParticleInstance>()
        for (player in visiblePlayers.keys) {
            val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
            batch.toSpawn.addAll(existing)
        }
    }

    private fun hideAllParticles() {
        val existing = particles.values.filterIsInstance<ClientSideParticleInstance>()
        for (player in visiblePlayers.keys) {
            val batch = playerPackets.computeIfAbsent(player) { PlayerPacketBatch() }
            batch.toDestroy.addAll(existing)
        }
    }

    private fun cleanup() {
        try {
            // Send immediate cleanup
            val existing = particles.values.filterIsInstance<ClientSideParticleInstance>()
            for (player in visiblePlayers.keys) {
                if (player.isValid && !player.isDead) {
                    try {
                        ClientSideParticleInstance.destroyParticlesInBatch(player, existing)
                    } catch (e: Exception) {
                        // Ignore
                    }
                }
            }

            particles.clear()
            visiblePlayers.clear()
            playerPackets.clear()
            activeCount.set(0)
        } catch (e: Exception) {
            // Ignore cleanup errors
        }
    }

    private fun createParticleFromTemplate(template: AbstractParticle): ParticleInstance {
        return particle2D {
            withTemplate(template)
            withOrigin(origin)

            if (positionColorSupply == null) {
                colorSupply?.let { withColor(it.invoke()) }
            }

            positionModifier { instance ->
                if (emitterShape != PointShape) {
                    emitterShape.maskLoc(instance.position)
                    transform.transformPosition(instance.position)
                    positionColorSupply?.let {
                        withColor(it.invoke(instance.position, emitterShape))
                    }
                    shapeMutator?.mutateLoc(instance.position)
                } else {
                    transform.transformPosition(instance.position)
                }
            }

            velocityModifier { instance ->
                if (radialVelocity.lengthSquared() > 0) {
                    instance.velocity
                        .set(instance.position.x, instance.position.y, instance.position.z)
                        .normalize()
                        .mul(radialVelocity)
                    instance.velocity.add(template.particleProperties.initialVelocity)
                } else {
                    instance.velocity.set(template.particleProperties.initialVelocity)
                }
            }
        }
    }

    // Utility methods
    fun getParticleCount(): Int = activeCount.get()
    fun getPlayersInRange(): List<Player> = visiblePlayers.keys.toList()
    fun setSpawnRate(rate: Int) { spawnRate = rate.coerceAtLeast(1) }
    fun setPosition(location: Location) {
        origin.set(location.x.toFloat(), location.y.toFloat(), location.z.toFloat())
    }
    fun getShape(): T = emitterShape

    fun getStats(): Map<String, Any> = mapOf(
        "activeParticles" to activeCount.get(),
        "playersInRange" to visiblePlayers.size,
        "queuedPackets" to playerPackets.values.sumOf {
            it.toSpawn.size + it.toUpdate.size + it.toDestroy.size
        },
        "isRunning" to isRunning.get()
    )
}