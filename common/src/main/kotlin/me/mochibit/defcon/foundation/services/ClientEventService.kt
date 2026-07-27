package me.mochibit.defcon.foundation.services

import net.minecraft.client.Camera
import net.minecraft.client.renderer.GameRenderer

interface ClientEventService {
    fun onComputeCameraAngles(listener: (ctx: CameraAngleContext) -> Unit)
}

val clientEventService: ClientEventService by lazy { loadService<ClientEventService>() }

data class CameraAngleContext(
    var pitch: Float,
    var yaw: Float,
    var roll: Float,
    val partialTick: Double,
    val camera: Camera,
    val renderer: GameRenderer,
)