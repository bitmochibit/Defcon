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

package me.mochibit.defcon.particles.templates.definition

import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.particles.templates.ParticleTemplateProperties
import me.mochibit.defcon.particles.templates.TextMode
import org.bukkit.Color

class ExplosionDustParticle(
    override val particleProperties: ParticleTemplateProperties =
        ParticleTemplateProperties(
            defaultColor = Color.fromRGB(49, 49, 49),
            textMode = TextMode(
                text = "\uE000",
            )
        )
) : AbstractParticle


