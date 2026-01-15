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

package me.mochibit.defcon.registry

import me.mochibit.defcon.config.StructuresConfiguration
import me.mochibit.defcon.content.element.AbstractElementRegistry
import me.mochibit.defcon.content.structures.PluginStructure
import me.mochibit.defcon.content.structures.PluginStructureFactory
import me.mochibit.defcon.content.structures.PluginStructureProperties

/**
 * Registry for all custom plugin structures.
 *
 * Each structure definition has exactly ONE immutable template instance.
 * Structures are stateless, so the template can be used directly without copying.
 */
object StructureRegistry : AbstractElementRegistry<PluginStructureProperties, PluginStructure<*>, StructuresConfiguration.StructureDefinition>(
    PluginStructureFactory,
) {
    override suspend fun retrieveDefinitions(): List<StructuresConfiguration.StructureDefinition> = StructuresConfiguration.getSchema()

    /**
     * Alias for get() - returns the immutable structure template
     */
    fun getStructure(id: String): PluginStructure<*>? = this[id]
}
