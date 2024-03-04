package com.mochibit.nuclearcraft.effects

import com.mochibit.nuclearcraft.NuclearCraft
import com.mochibit.nuclearcraft.math.Vector3
import com.mochibit.nuclearcraft.particles.shapes.*
import com.mochibit.nuclearcraft.utils.ColorUtils
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import org.bukkit.Particle

class NuclearMushroom(val center: Location) : AnimatedEffect() {
    private val startingColor = Color.fromRGB(255, 229, 159)
    private val endingColor = Color.fromRGB(36, 37, 38);

    val coreSpheroid = FullHemiSphereShape(Particle.REDSTONE, center,25.0, 15.0, 0.0, 0.0, -15.0)
    val secondarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center,25.0, 20.0, 10.0, 15.0, -8.0)
    val tertiarySpheroid = FullHemiSphereShape(Particle.REDSTONE, center,20.0, 25.0, 15.0, 20.0, -10.0)

    val stem = CylinderShape(Particle.REDSTONE, center, 1.0, 3.0, 3.0, 12.0)

    val footCloudMain = FullCylinderShape(Particle.REDSTONE, center, 6.0, 8.0, 8.0, 18.0)
    val footCloudSecondary = FullCylinderShape(Particle.REDSTONE, center, 3.0, 50.0, 50.0, 60.0)

    val maxAliveTick = 20 * 60 * 3;

    var riseSpeed = 2;
    var currentHeight = 0.0;

    var maxTemp = 4000.0;
    var coreTemperature = maxTemp;
    var middleTemperature = maxTemp;
    var outerTemperature = maxTemp;

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
        recalculateTempColor()
        if (tickAlive > maxAliveTick)
            this.destroy();
    }

    fun recalculateTempColor() {
        var coreColor = ColorUtils.tempToRGB(coreTemperature);
        var middleColor = ColorUtils.tempToRGB(middleTemperature);
        var outerColor = ColorUtils.tempToRGB(outerTemperature);

        if (coreTemperature > 1800)
            coreTemperature -= 0.1;

        if (middleTemperature > 1800)
            middleTemperature -= 5;

        if (outerTemperature > 1800)
            outerTemperature -= 10;

        coreSpheroid.particleBuilder.color(coreColor, 5f);
        secondarySpheroid.particleBuilder.color(middleColor, 5f);
        tertiarySpheroid.particleBuilder.color(outerColor, 5f);
    }


    private fun stretchCylinder() {
        if (stem.height >= currentHeight-10)
            return;

        stem.height += 1;
        stem.buildAndAssign();
    }

    private fun elevateSphere(delta: Double) {
        if (currentHeight > 120)
            return;

        val deltaMovement = riseSpeed*delta;

        // Elevate the sphere using transform translation
        coreSpheroid.transform = coreSpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        secondarySpheroid.transform = secondarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        tertiarySpheroid.transform = tertiarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        currentHeight += deltaMovement;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(NuclearCraft.instance) {
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
        secondarySpheroid.buildAndAssign().color(startingColor, 5f)
        tertiarySpheroid.buildAndAssign().color(startingColor, 5f)


        stem
            .buildAndAssign()
            .color(endingColor, 5f);

        stem.particleBuilder.count(0).offset(0.0, 0.1, 0.0)

        footCloudMain
            .buildAndAssign()
            .color(endingColor, 5f);

        footCloudSecondary
            .buildAndAssign()
            .snapToFloor()
            .color(endingColor, 5f);

    }

    override fun stop() {
        // Nothing to do here
    }


}