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

interface Vector3iSet {
    fun add(vector: Vector3i): Boolean
    fun contains(vector: Vector3i): Boolean
    fun remove(vector: Vector3i): Boolean
    fun removeAll(vectors: Collection<Vector3i>): Int
    fun addAll(vectors: Collection<Vector3i>): Int
    fun clear()
    fun isEmpty(): Boolean
    fun size(): Int
    fun getAllVectors(): Set<Vector3i>
}

