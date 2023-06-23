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
import java.lang.reflect.InvocationTargetException

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
            // Get behaviour from Item
            val behaviour = structureConfig.getString("$item.behaviour") ?: return@forEach
            val requiresInterface = structureConfig.getBoolean("$item.requires-interface")
            val structureBehaviour: StructureBehaviour? = StructureBehaviour.fromString(behaviour)
            var structure: PluginStructure? = null
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

            val structureBlocks = structure.structureBlocks

            // Loop through block dispositions
            val blockDispositions = structureConfig.getList("$item.block-dispositions")!!
            // Every list element is a node which has a y value and the dispositions

            for (blockDisposition in blockDispositions) {
                // Access the first level of the linkedHashmap, which contains the y and the dispositions
                val blockDispositionDef = blockDisposition as LinkedHashMap<*, *>
                val set = blockDispositionDef.keys.first()?: continue

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
                        structureBlocks.add(block?.let { StructureBlock(it, x, y, z) })
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

                for (structureBlock in structureBlocks) {
                    if (structureBlock?.block?.id == blockId) {
                        structureBlock?.isInterface = true
                    }
                }
            }

            structure.requiredInterface = requiresInterface
            registeredStructures!![item.toString()] = structure

        }
        structureConfiguration.saveConfig()
    }

    fun searchByBlock(location: Location): PluginStructure? {
        // Check if structure block then return the structure

        // If not a structure block, find the structure by the location
        val pluginBlockLocations = FloodFiller.getFloodFill(location, 200, true)
        // Loop through the found block locations, and get the minimum x, z foreach y
        // Generate a new array of StructureBlocks with the relative coordinates (x - minx .. )
        // Loop through the registered structures blocks and check if the structure is the same (rotate the structure three times until stopping, foreach registered structure)





        return null;
    }

    companion object {
        private var registeredStructures: HashMap<String, PluginStructure>? = null

        /* Registered items getter */
        @Throws(StructureNotRegisteredException::class)
        fun getRegisteredStructures(): HashMap<String, PluginStructure>? {
            if (registeredStructures == null) {
                throw StructureNotRegisteredException("Block not registered for some reason. Verify the initialization")
            }
            return registeredStructures
        }
    }
}
