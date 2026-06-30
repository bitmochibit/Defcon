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

package me.mochibit.defcon.extensions

import org.joml.Vector3i

fun Vector3i.toChunkCoordinate(): Vector3i {
    // Convert world coordinates to chunk coordinates
    return Vector3i((x shr 4), 0, (z shr 4))
}

fun Vector3i.toLocalChunkCoordinate(): Vector3i {
    // Convert world coordinates to local chunk coordinates (0-15 range)
    return Vector3i((x and 15), y, (z and 15))
}
