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

package me.mochibit.defcon.effects

import me.mochibit.defcon.Defcon
import me.mochibit.defcon.extensions.toVector3f
import me.mochibit.defcon.lifecycle.Lifecycled
import me.mochibit.defcon.math.Transform3D
import me.mochibit.defcon.math.Vector3
import me.mochibit.defcon.observer.Loadable
import me.mochibit.defcon.vertexgeometry.particle.ParticleShape
import org.bukkit.Bukkit
import org.joml.Matrix4d
import org.joml.Vector3d
import org.joml.Vector3f

/**
 * Represents an effect component that can be added to an effect.
 */
open class ParticleComponent(
    private val particleShape: ParticleShape,
    colorSuppliable: ColorSuppliable? = null,
    override val observers: MutableList<(Unit) -> Unit> = mutableListOf(),
    override var isLoaded: Boolean = false
) : EffectComponent, Loadable<Unit> {
    var emitBurstProbability = 1.0; private set
    var emitRate = 20; private set
    fun emitBurstProbability(value: Double) = apply { emitBurstProbability = value }
    fun emitRate(value: Int) = apply { emitRate = value }
    private var lifeCycledSuppliable: Lifecycled? = null
    var transform: Matrix4d
        get() = particleShape.transform
        private set(value) {
            particleShape.transform = value
        }

    fun transform(transform: Matrix4d) = apply { this.transform = transform }

    var visible: Boolean
        get() = particleShape.visible
        set(value) {
            particleShape.visible = value
        }

    fun visible(visible: Boolean) = apply { this.visible = visible }

    init {
        particleShape.onLoad {
            isLoaded = true
            this.observers.forEach { it(Unit) }
        }

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
            particleShape.visible = visible
        }, delay)
    }

    fun translate(translation: Vector3f) = apply {
        transform.translate(translation, transform)
        particleShape.updateTransformedVertexes()

    }

    fun rotate(axis: Vector3f, angle: Double) = apply {
        transform = transform.rotation(angle, axis)
        particleShape.updateTransformedVertexes()
    }

    /**
     * Apply a radial
     */
    fun applyRadialVelocityFromCenter(velocity: Vector3f) = apply {
        // Use the normalized direction as offset for the particle
        //particleBuilder.offset(normalized.x, particleBuilder.offsetY(), normalized.z);
        particleShape.particle.locationConsumer {
            val point = Vector3f((it.x - particleShape.spawnPoint.x).toFloat(),
                (it.y - particleShape.spawnPoint.y).toFloat(), (it.z - particleShape.spawnPoint.z).toFloat()
            )
            val direction = point.sub(particleShape.center)
            val normalized = direction.normalize()
            // Modify existing velocity to move directionally from the center (without overriding existing velocity)
            particleShape.particle.velocity(normalized.mul(velocity))
        }
    }

    override fun emit() {
        particleShape.emit(emitBurstProbability, emitRate)
    }

    override fun start() {
        lifeCycledSuppliable?.start()
        particleShape.emitter.start()
    }

    override fun update(delta: Float) {
        lifeCycledSuppliable?.update(delta)
        particleShape.emitter.update(delta)
    }

    override fun stop() {
        lifeCycledSuppliable?.stop()
        particleShape.emitter.stop()
    }

    override fun load() {
        particleShape.load()
    }
}