package com.mochibit.nuclearcraft.effects

import com.destroystokyo.paper.ParticleBuilder
import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.math.Vector3
import com.mochibit.nuclearcraft.particles.shapes.CylinderShape
import com.mochibit.nuclearcraft.particles.shapes.FullCircleShape
import com.mochibit.nuclearcraft.particles.shapes.FullSphereShape
import com.mochibit.nuclearcraft.particles.shapes.SphereShape
import com.mochibit.nuclearcraft.utils.MathFunctions
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle
import org.bukkit.util.Vector
import java.util.function.Supplier
import kotlin.math.*
import kotlin.random.Random.Default.nextInt

class NuclearMushroom(val center: Location) : AnimatedEffect() {
    private val startingColor = Color.fromRGB(243, 158, 3)
    private val endingColor = Color.fromRGB(72, 72, 72);
    val fireCloud = FullSphereShape(Particle.REDSTONE, 10.0, 15.0)
    val stem = CylinderShape(Particle.REDSTONE, 1.0, 3.0, 3.0, 12.0)
    val footCloud = FullCircleShape(Particle.REDSTONE, 30.0, 30.0, 15.0, 10.0)


    var currentHeight = 0.0;
    var temperature = 9000.0;
    override fun draw() {
        fireCloud.draw(center);
        stem.draw(center);
        footCloud.draw(center);
    }

    override fun animate(delta: Double) {
        elevateSphere(delta);
        stretchCylinder()

        if (tickAlive > 1500)
            this.destroy();

        if (temperature > 1)
            temperature =- delta;
    }



    private fun stretchCylinder() {
        if (stem.height >= currentHeight)
            return;

        stem.height += 1;
        stem.buildAndAssign();
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 50)
            return;

        // Elevate the sphere using transform translation
        val transformed = fireCloud.transform.translated(Vector3(0.0, delta, 0.0));
        fireCloud.transform = transformed;

        currentHeight += delta;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(NuclearCraft.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 100.0, 100.0, 100.0)
            fireCloud.particleBuilder.receivers(entities)
            stem.particleBuilder.receivers(entities)
            footCloud.particleBuilder.receivers(entities)
        };

        fireCloud.buildAndAssign();
        stem.buildAndAssign();
        footCloud.buildAndAssign();

        fireCloud.lerpColorFromCenter(startingColor, endingColor);
        stem.particleBuilder.color(endingColor, 5f);
        footCloud.particleBuilder.color(endingColor, 5f);

    }

    override fun stop() {
        // Nothing to do here
    }


}