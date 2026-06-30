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

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import me.mochibit.defcon.content.element.ElementBehaviour
import me.mochibit.defcon.content.element.ElementDefinition
import me.mochibit.defcon.content.structures.PluginStructure
import me.mochibit.defcon.content.structures.PluginStructureProperties
import me.mochibit.defcon.content.structures.StructureBehaviour
import me.mochibit.defcon.utils.Logger

object StructuresConfiguration : PluginConfiguration<StructuresConfiguration.StructuresConfig>("structures") {
    @Serializable
    data class StructuresConfig(
        val structures: List<StructureDefinitionJson>,
    )

    @Serializable
    data class StructureDefinitionJson(
        val id: String,
        @SerialName("display-name")
        val displayName: String = "Unnamed Structure",
        val description: String = "",
        val structure: StructureFormationJson,
        val behaviour: String,
        val properties: Map<String, JsonElement> = emptyMap(),
    )

    @Serializable
    data class StructureFormationJson(
        val type: String,
        val pattern: StructurePatternJson? = null,
        @SerialName("required-blocks")
        val requiredBlocks: List<String> = emptyList(),
        @SerialName("optional-blocks")
        val optionalBlocks: List<String> = emptyList(),
    )

    @Serializable
    data class StructurePatternJson(
        val levels: List<LevelJson>,
        val mappings: Map<String, MappingJson>,
        @SerialName("mapping-rules")
        val mappingRules: Map<String, MappingRuleJson> = emptyMap(),
    )

    @Serializable
    data class LevelJson(
        val rows: List<String>,
    )

    @Serializable
    data class MappingJson(
        val block: String? = null,
        @SerialName("any-of")
        val anyOf: List<String> = emptyList(),
        @SerialName("recommended-of")
        val recommendedOf: List<String> = emptyList(),
    )

    @Serializable
    data class MappingRuleJson(
        val min: Int = 0,
        val max: Int = Int.MAX_VALUE,
        @SerialName("any-location-non-air")
        val anyLocationNonAir: Boolean = false,
    )

    data class StructureDefinition(
        override val id: String,
        val displayName: String,
        val description: String,
        val formation: StructureFormation,
        override val behaviour: StructureBehaviour,
        override val behaviourData: Map<String, Any>,
    ) : ElementDefinition<PluginStructureProperties, PluginStructure<*>>

    data class StructureFormation(
        val type: FormationType,
        val pattern: StructurePattern? = null,
        val requiredBlocks: List<String> = emptyList(),
        val optionalBlocks: List<String> = emptyList(),
    ) {
        enum class FormationType {
            SHAPED,
            SHAPELESS,
        }
    }

    data class StructurePattern(
        val levels: List<Level>,
        val mappings: Map<Char, Mapping>,
        val mappingRules: Map<Char, MappingRule> = emptyMap(),
    ) {
        data class Level(
            val rows: List<String>,
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
            val anyLocationNonAir: Boolean = false,
        )
    }

    override suspend fun loadSchema(): StructuresConfig {
        val configText = readConfigFile()
        val config = json.decodeFromString<StructuresConfig>(configText)
        return config
    }

    override fun getDefaultSchema(): StructuresConfig = StructuresConfig(structures = emptyList())

    private fun parseStructureDefinition(structureJson: StructureDefinitionJson): StructureDefinition? {
        val formation = parseFormation(structureJson.id, structureJson.structure) ?: return null

        val behaviour =
            try {
                StructureBehaviour.valueOf(structureJson.behaviour.uppercase().replace("-", "_"))
            } catch (_: IllegalArgumentException) {
                Logger.warn("Structure ${structureJson.id} has invalid behaviour '${structureJson.behaviour}', skipping")
                return null
            }

        val behaviourData = mutableMapOf<String, Any>()
        structureJson.properties.forEach { (key, value) ->
            behaviourData[key] = value.toString()
        }

        return StructureDefinition(
            id = structureJson.id,
            displayName = structureJson.displayName,
            description = structureJson.description,
            formation = formation,
            behaviour = behaviour,
            behaviourData = behaviourData,
        )
    }

    suspend fun getStructureDefinitions(): List<StructureDefinition> {
        val config = getSchema()
        return config.structures.mapNotNull { structureJson ->
            parseStructureDefinition(structureJson)
        }
    }

    private fun parseFormation(
        structureId: String,
        formationJson: StructureFormationJson,
    ): StructureFormation? {
        val formationType =
            try {
                StructureFormation.FormationType.valueOf(formationJson.type.uppercase())
            } catch (_: IllegalArgumentException) {
                Logger.err("Structure $structureId has invalid formation type '${formationJson.type}', skipping")
                return null
            }

        return when (formationType) {
            StructureFormation.FormationType.SHAPED -> {
                val pattern =
                    formationJson.pattern?.let { parsePattern(structureId, it) } ?: run {
                        Logger.err("Structure $structureId is SHAPED but has no valid pattern, skipping")
                        return null
                    }
                StructureFormation(
                    type = formationType,
                    pattern = pattern,
                )
            }

            StructureFormation.FormationType.SHAPELESS -> {
                StructureFormation(
                    type = formationType,
                    requiredBlocks = formationJson.requiredBlocks,
                    optionalBlocks = formationJson.optionalBlocks,
                )
            }
        }
    }

    private fun parsePattern(
        structureId: String,
        patternJson: StructurePatternJson,
    ): StructurePattern? {
        if (patternJson.levels.isEmpty()) {
            Logger.warn("Structure $structureId has no levels in pattern")
            return null
        }

        val levels =
            patternJson.levels.map { levelJson ->
                StructurePattern.Level(levelJson.rows)
            }

        val mappings =
            patternJson.mappings
                .mapNotNull { (charKey, mappingJson) ->
                    if (charKey.length != 1) {
                        Logger.warn("Structure $structureId has invalid mapping key '$charKey' (must be single character)")
                        return@mapNotNull null
                    }
                    val char = charKey[0]
                    char to
                        StructurePattern.Mapping(
                            char = char,
                            block = mappingJson.block,
                            anyOf = mappingJson.anyOf,
                            recommendedOf = mappingJson.recommendedOf,
                        )
                }.toMap()

        val mappingRules =
            patternJson.mappingRules
                .mapNotNull { (charKey, ruleJson) ->
                    if (charKey.length != 1) {
                        Logger.warn("Structure $structureId has invalid mapping rule key '$charKey'")
                        return@mapNotNull null
                    }
                    val char = charKey[0]
                    char to
                        StructurePattern.MappingRule(
                            char = char,
                            min = ruleJson.min,
                            max = ruleJson.max,
                            anyLocationNonAir = ruleJson.anyLocationNonAir,
                        )
                }.toMap()

        return StructurePattern(
            levels = levels,
            mappings = mappings,
            mappingRules = mappingRules,
        )
    }

    override suspend fun cleanupSchema() {
        // No cleanup needed for structures
    }

    override fun getSerializer(): KSerializer<StructuresConfig> = StructuresConfig.serializer()
}
