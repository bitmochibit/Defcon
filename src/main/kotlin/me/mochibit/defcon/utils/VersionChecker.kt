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

package me.mochibit.defcon.utils

import me.mochibit.defcon.Defcon


fun versionGreaterThan(version: String): Boolean {
    val currentVersionParts = Defcon.minecraftVersion.split(".").map { it.toInt() }
    val targetVersionParts = version.split(".").map { it.toInt() }

    val maxLength = maxOf(currentVersionParts.size, targetVersionParts.size)

    for (i in 0 until maxLength) {
        val currentPart = if (i < currentVersionParts.size) currentVersionParts[i] else 0
        val targetPart = if (i < targetVersionParts.size) targetVersionParts[i] else 0

        if (currentPart > targetPart) return true
        if (currentPart < targetPart) return false
    }

    return false // Versions are equal
}

fun versionGreaterOrEqualThan(version: String): Boolean {
    return versionGreaterThan(version) || Defcon.minecraftVersion == version
}
