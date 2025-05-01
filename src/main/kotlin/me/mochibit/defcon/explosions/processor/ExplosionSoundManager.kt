package me.mochibit.defcon.explosions.processor

import com.github.shynixn.mccoroutine.bukkit.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import me.mochibit.defcon.Defcon
import me.mochibit.defcon.utils.FloodFill3D
import net.kyori.adventure.key.Key
import net.kyori.adventure.sound.Sound
import org.bukkit.Location
import org.bukkit.entity.Player
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.max
import kotlin.random.Random
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

object ExplosionSoundManager {
    // Track last played time for each sound type with thread-safe map
    private val lastPlayedTimes = ConcurrentHashMap<ExplosionSound, Long>()

    // Track ongoing repeating sound jobs
    private val repeatingSoundJobs = ConcurrentHashMap<String, Job>()

    // Location cache for enclosed space checks (with TTL)
    private data class CachedEnclosedResult(val isEnclosed: Boolean, val timestamp: Long)
    private val enclosedCache = ConcurrentHashMap<String, CachedEnclosedResult>()
    private const val ENCLOSED_CACHE_TTL = 10_000L // 10 seconds cache lifetime

    object DefaultSounds {
        // Sound definitions
        val ShockwaveHitSound = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = Sound.sound(Key.key("item.totem.use"), Sound.Source.WEATHER, 2f, 1f),
                    soundEnclosed = Sound.sound(Key.key("defcon", "nuke.shockwave_reach_outer"), Sound.Source.WEATHER, 2f, 0.1f),
                    repeats = 5,
                    randomizePitch = Pair(0.5f, 0.8f)
                ),
                SoundEntry(
                    soundNormal = Sound.sound(Key.key("entity.lightning_bolt.thunder"), Sound.Source.WEATHER, 2f, 0.1f),
                    repeats = 10,
                )
            ),
            cooldownMs = 1000L
        )

        val LargeExplosionWindBackground = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = Sound.sound(Key.key("item.elytra.flying"), Sound.Source.WEATHER, 1.0f, 0.1f),
                    repeats = 1,
                )
            ),
            cooldownMs = 5000L
        )

        val DistantExplosion = ExplosionSound(
            soundEntries = listOf(
                SoundEntry(
                    soundNormal = Sound.sound(Key.key("defcon", "nuke.set_distant"), Sound.Source.WEATHER, 2.0f, 0.1f),
                    repeats = 1,
                )
            ),
            cooldownMs = 5000L
        )

    }

    data class ExplosionSound(
        val soundEntries: List<SoundEntry>,
        val cooldownMs: Long = 2000L,
        val id: String = "sound_${System.nanoTime()}" // Unique identifier for each sound
    )

    data class SoundEntry(
        val soundNormal: Sound?,
        val soundEnclosed: Sound? = null,
        val repeats: Int = 1,
        val randomizePitch: Pair<Float, Float>? = null,
    )

    /**
     * Play sounds for a player with cooldown check
     */
    fun playSounds(
        sound: ExplosionSound,
        player: Player
    ): Boolean {
        val currentTime = System.currentTimeMillis()
        val lastPlayed = lastPlayedTimes[sound] ?: 0L

        // Check if sound is on cooldown
        if (currentTime - lastPlayed < sound.cooldownMs) {
            return false // Sound is on cooldown, don't play
        }

        // Update last played time
        lastPlayedTimes[sound] = currentTime

        // Determine if player is in an enclosed space
        val isEnclosed = checkEnclosedCached(player.location)

        // Play all sound entries
        playSoundEntries(sound.soundEntries, player, isEnclosed)

        return true
    }

    /**
     * Start playing a repeating sound for a specified duration
     * @param sound The sound to play repeatedly
     * @param player The player to play the sound for
     * @param duration How long to keep playing the sound
     * @param interval Delay between repetitions
     * @return A unique ID for the repeating sound job
     */
    fun startRepeatingSounds(
        sound: ExplosionSound,
        player: Player,
        duration: Duration,
        interval: Duration = 500.milliseconds
    ): String {
        val jobId = "${sound.id}_${player.uniqueId}_${System.currentTimeMillis()}"

        // Cancel existing job for this player and sound if it exists
        stopRepeatingSounds("${sound.id}_${player.uniqueId}")

        // Start new coroutine for repeating sound
        val job = Defcon.instance.launch {
            val endTime = System.currentTimeMillis() + duration.inWholeMilliseconds

            while (isActive && System.currentTimeMillis() < endTime) {
                // Play sound without respecting cooldown for repeating sounds
                val isEnclosed = checkEnclosedCached(player.location)
                playSoundEntries(sound.soundEntries, player, isEnclosed)

                delay(interval)
            }

            // Remove job when completed
            repeatingSoundJobs.remove(jobId)
        }

        repeatingSoundJobs[jobId] = job
        return jobId
    }

    /**
     * Start playing a repeating sound for multiple players with distance-based delays
     */
    fun startRepeatingSoundsWithDelay(
        sound: ExplosionSound,
        players: Collection<Player>,
        center: Location,
        duration: Duration,
        interval: Duration = 500.milliseconds,
        soundSpeed: Float = 20f
    ): List<String> {
        val jobIds = mutableListOf<String>()

        // Group players by distance for batch processing
        val playersByDelay = players.groupBy { player ->
            val distance = center.distance(player.location)
            val delaySeconds = distance / soundSpeed
            (delaySeconds * 1000).toLong() // Convert to milliseconds
        }

        // Launch coroutine for each delay group
        Defcon.instance.launch {
            playersByDelay.entries.sortedBy { it.key }.forEach { (delayMs, playerGroup) ->
                delay(delayMs)

                // Start repeating sound for each player in this group
                playerGroup.forEach { player ->
                    val jobId = startRepeatingSounds(sound, player, duration, interval)
                    jobIds.add(jobId)
                }
            }
        }

        return jobIds
    }

    /**
     * Stop a repeating sound by its job ID
     */
    fun stopRepeatingSounds(jobId: String) {
        repeatingSoundJobs[jobId]?.let { job ->
            job.cancel()
            repeatingSoundJobs.remove(jobId)
        }
    }

    /**
     * Stop all repeating sounds
     */
    fun stopAllRepeatingSounds() {
        repeatingSoundJobs.values.forEach { it.cancel() }
        repeatingSoundJobs.clear()
    }

    /**
     * Play sounds with delay based on distance
     */
    fun playSoundsWithDelay(
        sound: ExplosionSound,
        players: Collection<Player>,
        center: Location,
        soundSpeed: Float = 20f
    ) {
        // Check if sound is on cooldown first
        val currentTime = System.currentTimeMillis()
        val lastPlayed = lastPlayedTimes[sound] ?: 0L

        if (currentTime - lastPlayed < sound.cooldownMs) {
            return // Sound is on cooldown, don't play for any player
        }

        // Update last played time
        lastPlayedTimes[sound] = currentTime

        // Use a single coroutine for efficiency
        Defcon.instance.launch {
            // Group players by distance for batch processing
            val playersByDelay = players.groupBy { player ->
                val distance = center.distance(player.location)
                val delaySeconds = distance / soundSpeed
                (delaySeconds * 1000).toLong() // Convert to milliseconds
            }

            // Process each delay group
            playersByDelay.entries.sortedBy { it.key }.forEach { (delayMs, playerGroup) ->
                delay(delayMs)

                // Play for all players at this distance
                playerGroup.forEach { player ->
                    // Check if location is enclosed (with caching)
                    val isEnclosed = checkEnclosedCached(player.location)

                    // Play sound entries
                    playSoundEntries(sound.soundEntries, player, isEnclosed)
                }
            }
        }
    }

    /**
     * Helper method to play sound entries for a player
     */
    private fun playSoundEntries(
        soundEntries: List<SoundEntry>,
        player: Player,
        isEnclosed: Boolean
    ) {
        soundEntries.forEach { entry ->
            val selectedSound = if (isEnclosed) entry.soundEnclosed else entry.soundNormal

            if (selectedSound != null) {
                repeat(max(1, entry.repeats)) {
                    val finalSound = if (entry.randomizePitch != null) {
                        val randomPitch = Random.nextFloat() *
                            (entry.randomizePitch.second - entry.randomizePitch.first) +
                            entry.randomizePitch.first
                        Sound.sound(selectedSound.name(), selectedSound.source(), selectedSound.volume(), randomPitch)
                    } else {
                        selectedSound
                    }
                    player.playSound(finalSound)
                }
            }
        }
    }

    /**
     * Check if a location is enclosed, with caching for better performance
     */
    private fun checkEnclosedCached(location: Location): Boolean {
        val currentTime = System.currentTimeMillis()
        val blockKey = "${location.world.name}_${location.blockX}_${location.blockY}_${location.blockZ}"

        // Check if we have a valid cached result
        val cachedResult = enclosedCache[blockKey]
        if (cachedResult != null && currentTime - cachedResult.timestamp < ENCLOSED_CACHE_TTL) {
            return cachedResult.isEnclosed
        }

        // Calculate new result
        val isEnclosed = checkEnclosed(location)

        // Cache the result
        enclosedCache[blockKey] = CachedEnclosedResult(isEnclosed, currentTime)

        // Clean old cache entries periodically (1% chance each call)
        if (Random.nextInt(100) == 0) {
            cleanOldCacheEntries(currentTime)
        }

        return isEnclosed
    }

    /**
     * Check if a location is enclosed, with improved performance
     */
    private fun checkEnclosed(location: Location): Boolean {
        // Use a smaller limit for performance as we only need to know if it's enclosed
        val filled = FloodFill3D.getFloodFill(location, 150, true)
        return filled.count() < 150
    }

    /**
     * Clean old cache entries
     */
    private fun cleanOldCacheEntries(currentTime: Long) {
        enclosedCache.entries.removeIf {
            currentTime - it.value.timestamp > ENCLOSED_CACHE_TTL
        }
    }

    /**
     * Clear all cooldowns
     */
    fun resetCooldowns() {
        lastPlayedTimes.clear()
    }

    /**
     * Clear specific sound cooldown
     */
    fun resetCooldown(sound: ExplosionSound) {
        lastPlayedTimes.remove(sound)
    }
}