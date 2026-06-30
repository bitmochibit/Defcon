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

package me.mochibit.defcon.particles.templates

import org.bukkit.Color

interface AbstractParticle {
    val particleProperties: ParticleTemplateProperties

    var defaultColor: Color
        get() = particleProperties.defaultColor
        set(value) {
            particleProperties.defaultColor = value
        }

    fun initialVelocity(x: Double, y: Double, z: Double) {
        particleProperties.initialVelocity.set(x, y, z)
    }

    fun initialAcceleration(x: Double, y: Double, z: Double) {
        particleProperties.initialAcceleration.set(x, y, z)
    }

    fun initialDampening(x: Double, y: Double, z: Double) {
        particleProperties.initialDampening.set(x, y, z)
    }

    fun scale(x: Float, y: Float, z: Float) {
        particleProperties.displayProperties.scale.set(x, y, z)
    }

    fun displacement(x: Double, y: Double, z: Double) {
        particleProperties.displacement.set(x, y, z)
    }

    var maxLife: Long
        get() = particleProperties.maxLife
        set(value) {
            particleProperties.maxLife = value
        }


}
