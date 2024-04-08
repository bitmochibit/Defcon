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
            FloodFill3D.getFloodFillAsync(center, maxFloodBlocks + 1, true).thenAcceptAsync {
                info("Flood fill completed with ${it.size} blocks")
                if (it.size > maxFloodBlocks) {
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
                    return@thenAcceptAsync
                }

                info("${it.size} is less than $maxFloodBlocks, storing in chunks")
                affectedChunks = HashSet()
                for (location in it) {
                    MetaManager.setBlockData(location, BlockDataKey.RadiationLevel, 1.0)
                    affectedChunks.add(location.chunk)
                }
            }
        })
    }
}





