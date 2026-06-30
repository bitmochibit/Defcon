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

import me.mochibit.defcon.config.BlocksConfiguration
import me.mochibit.defcon.content.blocks.PluginBlock
import me.mochibit.defcon.content.blocks.PluginBlockFactory
import me.mochibit.defcon.content.blocks.PluginBlockProperties
import me.mochibit.defcon.content.element.AbstractElementRegistry

/**
 * Registry for all custom plugin blocks.
 *
 * Each block definition has exactly ONE immutable template instance.
 * Blocks are stateless, so the template can be used directly without copying.
 */
object BlockRegistry : AbstractElementRegistry<PluginBlockProperties, PluginBlock<*>, BlocksConfiguration.BlockDefinition>(
    PluginBlockFactory,
) {
    override suspend fun retrieveDefinitions(): List<BlocksConfiguration.BlockDefinition> = BlocksConfiguration.getBlockDefinitions()

    /**
     * Alias for get() - returns the immutable block template
     */
    fun getBlock(id: String): PluginBlock<*>? = this[id]
}
