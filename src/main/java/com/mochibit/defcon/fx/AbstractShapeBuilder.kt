package com.mochibit.defcon.fx

import org.bukkit.Location
import org.bukkit.Particle

abstract class AbstractShapeBuilder : ParticleShapeBuilder {
    protected var particle: Particle = Particle.FLAME
    protected var spawnPoint: Location = Location(null, .0, .0, .0)


    override fun setParticle(particle: Particle) : AbstractShapeBuilder {
        this.particle = particle
        return this
    }

    override fun setSpawnPoint(spawnPoint: Location) : AbstractShapeBuilder {
        this.spawnPoint = spawnPoint
        return this
    }

    override fun getParticleType() : Particle {
        return particle
    }

    override fun getSpawnLocation() : Location {
        return spawnPoint
    }


}