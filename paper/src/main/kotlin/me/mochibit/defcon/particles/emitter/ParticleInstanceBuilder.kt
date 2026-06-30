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
package me.mochibit.defcon.particles.emitter

import me.mochibit.defcon.extensions.random
import me.mochibit.defcon.particles.templates.AbstractParticle
import me.mochibit.defcon.utils.randomBrightness
import org.bukkit.Color
import org.joml.Vector3f

/**
 * Base builder interface for particle instances
 */
sealed interface ParticleInstanceBuilder {
    fun build(): ParticleInstance
}

typealias PositionModifier = (ParticleInstance) -> Unit
typealias VelocityModifier = (ParticleInstance) -> Unit

/**
 * Builder implementation for 2D particle instances
 */
class ParticleInstanceBuilder2D : ParticleInstanceBuilder {
    private var builderColor: Color? = null
    private var maxLife: Long? = null
    private var particleTemplate: AbstractParticle? = null
    private var positionModifier: PositionModifier? = null
    private var velocityModifier: VelocityModifier? = null
    private var origin: Vector3f? = null

    /**
     * Sets the color for this particle instance
     * @param color The color to use
     * @return This builder instance for chaining
     */
    fun withColor(color: Color): ParticleInstanceBuilder2D = apply {
        this.builderColor = color
    }

    /**
     * Sets the template for this particle instance
     * @param template The template to use
     * @return This builder instance for chaining
     */
    fun withTemplate(template: AbstractParticle): ParticleInstanceBuilder2D = apply {
        this.particleTemplate = template
    }

    fun withOrigin(origin: Vector3f): ParticleInstanceBuilder2D = apply {
        this.origin = origin
    }

    /**
     * Sets the maximum life duration for this particle instance
     * @param ticks Number of ticks the particle should live
     * @return This builder instance for chaining
     */
    fun withMaxLife(ticks: Long): ParticleInstanceBuilder2D = apply {
        require(ticks > 0) { "Max life must be positive" }
        this.maxLife = ticks
    }

    fun positionModifier(modifier: (ParticleInstance) -> Unit) = apply {
        this.positionModifier = modifier
    }

    fun velocityModifier(modifier: (ParticleInstance) -> Unit) = apply {
        this.velocityModifier = modifier
    }

    /**
     * Builds and returns a new ParticleInstance based on the configured properties
     * @throws IllegalStateException if the template is not set
     */
    override fun build(): ParticleInstance {
        val template = particleTemplate
            ?: throw IllegalStateException("Particle template must be set before building")

        val displayProperties = template.particleProperties.displayProperties.let {
            DisplayParticleProperties(
                interpolationDelay = it.interpolationDelay,
                interpolationDuration = it.interpolationDuration,
                translation = it.translation,
                scale = it.scale,
                rotationLeft = it.rotationLeft,
                rotationRight = it.rotationRight,
                billboard = it.billboard,
                brightness = it.brightness,
                viewRange = it.viewRange,
                shadowRadius = it.shadowRadius,
                shadowStrength = it.shadowStrength,
                width = it.width,
                height = it.height,
            )
        }
        val particleProperties = template.particleProperties.textMode?.let {
            TextDisplayParticleProperties(
                text = it.text,
                lineWidth = it.lineWidth,
                backgroundColor = it.backgroundColor,
                textOpacity = it.textOpacity,
                hasShadow = it.hasShadow,
                isSeeThrough = it.isSeeThrough,
                useDefaultBackground = it.useDefaultBackground,
                alignment = it.alignment
            )
        } ?: throw IllegalStateException("Text mode is not set in this particle template")

        return TextDisplayParticleInstance(
            particleProperties,
            displayProperties,
            maxLife = maxLife ?: template.particleProperties.maxLife,
        ).apply {

            positionModifier?.let {
                it(this)
            }

            velocityModifier?.let {
                it(this)
            }

            val baseColor = builderColor ?: template.particleProperties.defaultColor

            this.color = if (template.particleProperties.colorSettings.randomizeColorBrightness) {
                template.particleProperties.colorSettings.let {
                    baseColor.randomBrightness(
                        it.maxDarkenFactor,
                        it.minDarkenFactor,
                        it.maxLightenFactor,
                        it.minLightenFactor
                    )
                }
            } else {
                baseColor
            }

            if (template.particleProperties.displacement.lengthSquared() > 0) {
                // Apply a random displacement to the particle position
                template.particleProperties.displacement.let {
                    position.add(
                        (0.0..it.x).random(),
                        (0.0..it.y).random(),
                        (0.0..it.z).random()
                    )
                }
            }

            // Finally apply the origin
            origin?.let {
                position.add(it)
            }
        }
    }
}

fun particle2D(block: ParticleInstanceBuilder2D.() -> Unit): ParticleInstance =
    ParticleInstanceBuilder2D().apply(block).build()