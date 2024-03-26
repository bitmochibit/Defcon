package com.mochibit.defcon.vertexgeometry

import org.bukkit.Location
import org.bukkit.Particle

interface VertexShapeBuilder {
    fun build(): Array<Vertex>
}