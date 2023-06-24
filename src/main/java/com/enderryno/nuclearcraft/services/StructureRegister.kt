package com.enderryno.nuclearcraft.services

import com.enderryno.nuclearcraft.NuclearCraft
import com.enderryno.nuclearcraft.classes.PluginConfiguration
import com.enderryno.nuclearcraft.enums.ConfigurationStorages
import com.enderryno.nuclearcraft.enums.StructureBehaviour
import com.enderryno.nuclearcraft.exceptions.BlockNotRegisteredException
import com.enderryno.nuclearcraft.exceptions.StructureNotRegisteredException
import com.enderryno.nuclearcraft.interfaces.PluginBlock
import com.enderryno.nuclearcraft.interfaces.PluginStructure
import com.enderryno.nuclearcraft.utils.FloodFiller
import org.bukkit.Location
import org.bukkit.plugin.java.JavaPlugin

import com.enderryno.nuclearcraft.classes.StructureBlock
import com.enderryno.nuclearcraft.utils.Geometry

class StructureRegister() {
    private var pluginInstance: JavaPlugin? = null

    /**
     *
     * @param pluginInstance - The instance of the plugin
     */
    init {
        // Get the instance of the plugin
        this.pluginInstance = JavaPlugin.getPlugin(NuclearCraft::class.java);
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
            var structure: PluginStructure? = null

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
                        // Check if the block is a custom block
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
                // Check if the block is a custom block
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

            structure.requiredInterface = requiresInterface
            structure.id = item.toString()
            pluginInstance!!.getLogger().info("Registered structure $structure.id")
            registeredStructures[item.toString()] = structure

        }
        structureConfiguration.saveConfig()
    }

    fun searchByBlock(location: Location): MutableList<PluginStructure> {
        val foundStructures = ArrayList<PluginStructure>() as MutableList<PluginStructure>
        // Check if structure block then return the structure

        // If not a structure block, find the structure by the location
        var pluginBlockLocations = FloodFiller.getFloodFill(location, 200, customBlockOnly = true)

        /*
        Loop through the found block locations and retrieve the minimum x and z for each y level
        After that generate a new array of StructureBlocks with the relative coordinates (x - minx .. )
        For every registered structure, loop through the structure blocks and check if the structure is the same
        The structure check will rotate 3 times (90 degrees) to check if the structure is the same
         */

        if (pluginBlockLocations.isEmpty()) return foundStructures


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

        if (relativeStructureBlocks.isEmpty()) return foundStructures


        // Sort the relative structure blocks by Y, then by Z, then by X
        relativeStructureBlocks =
            relativeStructureBlocks.sortedWith(compareBy({ it.y }, { it.z }, { it.x })).toMutableList()



        for (registeredStructure in registeredStructures.values) {
            if (registeredStructure.structureBlocks.size != relativeStructureBlocks.size) {
                NuclearCraft.Companion.Logger.info("Structure ${registeredStructure.id} has different size")
                continue
            }

            val regBlocks =
                registeredStructure.structureBlocks.sortedWith(compareBy({ it.y }, { it.z }, { it.x })).toMutableList()

            // Check if the structure is the same, if not rotate 3 times and check again (90 degrees)
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
            }

        }

        return foundStructures;
    }

    companion object {
        private var registeredStructures: HashMap<String, PluginStructure> = HashMap()
    }
}
