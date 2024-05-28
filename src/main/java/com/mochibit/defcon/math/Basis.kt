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

package com.mochibit.defcon.math

import com.mochibit.defcon.utils.MathFunctions.EPSILON
import com.mochibit.defcon.utils.MathFunctions.MATH_SQRT12
import com.mochibit.defcon.utils.MathFunctions.MATH_SQRT2
import com.mochibit.defcon.utils.MathFunctions.clamp
import kotlin.math.*


// This class is a Linear Algebra basis class.
class Basis() : Cloneable {
    val rows: Array<Vector3> = arrayOf(
        Vector3(1.0, 0.0, 0.0),
        Vector3(0.0, 1.0, 0.0),
        Vector3(0.0, 0.0, 1.0)
    )

    fun set(
        xx: Double,
        xy: Double,
        xz: Double,
        yx: Double,
        yy: Double,
        yz: Double,
        zx: Double,
        zy: Double,
        zz: Double
    ) {
        rows[0][0] = xx
        rows[0][1] = xy
        rows[0][2] = xz

        rows[1][0] = yx
        rows[1][1] = yy
        rows[1][2] = yz

        rows[2][0] = zx
        rows[2][1] = zy
        rows[2][2] = zz
    }

    fun set(other: Basis) {
        rows[0] = other.rows[0]
        rows[1] = other.rows[1]
        rows[2] = other.rows[2]
    }

    companion object {
        /**
         * Factory method to create a basis from a scale vector
         * @param scale The scale vector
         * @return The basis
         */
        fun fromScale(scale: Vector3): Basis {
            return Basis(
                scale.x, 0.0, 0.0,
                0.0, scale.y, 0.0,
                0.0, 0.0, scale.z
            );
        }

        fun lookingAt(target: Vector3, up: Vector3): Basis {
            val vZ = target.normalized();
            val vX = up.cross(vZ);
            vX.normalize();
            val vY = vZ.cross(vX)

            val basis = Basis();
            basis.setColumns(vX, vY, vZ);
            return basis;
        }
    }


    constructor(axis: Vector3, angle: Double) : this() {
        setAxisAngle(axis, angle)
    }

    constructor(xAxis: Vector3, yAxis: Vector3, zAxis: Vector3) : this() {
        setColumns(xAxis, yAxis, zAxis);
    }

    constructor(
        xx: Double,
        xy: Double,
        xz: Double,
        yx: Double,
        yy: Double,
        yz: Double,
        zx: Double,
        zy: Double,
        zz: Double
    ) : this() {
        set(xx, xy, xz, yx, yy, yz, zx, zy, zz)
    }

    fun setColumns(x: Vector3, y: Vector3, z: Vector3) {
        setColumn(0, x);
        setColumn(1, y);
        setColumn(2, z);
    }

    fun setColumn(index: Int, value: Vector3) {
        rows[0][index] = value.x
        rows[1][index] = value.y
        rows[2][index] = value.z
    }

    fun getColumn(index: Int): Vector3 {
        return Vector3(rows[0][index], rows[1][index], rows[2][index])
    }

    fun getMainDiagonal(): Vector3 {
        return Vector3(rows[0][0], rows[1][1], rows[2][2])
    }

    fun setZero() {
        rows[0] = Vector3.ZERO
        rows[1] = Vector3.ZERO
        rows[2] = Vector3.ZERO
    }

    fun transposeXform(to: Basis): Basis {
        return Basis(
            rows[0].x * to[0].x + rows[1].x * to[1].x + rows[2].x * to[2].x,
            rows[0].x * to[0].y + rows[1].x * to[1].y + rows[2].x * to[2].y,
            rows[0].x * to[0].z + rows[1].x * to[1].z + rows[2].x * to[2].z,
            rows[0].y * to[0].x + rows[1].y * to[1].x + rows[2].y * to[2].x,
            rows[0].y * to[0].y + rows[1].y * to[1].y + rows[2].y * to[2].y,
            rows[0].y * to[0].z + rows[1].y * to[1].z + rows[2].y * to[2].z,
            rows[0].z * to[0].x + rows[1].z * to[1].x + rows[2].z * to[2].x,
            rows[0].z * to[0].y + rows[1].z * to[1].y + rows[2].z * to[2].y,
            rows[0].z * to[0].z + rows[1].z * to[1].z + rows[2].z * to[2].z
        );
    }


    fun cofac(row1: Int, col1: Int, row2: Int, col2: Int): Double {
        return (rows[row1][col1] * rows[row2][col2] - rows[row1][col2] * rows[row2][col1])
    }


    fun invert() {
        val co = Array(3) {
            cofac(1, 1, 2, 2); cofac(1, 2, 2, 0); cofac(1, 0, 2, 1)
        }

        val det = rows[0][0] * co[0] + rows[0][1] * co[1] + rows[0][2] * co[2]
        if (det == 0.0) {
            throw ArithmeticException("Matrix is singular and cannot be inverted")
        }

        val invDet = 1.0 / det
        set(
            co[0] * invDet, cofac(0, 2, 2, 1) * invDet, cofac(0, 1, 1, 2) * invDet,
            co[1] * invDet, cofac(0, 0, 2, 2) * invDet, cofac(0, 2, 1, 0) * invDet,
            co[2] * invDet, cofac(0, 1, 2, 0) * invDet, cofac(0, 0, 1, 1) * invDet
        );
    }

    fun transpose() {
        // Swap the elements
        rows[0][1] = rows[1][0].also { rows[1][0] = rows[0][1]  }
        rows[0][2] = rows[2][0].also { rows[2][0] = rows[0][2] }
        rows[1][2] = rows[2][1].also { rows[2][1] = rows[1][2] }
    }

    fun inverse(): Basis {
        val result = this.clone();
        result.invert();
        return result;
    }

    fun transposed(): Basis {
        val result = this.clone();
        result.transpose();
        return result;
    }

    fun determinant(): Double {
        return rows[0][0] * (rows[1][1] * rows[2][2] - rows[2][1] * rows[1][2]) -
                rows[1][0] * (rows[0][1] * rows[2][2] - rows[2][1] * rows[0][2]) +
                rows[2][0] * (rows[0][1] * rows[1][2] - rows[1][1] * rows[0][2])
    }

    fun rotate(axis: Vector3, angle: Double) {
        set(rotated(axis, angle));
    }

    fun rotated(axis: Vector3, angle: Double): Basis {
        return Basis(axis, angle) * (this);
    }

    fun rotateLocal(axis: Vector3, angle: Double) {
        set(rotatedLocal(axis, angle));
    }

    fun rotatedLocal(axis: Vector3, angle: Double): Basis {
        return (this) * Basis(axis, angle);
    }

    fun getRotationAxisAngle(): Pair<Vector3, Double> {
        val orthonormalizedBasis = orthonormalized();
        val det = orthonormalizedBasis.determinant();
        if (det < 0) {
            orthonormalizedBasis.scale(Vector3(-1.0, -1.0, -1.0));
        }
        val res = orthonormalizedBasis.getAxisAngle();
        return Pair(res.first, res.second);
    }

    fun getRotationAxisAngleLocal(): Pair<Vector3, Double> {
        val transposed = transposed();
        transposed.orthonormalize();
        val det = transposed.determinant();
        if (det < 0) {
            transposed.scale(Vector3(-1.0, -1.0, -1.0));
        }
        val res = transposed.getAxisAngle();
        return Pair(res.first, -res.second);
    }

    fun rotateToAlign(from: Vector3, to: Vector3) {
        val axis = from.cross(to).normalized();
        if (axis.lengthSquared() == 0.0)
            return;

        var dot = from.dot(to);
        dot = clamp(dot, -1.0, 1.0);

        val angle = acos(dot);
        rotate(axis, angle);
    }

    fun setAxisAngle(axis: Vector3, angle: Double) {
        // Rotation matrix from axis and angle, see https://en.wikipedia.org/wiki/Rotation_matrix#Rotation_matrix_from_axis_angle
        val axisSq = Vector3(axis.x * axis.x, axis.y * axis.y, axis.z * axis.z)
        val c = cos(angle)
        rows[0].x = axisSq.x + c * (1 - axisSq.x)
        rows[1].y = axisSq.y + c * (1 - axisSq.y)
        rows[2].z = axisSq.z + c * (1 - axisSq.z)
        val s = sin(angle)
        val t = 1 - c

        var xyzt = axis.x * axis.y * t
        var zyxs = axis.z * s
        rows[0].y = xyzt - zyxs
        rows[1].x = xyzt + zyxs

        xyzt = axis.x * axis.z * t
        zyxs = axis.y * s
        rows[0].z = xyzt + zyxs
        rows[2].x = xyzt - zyxs

        xyzt = axis.y * axis.z * t
        zyxs = axis.x * s
        rows[1].z = xyzt - zyxs
        rows[2].y = xyzt + zyxs
    }

    fun getAxisAngle(): Pair<Vector3, Double> {

        var x = 0.0;
        var y = 0.0;
        var z = 0.0;


        // SOURCE: https://www.euclideanspace.com/maths/geometry/rotations/conversions/matrixToAngle/index.htm
        if ((rows[0][1] - rows[1][0]) == 0.0 && (rows[0][2] - rows[2][0]) == 0.0 && (rows[1][2] - rows[2][1]) == 0.0) {
            // Singularity found.
            // First check for identity matrix which must have +1 for all terms in leading diagonal and zero in other terms.
            if (isDiagonal() && (abs(rows[0][0] + rows[1][1] + rows[2][2] - 3) < 3 * EPSILON)) {
                // This singularity is identity matrix so angle = 0.
                return Pair(Vector3(0.0, 1.0, 0.0), 0.0);
            }
            // Otherwise this singularity is angle = 180.
            val xx = (rows[0][0] + 1) / 2;
            val yy = (rows[1][1] + 1) / 2;
            val zz = (rows[2][2] + 1) / 2;
            val xy = (rows[0][1] + rows[1][0]) / 4;
            val xz = (rows[0][2] + rows[2][0]) / 4;
            val yz = (rows[1][2] + rows[2][1]) / 4;

            if ((xx > yy) && (xx > zz)) { // rows[0][0] is the largest diagonal term.
                if (xx < EPSILON) {
                    x = 0.0;
                    y = MATH_SQRT2;
                    z = MATH_SQRT12;
                } else {
                    x = sqrt(xx);
                    y = xy / x;
                    z = xz / x;
                }
            } else if (yy > zz) { // rows[1][1] is the largest diagonal term.
                if (yy < EPSILON) {
                    x = MATH_SQRT12;
                    y = 0.0;
                    z = MATH_SQRT12;
                } else {
                    y = sqrt(yy);
                    x = xy / y;
                    z = yz / y;
                }
            } else { // rows[2][2] is the largest diagonal term so base result on this.
                if (zz < EPSILON) {
                    x = MATH_SQRT12;
                    y = MATH_SQRT12;
                    z = 0.0;
                } else {
                    z = sqrt(zz);
                    x = xz / z;
                    y = yz / z;
                }
            }
            return Pair(Vector3(x, y, z), PI);
        }
        // As we have reached here there are no singularities so we can handle normally.
        var s = sqrt((rows[2][1] - rows[1][2]) * (rows[2][1] - rows[1][2]) + (rows[0][2] - rows[2][0]) * (rows[0][2] - rows[2][0]) + (rows[1][0] - rows[0][1]) * (rows[1][0] - rows[0][1])); // Used to normalize.

        if (abs(s) < EPSILON) {
            // Prevent divide by zero, should not happen if matrix is orthogonal and should be caught by singularity test above.
            s = 1.0;
        }

        x = (rows[2][1] - rows[1][2]) / s;
        y = (rows[0][2] - rows[2][0]) / s;
        z = (rows[1][0] - rows[0][1]) / s;

        return Pair(Vector3(x, y, z), acos((rows[0][0] + rows[1][1] + rows[2][2] - 1) / 2))
    }

    fun scale(scale: Vector3) {
        rows[0][0] *= scale.x
        rows[0][1] *= scale.x
        rows[0][2] *= scale.x

        rows[1][0] *= scale.y
        rows[1][1] *= scale.y
        rows[1][2] *= scale.y

        rows[2][0] *= scale.z
        rows[2][1] *= scale.z
        rows[2][2] *= scale.z
    }

    fun scaled(scale: Vector3): Basis {
        val result = this.clone();
        result.scale(scale);
        return result;
    }

    fun scaleLocal(scale: Vector3) {
        val result = scaledLocal(scale);
        set(result);
    }

    fun scaledLocal(scale: Vector3): Basis {
        return (this * Basis.fromScale(scale));
    }

    fun scaleOrthogonal(scale: Vector3) {
        val result = scaledOrthogonal(scale);
        set(result);
    }

    fun scaledOrthogonal(scale: Vector3): Basis {
        val result = this.clone();
        var vecS = Vector3(-1.0, -1.0, -1.0) + scale;
        // Sign of vecS (vecS.x + vecS.y + vecS.z)
        val sign = sign(vecS.x + vecS.y + vecS.z);
        val orthonBasis = result.orthonormalized();
        vecS = orthonBasis.xform(vecS);

        val dots: Vector3 = Vector3.ZERO;
        for (i in 0..2) {
            for (j in 0..2) {
                dots[j] += vecS[i] * abs(result.getColumn(i).normalized().dot(orthonBasis.getColumn(j)))
            }
        }

        if (sign != sign(dots.x + dots.y + dots.z)) {
            vecS = -vecS;
        }

        orthonBasis.scaleLocal(Vector3(1.0, 1.0, 1.0) + dots)
        return orthonBasis;
    }

    fun getUniformScale(): Double {
        return (rows[0].length() + rows[1].length() + rows[2].length()) / 3.0f;
    }

    fun getScale(): Vector3 {
        val detSign = sign(determinant());
        return getScaleAbs() * detSign;
    }

    fun getScaleAbs(): Vector3 {
        return Vector3(
            Vector3(rows[0][0], rows[1][0], rows[2][0]).length(),
            Vector3(rows[0][1], rows[1][1], rows[2][1]).length(),
            Vector3(rows[0][2], rows[1][2], rows[2][2]).length()
        )
    }

    fun getScaleLocal(): Vector3 {
        val detSign = sign(determinant());
        return Vector3(rows[0].length(), rows[1].length(), rows[2].length()) * detSign;
    }

    fun setDiagonal(diagonal: Vector3) {
        rows[0][0] = diagonal.x;
        rows[0][1] = .0;
        rows[0][2] = .0;

        rows[1][0] = .0;
        rows[1][1] = diagonal.y;
        rows[1][2] = .0;

        rows[2][0] = .0;
        rows[2][1] = .0;
        rows[2][2] = diagonal.z;
    }

    fun setAxisAngleScale(axis: Vector3, angle: Double, scale: Vector3) {
        setDiagonal(scale);
        rotate(axis, angle);
    }

    // Transposed dot products
    fun tdotx(vector: Vector3): Double {
        return rows[0].x * vector.x + rows[1].x * vector.y + rows[2].x * vector.z
    }

    fun tdoty(vector: Vector3): Double {
        return rows[0].y * vector.x + rows[1].y * vector.y + rows[2].y * vector.z
    }

    fun tdotz(vector: Vector3): Double {
        return rows[0].z * vector.x + rows[1].z * vector.y + rows[2].z * vector.z
    }

    fun isEqualApprox(other: Basis): Boolean {
        return rows[0] == other.rows[0] && rows[1] == other.rows[1] && rows[2] == other.rows[2]
    }

    fun isFinite(): Boolean {
        return rows[0].isFinite() && rows[1].isFinite() && rows[2].isFinite()
    }

    fun xform(vector: Vector3): Vector3 {
        return Vector3(
            rows[0].dot(vector),
            rows[1].dot(vector),
            rows[2].dot(vector)
        );
    }

    fun xformInv(vector: Vector3): Vector3 {
        return Vector3(
            (rows[0][0] * vector.x) + (rows[1][0] * vector.y) + (rows[2][0] * vector.z),
            (rows[0][1] * vector.x) + (rows[1][1] * vector.y) + (rows[2][1] * vector.z),
            (rows[0][2] * vector.x) + (rows[1][2] * vector.y) + (rows[2][2] * vector.z)
        );
    }

    fun isOrthonormal(): Boolean {
        val x = getColumn(0);
        val y = getColumn(1);
        val z = getColumn(2);
        return x.lengthSquared() == 1.0 && y.lengthSquared() == 1.0 && z.lengthSquared() == 1.0 &&
                x.dot(y) == 0.0 && x.dot(z) == 0.0 && y.dot(z) == 0.0;
    }

    fun isOrthogonal(): Boolean {
        val x = getColumn(0);
        val y = getColumn(1);
        val z = getColumn(2);
        return x.dot(y) == 0.0 && x.dot(z) == 0.0 && y.dot(z) == 0.0;
    }

    fun isConformal(): Boolean {
        val x = getColumn(0);
        val y = getColumn(1);
        val z = getColumn(2);
        val xLenSq = x.lengthSquared();
        return xLenSq == y.lengthSquared() && xLenSq == z.lengthSquared() && x.dot(y) == 0.0 && x.dot(z) == 0.0 && y.dot(
            z
        ) == 0.0;
    }

    fun isDiagonal(): Boolean {
        return (
                rows[0][1] == 0.0 && rows[0][2] == 0.0 &&
                        rows[1][0] == 0.0 && rows[1][2] == 0.0 &&
                        rows[2][0] == 0.0 && rows[2][1] == 0.0
                );
    }

    fun isRotation(): Boolean {
        return isConformal() && determinant() == 1.0;
    }

    fun lerp(to: Basis, weight: Double): Basis {
        val basis = Basis();
        basis.rows[0] = rows[0].lerp(to.rows[0], weight);
        basis.rows[1] = rows[1].lerp(to.rows[1], weight);
        basis.rows[2] = rows[2].lerp(to.rows[2], weight);
        return basis;
    }

    fun slerp(to: Basis, weight: Double): Basis {
        throw NotImplementedError("Slerp is not implemented for Basis yet (Quaternion is missing)");
    }

    fun orthonormalize() {

        val x = getColumn(0);
        var y = getColumn(1);
        var z = getColumn(2);

        x.normalize();
        y = (y - x * (x.dot(y)));
        y.normalize();
        z = (z - x * (x.dot(z)) - y * (y.dot(z)));
        z.normalize();

        setColumn(0, x);
        setColumn(1, y);
        setColumn(2, z);
    }

    fun orthonormalized(): Basis {
        val result = this.clone();
        result.orthonormalize();
        return result;
    }

    fun orthogonalize() {
        val scale = getScale();
        orthonormalize();
        scaleLocal(scale);
    }

    fun orthogonalized(): Basis {
        val result = this.clone();
        result.orthogonalize();
        return result;
    }

    fun diagonalize(): Basis {
        val iteMax = 1024;
        var offMatrixNorm2 = rows[0][1] * rows[0][1] + rows[0][2] * rows[0][2] + rows[1][2] * rows[1][2];

        val accRot = Basis();
        var ite = 0;
        while (offMatrixNorm2 > 0.0 && ite++ < iteMax) {
            val el012 = rows[0][1] * rows[0][1];
            val el022 = rows[0][2] * rows[0][2];
            val el122 = rows[1][2] * rows[1][2];

            var i = 0;
            var j = 0;

            if (el012 > el022) {
                if (el122 > el012) {
                    i = 1;
                    j = 2;
                } else {
                    i = 0;
                    j = 1;
                }
            } else {
                if (el122 > el022) {
                    i = 1;
                    j = 2;
                } else {
                    i = 0;
                    j = 2;
                }
            }

            var angle = 0.0;
            if (rows[j][j] == rows[i][i]) {
                angle = Math.PI / 4.0;
            } else {
                angle = 0.5 * atan(2 * rows[i][j] / (rows[j][j] - rows[i][i]));
            }

            val rot = Basis();
            rot.rows[i][i] = cos(angle);
            rot.rows[j][j] = rot.rows[i][i]

            rot.rows[j][i] = sin(angle)
            rot.rows[i][j] = -(rot.rows[j][i]);

            offMatrixNorm2 -= rows[i][j] * rows[i][j];

            this *= (rot * rot.transposed());
            accRot *= rot;
        }
        return accRot;
    }



    // Operator overloads

    operator fun get(index: Int): Vector3 {
        return rows[index]
    }

    operator fun times(other: Basis): Basis {
        return Basis(
            Vector3(other.tdotx(rows[0]), other.tdoty(rows[0]), other.tdotz(rows[0])),
            Vector3(other.tdotx(rows[1]), other.tdoty(rows[1]), other.tdotz(rows[1])),
            Vector3(other.tdotx(rows[2]), other.tdoty(rows[2]), other.tdotz(rows[2]))
        )
    }

    operator fun timesAssign(other: Basis) {
        set(
            other.tdotx(rows[0]), other.tdoty(rows[0]), other.tdotz(rows[0]),
            other.tdotx(rows[1]), other.tdoty(rows[1]), other.tdotz(rows[1]),
            other.tdotx(rows[2]), other.tdoty(rows[2]), other.tdotz(rows[2])
        )
    }

    operator fun plus(other: Basis): Basis {
        val result = this.clone();
        result += other;
        return result;
    }

    operator fun plusAssign(other: Basis) {
        rows[0] = rows[0] + other.rows[0]
        rows[1] = rows[1] + other.rows[1]
        rows[2] = rows[2] + other.rows[2]
    }

    operator fun minus(other: Basis): Basis {
        val result = this.clone();
        result -= other;
        return result;
    }

    operator fun minusAssign(other: Basis) {
        rows[0] = rows[0] - other.rows[0]
        rows[1] = rows[1] - other.rows[1]
        rows[2] = rows[2] - other.rows[2]
    }

    operator fun times(scalar: Double): Basis {
        val result = this.clone();
        result *= scalar;
        return result;
    }

    operator fun timesAssign(scalar: Double) {
        rows[0] = rows[0] * scalar
        rows[1] = rows[1] * scalar
        rows[2] = rows[2] * scalar
    }

    operator fun div(scalar: Double): Basis {
        val result = this.clone();
        result /= scalar;
        return result;
    }

    operator fun divAssign(scalar: Double) {
        rows[0] = rows[0] / scalar
        rows[1] = rows[1] / scalar
        rows[2] = rows[2] / scalar
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is Basis) return false

        if (!rows.contentEquals(other.rows)) return false

        return true
    }

    public override fun clone(): Basis {
        return Basis(
            rows[0].clone(),
            rows[1].clone(),
            rows[2].clone()
        )
    }

    override fun hashCode(): Int {
        return rows.contentHashCode()
    }


}

