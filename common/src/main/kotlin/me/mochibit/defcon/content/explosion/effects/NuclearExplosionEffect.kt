package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import me.mochibit.defcon.foundation.particles.ParticleEmitter
import me.mochibit.defcon.foundation.particles.ParticleSpawnTemplate
import me.mochibit.defcon.foundation.particles.shape.CylinderShape
import me.mochibit.defcon.foundation.particles.shape.SphereShape
import me.mochibit.defcon.foundation.particles.shape.SphereSurfaceShape
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

class NuclearExplosionEffect(
    private val level: ServerLevel,
    center: BlockPos,
    effectDuration: Duration,
    scope: CoroutineScope
) : ParticleVisualEffect(center, effectDuration, scope) {

    private val origin: Vec3 = Vec3.atCenterOf(center)
    private val riseSpeed = 4.0f
    private val maxHeight = 350.0f

    private var riseProgress = 0.0f
    private var currentHeight = 0.0f
    private var lastTickMillis = 0L

    private val coreCloud = ParticleEmitter(
        scope, level, origin, SphereShape(xzRadius = 45.0f, yRadius = 50.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 15.0f
        templates += ParticleSpawnTemplate(scale = 30f, initialVelocity = Vector3f(0f, -1.5f, 0f))
        translate(Vector3f(0f, -25f, 0f))
    }

    private val secondaryCloud = ParticleEmitter(
        scope, level, origin, SphereSurfaceShape(xzRadius = 50.0f, yRadius = 50.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 40.0f
        templates += ParticleSpawnTemplate(scale = 45f, initialVelocity = Vector3f(0f, -2.0f, 0f))
        translate(Vector3f(0f, -25f, 0f))
    }

    private val tertiaryCloud = ParticleEmitter(
        scope,level, origin, SphereSurfaceShape(xzRadius = 70.0f, yRadius = 70.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 45.0f
        templates += ParticleSpawnTemplate(scale = 50f, initialVelocity = Vector3f(0f, 3.5f, 0f))
        translate(Vector3f(0f, -30f, 0f))
    }

    private val quaternaryCloud = ParticleEmitter(
        scope, level, origin,
        SphereShape(xzRadius = 90.0f, yRadius = 60.0f, minY = 20.0, excludedXZRadius = 70.0)
    ).apply {
        baseCoolingRate = 50.0f
        templates += ParticleSpawnTemplate(scale = 55f, initialVelocity = Vector3f(0f, -5.5f, 0f))
        translate(Vector3f(0f, -15f, 0f))
    }

    private val neckSkirt = ParticleEmitter(
        scope, level, origin, SphereSurfaceShape(xzRadius = 45.0f, yRadius = 65.0f, minY = 65.0)
    ).apply {
        baseCoolingRate = 25.0f
        templates += ParticleSpawnTemplate(
            scale = 30f,
            initialVelocity = Vector3f(0f, -1.0f, 0f),
            colorOverride = Vector3f(0.5f, 0.5f, 0.5f) // stand-in for old Color.GRAY
        )
        translate(Vector3f(0f, -75f, 0f))
        visible = false
        applyRadialVelocityFromCenter(Vector3f(5.0f, 0f, 5.0f))
    }

    private val stem = ParticleEmitter(
        scope, level, origin, CylinderShape(radiusX = 15.0f, radiusZ = 15.0f, height = 1f)
    ).apply {
        templates += ParticleSpawnTemplate(scale = 40f, initialVelocity = Vector3f(0f, 2.0f, 0f))
        positionTemperatureFn = { pos, shape ->
            val t = (pos.y / shape.height.coerceAtLeast(1.0f)).coerceIn(0.0, 1.0)
            (3000.0 * (1.0 - t)).toFloat().coerceAtLeast(500f)
        }
    }

    private val stemShape = stem.getShape()
    private val neckConeShape = neckSkirt.getShape()

    private val emitters = listOf(coreCloud, secondaryCloud, tertiaryCloud, quaternaryCloud, neckSkirt, stem)

    override fun onStart() {
        lastTickMillis = System.currentTimeMillis()
        emitters.forEach { it.start() }
        neckSkirt.setVisibilityAfterDelay(true, 40.seconds)
    }

    override fun onStop() {
        emitters.forEach { it.stop() }
    }

    override fun onStep() {
        val now = System.currentTimeMillis()
        val delta = ((now - lastTickMillis) / 1000f).coerceAtLeast(0f)
        lastTickMillis = now

        riseProgress = (riseProgress + (riseSpeed * delta / maxHeight)).coerceAtMost(1.0f)
        val newHeight = riseProgress * maxHeight
        val heightDelta = newHeight - currentHeight
        currentHeight = newHeight

        val delta3 = Vector3f(0f, heightDelta, 0f)
        coreCloud.translate(delta3)
        secondaryCloud.translate(delta3)
        tertiaryCloud.translate(delta3)
        quaternaryCloud.translate(delta3)
        neckSkirt.translate(delta3)

        if (neckSkirt.visible && neckConeShape.minY > 0) {
            neckConeShape.minY -= (riseSpeed * 2 * delta)
        }

        stemShape.height = (currentHeight - 40f).coerceAtLeast(1f)
    }
}