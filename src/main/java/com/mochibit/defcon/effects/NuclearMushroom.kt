package com.mochibit.defcon.effects

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.shapes.*
import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle

class NuclearMushroom(val center: Location) : AnimatedEffect() {
    private val startingColor = Color.fromRGB(243, 158, 3)
    private val endingColor = Color.fromRGB(72, 72, 72);
    val fireCloud = FullSphereShape(Particle.REDSTONE, center,10.0, 15.0)
    val stem = CylinderShape(Particle.REDSTONE, center, 1.0, 3.0, 3.0, 12.0)
    val footCloudMain = FullCylinderShape(Particle.REDSTONE, center, 4.0, 25.0, 25.0, 30.0)
    val footCloudSecondary = FullCylinderShape(Particle.REDSTONE, center, 2.0, 40.0, 40.0, 50.0)


    var currentHeight = 0.0;
    var temperature = 500.0;
    override fun draw() {
        fireCloud.draw();
        stem.draw();
        footCloudMain.draw();
        footCloudSecondary.draw();
    }

    override fun animate(delta: Double) {
        elevateSphere(delta);
        stretchCylinder()
        decreaseTempColor()

        if (tickAlive > 1500)
            this.destroy();

        if (temperature > 1)
            temperature -= delta;
    }

    fun decreaseTempColor() {
        // Interpolate the color from the starting color to the ending color using the temperature
        val t = MathFunctions.map(1/temperature, 500.0, 1.0, 0.0, 1.0);
        val r = MathFunctions.lerp(startingColor.red, endingColor.red, t)
        val g = MathFunctions.lerp(startingColor.green, endingColor.green, t)
        val b = MathFunctions.lerp(startingColor.blue, endingColor.blue, t)
        val color = Color.fromRGB(r.toInt(), g.toInt(), b.toInt());

        fireCloud.lerpColorFromCenter(color, endingColor);
    }


    private fun stretchCylinder() {
        if (stem.height >= currentHeight-10)
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
        Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 100.0, 100.0, 100.0)
            fireCloud.particleBuilder.receivers(entities)
            stem.particleBuilder.receivers(entities)
            footCloudMain.particleBuilder.receivers(entities)
            footCloudSecondary.particleBuilder.receivers(entities)
        };

        fireCloud.buildAndAssign();
        stem.buildAndAssign();
        footCloudMain
            .buildAndAssign()

        footCloudSecondary
            .buildAndAssign()
            .snapToFloor();




        fireCloud.lerpColorFromCenter(startingColor, endingColor);
        stem.particleBuilder.color(endingColor, 5f);
        footCloudMain.particleBuilder.color(endingColor, 5f);
        footCloudSecondary.particleBuilder.color(endingColor, 5f);

    }

    override fun stop() {
        // Nothing to do here
    }


}