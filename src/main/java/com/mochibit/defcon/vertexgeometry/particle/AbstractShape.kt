package com.mochibit.defcon.vertexgeometry.particle

import com.mochibit.defcon.observer.Loadable
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import com.mochibit.defcon.vertexgeometry.morphers.ShapeMorpher
import com.mochibit.defcon.vertexgeometry.morphers.SnapToFloor
import org.bukkit.ChunkSnapshot
import org.bukkit.Location
import org.joml.Vector3d
import org.joml.Matrix4d
import org.joml.Vector3f
import java.util.concurrent.locks.ReentrantLock

abstract class AbstractShape(
    protected val shapeBuilder: VertexShapeBuilder,
    var spawnPoint: Location,
    override val observers: MutableList<(Array<Vertex>) -> Unit> = mutableListOf(),
    override var isLoaded: Boolean = false
) : Loadable<Array<Vertex>> {

    // Use immutable collections and atomic references where possible
    var center: Vector3f = Vector3f(0.0f, 0.0f, 0.0f)
    private var transformedCenter: Vector3d = Vector3d(0.0, 0.0, 0.0)
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

    var transform: Matrix4d = Matrix4d().identity()

    var xzPredicate: ((Double, Double) -> Boolean)? = null
    var yPredicate: ((Double) -> Boolean)? = null

    override fun load() {
        val newVertexes = buildAndProcessVertexes()
        _vertexes = newVertexes
        isLoaded = true
        observers.forEach { it.invoke(_vertexes!!) }
    }

    fun updateTransformedVertexes() {
        _vertexes?.forEach { vertex ->
            vertex.transformedPoint.set(vertex.point)
            transform.transformPosition(vertex.transformedPoint)
            // to optimize memory of this one
            vertex.globalPosition.set(spawnPoint.x + vertex.transformedPoint.x, spawnPoint.y + vertex.transformedPoint.y, spawnPoint.z + vertex.transformedPoint.z)
        }
        transformedCenter = transform.transformPosition(Vector3d(center))
    }

    private fun buildAndProcessVertexes(): Array<Vertex> {
        val builtVertexes = buildVertexes()
        return processVertexes(builtVertexes)
    }

    private fun processVertexes(vertexes: Array<Vertex>): Array<Vertex> {
        val result = vertexes.copyOf()
        center = Vector3f(
            result.map { it.point.x }.average().toFloat(),
            result.map { it.point.y }.average().toFloat(),
            result.map { it.point.z }.average().toFloat()
        )
        transformedCenter = transform.transformPosition(Vector3d(center))

        result.forEach { vertex ->
            val transformedPoint = Vector3d(vertex.point)
            transform.transformPosition(transformedPoint)
            vertex.transformedPoint = transformedPoint
            vertex.globalPosition.set(spawnPoint.x + transformedPoint.x, spawnPoint.y + transformedPoint.y, spawnPoint.z + transformedPoint.z)
        }
//        return shapeMorpher?.takeIf { !dynamicMorph }?.morph(result) ?: result
        return shapeMorpher?.takeIf { !dynamicMorph }?.morph(result) ?: result
    }

    open fun buildVertexes(): Array<Vertex> {
        return shapeBuilder.build()
    }

    fun setXZPredicate(predicate: (Double, Double) -> Boolean) = apply { this.xzPredicate = predicate }
    fun setYPredicate(predicate: (Double) -> Boolean) = apply { this.yPredicate = predicate }

    open fun draw(vertex: Vertex) {
        if (!visible) return

        val transformedPoint = vertex.transformedPoint
        if (xzPredicate?.invoke(transformedPoint.x, transformedPoint.z) == false) return
        if (yPredicate?.invoke(transformedPoint.y) == false) return

        effectiveDraw(
            if (dynamicMorph) shapeMorpher?.morphVertex(vertex) ?: vertex else vertex,
            spawnPoint.world.name
        )
    }

    abstract fun effectiveDraw(vertex: Vertex, worldName: String)

    // Shape morphing methods
    fun snapToFloor(
        maxDepth: Double = 0.0,
        startYOffset: Double = 0.0,
        easeFromPoint: Vector3d? = null,
        dynamicMorph: Boolean = false,
        chunkSnapshotCache: MutableMap<Pair<Int, Int>, ChunkSnapshot>
    ): AbstractShape {
        this.dynamicMorph = dynamicMorph
        this.shapeMorpher = SnapToFloor(spawnPoint.world, maxDepth, startYOffset, easeFromPoint, chunkSnapshotCache)
        return this
    }
}
