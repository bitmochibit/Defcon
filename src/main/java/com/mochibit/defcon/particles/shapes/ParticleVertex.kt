package com.mochibit.defcon.particles.shapes

import com.mochibit.defcon.math.Vector3

data class ParticleVertex(var point: Vector3, var transformedPoint: Vector3 = point, var spawnTime : Long = 0)
