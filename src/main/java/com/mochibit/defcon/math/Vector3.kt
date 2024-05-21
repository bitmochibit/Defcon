package com.mochibit.defcon.math

import com.mochibit.defcon.utils.MathFunctions
import org.bukkit.World
import kotlin.math.atan2
import kotlin.math.sqrt

// This class is a Linear Algebra vector3 class.
class Vector3(
    var x: Double,
    var y: Double,
    var z: Double
) : Cloneable {

    companion object {
        val ZERO = Vector3(0.0, 0.0, 0.0)

        val LEFT = Vector3(-1.0, 0.0, 0.0)
        val RIGHT = Vector3(1.0, 0.0, 0.0)
        val UP = Vector3(0.0, 1.0, 0.0)
        val DOWN = Vector3(0.0, -1.0, 0.0)

        val FORWARD = Vector3(0.0, 0.0, 1.0)
        val BACKWARD = Vector3(0.0, 0.0, -1.0)

        fun fromLocation(location: org.bukkit.Location): Vector3 {
            return Vector3(location.x, location.y, location.z)
        }

        fun fromBukkitVector(vector: org.bukkit.util.Vector): Vector3 {
            return Vector3(vector.x, vector.y, vector.z)
        }
    }

    constructor() : this(0.0, 0.0, 0.0)

    fun length(): Double {
        return sqrt(x * x + y * y + z * z)
    }

    fun lengthSquared(): Double {
        return x * x + y * y + z * z
    }

    fun normalize() {
        val lengthSq = lengthSquared();
        if (lengthSq == 0.0) {
            return;
        }

        val length = sqrt(lengthSq);
        x /= length;
        y /= length;
        z /= length;
    }

    fun normalized(): Vector3 {
        val result = this.clone()
        result.normalize()
        return result
    }

    fun dot(other: Vector3): Double {
        return x * other.x + y * other.y + z * other.z
    }

    fun cross(other: Vector3): Vector3 {
        return Vector3(
            y * other.z - z * other.y,
            z * other.x - x * other.z,
            x * other.y - y * other.x
        )
    }

    fun min(other: Vector3): Vector3 {
        return Vector3(
            kotlin.math.min(x, other.x),
            kotlin.math.min(y, other.y),
            kotlin.math.min(z, other.z)
        )
    }

    fun max(other: Vector3): Vector3 {
        return Vector3(
            kotlin.math.max(x, other.x),
            kotlin.math.max(y, other.y),
            kotlin.math.max(z, other.z)
        )
    }

    fun abs(): Vector3 {
        return Vector3(
            kotlin.math.abs(x),
            kotlin.math.abs(y),
            kotlin.math.abs(z)
        )
    }

    fun sign(): Vector3 {
        return Vector3(
            kotlin.math.sign(x),
            kotlin.math.sign(y),
            kotlin.math.sign(z)
        )
    }

    fun ceil(): Vector3 {
        return Vector3(
            kotlin.math.ceil(x),
            kotlin.math.ceil(y),
            kotlin.math.ceil(z)
        )
    }

    fun floor(): Vector3 {
        return Vector3(
            kotlin.math.floor(x),
            kotlin.math.floor(y),
            kotlin.math.floor(z)
        )
    }

    fun round(): Vector3 {
        return Vector3(
            kotlin.math.round(x),
            kotlin.math.round(y),
            kotlin.math.round(z)
        )
    }

    fun distanceSquared(other: Vector3): Double {
        val dx = x - other.x
        val dy = y - other.y
        val dz = z - other.z
        return dx * dx + dy * dy + dz * dz
    }

    fun distance(other: Vector3): Double {
        return sqrt(distanceSquared(other))
    }

    fun lerp(other: Vector3, t: Double): Vector3 {
        return Vector3(
            MathFunctions.lerp(x, other.x, t),
            MathFunctions.lerp(y, other.y, t),
            MathFunctions.lerp(z, other.z, t)
        )
    }

    /**
     * Spherical lerp between two vectors with a given weight. Use caution when using this method, as it is heavier than the regular Lerp.
     * @param to The target vector
     * @param weight The weight of the lerp
     * @return The lerped vector
     * @see Vector3.lerp
     *
     */
    fun slerp(to: Vector3, weight: Double): Vector3 {
        val startLengthSq = lengthSquared()
        val endLengthSq = to.lengthSquared()

        if (startLengthSq == 0.0 || endLengthSq == 0.0)
            return lerp(to, weight);

        val axis = cross(to);
        val axisLengthSq = axis.lengthSquared();
        // Check if collinear vector (no rotation needed)
        if (axisLengthSq == 0.0)
            return lerp(to, weight);

        axis /= sqrt(axisLengthSq);
        val startLength = sqrt(startLengthSq);
        val resultLength = MathFunctions.lerp(startLength, sqrt(endLengthSq), weight);
        val angle = angleTo(to);
        return rotated(axis, angle*weight) * (resultLength / startLength);
    }

    fun rotated(axis: Vector3, angle: Double): Vector3 {
        return Basis(axis, angle).xform(this)
    }

    fun clamp(min: Vector3, max: Vector3): Vector3 {
        return Vector3(
            x.coerceIn(min.x, max.x),
            y.coerceIn(min.y, max.y),
            z.coerceIn(min.z, max.z)
        )
    }

    fun angleTo(other: Vector3): Double {
        return atan2(cross(other).length(), dot(other))
    }

    fun isFinite(): Boolean {
        return x.isFinite() && y.isFinite() && z.isFinite()
    }

    // Operator overloading
    operator fun set(index: Int, value: Double) {
        when (index) {
            0 -> x = value
            1 -> y = value
            2 -> z = value
            else -> throw IndexOutOfBoundsException("Index $index is out of bounds")
        }
    }

    operator fun get(index: Int): Double {
        return when (index) {
            0 -> x
            1 -> y
            2 -> z
            else -> throw IndexOutOfBoundsException("Index $index is out of bounds")
        }
    }

    operator fun plus(other: Vector3): Vector3 {
        return Vector3(x + other.x, y + other.y, z + other.z)
    }

    operator fun plusAssign(other: Vector3) {
        x += other.x
        y += other.y
        z += other.z
    }

    operator fun minus(other: Vector3): Vector3 {
        return Vector3(x - other.x, y - other.y, z - other.z)
    }

    operator fun minusAssign(other: Vector3) {
        x -= other.x
        y -= other.y
        z -= other.z
    }

    operator fun times(scalar: Double): Vector3 {
        return Vector3(x * scalar, y * scalar, z * scalar)
    }

    operator fun timesAssign(scalar: Double) {
        x *= scalar
        y *= scalar
        z *= scalar
    }


    operator fun div(scalar: Double): Vector3 {
        return Vector3(x / scalar, y / scalar, z / scalar)
    }

    operator fun divAssign(scalar: Double) {
        x /= scalar
        y /= scalar
        z /= scalar
    }


    operator fun times(other: Vector3): Vector3 {
        return Vector3(x * other.x, y * other.y, z * other.z)
    }

    operator fun timesAssign(other: Vector3) {
        x *= other.x
        y *= other.y
        z *= other.z
    }

    operator fun div(other: Vector3): Vector3 {
        return Vector3(x / other.x, y / other.y, z / other.z)
    }

    operator fun divAssign(other: Vector3) {
        x /= other.x
        y /= other.y
        z /= other.z
    }

    operator fun unaryMinus(): Vector3 {
        return Vector3(-x, -y, -z)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Vector3) return false

        if (x != other.x) return false
        if (y != other.y) return false
        if (z != other.z) return false

        return true
    }

    operator fun compareTo(other: Vector3): Int {
        val xComparison = x.compareTo(other.x)
        if (xComparison != 0) {
            return xComparison
        }
        val yComparison = y.compareTo(other.y)
        if (yComparison != 0) {
            return yComparison
        }
        return z.compareTo(other.z)
    }

    override fun hashCode(): Int {
        var result = x.hashCode()
        result = 31 * result + y.hashCode()
        result = 31 * result + z.hashCode()
        return result
    }

    override fun toString(): String {
        return "Vector3(x=$x, y=$y, z=$z)"
    }

    public override fun clone(): Vector3 {
        return Vector3(x, y, z)
    }

    fun toBukkitVector(): org.bukkit.util.Vector {
        return org.bukkit.util.Vector(x, y, z)
    }

    fun toLocation(world: World): org.bukkit.Location {
        return org.bukkit.Location(world, x, y, z)
    }


}