/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2025 mochibit.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

package me.mochibit.defcon.config

import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.content.structures.PluginStructure
import me.mochibit.defcon.content.structures.PluginStructureProperties
import me.mochibit.defcon.content.structures.StructureBehaviour
import me.mochibit.defcon.utils.Logger
import org.bukkit.configuration.ConfigurationSection

object StructuresConfiguration : PluginConfiguration<List<StructuresConfiguration.StructureDefinition>>("structures") {
    data class StructureDefinition(
        val id: String,
        val displayName: String,
        val description: String,
        val formation: StructureFormation,
        override val behaviour: StructureBehaviour,
        override val behaviourData: Map<String, Any>,
    ): ElementDefinition<PluginStructureProperties, PluginStructure>

    data class StructureFormation(
        val type: FormationType,
        val pattern: StructurePattern? = null,
        val requiredBlocks: List<String> = emptyList(),
        val optionalBlocks: List<String> = emptyList()
    ) {
        enum class FormationType {
            SHAPED,
            SHAPELESS
        }
    }

    data class StructurePattern(
        val levels: List<Level>,
        val mappings: Map<Char, Mapping>,
        val mappingRules: Map<Char, MappingRule> = emptyMap()
    ) {
        data class Level(
            val rows: List<String>
        )

        data class Mapping(
            val char: Char,
            val block: String? = null,
            val anyOf: List<String> = emptyList(),
            val recommendedOf: List<String> = emptyList(),
        )

        data class MappingRule(
            val char: Char,
            val min: Int,
            val max: Int,
            val anyLocationNonAir: Boolean = false
        )
    }


    override suspend fun loadSchema(): List<StructureDefinition> {
        val tempStructures = mutableListOf<StructureDefinition>()

        config.getConfigurationSection("structures")?.let { structuresSection ->
            tempStructures.addAll(parseStructuresFromSection(structuresSection))
        }

        return tempStructures.toList()
    }

    private fun parseStructuresFromSection(section: ConfigurationSection): List<StructureDefinition> {
        return section.getKeys(false).mapNotNull { structureId ->
            val structureSection = section.getConfigurationSection(structureId) ?: run {
                Logger.warn("Structure $structureId has no configuration section, skipping")
                return@mapNotNull null
            }
            parseStructureDefinition(structureId, structureSection)
        }
    }

    private fun parseStructureDefinition(
        id: String,
        structureSection: ConfigurationSection
    ): StructureDefinition? {
        val displayName = structureSection.getString("display-name") ?: "Unnamed Structure"
        val description = structureSection.getString("description") ?: ""

        val structureConfigSection = structureSection.getConfigurationSection("structure") ?: run {
            Logger.err("Structure $id has no 'structure' section, skipping")
            return null
        }

        val formation = parseFormation(id, structureConfigSection) ?: return null

        val behaviourStr = structureSection.getString("behaviour") ?: run {
            Logger.warn("Structure $id has no behaviour defined, skipping")
            return null
        }

        val behaviour = try {
            StructureBehaviour.valueOf(behaviourStr.uppercase().replace("-", "_"))
        } catch (e: IllegalArgumentException) {
            Logger.warn("Structure $id has invalid behaviour '$behaviourStr', skipping")
            return null
        }

        val properties = mutableMapOf<String, Any>()
        structureSection.getConfigurationSection("properties")?.let { propertiesSection ->
            propertiesSection.getKeys(false).forEach { key ->
                propertiesSection.get(key)?.let { value ->
                    properties[key] = value
                }
            }
        }

        val behaviourData = properties.toMap()

        return StructureDefinition(
            id = id,
            displayName = displayName,
            description = description,
            formation = formation,
            behaviour = behaviour,
            behaviourData = behaviourData
        )
    }

    private fun parseFormation(
        structureId: String,
        structureSection: ConfigurationSection
    ): StructureFormation? {
        val typeStr = structureSection.getString("type") ?: run {
            Logger.err("Structure $structureId has no 'type' defined, skipping")
            return null
        }

        val formationType = try {
            StructureFormation.FormationType.valueOf(typeStr.uppercase())
        } catch (e: IllegalArgumentException) {
            Logger.err("Structure $structureId has invalid formation type '$typeStr', skipping")
            return null
        }

        return when (formationType) {
            StructureFormation.FormationType.SHAPED -> {
                val pattern = parsePattern(structureId, structureSection) ?: run {
                    Logger.err("Structure $structureId is SHAPED but has no valid pattern, skipping")
                    return null
                }
                StructureFormation(
                    type = formationType,
                    pattern = pattern
                )
            }
            StructureFormation.FormationType.SHAPELESS -> {
                val requiredBlocks = structureSection.getStringList("required-blocks")
                val optionalBlocks = structureSection.getStringList("optional-blocks")
                StructureFormation(
                    type = formationType,
                    requiredBlocks = requiredBlocks,
                    optionalBlocks = optionalBlocks
                )
            }
        }
    }

    private fun parsePattern(
        structureId: String,
        structureSection: ConfigurationSection
    ): StructurePattern? {
        val patternSection = structureSection.getConfigurationSection("pattern") ?: return null

        // Parse levels (y-0, y-1, y-2, etc.)
        val levels = mutableListOf<StructurePattern.Level>()
        var levelIndex = 0
        while (true) {
            val levelKey = "y-$levelIndex"
            val levelData = patternSection.getStringList(levelKey)
            if (levelData.isEmpty()) {
                break
            }
            levels.add(StructurePattern.Level(levelData))
            levelIndex++
        }

        if (levels.isEmpty()) {
            Logger.warn("Structure $structureId has no levels in pattern")
            return null
        }

        // Parse mappings
        val mappingSection = structureSection.getConfigurationSection("mapping") ?: run {
            Logger.warn("Structure $structureId has no mapping section")
            return null
        }

        val mappings = mutableMapOf<Char, StructurePattern.Mapping>()
        mappingSection.getKeys(false).forEach { charKey ->
            if (charKey.length != 1) {
                Logger.warn("Structure $structureId has invalid mapping key '$charKey' (must be single character)")
                return@forEach
            }
            val char = charKey[0]
            val mappingData = mappingSection.getConfigurationSection(charKey)

            if (mappingData != null) {
                val block = mappingData.getString("block")
                val anyOf = mappingData.getStringList("any-of")
                val recommendedOf = mappingData.getStringList("recommended-of")

                mappings[char] = StructurePattern.Mapping(
                    char = char,
                    block = block,
                    anyOf = anyOf,
                    recommendedOf = recommendedOf
                )
            }
        }

        // Parse mapping rules
        val mappingRules = mutableMapOf<Char, StructurePattern.MappingRule>()
        structureSection.getConfigurationSection("mapping-rules")?.let { rulesSection ->
            rulesSection.getKeys(false).forEach { charKey ->
                if (charKey.length != 1) {
                    Logger.warn("Structure $structureId has invalid mapping rule key '$charKey'")
                    return@forEach
                }
                val char = charKey[0]
                val ruleData = rulesSection.getConfigurationSection(charKey) ?: return@forEach

                val min = ruleData.getInt("min", 0)
                val max = ruleData.getInt("max", Int.MAX_VALUE)
                val anyLocationNonAir = ruleData.getBoolean("any-location-non-air", false)

                mappingRules[char] = StructurePattern.MappingRule(
                    char = char,
                    min = min,
                    max = max,
                    anyLocationNonAir = anyLocationNonAir
                )
            }
        }

        return StructurePattern(
            levels = levels,
            mappings = mappings,
            mappingRules = mappingRules
        )
    }

    override suspend fun cleanupSchema() {
        // No cleanup needed for structures
    }

}