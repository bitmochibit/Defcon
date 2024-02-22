package com.mochibit.nuclearcraft.math

import com.mochibit.nuclearcraft.NuclearCraft.Companion.Logger.info

class Transform3D(var basis: Basis, var origin: Vector3) : Cloneable {

    constructor() : this(Basis(), Vector3());
    fun set(transform: Transform3D) {
        basis = transform.basis;
        origin = transform.origin;
    }
    fun set(xx: Double, xy: Double, xz: Double, yx: Double, yy: Double, yz: Double, zx: Double, zy: Double, zz: Double, tx: Double, ty: Double, tz: Double) {
        basis.set(xx, xy, xz, yx, yy, yz, zx, zy, zz);
        origin.x = tx;
        origin.y = ty;
        origin.z = tz;
    }

    fun invert() {
        basis.transpose();
        origin = basis.xform(-origin)
    }

    fun inverse() : Transform3D {
        val res = clone();
        res.invert();
        return res;
    }

    fun affineInvert() {
        basis.invert();
        origin = basis.xform(-origin);
    }

    fun affineInverse() : Transform3D {
        val res = clone();
        res.affineInvert();
        return res;
    }

    fun rotate(axis: Vector3, angle: Double) {
        set(rotated(axis, angle));
    }


    /**
     * Rotates the transform around the given axis by the given angle
     * @param axis The axis to rotate around
     * @param angle The angle to rotate by
     * @return The rotated transform
     * @see Basis
     */
    fun rotated(axis: Vector3, angle: Double): Transform3D {
        val rotBasis = Basis(axis, angle);
        return Transform3D(rotBasis * basis, rotBasis.xform(origin))
    }

    fun rotatedLocal(axis: Vector3, angle: Double): Transform3D {
        val rotBasis = Basis(axis, angle);
        return Transform3D(basis * rotBasis, origin);
    }


    fun rotateBasis(axis: Vector3, angle: Double) {
        basis.rotate(axis, angle);
    }

    fun setLookAt(eye: Vector3, target: Vector3, up: Vector3 = Vector3(0.0, 1.0, 0.0)) {
        basis = Basis.lookingAt(target - eye, up);
        origin = eye;
    }

    fun lookingAt(target: Vector3, up: Vector3 = Vector3(0.0, 1.0, 0.0)) : Transform3D {
        val newTransform = clone();
        newTransform.basis = Basis.lookingAt(target - origin, up);
        return newTransform;
    }

    fun scale(scale: Vector3) {
        basis.scale(scale);
        origin = origin * scale;
    }

    fun scaled(scale: Vector3) : Transform3D {
        return Transform3D(basis.scaled(scale), origin * scale);
    }

    fun scaledLocal(scale: Vector3) : Transform3D {
        return Transform3D(basis.scaledLocal(scale), origin);
    }


    fun scaleBasis(scale: Vector3) {
        basis.scale(scale);
    }

    fun translateLocal(translation: Vector3) {
        for (i in 0..2) {
            origin[i] += basis[i].dot(translation)
        }
    }

    fun translated(translation: Vector3) : Transform3D {
        return Transform3D(basis, origin + translation);
    }

    fun translatedLocal(translation: Vector3) : Transform3D {
        return Transform3D(basis, origin + basis.xform(translation));
    }

    fun orthonormalize() {
        basis.orthonormalize();
    }

    fun orthonormalized() : Transform3D {
        val res = clone();
        res.orthonormalize();
        return res;
    }

    fun orthogonalize() {
        basis.orthogonalize();
    }

    fun orthogonalized() : Transform3D {
        val res = clone();
        res.orthogonalize();
        return res;
    }

    fun isFinite() : Boolean {
        return basis.isFinite() && origin.isFinite();
    }


    fun xform(vector: Vector3): Vector3 {
        return Vector3(
            basis.rows[0].dot(vector) + origin.x,
            basis.rows[1].dot(vector) + origin.y,
            basis.rows[2].dot(vector) + origin.z
        );
    }

    fun xform(vectors: HashSet<Vector3>) : HashSet<Vector3> {
        val result = HashSet<Vector3>();
        for (vector in vectors) {
            result.add(xform(vector));
        }
        return result;
    }

    fun xformInv(vector: Vector3): Vector3 {
        val vec = vector - origin;
        return Vector3(
            (basis.rows[0][0] * vec.x) + (basis.rows[1][0] * vec.y) + (basis.rows[2][0] * vec.z),
            (basis.rows[0][1] * vec.x) + (basis.rows[1][1] * vec.y) + (basis.rows[2][1] * vec.z),
            (basis.rows[0][2] * vec.x) + (basis.rows[1][2] * vec.y) + (basis.rows[2][2] * vec.z)
        );
    }

    fun xformInv(vectors: HashSet<Vector3>) : HashSet<Vector3> {
        val result = HashSet<Vector3>();
        for (vector in vectors) {
            result.add(xformInv(vector));
        }
        return result;
    }

    override fun equals(other: Any?): Boolean {
        if (other is Transform3D) {
            return basis == other.basis && origin == other.origin;
        }
        return false;
    }

    operator fun timesAssign(transform: Transform3D) {
        origin = xform(transform.origin);
        basis = basis*transform.basis;
    }

    operator fun times(transform: Transform3D) : Transform3D {
        val clone = clone();
        clone *= transform;
        return clone;
    }

    operator fun timesAssign(scalar : Double) {
        origin = origin * scalar;
        basis = basis * scalar;
    }

    operator fun times(scalar : Double) : Transform3D {
        val clone = clone();
        clone *= scalar;
        return clone;
    }

    operator fun divAssign(scalar : Double) {
        origin = origin / scalar;
        basis = basis / scalar;
    }

    operator fun div(scalar : Double) : Transform3D {
        val clone = clone();
        clone /= scalar;
        return clone;
    }

    override fun toString(): String {
        return "[X: ${basis.getColumn(0)} Y: ${basis.getColumn(1)} Z: ${basis.getColumn(2)} Origin: $origin]";
    }

    override fun clone(): Transform3D {
        return Transform3D(basis.clone(), origin.clone());
    }
}