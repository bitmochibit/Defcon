package com.mochibit.defcon.vertexgeometry.particle

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.observer.Loadable
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import com.mochibit.defcon.vertexgeometry.morphers.ShapeMorpher
import com.mochibit.defcon.vertexgeometry.morphers.SnapToFloor
import org.bukkit.Location
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

abstract class AbstractShape(
    protected val shapeBuilder: VertexShapeBuilder,
    var spawnPoint: Location,
    override val observers: MutableList<(Array<Vertex>) -> Unit> = mutableListOf(),
    override var isLoaded: Boolean = false
) : Loadable<Array<Vertex>> {

    // Use immutable collections and atomic references where possible
    var center: Vector3 = Vector3.ZERO
    private var transformedCenter: Vector3 = Vector3.ZERO
    var visible: Boolean = true
    private var shapeMorpher: ShapeMorpher? = null
    private var dynamicMorph: Boolean = false

    private val lock = ReentrantLock()

    // _vertexes should be final and set only once if possible
    @Volatile
    private var _vertexes: Array<Vertex>? = null

    // Public getter
    protected val vertexes: Array<Vertex>
        get() = _vertexes ?: throw IllegalStateException("Vertexes not loaded")

    var transform: Transform3D = Transform3D()
        set(value) {
            lock.withLock {
                field = value
                updateTransformedVertexes()
            }
        }

    var xzPredicate: ((Double, Double) -> Boolean)? = null
    var yPredicate: ((Double) -> Boolean)? = null

    override fun load() {
        val newVertexes = buildAndProcessVertexes()
        lock.withLock {
            _vertexes = newVertexes
            isLoaded = true
            observers.forEach { it.invoke(_vertexes!!) }
        }
    }

    private fun updateTransformedVertexes() {
        lock.withLock {
            _vertexes?.forEach { vertex ->
                vertex.transformedPoint = transform.xform(vertex.point)
                vertex.globalPosition = spawnPoint.clone().add(vertex.transformedPoint.toBukkitVector())
            }
            transformedCenter = transform.xform(center)
        }
    }

    private fun buildAndProcessVertexes(): Array<Vertex> {
        val builtVertexes = buildVertexes()
        return processVertexes(builtVertexes)
    }

    private fun processVertexes(vertexes: Array<Vertex>): Array<Vertex> {
        lock.withLock {
            val result = vertexes.copyOf()
            center = Vector3(
                result.map { it.point.x }.average(),
                result.map { it.point.y }.average(),
                result.map { it.point.z }.average()
            )
            transformedCenter = transform.xform(center)

            result.forEach { vertex ->
                vertex.transformedPoint = transform.xform(vertex.point)
                vertex.globalPosition = spawnPoint.clone().add(vertex.transformedPoint.toBukkitVector())
            }
            return shapeMorpher?.takeIf { !dynamicMorph }?.morph(result) ?: result
        }
    }

    open fun buildVertexes(): Array<Vertex> {
        return shapeBuilder.build()
    }

    fun setXZPredicate(predicate: (Double, Double) -> Boolean) = apply { this.xzPredicate = predicate }
    fun setYPredicate(predicate: (Double) -> Boolean) = apply { this.yPredicate = predicate }

    open fun draw(vertex: Vertex) {
        if (!visible) return

        lock.withLock {
            val transformedPoint = vertex.transformedPoint
            if (xzPredicate?.invoke(transformedPoint.x, transformedPoint.z) == false) return
            if (yPredicate?.invoke(transformedPoint.y) == false) return

            effectiveDraw(
                if (dynamicMorph) shapeMorpher?.morphVertex(vertex) ?: vertex else vertex
            )
        }
    }

    abstract fun effectiveDraw(vertex: Vertex)

    // Shape morphing methods
    fun snapToFloor(
        maxDepth: Double = 0.0,
        startYOffset: Double = 0.0,
        easeFromPoint: Location? = null,
        dynamicMorph: Boolean = false
    ): AbstractShape {
        this.dynamicMorph = dynamicMorph
        this.shapeMorpher = SnapToFloor(maxDepth, startYOffset, easeFromPoint)
        return this
    }

    private inline fun <T> ReentrantLock.withLock(action: () -> T): T {
        lock()
        return try {
            action()
        } finally {
            unlock()
        }
    }
}
