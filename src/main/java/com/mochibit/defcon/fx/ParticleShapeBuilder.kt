package com.mochibit.defcon.fx

import org.bukkit.Location
import org.bukkit.Particle

interface ParticleShapeBuilder {
    fun build(): Array<ParticleVertex>
    fun setParticle(particle: Particle) : ParticleShapeBuilder
    fun setSpawnPoint(spawnPoint: Location) : ParticleShapeBuilder
    fun getParticleType() : Particle
    fun getSpawnLocation() : Location
}