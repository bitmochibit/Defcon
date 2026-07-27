package me.mochibit.defcon.foundation.services

import net.neoforged.neoforge.client.event.ViewportEvent
import net.neoforged.neoforge.common.NeoForge

class NeoforgeClientEventService: ClientEventService {
    override fun onComputeCameraAngles(listener: (ctx: CameraAngleContext) -> Unit) {
        NeoForge.EVENT_BUS.addListener<ViewportEvent.ComputeCameraAngles> { e ->
            val ctx = CameraAngleContext(e.pitch, e.yaw, e.roll, e.partialTick, e.camera, e.renderer)
            listener(ctx)
            e.pitch = ctx.pitch
            e.yaw = ctx.yaw
            e.roll = ctx.roll
        }
    }
}