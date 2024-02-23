package com.mochibit.nuclearcraft.effects

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.math.Vector3
import com.mochibit.nuclearcraft.particles.shapes.FullSphereShape
import com.mochibit.nuclearcraft.particles.shapes.SphereShape
import com.mochibit.nuclearcraft.utils.MathFunctions
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import kotlin.math.*
import kotlin.random.Random.Default.nextInt

class NuclearMushroom(val center: Location) : AnimatedEffect() {

    private val mainCloudEffect = ParticleBuilder(Particle.REDSTONE).force(true)
    private val cloudStemEffect = ParticleBuilder(Particle.REDSTONE).force(true)
    private var currentHeight = 0.0
    private val cylinder = createFilledCircle(1.0, 12.0, 10.0);

    private val startingColor = Color.fromRGB(243, 158, 3)
    private val endingColor = Color.fromRGB(72, 72, 72);

    val sphereShape = FullSphereShape(Particle.REDSTONE, 10.0, 15.0)
    var height = 0.0;

    override fun draw() {
        sphereShape.draw(center);
        //drawCyl();
    }

    override fun animate(delta: Double) {
//        elevateSphere(delta);
//        stretchCylinder()

        if (tickAlive > 1500)
            this.destroy();
    }

    private fun drawCyl() {
        val basis = center.clone().set(center.x, center.y, center.z)
        for (vector in cylinder) {
            // For optimization, 95% of the time we skip the vector
            if (nextInt(0, 100) <= 95)
                continue;


            val location = basis.clone().add(vector)
            // Interpolate the color based on the height
            val ratio = (location.y / (location.y + currentHeight))
            val r = MathFunctions.lerp(startingColor.red, endingColor.red, ratio)
            val g = MathFunctions.lerp(startingColor.green, endingColor.green, ratio)
            val b = MathFunctions.lerp(startingColor.blue, endingColor.blue, ratio)
            cloudStemEffect
                .location(location)
                .color(Color.fromRGB(r.toInt(), g.toInt(), b.toInt()), 5f)
                .count(0)
                .offset(0.0, 0.1, 0.0)
                .spawn()
        }
    }

    private fun drawSphere() {
        val basis = center.clone().set(center.x, center.y + currentHeight, center.z)
        for (vector in sphereShape.points) {
            // For optimization, 95% of the time we skip the vector
            if (nextInt(0, 100) <= 95)
                continue;

            val location = basis.clone().add(vector.toBukkitVector())
            // Interpolate the color based on the distance squared from the center
            val distance = location.distanceSquared(basis)
            val ratio = distance / 140
            val r = MathFunctions.lerp(startingColor.red, endingColor.red, ratio)
            val g = MathFunctions.lerp(startingColor.green, endingColor.green, ratio)
            val b = MathFunctions.lerp(startingColor.blue, endingColor.blue, ratio)

            mainCloudEffect
               // .location(location)
                .color(Color.fromRGB(r.toInt(), g.toInt(), b.toInt()), 5f)
                .count(0)
                .spawn()
        }
    }

    private fun stretchCylinder() {

        val newCylinder = createCylinder(1.0, 3.0, 8.0)
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


    private fun createCylinder(height: Double, radius: Double, rate: Double) : HashSet<Vector> {
        val result = HashSet<Vector>();
        var y = 0.0
        while (y < height) {
            val circle = createCircle(radius, radius, 1.0, rate, 0.0);
            result.addAll(circle);
            y += 0.1
        }
        return result;
    }

    private fun createFilledCircle(radius: Double, rate: Double, radiusRate: Double): HashSet<Vector> {
        val result = HashSet<Vector>()
        var dynamicRate = 0.0
        var i = 0.1
        while (i < radius) {
            dynamicRate += rate / (radius / radiusRate)
            result.addAll(createCircle(i, i, 1.0, rate, 0.0))
            i += radiusRate
        }
        return result;
    }


    private fun createCircle(
        radius: Double,
        radius2: Double,
        extension: Double,
        rate: Double,
        limit: Double,
    ): HashSet<Vector> {
        val result = HashSet<Vector>()

        var limit = limit
        val rateDiv = java.lang.Math.PI / abs(rate)

        // If no limit is specified do a full loop.
        if (limit == 0.0) limit = MathFunctions.TAU
        else if (limit == -1.0) limit = MathFunctions.TAU / abs(extension)

        // If the extension changes (isn't 1), the wave might not do a full
        // loop anymore. So by simply dividing PI from the extension you can get the limit for a full loop.
        // By full loop it means: sin(bx) {0 < x < PI} if b (the extension) is equal to 1
        // Using period => T = 2PI/|b|
        var theta = 0.0
        while (theta <= limit) {
            // In order to curve our straight line in the loop, we need to
            // use cos and sin. It doesn't matter, you can get x as sin and z as cos.
            // But you'll get weird results if you use si+n or cos for both or using tan or cot.
            val x = radius * cos(extension * theta)
            val z = radius2 * sin(extension * theta)


            val phi = atan2(z, x)
            val directionX = cos(extension * phi)
            val directionZ = sin(extension * phi)

            cloudStemEffect.offset(directionX, cloudStemEffect.offsetY(), directionZ)

            result.add(Vector(x, 0.0, z))
            theta += rateDiv
        }

        return result;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(NuclearCraft.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 100.0, 100.0, 100.0)
            mainCloudEffect.receivers(entities)
            cloudStemEffect.receivers(entities)
        };

        sphereShape.particleBuilder.color(startingColor, 5f)
        sphereShape.buildAndAssign();
    }

    override fun stop() {
        // Nothing to do here
    }
}