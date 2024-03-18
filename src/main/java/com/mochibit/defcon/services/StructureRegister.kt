package com.mochibit.defcon.services

import com.mochibit.defcon.Defcon
import com.mochibit.defcon.classes.PluginConfiguration
import com.mochibit.defcon.enums.ConfigurationStorages
import com.mochibit.defcon.enums.StructureBehaviour
import com.mochibit.defcon.exceptions.BlockNotRegisteredException
import com.mochibit.defcon.interfaces.PluginBlock
import com.mochibit.defcon.interfaces.StructureDefinition
import com.mochibit.defcon.utils.FloodFiller
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

import com.mochibit.defcon.classes.StructureBlock
import com.mochibit.defcon.classes.structures.StructureQuery
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.utils.MetaManager
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.LinkedHashMap

class StructureRegister() {
    private var pluginInstance: JavaPlugin? = null

    init {
        // Get the instance of the plugin
        this.pluginInstance = JavaPlugin.getPlugin(Defcon::class.java);
    }

    fun registerStructures() {
        registeredStructures = HashMap()
        /* REGISTER THE ITEMS COMING FROM THE CONFIG */
        val structureConfiguration = PluginConfiguration(pluginInstance, ConfigurationStorages.Structures)
        val structureConfig = structureConfiguration.config
        val registeredBlocks: HashMap<String?, PluginBlock?>? = try {
            BlockRegister.getRegisteredBlocks()
        } catch (e: BlockNotRegisteredException) {
            throw RuntimeException("Unable to register structures, blocks not registered")
        }
        structureConfig!!.getList("enabled-structures")!!.forEach { item: Any? ->
            var structure: StructureDefinition? = null

            // Get behaviour from Item
            val behaviour = structureConfig.getString("$item.behaviour") ?: return@forEach
            val requiresInterface = structureConfig.getBoolean("$item.requires-interface")
            val structureBehaviour: StructureBehaviour? = StructureBehaviour.fromString(behaviour)


            try {
                if (structureBehaviour != null) {
                    structure = structureBehaviour.structureClass?.getDeclaredConstructor()?.newInstance()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
            if (structure == null) return@forEach


            val rawDefinitions = structureConfig.getStringList("$item.block-set")
            val customBlockDefinitions = HashMap<String, String>()
            for (rawDefinition in rawDefinitions) {
                val definition = rawDefinition.split(":".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()
                customBlockDefinitions[definition[0]] = definition[1]
            }

            // Loop through block dispositions
            val blockDispositions = structureConfig.getList("$item.block-dispositions")!!
            // Every list element is a node which has a y value and the dispositions

            for (blockDisposition in blockDispositions) {
                // Access the first level of the linkedHashmap, which contains the y and the dispositions
                val blockDispositionDef = blockDisposition as LinkedHashMap<*, *>
                val set = blockDispositionDef.keys.first() ?: continue

                val effectiveBlockDisposition = blockDispositionDef[set] as LinkedHashMap<*, *>


                // Get the Y coordinate
                val y = effectiveBlockDisposition["y"] as Int
                val dispositions = effectiveBlockDisposition["dispositions"] as List<*>

                // Every disposition is a different Z coordinate
                for (z in dispositions.indices) {
                    // Split each dispositions by the comma, every sub-disposition it's a different X coordinate (block)
                    val dispositionBlocks =
                        dispositions[z].toString().split(",".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

                    // Loop through the disposition blocks
                    for (x in dispositionBlocks.indices) {
                        val blockName = dispositionBlocks[x]
                        // Check if the block is a definitions block
                        if (!customBlockDefinitions.containsKey(blockName)) {
                            throw RuntimeException("Unable to register structure, block variable $blockName is unknown")
                        }
                        val blockId = customBlockDefinitions[blockName]
                        val block = registeredBlocks?.get(blockId)
                            ?: throw RuntimeException("Unable to register structure, block $blockId is unknown")

                        structure.structureBlocks.add(StructureBlock(block, x, y, z))
                    }
                }
            }

            // Register interface blocks
            val interfaceBlockNames = structureConfig.getStringList("$item.interface-blocks")
            for (interfaceBlockName in interfaceBlockNames) {
                // Check if the block is a definitions block
                if (!customBlockDefinitions.containsKey(interfaceBlockName)) {
                    throw RuntimeException("Unable to register structure, block variable $interfaceBlockName is unknown")
                }
                val blockId = customBlockDefinitions[interfaceBlockName]

                for (structureBlock in structure.structureBlocks) {
                    if (structureBlock.block.id == blockId) {
                        structureBlock.isInterface = true
                    }
                }
            }

            // Sort the structure blocks
            structure.structureBlocks = structure.structureBlocks.sortedWith(compareBy({ it.y }, { it.z }, { it.x })).toMutableList()

            structure.requiredInterface = requiresInterface
            structure.id = item.toString()
            pluginInstance!!.getLogger().info("Registered structure $structure.id")
            registeredStructures[item.toString()] = structure

        }
        structureConfiguration.saveConfig()
    }

    fun searchByBlock(location: Location): StructureQuery {
        val foundStructures = ArrayList<StructureDefinition>() as MutableList<StructureDefinition>
        // Check if structure block then return the structure
        val foundStructureId = MetaManager.getBlockData<String>(location, BlockDataKey.StructureId);
        if (foundStructureId != null) {
            val foundStructure = registeredStructures[foundStructureId]
            if (foundStructure != null) {
                foundStructures.add(foundStructure)
                return StructureQuery(foundStructures, Collections.singletonList(location))
            }
        }



        // If not a structure block, find the structure by the location
        var pluginBlockLocations = FloodFiller.getFloodFill(location, 200, customBlockOnly = true)

        if (pluginBlockLocations.isEmpty()) return StructureQuery(foundStructures, pluginBlockLocations)

        // Sort the locations by Y
        pluginBlockLocations = pluginBlockLocations.sortedBy { it.y }

        // Get the minimum x and z for each y level
        val minX = pluginBlockLocations.minBy { it.x }
        val minY = pluginBlockLocations.minBy { it.y }
        val minZ = pluginBlockLocations.minBy { it.z }


        // Generate a new array of StructureBlocks with the relative coordinates (x - minx .. )
        var relativeStructureBlocks: MutableList<StructureBlock> = ArrayList()
        for (pluginBlockLocation in pluginBlockLocations) {
            val pluginBlock = BlockRegister.getBlock(pluginBlockLocation) ?: continue
            val relativeX = pluginBlockLocation.x - minX.x
            val relativeY = pluginBlockLocation.y - minY.y
            val relativeZ = pluginBlockLocation.z - minZ.z
            val structureBlock = StructureBlock(pluginBlock, relativeX.toInt(), relativeY.toInt(), relativeZ.toInt())
            relativeStructureBlocks.add(structureBlock)
        }

        if (relativeStructureBlocks.isEmpty()) return StructureQuery(foundStructures, pluginBlockLocations)


        // Sort the relative structure blocks by Y, then by Z, then by X
        relativeStructureBlocks =
            relativeStructureBlocks.sortedWith(compareBy({ it.y }, { it.z }, { it.x })).toMutableList()

        structureLoop@for (registeredStructure in registeredStructures.values) {
            if (registeredStructure.structureBlocks.size != relativeStructureBlocks.size) {
                continue
            }
            val regBlocks = registeredStructure.structureBlocks

            // Check if the structure is the same, if not rotate 3 times and check again (90 degrees)
            var rotations = 0
            while (rotations < 3) {
                var isSame = true
                for (i in regBlocks.indices) {
                    val registeredStructureBlock = regBlocks[i]
                    val relativeStructureBlock = relativeStructureBlocks[i]

                    if (registeredStructureBlock.block.id != relativeStructureBlock.block.id) {
                        isSame = false
                        break
                    }
                    if (registeredStructureBlock.x != relativeStructureBlock.x) {
                        isSame = false
                        break
                    }
                    if (registeredStructureBlock.y != relativeStructureBlock.y) {
                        isSame = false
                        break
                    }
                    if (registeredStructureBlock.z != relativeStructureBlock.z) {
                        isSame = false
                        break
                    }
                }

                if (isSame) {
                    foundStructures.add(registeredStructure)
                    continue@structureLoop
                } else {
                    rotations++
                    // Rotate the relative structure blocks
                    relativeStructureBlocks = com.mochibit.defcon.utils.Geometry.rotateStructureBlockPlaneXZ(relativeStructureBlocks)
                }
            }
        }

        return StructureQuery(foundStructures, pluginBlockLocations);
    }

    companion object {
        private var registeredStructures: HashMap<String, StructureDefinition> = HashMap()
    }
}
