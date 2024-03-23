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

    // Mushroom cloud components
    val coreSpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 14.0, 0.0, 0.0,3.0, yStart = -15.0, yEnd = -8.0, hollow = true, ignoreTopSurface = true, ignoreBottomSurface = true)
    val secondarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 22.0, 20.0, 0.0, 0.0, 4.0, yStart = 14.0, hollow = true, ignoreBottomSurface = true)
    val tertiarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center, 20.0, 25.0, 0.0, 14.0, 4.0, -10.0, 16.0, hollow = true, ignoreTopSurface = true)

    // Mushroom cloud neck
    val primaryNeck = CylinderShape(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 1.0, 14.0, 14.0, 30.0)

    // Mushroom cloud stem
    val stem = CylinderShape(Particle.REDSTONE, center, 1.0, 4.0, 4.0, 18.0)

    // Mushroom cloud foot
    val footCloudMain = CylinderShape(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 10.0, 4.0, 4.0, 16.0)
    val footCloudSecondary = FullCylinderShape(Particle.CAMPFIRE_SIGNAL_SMOKE, center, 1.0, 20.0, 20.0, 16.0)

    // Condensation cloud
    val condensationCloud = FullHemiSphereShape(Particle.CLOUD, center, 20.0, 20.0, 0.0, 0.0, 1.0, -10.0, 20.0, hollow = true, ignoreBottomSurface = true)

    override fun draw() {
        coreSpheroid.draw();
        secondarySpheroid.draw();
        tertiarySpheroid.draw();

        primaryNeck.draw();

        stem.draw();
        footCloudMain.draw();
        footCloudSecondary.draw();

        condensationCloud.draw();
    }

    override fun animate(delta: Double) {
        elevateSphere(delta);
        stretchCylinder()
        coolComponents()

        stretchCondensationCloud(delta)

        if (tickAlive > maxAliveTick)
            this.destroy();

        // after 60 seconds, take the tertiarySpheroid and move transitionProgress from 0 to 1 over 20 seconds

        tertiarySpheroid.transitionProgress = (tickAlive) / (10 * 20.0);
        secondarySpheroid.transitionProgress = (tickAlive) / (10 * 20.0);


        if (tickAlive > 20 * 15) {
            stem.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
            footCloudMain.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
            footCloudSecondary.transitionProgress = (tickAlive - 20 * 15) / (20 * 20.0);
        }


    }

    fun coolComponents() {
        coreSpheroid.temperature -= 15;

        secondarySpheroid.temperature -= 50;
        tertiarySpheroid.temperature -= 50;

        stem.temperature -= 25;
        footCloudMain.temperature -= 40;
        footCloudSecondary.temperature -= 70;
    }


    private fun stretchCondensationCloud(delta: Double) {
        if (condensationCloud.radiusXZ >= 150 )
            return;

        condensationCloud.radiusXZ += 5*delta;

        condensationCloud.buildAndAssign();
    }

    private fun stretchCylinder() {
        if (stem.height >= currentHeight - 10)
            return;

        stem.height += 1;
        stem.buildAndAssign();
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 100.0)
            return;

        val deltaMovement = riseSpeed * delta;

        // Elevate the sphere using transform translation
        coreSpheroid.transform = coreSpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        secondarySpheroid.transform = secondarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        tertiarySpheroid.transform = tertiarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        primaryNeck.transform = primaryNeck.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        currentHeight += deltaMovement;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 300.0, 300.0, 300.0)
            coreSpheroid.particleBuilder.receivers(entities)
            secondarySpheroid.particleBuilder.receivers(entities)
            tertiarySpheroid.particleBuilder.receivers(entities)

            primaryNeck.particleBuilder.receivers(entities)

            stem.particleBuilder.receivers(entities)

            footCloudMain.particleBuilder.receivers(entities)
            footCloudSecondary.particleBuilder.receivers(entities)

            condensationCloud.particleBuilder.receivers(entities)
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



        primaryNeck.transform = primaryNeck.transform.translated(Vector3(0.0, -14.0, 0.0))
        primaryNeck
            .buildAndAssign()
            .snapToFloor(5.0, 5.0)
            .baseColor(endingColor)
            .radialSpeed(0.05).
            particleBuilder.count(0).offset(0.0, 0.05, 0.0)

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

        condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, 20.0, 0.0))
        condensationCloud
            .buildAndAssign()
            .baseColor(Color.fromRGB(255, 255, 255))
            .radialSpeed(0.5)
            .particleBuilder.count(0).offset(0.0, 0.0, 0.0)
    }

    override fun stop() {
        // Nothing to do here
    }


}