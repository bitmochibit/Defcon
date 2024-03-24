package com.mochibit.defcon.effects

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.fx.ParticleShape
import com.mochibit.defcon.fx.shapes.CylinderBuilder
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.fx.shapes.SphereBuilder
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
    private val coreSpheroid = ParticleShape(
        SphereBuilder()
            .withRadiusXZ(14.0)
            .withRadiusY(20.0)
            .withDensity(3.0)
            .withYStart(-15.0)
            .withYEnd(-8.0)
            .hollow(true)
            .ignoreBottomSurface(true)
            .ignoreBottomSurface(true),
        Particle.REDSTONE,
        center
    )
    private val secondarySpheroid = ParticleShape(
        SphereBuilder()
            .withRadiusXZ(20.0)
            .withRadiusY(22.0)
            .withDensity(4.0)
            .withYStart(14.0)
            .hollow(true)
            .ignoreBottomSurface(true),
        Particle.REDSTONE,
        center
    )
    private val tertiarySpheroid = ParticleShape(
        SphereBuilder()
            .withRadiusXZ(25.0)
            .withRadiusY(20.0)
            .skipRadiusXZ(14.0)
            .withDensity(4.0)
            .withYStart(-10.0)
            .withYEnd(16.0)
            .hollow(true)
            .ignoreTopSurface(true),
        Particle.REDSTONE,
        center
    )

    // Mushroom cloud neck
    private val primaryNeck = ParticleShape(
        CylinderBuilder()
            .withHeight(1.0)
            .withRadiusX(14.0)
            .withRadiusZ(14.0)
            .withRate(30.0)
            .hollow(true),
        Particle.CAMPFIRE_SIGNAL_SMOKE,
        center
    )

    // Mushroom cloud stem
    private val stem = ParticleShape(
        CylinderBuilder()
            .withHeight(90.0)
            .withRadiusX(4.0)
            .withRadiusZ(4.0)
            .withRate(18.0)
            .hollow(true),
        Particle.REDSTONE,
        center
    )

    // Mushroom cloud foot
    private val footCloudMain = ParticleShape(
        CylinderBuilder()
            .withHeight(10.0)
            .withRadiusX(4.0)
            .withRadiusZ(4.0)
            .withRate(16.0)
            .hollow(true),
        Particle.CAMPFIRE_SIGNAL_SMOKE,
        center
    )
    private val footCloudSecondary = ParticleShape(
        CylinderBuilder()
            .withHeight(1.0)
            .withRadiusX(20.0)
            .withRadiusZ(20.0)
            .withRate(16.0),
        Particle.CAMPFIRE_SIGNAL_SMOKE,
        center
    )

    // Condensation cloud
    private val condensationCloud = ParticleShape(
        SphereBuilder()
            .withRadiusXZ(20.0)
            .withRadiusY(20.0)
            .withDensity(1.0)
            .withYStart(-10.0)
            .withYEnd(20.0)
            .hollow(true)
            .ignoreBottomSurface(true),
        Particle.CLOUD,
        center
    )

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
        coolComponents()

        if (condensationCloud.isVisible()) {
            stretchCondensationCloud(delta)
        }

        if (tickAlive > 20 * 20) {
            condensationCloud.visible(false)
        }

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
        if (tickAlive % 40 != 0) return
        val condensationCloudBuilder = (condensationCloud.particleShapeBuilder as SphereBuilder)

        val currentRadiusXZ = condensationCloudBuilder.getRadiusXZ()
        if (currentRadiusXZ > 150) return

        condensationCloudBuilder.withRadiusXZ(currentRadiusXZ + 10.0)
        condensationCloudBuilder.withRadiusY(currentRadiusXZ + 8.0)
        condensationCloud.buildAndAssign()
    }


    private fun elevateSphere(delta: Double) {
        if (currentHeight > 100.0) return;

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

        stem.heightPredicate(this::visibleWhenLessThanCurrentHeight)
        condensationCloud.heightPredicate(this::stripesHeight)
    }

    override fun stop() {
        // Nothing to do here
    }

    fun stripesHeight(value: Double): Boolean {
        // Every 10 blocks show 20 blocks of stripes
        return value % 20 < 10;
    }

    fun visibleWhenLessThanCurrentHeight(value: Double): Boolean {
        return value < currentHeight-10;
    }


}