/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package com.mochibit.defcon.effects.nuclear

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.effects.AnimatedEffect
import com.mochibit.defcon.explosions.NuclearComponent
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import com.mochibit.defcon.math.Vector3
import org.bukkit.Bukkit
import org.bukkit.Color
import org.bukkit.Location
import kotlin.math.floor

class NuclearExplosionVFX(private val nuclearComponent: NuclearComponent, val center: Location) : AnimatedEffect() {
    private val startingColor = Color.fromRGB(255, 229, 159)
    private val endingColor = Color.fromRGB(88, 87, 84);
    var maxTemp = 4000.0;
    val maxAliveTick = 20 * 60 * 3;

    var riseSpeed = 4.5;
    var currentHeight = 0.0;

    override fun drawRate(): Int {
        return 1;
    }

    private val nuclearMushroom = NuclearMushroom(nuclearComponent, center)



    override fun draw() {
        nuclearMushroom.emit()
    }



    override fun animate(delta: Double) {
        elevateSphere(delta);
        coolComponents()

//        if (condensationCloud.isVisible()) {
//            stretchCondensationCloud(delta)
//        }

//        if (tickAlive > 20 * 20) {
//            condensationCloud.visible(false)
//        }

        if (tickAlive > maxAliveTick)
            this.destroy();

//        if (tickAlive > 40 * 20 && !primaryNeck.isVisible()) {
//            primaryNeck.visible(true)
//        }

        processTemperatureTransition(coreSpheroid, 0.0, 70.0)
        processTemperatureTransition(secondarySpheroid, 0.0, 50.0)
        processTemperatureTransition(tertiarySpheroid, 0.0, 30.0)

        processTemperatureTransition(stem, 15.0, 20.0)
//        processTemperatureTransition(footCloudMain, 15.0, 10.0)
//        processTemperatureTransition(footCloudSecondary, 15.0, 10.0)
    }

    fun coolComponents() {
        coreSpheroid.temperature -= 0.1;

        secondarySpheroid.temperature -= 1;
        tertiarySpheroid.temperature -= 5;

        stem.temperature -= 10;
//        footCloudMain.temperature -= 40;
//        footCloudSecondary.temperature -= 70;
    }


//    private fun stretchCondensationCloud(delta: Double) {
//        if (tickAlive % 40 != 0) return
//        val condensationCloudBuilder = (condensationCloud.shapeBuilder as SphereBuilder)
//
//        val currentRadiusXZ = condensationCloudBuilder.getRadiusXZ()
//        if (currentRadiusXZ > 150) return
//
//        val currentRadiusY = condensationCloudBuilder.getRadiusY()
//
//        condensationCloudBuilder.withRadiusXZ(currentRadiusXZ + 15.0)
//        condensationCloudBuilder.withRadiusY(currentRadiusY + 20.0)
//        condensationCloud.buildAndAssign()
//    }


    private fun elevateSphere(delta: Double) {
        if (currentHeight > 80.0) return;

        val deltaMovement = riseSpeed * delta;

        // Elevate the sphere using transform translation
        coreSpheroid.transform = coreSpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        secondarySpheroid.transform = secondarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));
        tertiarySpheroid.transform = tertiarySpheroid.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        //primaryNeck.transform = primaryNeck.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        //condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, deltaMovement, 0.0));

        currentHeight += deltaMovement;
    }


    override fun start() {
        Bukkit.getScheduler().callSyncMethod(Defcon.instance) {
            // Get nearby entities
            val entities = center.world.getNearbyPlayers(center, 300.0, 300.0, 300.0)
//            coreSpheroid.particleBuilder.receivers(entities)
//            secondarySpheroid.particleBuilder.receivers(entities)
//            tertiarySpheroid.particleBuilder.receivers(entities)

            //primaryNeck.particleBuilder.receivers(entities)

            //stem.particleBuilder.receivers(entities)

            //footCloudMain.particleBuilder.receivers(entities)
            //footCloudSecondary.particleBuilder.receivers(entities)

            //condensationCloud.particleBuilder.receivers(entities)
        };


        coreSpheroid.buildAndAssign().color(startingColor, 5f)
            .temperature(maxTemp, 1500.0, maxTemp)
            .baseColor(endingColor)
            .temperatureEmission(true)
            .velocity(Vector3(0.0, 4.0, 0.0))
            //.particleBuilder.count(0).offset(1.0, 1.0, 1.0)
        secondarySpheroid.buildAndAssign().color(startingColor, 5f)
            .temperature(maxTemp, 1500.0, maxTemp)
            .baseColor(endingColor)
            .temperatureEmission(true)
            .velocity(Vector3(0.0, 4.0, 0.0))
            //.particleBuilder.count(0).offset(1.0, 1.0, 1.0)

        tertiarySpheroid.buildAndAssign().color(startingColor, 5f)
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)
            .temperatureEmission(true)
            .velocity(Vector3(0.0, -2.0, 0.0))
            //.particleBuilder.count(0).offset(1.0, 1.0, 1.0)



//        primaryNeck.transform = primaryNeck.transform.translated(Vector3(0.0, -14.0, 0.0))
//        primaryNeck
//            .buildAndAssign()
//            .snapToFloor(5.0, 5.0)
//            .baseColor(endingColor)
//            .radialSpeed(0.01).
//            visible(false)
//            .particleBuilder.count(0).offset(0.0, -0.01, 0.0)
//
        stem
            .buildAndAssign()
            .baseColor(endingColor)
            .temperature(maxTemp, 1500.0, maxTemp)
            .temperatureEmission(true)
            .velocity(Vector3(0.0, 4.5, 0.0))
            .heightPredicate(this::visibleWhenLessThanCurrentHeight)
           // .particleBuilder.count(0).offset(0.0, 0.1, 0.0)
//
//        footCloudMain
//            .buildAndAssign()
//            .baseColor(endingColor)
//            .temperature(maxTemp, 1500.0, maxTemp)
//            .radialSpeed(0.01)
//            .particleBuilder.count(0).offset(0.0, 0.01, 0.0)
//
//        footCloudSecondary
//            .buildAndAssign()
//            .snapToFloor(5.0, 5.0)
//            .baseColor(endingColor)
//            .radialSpeed(0.05)
//            .xPredicate(this::stripesWidth)
//            .zPredicate(this::stripesWidth)
//            .particleBuilder.count(0).offset(0.0, 0.005, 0.0)
//
//        condensationCloud.transform = condensationCloud.transform.translated(Vector3(0.0, 20.0, 0.0))
//        condensationCloud
//            .buildAndAssign()
//            .baseColor(Color.fromRGB(255, 255, 255))
//            .radialSpeed(0.5)
//            .heightPredicate(this::stripesHeight)
//            .particleBuilder.count(0).offset(0.0, 0.0, 0.0)
    }

    override fun stop() {
        // Nothing to do here
    }

    private fun processTemperatureTransition(particleShape: ParticleShape, startAfterSeconds: Double, durationSeconds: Double, minTemperature: Double = 1500.0) {
        if (particleShape.temperature > minTemperature) return
        if (tickAlive < startAfterSeconds*20) return

        particleShape.transitionProgress = (tickAlive - startAfterSeconds*20) / (durationSeconds*20.0)
    }

    fun stripesHeight(value: Double): Boolean {
        // Every 10 blocks show 20 blocks of stripes
        return value % 20 < 10;
    }
    fun visibleWhenLessThanCurrentHeight(value: Double): Boolean {
        return value < currentHeight-10;
    }

    fun stripesWidth(value: Double): Boolean {
        // Every 5 blocks show 1 blocks of stripes
        return floor(value) % 5 < 1;
    }




}