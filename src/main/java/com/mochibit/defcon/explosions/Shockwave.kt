package com.mochibit.defcon.explosions

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.observer.Loadable
import com.mochibit.defcon.threading.jobs.SimpleSchedulable
import com.mochibit.defcon.threading.runnables.ScheduledRunnable
import com.mochibit.defcon.utils.Geometry
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.*
import org.bukkit.block.Block
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread
import kotlin.math.*
import kotlin.random.Random

class Shockwave(
    val center: Location,
    shockwaveRadiusStart: Double,
    private val shockwaveRadius: Double,
    val shockwaveHeight: Double, override val observers: MutableList<(Unit) -> Unit> = mutableListOf(),
    override var isLoaded: Boolean = false
): Loadable<(Unit) -> Unit, Unit> {
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

    private var currentRadius = shockwaveRadiusStart
    private val chunkSnapshots: MutableMap<Pair<Int, Int>, ChunkSnapshot> = mutableMapOf()
    private val explosionSchedule = ScheduledRunnable().maxMillisPerTick(30.0)
    private val processedBlocksCoordinates = hashSetOf<Triple<Int, Int, Int>>()
    private fun cacheSnapshots() : CompletableFuture<Unit> {
        return CompletableFuture.supplyAsync {
            // Capture the snapshot of all chunks within the maximum radius
            val maxRadiusChunks = floor(shockwaveRadius / 16.0).toInt() + 1
            val centerChunkX = center.chunk.x
            val centerChunkZ = center.chunk.z

            for (dx in -maxRadiusChunks..maxRadiusChunks) {
                for (dz in -maxRadiusChunks..maxRadiusChunks) {
                    val chunk = center.world.getChunkAtAsync(centerChunkX + dx, centerChunkZ + dz).join()
                    chunkSnapshots[Pair(chunk.x, chunk.z)] = chunk.chunkSnapshot
                }
            }
            // After the snapshot is captured, start the explosion (notify observers)
            notifyObservers(Unit)
        }
    }

    override fun load() {
        thread(name="Shockwave cache thread") {
            cacheSnapshots().join()
            isLoaded = true
        }
    }
    fun explode() {
        thread(name = "Shockwave Thread - World: ${center.world.name} Center: ${center.blockX + center.blockY + center.blockZ}") {
            if (!isLoaded) {
                cacheSnapshots().join()
            }
            explosionSchedule.start()
            while (currentRadius < shockwaveRadius) {
                val explosionPower = MathFunctions.lerp(4.0, 2.0, currentRadius / shockwaveRadius)
                val columns = generateShockwaveColumns(currentRadius, explosionPower.toFloat())
                for (location in columns) {
                    location.world.spawnParticle(Particle.EXPLOSION_HUGE, location, 1, 0.0, 0.0, 0.0, 0.0)
                    simulateExplosion(location, explosionPower)
                    Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
                        location.getNearbyLivingEntities(5.0, 5.0, 5.0).forEach { entity ->
                            entity.damage(explosionPower * 12)
                        }
                    }
                }
                currentRadius += 1.0
                //Thread.sleep(30)
            }

        }
    }



    private fun simulateExplosion(location: Location, explosionPower: Double) {
        val world = location.world ?: return
        val radius = explosionPower
        val innerRadius = radius - 1
        val innerRadiusSquared = innerRadius * innerRadius
        val radiusSquared = radius * radius

        val baseX = location.blockX
        val baseY = location.blockY
        val baseZ = location.blockZ

        // Loop through all blocks within the radius
        for (x in -radius.toInt()..radius.toInt()) {
            for (y in -radius.toInt()..radius.toInt()) {
                for (z in -radius.toInt()..radius.toInt()) {
                    val distanceSquared = (x * x + y * y + z * z)

                    // Skip if outside the outer radius
                    if (distanceSquared > radiusSquared) continue

                    val newX = baseX + x
                    val newY = baseY + y
                    val newZ = baseZ + z

                    if (processedBlocksCoordinates.contains(Triple(newX, newY, newZ))) continue

                    val chunkX = newX shr 4
                    val chunkZ = newZ shr 4
                    val chunkSnapshot = chunkSnapshots[Pair(chunkX, chunkZ)] ?: continue

                    val localX = newX and 15
                    val localZ = newZ and 15
                    val blockType = chunkSnapshot.getBlockType(localX, newY, localZ)
                    // Skip if the block is air, blacklisted, or liquid
                    if (blockType == Material.AIR || BLOCK_TRANSFORMATION_BLACKLIST.contains(blockType) || LIQUID_MATERIALS.contains(blockType)) continue
                    val block = world.getBlockAt(newX, newY, newZ)

                    if (blockType.name.endsWith("_LOG") && currentRadius > shockwaveRadius / 3) {
                        processedBlocksCoordinates.add(Triple(newX, newY, newZ))
                        val newMaterial = LOG_REPLACEMENTS.random()
                        explosionSchedule.addWorkload(SimpleSchedulable {
                            block.type = newMaterial
                        })
                        continue
                    }


                    if (blockType == Material.DIRT && currentRadius > shockwaveRadius / 2) {
                        processedBlocksCoordinates.add(Triple(newX, newY, newZ))
                        val newMaterial = DIRT_REPLACEMENTS.random()
                        explosionSchedule.addWorkload(SimpleSchedulable {
                            block.type = newMaterial
                        })
                        continue
                    }

                    if (blockType.name.endsWith("_LEAVES")) {
                        processedBlocksCoordinates.add(Triple(newX, newY, newZ))
                        explosionSchedule.addWorkload(SimpleSchedulable {
                            block.type = Material.AIR
                        })
                        continue
                    }

                    //processedBlocksCoordinates.add(Triple(newX, newY, newZ))
                    // Inside the inner radius: destroy the block
                    if (distanceSquared < innerRadiusSquared) {
                        processedBlocksCoordinates.add(Triple(newX, newY, newZ))
                        explosionSchedule.addWorkload(SimpleSchedulable {
                            block.type = Material.AIR
                        })
                        continue
                    } else {
                        // At the edge between the inner and outer radius: replace the block
                        val newMaterial = when {
                            blockType.name.endsWith("_STAIRS") -> RUINED_STAIRS_BLOCK.random()
                            blockType.name.endsWith("_SLAB") -> RUINED_SLABS_BLOCK.random()
                            blockType.name.endsWith("_WALL") -> RUINED_WALLS_BLOCK.random()
                            else -> RUINED_BLOCKS.random()
                        }
                        explosionSchedule.addWorkload(SimpleSchedulable {
                            block.type = newMaterial
                        })
                    }
                }
            }
        }
    }




    private fun generateShockwaveColumns(radius: Double, explosionPower: Float): Set<Location> {
        val ringElements = mutableSetOf<Location>()
        val angleIncrement = 2 * Math.PI / 360 // 360 points around the circle

        var previousHeight: Double? = null
        var previousPosition: Location? = null
        val loc = Location(center.world, 0.0, 0.0, 0.0)

        for (i in 0 until 360) {
            val angleRad = i * angleIncrement
            val dirX = cos(angleRad) * radius
            val dirZ = sin(angleRad) * radius

            val x = center.x + dirX
            val z = center.z + dirZ
            loc.set(x, center.y, z)

            // Get the terrain height from the snapshot
            val chunkX = floor(x).toInt() shr 4
            val chunkZ = floor(z).toInt() shr 4
            val chunkSnapshot = chunkSnapshots[Pair(chunkX, chunkZ)]!!
            val floorHeight = Geometry.getMinYUsingSnapshot(loc, shockwaveHeight * 2, chunkSnapshot)

            // Connect with the previous point if height difference is significant
            if (previousHeight != null && abs(previousHeight - floorHeight.y) > 1) {
                val minY = minOf(previousHeight, floorHeight.y)
                val maxY = maxOf(previousHeight, floorHeight.y)
                val steps = (maxY - minY).toInt()

                for (y in 0..steps) {
                    val interpolatedY = minY + y
                    val interpolatedX = previousPosition!!.x + (x - previousPosition.x) * y / steps
                    val interpolatedZ = previousPosition.z + (z - previousPosition.z) * y / steps

                    ringElements.add(Location(center.world, interpolatedX, interpolatedY, interpolatedZ))
                }
            }

            // Add the current point
            ringElements.add(floorHeight)

            // Store the current point for the next iteration
            previousHeight = floorHeight.y
            previousPosition = loc.clone() // Clone here only once to avoid creating unnecessary objects
        }

        return ringElements
    }


}
