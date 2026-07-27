package me.mochibit.defcon.content.explosion.client

import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import me.mochibit.defcon.foundation.util.FloodFill3D
import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.sounds.SoundEvent
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.level.Level
import net.minecraft.world.phys.Vec3
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

object ExplosionSoundManager {

    private val lastPlayedTimes = ConcurrentHashMap<ExplosionSound, Long>()
    private val repeatingSoundJobs = ConcurrentHashMap<String, Job>()

    private data class CachedEnclosedResult(val isEnclosed: Boolean, val timestamp: Long)
    private val enclosedCache = ConcurrentHashMap<Long, CachedEnclosedResult>()
    private const val ENCLOSED_CACHE_TTL = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default + CoroutineName("defcon-explosion-sound"))

    data class ExplosionSound(
        val soundEntries: List<SoundEntry>,
        val cooldownMs: Long = 2000L,
        val id: String = "sound_${System.nanoTime()}"
    )

    data class SoundEntry(
        val soundNormal: SoundEvent,
        val soundEnclosed: SoundEvent? = null,
        val soundSource: SoundSource = SoundSource.WEATHER,
        val volume: Float = 1f,
        val pitch: Float = 1f,
        val repeats: Int = 1,
        val randomizePitch: Pair<Float, Float>? = null,
    )

    object DefaultSounds {
        val ShockwaveHitSound = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = SoundEvents.TOTEM_USE,
                    volume = 2f, pitch = 1f,
                    repeats = 5,
                    randomizePitch = 0.5f to 0.8f
                ),
                SoundEntry(
                    soundNormal = SoundEvents.LIGHTNING_BOLT_THUNDER,
                    volume = 2f, pitch = 0.5f,
                    repeats = 10
                )
            ),
            cooldownMs = 1000L
        )

        val LargeExplosionWindBackground = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = SoundEvents.ELYTRA_FLYING,
                    volume = 1f, pitch = 0.1f,
                    repeats = 1,
                ),
            ),
            cooldownMs = 5000L
        )

        val DistantExplosion = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = SoundEvents.LIGHTNING_BOLT_THUNDER,
                    volume = 2f, pitch = 0.1f,
                    repeats = 1
                )
            ),
            cooldownMs = 1000L
        )
    }

    fun playSounds(sound: ExplosionSound, at: Vec3): Boolean {
        val now = System.currentTimeMillis()
        val last = lastPlayedTimes[sound] ?: 0L
        if (now - last < sound.cooldownMs) return false
        lastPlayedTimes[sound] = now

        playSoundEntries(sound.soundEntries, at, checkEnclosedCached(at))
        return true
    }

    fun playSoundsWithDelay(sound: ExplosionSound, center: Vec3, soundSpeed: Float = 20f) {
        val now = System.currentTimeMillis()
        val last = lastPlayedTimes[sound] ?: 0L
        if (now - last < sound.cooldownMs) return
        lastPlayedTimes[sound] = now

        val player = Minecraft.getInstance().player ?: return
        val delayMs = ((player.position().distanceTo(center) / soundSpeed) * 1000).toLong()

        scope.launch {
            delay(delayMs)
            val pos = Minecraft.getInstance().player?.position() ?: center
            playSoundEntries(sound.soundEntries, pos, checkEnclosedCached(pos))
        }
    }

    fun startRepeatingSounds(
        sound: ExplosionSound,
        origin: () -> Vec3,
        duration: Duration,
        interval: Duration = 500.milliseconds
    ): String {
        val jobId = "${sound.id}_${System.currentTimeMillis()}"
        stopRepeatingSounds(sound.id)

        val job = scope.launch {
            val endTime = System.currentTimeMillis() + duration.inWholeMilliseconds
            while (isActive && System.currentTimeMillis() < endTime) {
                val pos = origin()
                playSoundEntries(sound.soundEntries, pos, checkEnclosedCached(pos))
                delay(interval)
            }
            repeatingSoundJobs.remove(jobId)
        }
        repeatingSoundJobs[jobId] = job
        return jobId
    }

    fun stopRepeatingSounds(jobId: String) {
        repeatingSoundJobs[jobId]?.cancel()
        repeatingSoundJobs.remove(jobId)
    }

    fun stopAllRepeatingSounds() {
        repeatingSoundJobs.values.forEach { it.cancel() }
        repeatingSoundJobs.clear()
    }

    private fun playSoundEntries(entries: List<SoundEntry>, at: Vec3, isEnclosed: Boolean) {
        Minecraft.getInstance().execute {
            val level = Minecraft.getInstance().level ?: return@execute
            entries.forEach { entry ->
                val sound = if (isEnclosed) (entry.soundEnclosed ?: entry.soundNormal) else entry.soundNormal
                repeat(max(1, entry.repeats)) {
                    val pitch = entry.randomizePitch?.let { (lo, hi) -> Random.nextFloat() * (hi - lo) + lo } ?: entry.pitch
                    level.playLocalSound(at.x, at.y, at.z, sound, entry.soundSource, entry.volume, pitch, false)
                }
            }
        }
    }

    private fun checkEnclosedCached(pos: Vec3): Boolean {
        val level = Minecraft.getInstance().level ?: return false
        val blockPos = BlockPos.containing(pos)
        val key = blockPos.asLong()
        val now = System.currentTimeMillis()

        enclosedCache[key]?.let { if (now - it.timestamp < ENCLOSED_CACHE_TTL) return it.isEnclosed }

        val isEnclosed = checkEnclosed(level, blockPos)
        enclosedCache[key] = CachedEnclosedResult(isEnclosed, now)
        if (Random.nextInt(100) == 0) cleanOldCacheEntries(now)
        return isEnclosed
    }

    private fun checkEnclosed(level: Level, pos: BlockPos): Boolean {
        val filled = FloodFill3D.getFloodFill(pos, level, 150, true)
        return filled.count() < 150
    }

    private fun cleanOldCacheEntries(now: Long) {
        enclosedCache.entries.removeIf { now - it.value.timestamp > ENCLOSED_CACHE_TTL }
    }

    fun resetCooldowns() = lastPlayedTimes.clear()
    fun resetCooldown(sound: ExplosionSound) { lastPlayedTimes.remove(sound) }
}