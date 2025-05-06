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

package me.mochibit.defcon.registers.listener

/**
 * Indicates that a class is compatible with specific Minecraft version range.
 *
 * @param fromVersion The minimum Minecraft version this class supports (inclusive)
 * @param toVersion The maximum Minecraft version this class supports (inclusive), or "latest" for no upper bound
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class VersionIndicator(
    val fromVersion: String,
    val toVersion: String = "latest"
)

/**
 * Indicates that a class is compatible with all Minecraft versions.
 * Classes with this annotation will always be registered regardless of server version.
 */
@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.RUNTIME)
annotation class UniversalVersionIndicator