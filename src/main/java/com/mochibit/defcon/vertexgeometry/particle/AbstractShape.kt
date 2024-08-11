package com.mochibit.defcon.vertexgeometry.particle

import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.math.Transform3D
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.vertexgeometry.vertexes.Vertex
import com.mochibit.defcon.vertexgeometry.VertexShapeBuilder
import com.mochibit.defcon.vertexgeometry.morphers.ShapeMorpher
import com.mochibit.defcon.vertexgeometry.morphers.SnapToFloor
import org.bukkit.Location

abstract class AbstractShape(
    val shapeBuilder: VertexShapeBuilder,
    var spawnPoint: Location
) {
    var center: Vector3 = Vector3.ZERO
    var transformedCenter = Vector3.ZERO
        private set

    var visible = true
        private set
    fun visible(value: Boolean) = apply { this.visible = value }

    var shapeMorpher: ShapeMorpher? = null
        private set
    fun shapeMorpher(value: ShapeMorpher) = apply { this.shapeMorpher = value }

    var dynamicMorph = false
        private set
    fun dynamicMorph(value: Boolean) = apply { this.dynamicMorph = value }

    private var minY = 0.0
    private var maxY = 0.0
    private var _vertexes: Array<Vertex>? = null
    protected var vertexes: Array<Vertex>
        get() {
            if (_vertexes == null) {
                _vertexes = buildAndProcessVertexes()
                onShapeCompleted.forEach { it.invoke(_vertexes!!)}
            }
            return _vertexes!!
        }
        set(value) {
            _vertexes = processVertexes(value)
            onShapeCompleted.forEach { it.invoke(_vertexes!!)}
        }

    var transform = Transform3D()
        set(value) {
            field = value
            updateTransformedVertexes()
        }

    private var xzPredicate: ((Double, Double) -> Boolean)? = null
    private var yPredicate: ((Double) -> Boolean)? = null

    protected val onShapeCompleted = mutableListOf<(Array<Vertex>) -> Unit>()


    private fun updateTransformedVertexes() {
        _vertexes?.forEach { vertex ->
            vertex.transformedPoint = transform.xform(vertex.point)
            vertex.globalPosition = spawnPoint.clone().add(vertex.transformedPoint.toBukkitVector())
        }
        transformedCenter = transform.xform(center)
    }

    private fun buildAndProcessVertexes(): Array<Vertex> {
        val builtVertexes = buildVertexes()
        return processVertexes(builtVertexes)
    }

    private fun processVertexes(vertexes: Array<Vertex>): Array<Vertex> {
        val result = vertexes.copyOf()
        center = Vector3(
            result.map { it.point.x }.average(),
            result.map { it.point.y }.average(),
            result.map { it.point.z }.average()
        )
        transformedCenter = transform.xform(center)

        minY = result.minOfOrNull { it.point.y } ?: 0.0
        maxY = result.maxOfOrNull { it.point.y } ?: 0.0

        result.forEach { vertex ->
            vertex.transformedPoint = transform.xform(vertex.point)
            vertex.globalPosition = spawnPoint.clone().add(vertex.transformedPoint.toBukkitVector())
        }
        return if(shapeMorpher != null && !dynamicMorph) shapeMorpher!!.morph(result) else result
    }

    open fun buildVertexes(): Array<Vertex> {
        return shapeBuilder.build()
    }

    fun xzPredicate(predicate: (Double, Double) -> Boolean) = apply { this.xzPredicate = predicate }
    fun yPredicate(predicate: (Double) -> Boolean) = apply { this.yPredicate = predicate }

    open fun draw(vertex: Vertex) {
        if (!visible) return

        val transformedPoint = vertex.transformedPoint
        if (xzPredicate != null && !xzPredicate!!.invoke(transformedPoint.x, transformedPoint.z)) return
        if (yPredicate != null && !yPredicate!!.invoke(transformedPoint.y)) return

        effectiveDraw(
            if (dynamicMorph && shapeMorpher != null) shapeMorpher!!.morphVertex(vertex) else vertex
        )
    }

    abstract fun effectiveDraw(vertex: Vertex)

    // Shape morphing methods
    fun snapToFloor(
        maxDepth: Double = 0.0,
        startYOffset: Double = 0.0,
        easeFromPoint: Location? = null,
        dynamicMorph: Boolean = false
    ): AbstractShape {
        dynamicMorph(dynamicMorph)
        shapeMorpher(SnapToFloor(maxDepth, startYOffset, easeFromPoint))
        return this
    }
}
