package com.mochibit.nuclearcraft.effects

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.lifecycle.CycleEngine
import com.mochibit.nuclearcraft.threading.jobs.SimpleCompositionJob
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.util.Vector
import kotlin.math.PI
import kotlin.math.abs


class NuclearMushroom(val center: Location): AnimatedEffect() {

    // This code is only a prototype, it will be better structurized, it's only for testing purposes
    private var currentHeight = 0.0
    private val sphere = createSphere(10)

    private val startingColor = Color.fromRGB(243,158,3)
    val endingColor = Color.fromRGB(72,72,72);
    private var currentColor = startingColor

    override fun animate(delta: Double) {
        drawSphere();
        elevateSphere(delta);
    }

    private fun drawSphere() {
        val basis = center.clone().set(center.x, center.y + currentHeight, center.z)
        for (vector in sphere) {
            val location = basis.clone().add(vector)
            val dustOptions = DustOptions(currentColor, 10.0F);
            basis.world.spawnParticle(Particle.REDSTONE, location, 1, .5, .5, .5, 10.0, dustOptions, false)
        }
    }


    private fun elevateSphere(delta: Double) {
        if (currentHeight > 100)
            return;

        currentHeight += delta * PI*2;
    }

    private fun createSphere(radius: Int, filled: Boolean = true): List<Vector> {
        val sphere = ArrayList<Vector>()
        for (x in -radius..radius) {
            for (y in -radius..radius) {
                for (z in -radius..radius) {
                    val distance = (x * x + y * y + z * z);
                    val distanceCond = if (filled) distance <= radius * radius else distance == radius * radius
                    if (distanceCond) {
                        sphere.add(Vector(x.toDouble(), y.toDouble(), z.toDouble()))
                    }
                }
            }
        }

        return sphere
    }

    override fun start() {
    }

    override fun stop() {
        // Nothing to do here
    }
}