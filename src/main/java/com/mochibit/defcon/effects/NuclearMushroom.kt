package com.mochibit.defcon.effects

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.particles.shapes.*
import com.mochibit.defcon.particles.shapes.FullHemiSphereShape
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle

class NuclearMushroom(val center: Location) : AnimatedEffect() {
    private val startingColor = Color.fromRGB(255, 229, 159)
    private val endingColor = Color.fromRGB(88, 87, 84);
    var maxTemp = 4000.0;
    val maxAliveTick = 20 * 60 * 3;

    var riseSpeed = 2;
    var currentHeight = 0.0;

    // Shapes
    val coreSpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 15.0, 0.0, 0.0, -15.0)
    val secondarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 22.0, 20.0, 10.0, 15.0, -8.0)
    val tertiarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 25.0, 15.0, 20.0, -10.0)

    val stem = CylinderShape(Particle.REDSTONE, center, 1.0, 3.0, 3.0, 12.0)

    val footCloudMain = FullCylinderShape(Particle.REDSTONE, center, 6.0, 8.0, 8.0, 18.0)
    val footCloudSecondary = FullCylinderShape(Particle.REDSTONE, center, 3.0, 50.0, 50.0, 60.0)

    override fun draw() {
        coreSpheroid.draw();
        secondarySpheroid.draw();
        tertiarySpheroid.draw();

        stem.draw();
        footCloudMain.draw();
        footCloudSecondary.draw();
    }

    override fun animate(delta: Double) {
        elevateSphere(delta);
        stretchCylinder()
        coolComponents()
        if (tickAlive > maxAliveTick)
            this.destroy();
    }

    fun coolComponents() {
        coreSpheroid.temperature -= 2;
        secondarySpheroid.temperature -= 5;
        tertiarySpheroid.temperature -= 10;

        stem.temperature -= 10;
        footCloudMain.temperature -= 15;
        footCloudSecondary.temperature -= 20;
    }



    private fun stretchCylinder() {
        if (stem.height >= currentHeight - 10)
            return;

        stem.height += 1;
        stem.buildAndAssign();
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 120)
            return;

        val deltaMovement = riseSpeed * delta;

        // Elevate the sphere using transform translation
        coreSpheroid.transform = coreSpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        secondarySpheroid.transform = secondarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        tertiarySpheroid.transform = tertiarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        currentHeight += deltaMovement;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 300.0, 300.0, 300.0)
            coreSpheroid.particleBuilder.receivers(entities)
            secondarySpheroid.particleBuilder.receivers(entities)
            tertiarySpheroid.particleBuilder.receivers(entities)

            stem.particleBuilder.receivers(entities)

            footCloudMain.particleBuilder.receivers(entities)
            footCloudSecondary.particleBuilder.receivers(entities)
        };

        coreSpheroid.buildAndAssign().color(startingColor, 5f)
            .temperature(maxTemp, 1500.0, maxTemp)
            .baseColor(endingColor)
        secondarySpheroid.buildAndAssign().color(startingColor, 5f)
            .temperature(maxTemp, 1500.0, maxTemp)
            .baseColor(endingColor)
        tertiarySpheroid.buildAndAssign().color(startingColor, 5f)
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)


        stem
            .buildAndAssign()
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)

        stem.particleBuilder.count(0).offset(0.0, 0.1, 0.0)

        footCloudMain
            .buildAndAssign()
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)

        footCloudSecondary
            .buildAndAssign()
            .snapToFloor(5.0, 5.0)
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)

    }

    override fun stop() {
        // Nothing to do here
    }


}