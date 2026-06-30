/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
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

package me.mochibit.defcon.utils.collection

import org.joml.Vector3i

class Vector3iSetFull : Vector3iSet {
    private val set = HashSet<Vector3iKey>()

    // Custom key class with optimized equals/hashCode
    private data class Vector3iKey(val x: Int, val y: Int, val z: Int) {
        constructor(vector: Vector3i) : this(vector.x, vector.y, vector.z)
        override fun hashCode(): Int {
            // High-quality hash function for 3D coordinates
            var result = x
            result = 31 * result + y
            result = 37 * result + z
            return result
        }

        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (other !is Vector3iKey) return false
            return x == other.x && y == other.y && z == other.z
        }
    }

    override fun add(vector: Vector3i): Boolean {
        return set.add(Vector3iKey(vector))
    }

    fun add(x: Int, y: Int, z: Int): Boolean {
        return set.add(Vector3iKey(x, y, z))
    }

    override fun contains(vector: Vector3i): Boolean {
        return set.contains(Vector3iKey(vector))
    }

    fun contains(x: Int, y: Int, z: Int): Boolean {
        return set.contains(Vector3iKey(x, y, z))
    }

    override fun remove(vector: Vector3i): Boolean {
        return set.remove(Vector3iKey(vector))
    }

    fun remove(x: Int, y: Int, z: Int): Boolean {
        return set.remove(Vector3iKey(x, y, z))
    }

    override fun removeAll(vectors: Collection<Vector3i>): Int {
        var removed = 0
        vectors.forEach { vector ->
            if (set.remove(Vector3iKey(vector))) {
                removed++
            }
        }
        return removed
    }

    override fun addAll(vectors: Collection<Vector3i>): Int {
        var added = 0
        vectors.forEach { vector ->
            if (set.add(Vector3iKey(vector))) {
                added++
            }
        }
        return added
    }

    override fun clear() {
        set.clear()
    }

    override fun isEmpty(): Boolean = set.isEmpty()

    override fun size(): Int = set.size

    override fun getAllVectors(): Set<Vector3i> {
        return set.map { Vector3i(it.x, it.y, it.z) }.toSet()
    }
}