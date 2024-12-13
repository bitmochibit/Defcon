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
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

class Shockwave(
    private val center: Location,
    private val shockwaveRadiusStart: Int,
    private val shockwaveRadius: Int,
    private val shockwaveHeight: Double,
    private val shockwaveSpeed: Long = 160L
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
    }

    private val world = center.world
    private val explosionColumns: ConcurrentLinkedQueue<Triple<Int, Float, Set<Vector3i>>> = ConcurrentLinkedQueue()
    private val processedBlockCoordinates = ConcurrentHashMap.newKeySet<Triple<Int, Int, Int>>()

    private val executorService = ForkJoinPool.commonPool()

    fun explode() {
        val locationCursor = Location(center.world, -1.0, 0.0, 0.0)
        val entities = world.getNearbyEntities(center, shockwaveRadius.toDouble(), shockwaveRadius.toDouble(), shockwaveRadius.toDouble())
        val visitedEntities = mutableSetOf<Entity>()

        executorService.submit {
            (shockwaveRadiusStart..shockwaveRadius).forEach { currentRadius ->
                val explosionPower = MathFunctions.lerp(4.0, 16.0, currentRadius / shockwaveRadius.toDouble())
                val columns = generateShockwaveColumns(currentRadius)
                columns.forEach { location ->
                    locationCursor.set(location.x.toDouble(), location.y.toDouble(), location.z.toDouble())
                    world.spawnParticle(Particle.EXPLOSION_HUGE, locationCursor, 0)
                    entities.parallelStream().forEach entityLoop@{ entity ->
                        if (visitedEntities.contains(entity)) return@entityLoop



                        val distanceSquared = entity.location.distanceSquared(locationCursor)
                        // Check if the entity is within range and height bounds
                        // @todo FIX THIS
                        if (entity.location.y.toInt() !in location.y..((location.y + shockwaveHeight).toInt()) && distanceSquared > 25) return@entityLoop

                        applyExplosionEffects(entity, explosionPower.toFloat())
                        visitedEntities.add(entity)
                    }
                }
                // Wait for shockwaveSpeed (blocks per second) and convert it to a MILLIS wait time (how much it waits for the next radius)
                TimeUnit.MILLISECONDS.sleep((1000 / shockwaveSpeed))
            }
        }
    }

    private fun startExplosionProcessor() {
        while (explosionColumns.isNotEmpty()) {
            val rColumns = explosionColumns.poll() ?: continue
            val (currentRadius, explosionPower, locations) = rColumns
            locations.parallelStream().forEach { location ->
                simulateExplosion(location, explosionPower, currentRadius)
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

        val blockChanges = mutableListOf<Pair<Block, Material>>()

        (-radius..radius).asSequence().flatMap { x ->
            (-radius..radius).asSequence().flatMap { y ->
                (-radius..radius).asSequence().map { z -> Triple(x, y, z) }
            }
        }.filter { (x, y, z) ->
            val distanceSquared = x * x + y * y + z * z
            distanceSquared <= radiusSquared
        }.forEach { (x, y, z) ->
            val newX = baseX + x
            val newY = (baseY + y).coerceIn(worldMinHeight, worldMaxHeight)
            val newZ = baseZ + z

            if (!processedBlockCoordinates.add(Triple(newX, newY, newZ))) return@forEach

            val block = world.getBlockAt(newX, newY, newZ)
            if (block.type != Material.AIR && !BLOCK_TRANSFORMATION_BLACKLIST.contains(block.type) && !LIQUID_MATERIALS.contains(
                    block.type
                )
            ) {
                blockChanges.add(block to Material.AIR)
            }
        }

        blockChanges.forEach { (block, material) -> block.setType(material, false) }

    }

    private fun applyExplosionEffects(entity: Entity, explosionPower: Float) {
        runLater(1L) {
            val knockback = Vector(entity.location.x - center.x, entity.location.y - center.y, entity.location.z - center.z)
                .normalize().multiply(explosionPower * 2.0)
            entity.velocity = knockback

            if (entity !is LivingEntity) return@runLater

            entity.damage(explosionPower * 4.0)
            if (entity !is Player) return@runLater

            val inv = 2/explosionPower
            try {
                CameraShake(entity, CameraShakeOptions(1.6f, 0.04f, 3.7f*inv, 3.0f*inv))
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
        val loc = Vector3i(0, 0, 0)

        (0 until steps).forEach { i ->
            val angleRad = i * angleIncrement
            val x = (centerX + cos(angleRad) * radius).toInt()
            val z = (centerZ + sin(angleRad) * radius).toInt()
            loc.set(x, centerY, z)

            val highestY = world.getHighestBlockYAt(x, z)
            ringElements.add(Vector3i(x, highestY, z))
        }

        return ringElements
    }
}
