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
    val coreSpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 14.0, 0.0, 0.0,1.5, yStart = -16.0)
    val secondarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 22.0, 20.0, 20.0, 14.0, 1.5, yStart = -8.0)
    val tertiarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 25.0, 18.0, 20.0, 2.0, -10.0, 16.0)

    val stem = CylinderShape(Particle.REDSTONE, center, 1.0, 4.0, 4.0, 18.0)

    val footCloudMain = CylinderShape(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 10.0, 4.0, 4.0, 16.0)
    val footCloudSecondary = FullCylinderShape(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 1.0, 50.0, 50.0, 60.0)

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

        // after 60 seconds, take the tertiarySpheroid and move transitionProgress from 0 to 1 over 20 seconds
        if (tickAlive > 20 * 30) {
            tertiarySpheroid.transitionProgress = (tickAlive - 20 * 30) / (20 * 20.0);
            secondarySpheroid.transitionProgress = (tickAlive - 20 * 30) / (30 * 20.0);
        }

        if (tickAlive > 20 * 15) {
            stem.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
            footCloudMain.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
            footCloudSecondary.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
        }


    }

    fun coolComponents() {
        coreSpheroid.temperature -= 1;
        secondarySpheroid.temperature -= 4;
        tertiarySpheroid.temperature -= 5;

        stem.temperature -= 25;
        footCloudMain.temperature -= 40;
        footCloudSecondary.temperature -= 70;
    }



    private fun stretchCylinder() {
        if (stem.height >= currentHeight - 10)
            return;

        stem.height += 1;
        stem.buildAndAssign();
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 60.0)
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
            .temperatureEmission(true)
        secondarySpheroid.buildAndAssign().color(startingColor, 5f)
            .temperature(maxTemp, 1500.0, maxTemp)
            .baseColor(endingColor)
            .temperatureEmission(true)
        tertiarySpheroid.buildAndAssign().color(startingColor, 5f)
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)
            .temperatureEmission(true)


        stem
            .buildAndAssign()
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)
            .temperatureEmission(true).
            particleBuilder.count(0).offset(0.0, 0.1, 0.0)

        footCloudMain
            .buildAndAssign()
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)
            .radialSpeed(0.01)
            .particleBuilder.count(0).offset(0.0, 0.005, 0.0)

        footCloudSecondary
            .buildAndAssign()
            .snapToFloor(5.0, 5.0)
            .baseColor(endingColor)
            .radialSpeed(0.05).
            particleBuilder.count(0).offset(0.0, 0.005, 0.0)
    }

    override fun stop() {
        // Nothing to do here
    }


}