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

package com.mochibit.defcon.effects

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.extensions.toVector3
import com.mochibit.defcon.lifecycle.Lifecycled
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Bukkit

/**
 * Represents an effect component that can be added to an effect.
 */
open class ParticleComponent(
    private val particleShape: ParticleShape,
    colorSuppliable: ColorSuppliable? = null
) : EffectComponent {
    var emitBurstProbability = 1.0; private set
    var emitRate = 20; private set
    fun emitBurstProbability(value: Double) = apply { emitBurstProbability = value }
    fun emitRate(value: Int) = apply { emitRate = value }
    private var lifeCycledSuppliable: Lifecycled? = null
    var transform: Transform3D
        get() = particleShape.transform
        private set(value) {
            particleShape.transform = value
        }

    fun transform(transform: Transform3D) = apply { this.transform = transform }

    fun onLoad(onLoad: () -> Unit) = apply { particleShape.onLoad(onLoad) }

    val loaded get() = particleShape.loaded


    var visible: Boolean
        get() = particleShape.visible
        set(value) {
            particleShape.visible(value)
        }

    fun visible(visible: Boolean) = apply { this.visible = visible }

    init {
        colorSuppliable?.let { particleShape.particle.colorSupplier(it.colorSupplier) }
        if (colorSuppliable is Lifecycled) {
            lifeCycledSuppliable = colorSuppliable
        }
    }

    /**
     * Set the shape visibility after a delay
     * @param visible The visibility state
     * @param delay The delay in ticks
     */
    fun setVisibilityAfterDelay(visible: Boolean, delay: Long) = apply {
        Bukkit.getScheduler().runTaskLaterAsynchronously(Defcon.instance, Runnable {
            particleShape.visible(visible)
        }, delay)
    }

    fun translate(translation: Vector3) = apply {
        transform = transform.translated(translation)
    }

    fun rotate(axis: Vector3, angle: Double) = apply {
        transform = transform.rotated(axis, angle)
    }

    /**
     * Apply a radial
     */
    fun applyRadialVelocityFromCenter(velocity: Vector3) = apply {
        // Use the normalized direction as offset for the particle
        //particleBuilder.offset(normalized.x, particleBuilder.offsetY(), normalized.z);
        particleShape.particle.locationConsumer {
            val point = it.clone().subtract(particleShape.spawnPoint).toVector3()
            val direction = point - particleShape.center
            val normalized = direction.normalized()
            // Modify existing velocity to move directionally from the center (without overriding existing velocity)
            particleShape.particle.velocity(normalized * velocity)
        }
    }

    override fun emit() {
        particleShape.emit(emitBurstProbability, emitRate)
    }

    override fun start() {
        lifeCycledSuppliable?.start()
    }

    override fun update(delta: Double) {
        lifeCycledSuppliable?.update(delta)
    }

    override fun stop() {
        lifeCycledSuppliable?.stop()
    }
}