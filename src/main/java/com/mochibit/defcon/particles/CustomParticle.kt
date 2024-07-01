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

package com.mochibit.defcon.particles

import org.bukkit.Location
import org.bukkit.entity.Display
import org.bukkit.entity.Display.Billboard
import org.bukkit.entity.Display.Brightness
import org.bukkit.util.Transformation
import org.joml.Matrix4f

abstract class CustomParticle(private val particleHandler: ParticleEntityHandler) : PluginParticle {
    override fun spawn(location: Location) {
        particleHandler.spawn(location);
    }

    override fun remove() {
        particleHandler.remove();
    }



    fun getHandler(): ParticleEntityHandler = particleHandler;

    companion object {
        fun availableHandler(): ParticleEntityHandler {
            return DisplayParticleHandler();
        }
    }
}