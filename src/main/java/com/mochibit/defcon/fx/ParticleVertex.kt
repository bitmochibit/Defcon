package com.mochibit.defcon.fx

import com.mochibit.defcon.math.Vector3

data class ParticleVertex(var point: Vector3, var transformedPoint: Vector3 = point, var spawnTime : Long = 0)
