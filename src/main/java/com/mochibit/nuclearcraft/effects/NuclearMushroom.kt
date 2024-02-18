package com.mochibit.nuclearcraft.effects

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.utils.Math
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.Particle.DustOptions
import org.bukkit.scheduler.BukkitScheduler
import org.bukkit.util.Vector
import kotlin.math.PI

class NuclearMushroom(val center: Location) : AnimatedEffect() {

    private var currentHeight = 0.0
    private val sphere = createSphere(10)
    private val cylinder = createCylinder(4, 1, true)

    private val startingColor = Color.fromRGB(243, 158, 3)
    private val endingColor = Color.fromRGB(72, 72, 72);
    private var currentColor = startingColor
    private val particleBuilder = ParticleBuilder(Particle.REDSTONE).force(true)

    override fun animate(delta: Double) {
        // call every 20 ticks
        if (tickAlive % 5 != 0)
            return;

        drawSphere();
        drawCyl();

        elevateSphere(delta);
        if (tickAlive > 1000)
            interpolateColor(delta);

        stretchCylinder()

        if (tickAlive > 1500)
            this.destroy();

    }

    private fun drawCyl() {
        val basis = center.clone().set(center.x, center.y, center.z)
        for (vector in cylinder) {
            val location = basis.clone().add(vector)
            particleBuilder
                .location(location)
                .color(endingColor, 10f)
                .spawn()
        }
    }
    private fun drawSphere() {
        val basis = center.clone().set(center.x, center.y + currentHeight, center.z)
        for (vector in sphere) {
            val location = basis.clone().add(vector)
            particleBuilder
                .location(location)
                .color(currentColor, 10f)
                .spawn()
        }
    }

    private fun stretchCylinder() {
        // Every time currentHeight is an integer, we add a new cylinder
        if (currentHeight % 5 == 0.0)
            return;


        val newCylinder = createCylinder(4, 1, true)
        // Apply the new cylinder
        for (vector in newCylinder) {
            cylinder.add(vector.setY(currentHeight))
        }
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 50)
            return;

        currentHeight += delta * PI;
    }
    private fun interpolateColor(delta: Double) {
        val r = Math.lerp(currentColor.red, endingColor.red, delta);
        val g = Math.lerp(currentColor.green, endingColor.green, delta);
        val b = Math.lerp(currentColor.blue, endingColor.blue, delta);
        currentColor = Color.fromRGB(r, g, b);
    }

    private fun createSphere(radius: Int, filled: Boolean = true): MutableList<Vector> {
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
    private fun createCylinder(radius: Int, height: Int, filled: Boolean): MutableList<Vector> {
        val cylinder = ArrayList<Vector>()
        for (x in -radius..radius) {
            for (y in 0..height) {
                for (z in -radius..radius) {
                    val distance = (x * x + z * z);
                    val distanceCond = if (filled) distance <= radius * radius else distance == radius * radius
                    if (distanceCond) {
                        cylinder.add(Vector(x.toDouble(), y.toDouble(), z.toDouble()))
                    }
                }
            }
        }
        return cylinder
    }

    override fun start() {
        particleBuilder
            .count(1)
            .extra(10.0)

        Bukkit.getScheduler().callSyncMethod(NuclearCraft.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 100.0, 100.0, 100.0)
            particleBuilder.receivers(entities)
        };

    }

    override fun stop() {
        // Nothing to do here
    }
}