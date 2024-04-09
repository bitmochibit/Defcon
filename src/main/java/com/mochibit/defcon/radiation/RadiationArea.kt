package com.mochibit.defcon.radiation

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.Defcon.Companion.Logger.info
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.math.Vector3
import com.mochibit.defcon.utils.FloodFill3D
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Bukkit
import org.bukkit.Chunk
import org.bukkit.Location

class RadiationArea(private val center: Location, private val maxFloodBlocks: Int = 20000, private val cuboidRadius : Vector3) {


    private var cuboidVertexes : HashSet<CuboidVertex> = HashSet()
    private var affectedChunks : HashSet<Chunk> = HashSet()

    companion object {
        private val radiationAreas = HashSet<RadiationArea>()
        fun fromCenter(center: Location, maxFloodBlocks: Int = 20000, cuboidRadius : Vector3 = Vector3(20000.0, 100.0, 20000.0)) : RadiationArea {
            radiationAreas.add(
                RadiationArea(center, maxFloodBlocks, cuboidRadius).apply {
                    generate()
                }
            )
            return radiationAreas.last()
        }

        fun checkIfInBounds(location: Location) : Boolean {
            for (area in radiationAreas) {
                if (area.checkIfInBounds(location))
                    return true
            }
            return false
        }
    }

    fun checkIfInBounds(location: Location) : Boolean {
        if (cuboidVertexes.isEmpty()) {
            return false
        }

        // Get the min coordinates and the max coordinates of the vertexes and check if the location is inside the cuboid
        val min = cuboidVertexes.first()
        val max = cuboidVertexes.last()

        if (location.x >= min.x && location.x <= max.x &&
            location.y >= min.y && location.y <= max.y &&
            location.z >= min.z && location.z <= max.z) {
            return true
        }

        return false
    }

    /**
     * Flood fills the radiation area.
     * If the area is bigger than a threshold, it will be a simple cuboid.
     * The area will be stored inside the chunkData block per block, or the vertexes if it's a cuboid.
     * For caching purposes, the area will be stored in a HashSet and removed when the chunk containing the area is unloaded.
     * For keeping track of the areas, they will be stored in a file, containing the affected chunks.
     */

    fun generate() {
        Bukkit.getScheduler().runTaskAsynchronously(Defcon.instance, Runnable {
            val locations = FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).join();
            info("Flood fill completed with ${locations.size} blocks")
            if (locations.size > maxFloodBlocks) {
                synchronized(this.cuboidVertexes) {
                    cuboidVertexes = HashSet(
                        listOf(
                            CuboidVertex(
                                center.x - cuboidRadius.x,
                                center.y - cuboidRadius.y,
                                center.z - cuboidRadius.z
                            ),
                            CuboidVertex(
                                center.x + cuboidRadius.x,
                                center.y + cuboidRadius.y,
                                center.z + cuboidRadius.z
                            )
                        )
                    )
                    affectedChunks = HashSet()
                }
                return@Runnable
            }

            info("${locations.size} is less than $maxFloodBlocks, storing in chunks")
            affectedChunks = HashSet()
            for (location in locations) {
                MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, 1.0)
                affectedChunks.add(location.chunk)
            }
        })
    }
}





