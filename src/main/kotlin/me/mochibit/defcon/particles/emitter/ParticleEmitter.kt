/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

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
 */
class ParticleEmitter(
    position: Location,
    range: Double,
    private val maxParticles: Int = 5000,
    var emitterShape: EmitterShape = PointShape,
    val transform: Matrix4f = Matrix4f(),
    val spawnableParticles: MutableList<AbstractParticle> = mutableListOf(),
    var shapeMutator: AbstractShapeMutator? = null
) : Lifecycled {

    companion object {
        // LOD settings
        private const val LOD_CLOSE = 1    // Update every frame
        private const val LOD_MEDIUM = 2   // Update every 2 frames
        private const val LOD_FAR = 4      // Update every 4 frames


        private const val LOD_CLOSE_DISTANCE_SQ = 100.0
        private const val LOD_MEDIUM_DISTANCE_SQ = 400.0
    }

    // Core position data
    private val origin: Vector3f = Vector3f(position.x.toFloat(), position.y.toFloat(), position.z.toFloat())
    val world: World = position.world
    private val rangeSquared = range * range

    // Object pooling for vector reuse
    private val vectorPool = ObjectPool(100) { Vector3f() }

    // Reusable vectors for particle creation
    private val positionCursor: Vector3f = Vector3f()

    // Thread-safe collections for particles and player tracking
    private val particles = ArrayList<ParticleInstance>(maxParticles)
    private val particlesToRemove = ObjectArrayList<ParticleInstance>(100)
    private val visiblePlayers = ConcurrentHashMap<Player, Int>(16) // Player -> LOD level

    // Batched updates
    private val batchedUpdates = HashMap<Player, ObjectArrayList<ClientSideParticleInstance>>()

    // State tracking
    private val activeCount = AtomicInteger(0)
    private val dyingOut = AtomicBoolean(false)
    private val updateCounter = AtomicInteger(0)

    // Settings
    val radialVelocity = Vector3f(0f, 0f, 0f)
    private var particlesPerFrame = 10
    private var spawnProbability = 1.0f
    var visible = true
        set(value) {
            if (field == value) return
            field = value

            if (!value) {
                // Hide all particles if visibility turned off
                val players = getPlayersInRange()
                synchronized(particles) {
                    particles.forEach {
                        if (it is ClientSideParticleInstance) {
                            players.forEach { player -> it.sendDespawnPacket(player) }
                        }
                    }
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

        // Create particle instance
        val newParticle = ParticleInstance.fromTemplate(particle, positionCursor)

        // Apply radial velocity if needed
        if (radialVelocity.lengthSquared() > 0) {
            val velocity = vectorPool.obtain()
            velocity.set(positionCursor)
                .sub(origin)
                .normalize()
                .mul(radialVelocity)

            newParticle.applyVelocity(velocity)
            vectorPool.free(velocity)
        }

        // Add to particle collection
        synchronized(particles) {
            particles.add(newParticle)
        }

        activeCount.incrementAndGet()

        // Send spawn packets to players in range
        val playersInRange = getPlayersInRange()
        synchronized(particles) {
            playersInRange.forEach { player ->
                newParticle.show(player)
            }
        }

    }

    override fun start() {}

    /**
     * Update particles in a specific range (for parallel processing)
     */
    private fun updateParticleRange(start: Int, end: Int, delta: Float, players: List<Player>) {
        val currentFrame = updateCounter.get()
        for (i in start until end) {
            if (i >= particles.size) break

            val particle = particles[i]
            var needsPositionUpdate = false

            // Update based on LOD
//            if (i % 4 == currentFrame % 4) {  // Stagger updates across frames
//            }
            needsPositionUpdate = particle.update(delta)
            // Handle client-side particles
            if (needsPositionUpdate) {
                if (particle is ClientSideParticleInstance) {
                    for (player in players) {
//                        val lodLevel = visiblePlayers[player] ?: continue
//
//                        // Apply LOD to packet sending
//                        val playerCounter = playerUpdateCounters.compute(player) { _, count ->
//                            if (count == null) 1 else count + 1
//                        }!!

//                        if (playerCounter % lodLevel == 0) {
                            synchronized(batchedUpdates) {
                                batchedUpdates.computeIfAbsent(player) {
                                    ObjectArrayList<ClientSideParticleInstance>()
                                }.add(particle)
                            }
//                        }
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
     * Update all particles with optimized processing for large numbers
     */
    override fun update(delta: Float) {
        // Update visible players list with distance-based LOD
        updateVisiblePlayers()
        val players = visiblePlayers.keys.toList()

        // Spawn new particles if needed
        if (activeCount.get() < maxParticles &&
            !dyingOut.get() &&
            visible &&
            spawnableParticles.isNotEmpty() &&
            ThreadLocalRandom.current().nextFloat() < spawnProbability
        ) {
            // Batch particle creation for better performance
            val particlesToCreate = minOf(particlesPerFrame, maxParticles - activeCount.get())
            repeat(particlesToCreate) {
                spawnParticle(spawnableParticles[ThreadLocalRandom.current().nextInt(spawnableParticles.size)])
            }
        }

        // Reset batched updates
        synchronized(batchedUpdates) {
            batchedUpdates.clear()
        }

        // Update particle range
        updateParticleRange(0, particles.size, delta, players)

        // Send batched position updates
        synchronized(batchedUpdates) {
            for ((player, particleList) in batchedUpdates) {
                // Send as batch packet if supported, otherwise send individually
                for (particle in particleList) {
                    particle.updatePosition(player)
                }
            }
            batchedUpdates.clear()
        }

        // Remove dead particles
        synchronized(particlesToRemove) {
            val clientSideParticles = particlesToRemove.filterIsInstance<ClientSideParticleInstance>()
            if (clientSideParticles.isNotEmpty()) {
                val destroyPacket = WrapperPlayServerDestroyEntities(
                    *(clientSideParticles.map { it.particleID }.toIntArray())
                )

                for (player in getPlayersInRange()) {
                    PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
                }
            }

            activeCount.addAndGet(-clientSideParticles.size)

            particles.removeAll(particlesToRemove)
            particlesToRemove.clear()
        }


        updateCounter.incrementAndGet()
    }

    /**
     * Get LOD level based on player distance
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
                    synchronized(particles) {
                        particles.forEach {
                            if (it is ClientSideParticleInstance) {
                                it.sendSpawnPacket(player)
                            }
                        }
                    }
                }
            } else if (visiblePlayers.containsKey(player)) {
                // Player went out of range, despawn particles
                synchronized(particles) {
                    particles.forEach {
                        if (it is ClientSideParticleInstance) {
                            it.sendDespawnPacket(player)
                        }
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
}

/**
 * Object pool for reusing objects to reduce garbage collection
 */
class ObjectPool<T>(capacity: Int, private val factory: () -> T) {
    private val pool = ArrayList<T>(capacity)

    init {
        repeat(capacity) {
            pool.add(factory())
        }
    }

    fun obtain(): T {
        return if (pool.isEmpty()) {
            factory()
        } else {
            synchronized(pool) {
                if (pool.isNotEmpty()) pool.removeAt(pool.size - 1) else factory()
            }
        }
    }

    fun free(obj: T) {
        synchronized(pool) {
            pool.add(obj)
        }
    }
}