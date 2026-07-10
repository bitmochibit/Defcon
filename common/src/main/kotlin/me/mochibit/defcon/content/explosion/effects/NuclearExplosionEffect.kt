package me.mochibit.defcon.content.explosion.effects

import kotlinx.coroutines.CoroutineScope
import kotlinx.serialization.Contextual
import kotlinx.serialization.Serializable
import me.mochibit.defcon.foundation.particles.ParticleEmitter
import me.mochibit.defcon.foundation.particles.ParticleSpawnTemplate
import me.mochibit.defcon.foundation.particles.ParticleSpawner
import me.mochibit.defcon.foundation.particles.shape.CylinderShape
import me.mochibit.defcon.foundation.particles.shape.SphereShape
import me.mochibit.defcon.foundation.particles.shape.SphereSurfaceShape
import net.minecraft.core.BlockPos
import net.minecraft.server.level.ServerLevel
import net.minecraft.world.phys.Vec3
import org.joml.Vector3f
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Serializable
data class NuclearExplosionParams(
    @Contextual override val center: BlockPos,
    override val effectDuration: Duration,
) : EffectParams

class NuclearExplosionEffect(
    params: NuclearExplosionParams,
    spawner: ParticleSpawner,
    scope: CoroutineScope
) : ParticleVisualEffect<NuclearExplosionParams>(params, spawner, scope) {

    private val origin: Vec3 = Vec3.atCenterOf(params.center)
    private val riseSpeed = 4.0f
    private val maxHeight = 350.0f

    private var riseProgress = 0.0f
    private var currentHeight = 0.0f
    private var lastTickMillis = 0L

    private val coreCloud = ParticleEmitter(
        scope, spawner, origin, SphereShape(xzRadius = 45.0f, yRadius = 50.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 15.0f
        templates += ParticleSpawnTemplate(speed = 0.5f, scale = 30f, initialVelocity = Vector3f(0f, -.5f, 0f))
        translate(Vector3f(0f, -25f, 0f))
    }

    private val secondaryCloud = ParticleEmitter(
        scope, spawner, origin, SphereSurfaceShape(xzRadius = 50.0f, yRadius = 50.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 40.0f
        templates += ParticleSpawnTemplate(speed = 0.5f, scale = 45f, initialVelocity = Vector3f(0f, -1.0f, 0f))
        translate(Vector3f(0f, -25f, 0f))
    }

    private val tertiaryCloud = ParticleEmitter(
        scope,spawner, origin, SphereSurfaceShape(xzRadius = 70.0f, yRadius = 70.0f, minY = 0.0)
    ).apply {
        baseCoolingRate = 45.0f
        templates += ParticleSpawnTemplate(speed = 0.5f, scale = 50f, initialVelocity = Vector3f(0f, 2.5f, 0f))
        translate(Vector3f(0f, -30f, 0f))
    }

    private val quaternaryCloud = ParticleEmitter(
        scope, spawner, origin,
        SphereShape(xzRadius = 90.0f, yRadius = 60.0f, minY = 20.0, excludedXZRadius = 70.0)
    ).apply {
        baseCoolingRate = 50.0f
        templates += ParticleSpawnTemplate(speed = 0.5f, scale = 55f, initialVelocity = Vector3f(0f, -3.5f, 0f))
        translate(Vector3f(0f, -15f, 0f))
    }

    private val neckSkirt = ParticleEmitter(
        scope, spawner, origin, SphereSurfaceShape(xzRadius = 45.0f, yRadius = 65.0f, minY = 65.0)
    ).apply {
        baseCoolingRate = 25.0f
        templates += ParticleSpawnTemplate(
            speed = 0.5f,
            scale = 30f,
            initialVelocity = Vector3f(0f, -1.0f, 0f),
            colorOverride = Vector3f(0.5f, 0.5f, 0.5f) // stand-in for old Color.GRAY
        )
        translate(Vector3f(0f, -75f, 0f))
        visible = false
        applyRadialVelocityFromCenter(Vector3f(5.0f, 0f, 5.0f))
    }

    private val stem = ParticleEmitter(
        scope, spawner, origin, CylinderShape(radiusX = 15.0f, radiusZ = 15.0f, height = 1f)
    ).apply {
        templates += ParticleSpawnTemplate(speed = 0.5f, scale = 40f, initialVelocity = Vector3f(0f, 2.0f, 0f))
        positionTemperatureFn = { pos, shape ->
            val t = (pos.y / shape.height.coerceAtLeast(1.0f)).coerceIn(0.0, 1.0)
            (3000.0 * (t)).toFloat().coerceAtLeast(500f)
        }
    }

    private val stemShape = stem.getShape()
    private val neckConeShape = neckSkirt.getShape()

    private val emitters = listOf(coreCloud, secondaryCloud, tertiaryCloud, quaternaryCloud, neckSkirt, stem)

    override fun onStart() {
        lastTickMillis = System.currentTimeMillis()
        neckSkirt.setVisibilityAfterDelay(true, 40.seconds)
    }

    override fun onStop() {
    }

    override fun onStep(deltaTime: Float) {
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

        emitters.forEach { it.step(deltaTime) }
    }
}