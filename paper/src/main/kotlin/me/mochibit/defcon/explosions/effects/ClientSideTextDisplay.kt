package me.mochibit.defcon.explosions.effects

import com.github.retrooper.packetevents.PacketEvents
import com.github.retrooper.packetevents.protocol.entity.data.EntityData
import com.github.retrooper.packetevents.protocol.entity.data.EntityDataTypes
import com.github.retrooper.packetevents.util.Quaternion4f
import com.github.retrooper.packetevents.util.Vector3f
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerDestroyEntities
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityMetadata
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerEntityTeleport
import com.github.retrooper.packetevents.wrapper.play.server.WrapperPlayServerSpawnEntity
import io.github.retrooper.packetevents.util.SpigotConversionUtil
import me.mochibit.defcon.extensions.toInt
import me.mochibit.defcon.particles.emitter.toPacketWrapper
import net.kyori.adventure.text.Component
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.EntityType
import org.bukkit.entity.Player
import org.bukkit.entity.TextDisplay
import org.joml.Matrix4f
import java.util.*
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * Client-side text display entity visible only to a specific player
 * Supports smooth interpolation for opacity changes, scaling, and billboard settings
 */
class ClientSideTextDisplay(private val player: Player) {
    private val id = Random.nextInt()
    private val metaDataList: MutableList<EntityData>

    // Track current values to avoid unnecessary updates
    private var currentOpacity: Int = 255
    private var currentScale: Vector3f = Vector3f(1f, 1f, 1f)
    private var currentBillboard: Display.Billboard = Display.Billboard.FIXED

    init {
        val hasShadow = false
        val isSeeThrough = true  // Make see-through for better flash effect
        val useDefaultBackground = false
        @Suppress("KotlinConstantConditions")
        val textFlags = ((hasShadow.toInt() or
                        (isSeeThrough.toInt() shl 1) or
                        (useDefaultBackground.toInt() shl 2) or
                        (TextDisplay.TextAlignment.CENTER.ordinal shl 3)))

        // Set initial opacity to maximum
        currentOpacity = 255

        metaDataList = mutableListOf(
            EntityData(8, EntityDataTypes.INT, 0), // Interpolation delay
            EntityData(9, EntityDataTypes.INT, 5), // Interpolation duration (5 ticks = 250ms)
            EntityData(10, EntityDataTypes.INT, 10), // Teleport/Rotation duration

            EntityData(11, EntityDataTypes.VECTOR3F, Vector3f(0f, 0f, 0f)), // Translation
            EntityData(12, EntityDataTypes.VECTOR3F, currentScale), // Scale
            EntityData(13, EntityDataTypes.QUATERNION, Quaternion4f(0f, 0f, 0f, 1f)), // Left rotation
            EntityData(14, EntityDataTypes.QUATERNION, Quaternion4f(0f, 0f, 0f, 1f)), // Right rotation

            EntityData(15, EntityDataTypes.BYTE, currentBillboard.ordinal.toByte()), // Billboard constraints

            EntityData(
                16,
                EntityDataTypes.INT,
                (15 shl 4) or (15 shl 20) // Brightness override (max brightness)
            ),

            EntityData(17, EntityDataTypes.FLOAT, 10000f), // View range
            EntityData(18, EntityDataTypes.FLOAT, 0f), // Shadow radius
            EntityData(19, EntityDataTypes.FLOAT, 0f), // Shadow strength
            EntityData(20, EntityDataTypes.FLOAT, 0f), // Width
            EntityData(21, EntityDataTypes.FLOAT, 0f), // Height

            EntityData(22, EntityDataTypes.INT, -1), // Glow color override

            EntityData(23, EntityDataTypes.ADV_COMPONENT, Component.text(" ")), // Text component
            EntityData(24, EntityDataTypes.INT, 0), // Line width
            EntityData(25, EntityDataTypes.INT, Color.WHITE.asARGB()), // Background color
            EntityData(26, EntityDataTypes.BYTE, (-1).toByte()), // Text opacity
            EntityData(27, EntityDataTypes.BYTE, textFlags.toByte()) // Text flags
        )
    }

    /**
     * Summons the text display entity for the player
     */
    fun summon() {
        val spawnPacket = WrapperPlayServerSpawnEntity(
            id,
            UUID.randomUUID(),
            SpigotConversionUtil.fromBukkitEntityType(EntityType.TEXT_DISPLAY),
            SpigotConversionUtil.fromBukkitLocation(player.location),
            0f, 0, null
        )
        val packetApi = PacketEvents.getAPI().playerManager
        packetApi.sendPacket(player, spawnPacket)

        val metadataPacket = WrapperPlayServerEntityMetadata(
            id,
            metaDataList
        )
        packetApi.sendPacket(player, metadataPacket)
    }

    /**
     * Sets the billboard type for automatic orientation
     * @param billboard The billboard type to use
     */
    fun setBillboard(billboard: Display.Billboard) {
        if (billboard == currentBillboard) return

        currentBillboard = billboard

        val billboardPacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(15, EntityDataTypes.BYTE, billboard.ordinal.toByte()))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, billboardPacket)
    }

    /**
     * Sets the rotation of the text display
     * @param pitch The pitch rotation in degrees
     * @param yaw The yaw rotation in degrees
     */
    fun setRotation(quaternion4f: Quaternion4f) {
        val rotationPacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(14, EntityDataTypes.QUATERNION, quaternion4f))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, rotationPacket)

    }

    /**
     * Teleports the text display to a new location
     */
    fun teleport(location: Location) {
        val teleportPacket = WrapperPlayServerEntityTeleport(
            id,
            SpigotConversionUtil.fromBukkitLocation(location),
            false
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, teleportPacket)
    }


    fun applyTransform(matrix: Matrix4f) {
        val translation = org.joml.Vector3f()
        val scale = org.joml.Vector3f()
        val rotationTotal = org.joml.Quaternionf()

        matrix.getTranslation(translation)
        matrix.getScale(scale)
        matrix.getUnnormalizedRotation(rotationTotal)

        // Decompose rotationTotal into Left and Right
        // For simplicity: we'll split it into two halves
        val left = org.joml.Quaternionf().identity()
        val right = org.joml.Quaternionf().set(rotationTotal)

        // You could do more complex splitting (see below), but this is safe baseline
        val rotationLeft = Quaternion4f(left.x(), left.y(), left.z(), left.w())
        val rotationRight = Quaternion4f(right.x(), right.y(), right.z(), right.w())

        val packets = listOf(
            EntityData(11, EntityDataTypes.VECTOR3F, translation.toPacketWrapper()),
            EntityData(12, EntityDataTypes.VECTOR3F, scale.toPacketWrapper()),
            EntityData(13, EntityDataTypes.QUATERNION, rotationLeft),
            EntityData(14, EntityDataTypes.QUATERNION, rotationRight)
        )

        PacketEvents.getAPI().playerManager.sendPacket(
            player,
            WrapperPlayServerEntityMetadata(id, packets)
        )
    }

    /**
     * Sets the scale of the text display
     * @param width Width scale factor
     * @param height Height scale factor
     * @param depth Depth scale factor
     */
    fun setScale(width: Float, height: Float, depth: Float) {
        val newScale = Vector3f(width, height, depth)

        // Skip if scale hasn't changed significantly
        if (abs(newScale.x - currentScale.x) < 0.1f &&
            abs(newScale.y - currentScale.y) < 0.1f &&
            abs(newScale.z - currentScale.z) < 0.1f) {
            return
        }

        currentScale = newScale

        val scalePacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(12, EntityDataTypes.VECTOR3F, currentScale))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, scalePacket)
    }

    /**
     * Sets the opacity of the text display with smooth interpolation
     * @param opacity The opacity value (0-255)
     */
    fun setOpacity(opacity: Int) {
        // Skip if opacity hasn't changed significantly (avoid packet spam)
        if (abs(opacity - currentOpacity) < 5) return

        currentOpacity = opacity

        val colorWithOpacity = Color.WHITE.setAlpha(opacity).asARGB()

        val opacityPacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(25, EntityDataTypes.INT, colorWithOpacity))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, opacityPacket)
    }

    fun setColor(color: Color) {
        val colorWithOpacity = color.asARGB()

        val colorPacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(25, EntityDataTypes.INT, colorWithOpacity))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, colorPacket)
    }

    /**
     * Updates the interpolation duration for smooth transitions
     * @param durationTicks The duration in ticks (20 ticks = 1 second)
     */
    fun setInterpolationDuration(durationTicks: Int) {
        val interpolationPacket = WrapperPlayServerEntityMetadata(
            id,
            listOf(EntityData(9, EntityDataTypes.INT, durationTicks))
        )
        PacketEvents.getAPI().playerManager.sendPacket(player, interpolationPacket)
    }

    /**
     * Removes the text display entity
     */
    fun remove() {
        val destroyPacket = WrapperPlayServerDestroyEntities(id)
        PacketEvents.getAPI().playerManager.sendPacket(player, destroyPacket)
    }
}