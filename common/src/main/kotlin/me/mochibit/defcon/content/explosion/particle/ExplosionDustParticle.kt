package me.mochibit.defcon.content.explosion.particle

import com.mojang.serialization.Codec
import com.mojang.serialization.MapCodec
import com.mojang.serialization.codecs.RecordCodecBuilder
import me.mochibit.defcon.foundation.registry.ModParticles
import net.minecraft.client.multiplayer.ClientLevel
import net.minecraft.client.particle.*
import net.minecraft.core.particles.ParticleOptions
import net.minecraft.core.particles.ParticleType
import net.minecraft.network.RegistryFriendlyByteBuf
import net.minecraft.network.codec.ByteBufCodecs
import net.minecraft.network.codec.StreamCodec
import net.minecraft.util.ExtraCodecs
import org.joml.Vector3f

// TEMPORARY
object BlackbodyColor {
    private val stops = listOf(
        0f to Vector3f(0.12f, 0.12f, 0.12f),
        700f to Vector3f(0.35f, 0.08f, 0.05f),
        1600f to Vector3f(0.85f, 0.22f, 0.04f),
        3000f to Vector3f(1.00f, 0.55f, 0.15f),
        5200f to Vector3f(1.00f, 0.92f, 0.75f)
    )

    fun colorFor(temperature: Float): Vector3f {
        val t = temperature.coerceIn(stops.first().first, stops.last().first)
        for (i in 0 until stops.size - 1) {
            val (t0, c0) = stops[i]
            val (t1, c1) = stops[i + 1]
            if (t in t0..t1) {
                val f = (t - t0) / (t1 - t0)
                return Vector3f(
                    c0.x() + (c1.x() - c0.x()) * f,
                    c0.y() + (c1.y() - c0.y()) * f,
                    c0.z() + (c1.z() - c0.z()) * f
                )
            }
        }
        return stops.last().second
    }
}

class ExplosionDustParticle(
    level: ClientLevel,
    x: Double, y: Double, z: Double,
    xSpeed: Double, ySpeed: Double, zSpeed: Double,
    private val options: ExplosionDustParticleOptions,
    private val sprites: SpriteSet
) : TextureSheetParticle(level, x, y, z, xSpeed, ySpeed, zSpeed) {
    private val brightnessJitter = random.nextFloat() * 0.4f + 0.8f

    init {
        friction = options.friction
        gravity = options.gravity
        speedUpWhenYMotionIsBlocked = options.speedUpWhenYMotionIsBlocked
        hasPhysics = options.hasCollision

        xd = xSpeed * options.speed
        yd = ySpeed * options.speed
        zd = zSpeed * options.speed

        clampToMaxSpeed()

        quadSize *= 0.75f * options.scale
        lifetime = ((options.baseLifetime + random.nextInt(options.randomLifetime.coerceAtLeast(1)))
                ).coerceAtLeast(1)

        applyColorForAge()
        setSpriteFromAge(sprites)
    }

    private fun currentTemperature(): Float {
        if (options.colorOverride != null) return Float.NaN
        val ageSeconds = age / 20f
        return (options.initialTemperature - options.coolingRate * ageSeconds)
            .coerceAtLeast(options.ambientTemperature)
    }

    private fun applyColorForAge() {
        val base = options.colorOverride ?: BlackbodyColor.colorFor(currentTemperature())
        rCol = (base.x() * brightnessJitter).coerceIn(0f, 1f)
        gCol = (base.y() * brightnessJitter).coerceIn(0f, 1f)
        bCol = (base.z() * brightnessJitter).coerceIn(0f, 1f)
    }

    override fun tick() {
        super.tick()
        applyColorForAge()
        clampToMaxSpeed()
//        setSpriteFromAge(sprites)
    }

    private fun clampToMaxSpeed() {
        if (options.maxSpeed <= 0f) return
        val speed = Math.sqrt(xd * xd + yd * yd + zd * zd)
        if (speed > options.maxSpeed) {
            val f = options.maxSpeed / speed
            xd *= f; yd *= f; zd *= f
        }
    }

    override fun getQuadSize(scaleFactor: Float): Float {
        if (!options.shrinkOverTime) return quadSize
        val ageFraction = (age.toFloat() + scaleFactor) / lifetime.toFloat()
        return quadSize * (1.0f - ageFraction * ageFraction)
    }

    override fun getRenderType(): ParticleRenderType = ParticleRenderType.PARTICLE_SHEET_OPAQUE

    class Provider(private val sprites: SpriteSet) : ParticleProvider<ExplosionDustParticleOptions> {
        override fun createParticle(
            options: ExplosionDustParticleOptions, level: ClientLevel,
            x: Double, y: Double, z: Double,
            xSpeed: Double, ySpeed: Double, zSpeed: Double
        ): Particle = ExplosionDustParticle(level, x, y, z, xSpeed, ySpeed, zSpeed, options, sprites)
    }
}

class ExplosionDustParticleOptions(
    val scale: Float = 1.0f,
    val speed: Float = 1.0f,

    val initialTemperature: Float = 3000f,
    val coolingRate: Float = 400f,
    val ambientTemperature: Float = 0f,
    val colorOverride: Vector3f? = null,

    val baseLifetime: Int = 40,
    val randomLifetime: Int = 20,

    val friction: Float = 0.95f,
    val gravity: Float = 0.0f,
    val maxSpeed: Float = 0.5f,

    val hasCollision: Boolean = true,
    val speedUpWhenYMotionIsBlocked: Boolean = true,
    val shrinkOverTime: Boolean = true,
) : ParticleOptions {

    override fun getType(): ParticleType<ExplosionDustParticleOptions> = ModParticles.ExplosionDustParticle.get()

    companion object {
        val CODEC: MapCodec<ExplosionDustParticleOptions> = RecordCodecBuilder.mapCodec { instance ->
            instance.group(
                Codec.FLOAT.optionalFieldOf("scale", 1.0f).forGetter { it.scale },
                Codec.FLOAT.optionalFieldOf("speed", 1.0f).forGetter { it.speed },

                Codec.FLOAT.optionalFieldOf("initial_temperature", 3000f).forGetter { it.initialTemperature },
                Codec.FLOAT.optionalFieldOf("cooling_rate", 400f).forGetter { it.coolingRate },
                Codec.FLOAT.optionalFieldOf("ambient_temperature", 0f).forGetter { it.ambientTemperature },
                ExtraCodecs.VECTOR3F.optionalFieldOf("color_override")
                    .forGetter { java.util.Optional.ofNullable(it.colorOverride) },

                Codec.INT.optionalFieldOf("base_lifetime", 40).forGetter { it.baseLifetime },
                Codec.INT.optionalFieldOf("random_lifetime", 20).forGetter { it.randomLifetime },

                Codec.FLOAT.optionalFieldOf("friction", 0.95f).forGetter { it.friction },
                Codec.FLOAT.optionalFieldOf("gravity", 0.0f).forGetter { it.gravity },
                Codec.FLOAT.optionalFieldOf("max_speed", 0.5f).forGetter { it.maxSpeed },

                Codec.BOOL.optionalFieldOf("has_collision", true).forGetter { it.hasCollision },
                Codec.BOOL.optionalFieldOf("speed_up_when_y_motion_blocked", true)
                    .forGetter { it.speedUpWhenYMotionIsBlocked },
                Codec.BOOL.optionalFieldOf("shrink_over_time", true).forGetter { it.shrinkOverTime }
            ).apply(instance) { scale, speed, initTemp, cooling, ambient, colorOpt,
                                baseLife, randLife, fric, grav, maxSpd, collision, speedUp, shrink ->
                ExplosionDustParticleOptions(
                    scale = scale, speed = speed,
                    initialTemperature = initTemp, coolingRate = cooling, ambientTemperature = ambient,
                    colorOverride = colorOpt.orElse(null),
                    baseLifetime = baseLife, randomLifetime = randLife,
                    friction = fric, gravity = grav, maxSpeed = maxSpd,
                    hasCollision = collision, speedUpWhenYMotionIsBlocked = speedUp, shrinkOverTime = shrink
                )
            }
        }

        val STREAM_CODEC = object : StreamCodec<RegistryFriendlyByteBuf, ExplosionDustParticleOptions> {
            override fun decode(buffer: RegistryFriendlyByteBuf): ExplosionDustParticleOptions {
                val scale = ByteBufCodecs.FLOAT.decode(buffer)
                val speed = ByteBufCodecs.FLOAT.decode(buffer)
                val initTemp = ByteBufCodecs.FLOAT.decode(buffer)
                val cooling = ByteBufCodecs.FLOAT.decode(buffer)
                val ambient = ByteBufCodecs.FLOAT.decode(buffer)
                val hasColorOverride = ByteBufCodecs.BOOL.decode(buffer)
                val colorOverride = if (hasColorOverride) ByteBufCodecs.VECTOR3F.decode(buffer) else null
                return ExplosionDustParticleOptions(
                    scale = scale, speed = speed,
                    initialTemperature = initTemp, coolingRate = cooling, ambientTemperature = ambient,
                    colorOverride = colorOverride,
                    baseLifetime = ByteBufCodecs.INT.decode(buffer),
                    randomLifetime = ByteBufCodecs.INT.decode(buffer),
                    friction = ByteBufCodecs.FLOAT.decode(buffer),
                    gravity = ByteBufCodecs.FLOAT.decode(buffer),
                    maxSpeed = ByteBufCodecs.FLOAT.decode(buffer),
                    hasCollision = ByteBufCodecs.BOOL.decode(buffer),
                    speedUpWhenYMotionIsBlocked = ByteBufCodecs.BOOL.decode(buffer),
                    shrinkOverTime = ByteBufCodecs.BOOL.decode(buffer),
                )
            }

            override fun encode(buffer: RegistryFriendlyByteBuf, value: ExplosionDustParticleOptions) {
                ByteBufCodecs.FLOAT.encode(buffer, value.scale)
                ByteBufCodecs.FLOAT.encode(buffer, value.speed)
                ByteBufCodecs.FLOAT.encode(buffer, value.initialTemperature)
                ByteBufCodecs.FLOAT.encode(buffer, value.coolingRate)
                ByteBufCodecs.FLOAT.encode(buffer, value.ambientTemperature)
                ByteBufCodecs.BOOL.encode(buffer, value.colorOverride != null)
                value.colorOverride?.let { ByteBufCodecs.VECTOR3F.encode(buffer, it) }
                ByteBufCodecs.INT.encode(buffer, value.baseLifetime)
                ByteBufCodecs.INT.encode(buffer, value.randomLifetime)
                ByteBufCodecs.FLOAT.encode(buffer, value.friction)
                ByteBufCodecs.FLOAT.encode(buffer, value.gravity)
                ByteBufCodecs.FLOAT.encode(buffer, value.maxSpeed)
                ByteBufCodecs.BOOL.encode(buffer, value.hasCollision)
                ByteBufCodecs.BOOL.encode(buffer, value.speedUpWhenYMotionIsBlocked)
                ByteBufCodecs.BOOL.encode(buffer, value.shrinkOverTime)
            }
        }
    }
}