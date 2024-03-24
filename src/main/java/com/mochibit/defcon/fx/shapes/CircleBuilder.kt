package com.mochibit.defcon.fx.shapes

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.fx.AbstractShapeBuilder
import com.mochibit.defcon.fx.ParticleVertex
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.MathFunctions
import kotlin.math.*

class CircleBuilder() : AbstractShapeBuilder() {
    private var radiusX: Double = 0.0
    private var radiusZ: Double = 0.0
    private var extension: Double = 1.0
    private var rate: Double = 1.0
    private var maxAngle: Double = 0.0
    private var hollow: Boolean = false
    override fun build(): Array<ParticleVertex> {


        if (hollow)
            return makeCircle(radiusX, radiusZ, rate).toTypedArray()


        val result = HashSet<ParticleVertex>()
        var x = 0.0
        var z = 0.0
        var dynamicRate = 0.0
        val radiusRate = 3.0
        while (x < radiusX || z < radiusZ) {
            dynamicRate += rate / (radiusX / radiusRate)

            result.addAll(makeCircle(x,z, dynamicRate))

            x += radiusRate
            z += radiusRate

            if (x > radiusX)
                x = radiusX

            if (z > radiusZ)
                z = radiusZ
        }


        return result.toTypedArray();
    }

    private fun makeCircle(radiusX: Double, radiusZ: Double, rate: Double) : HashSet<ParticleVertex> {
        val result = HashSet<ParticleVertex>()

        val rateDiv = PI / abs(rate)

        // If no limit is specified do a full loop.
        if (maxAngle == 0.0) maxAngle = MathFunctions.TAU
        else if (maxAngle == -1.0) maxAngle = MathFunctions.TAU / abs(extension)

        // If the extension changes (isn't 1), the wave might not do a full
        // loop anymore. So by simply dividing PI from the extension you can get the limit for a full loop.
        // By full loop it means: sin(bx) {0 < x < PI} if b (the extension) is equal to 1
        // Using period => T = 2PI/|b|
        var theta = 0.0
        while (theta <= maxAngle) {
            // In order to curve our straight line in the loop, we need to
            // use cos and sin. It doesn't matter, you can get x as sin and z as cos.
            // But you'll get weird results if you use si+n or cos for both or using tan or cot.
            val x = radiusX * cos(extension * theta)
            val z = radiusZ * sin(extension * theta)

            result.add(ParticleVertex(Vector3(x, 0.0, z)))
            theta += rateDiv
        }

        return result
    }

    // Builder methods
    fun withRadiusX(radiusX: Double): CircleBuilder {
        this.radiusX = radiusX
        return this
    }

    fun withRadiusZ(radiusZ: Double): CircleBuilder {
        this.radiusZ = radiusZ
        return this
    }

    fun withExtension(extension: Double): CircleBuilder {
        this.extension = extension
        return this
    }

    fun withRate(rate: Double): CircleBuilder {
        this.rate = rate
        return this
    }

    fun withMaxAngle(maxAngle: Double): CircleBuilder {
        this.maxAngle = maxAngle
        return this
    }

    fun hollow(hollow: Boolean): CircleBuilder {
        this.hollow = hollow
        return this
    }

    // Getters

    fun getRadiusX(): Double {
        return radiusX
    }

    fun getRadiusZ(): Double {
        return radiusZ
    }

    fun getExtension(): Double {
        return extension
    }

    fun getRate(): Double {
        return rate
    }

    fun getMaxAngle(): Double {
        return maxAngle
    }

    fun isHollow(): Boolean {
        return hollow
    }

}