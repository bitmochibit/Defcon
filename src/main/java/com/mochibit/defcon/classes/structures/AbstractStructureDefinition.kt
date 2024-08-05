/*
 *
 * DEFCON: Nuclear warfare plugin for minecraft servers.
 * Copyright (c) 2024 mochibit.
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

package com.mochibit.defcon.classes.structures
import com.mochibit.defcon.classes.StructureBlock
import com.mochibit.defcon.enums.BlockDataKey
import com.mochibit.defcon.enums.StructureBehaviour
import com.mochibit.defcon.interfaces.StructureDefinition
import com.mochibit.defcon.utils.MetaManager
import org.bukkit.Location

abstract class AbstractStructureDefinition : StructureDefinition {
    final override var id: String = ""
    final override var structureBehaviour: StructureBehaviour? = null
    final override var structureBlocks: MutableList<StructureBlock> = mutableListOf()
    final override var requiredInterface: Boolean = false

    override fun saveToWorld(locations: List<Location>) {
        // Save each location passed to the block meta at the specified location
        for (location in locations) {
            MetaManager.setBlockData(location, BlockDataKey.StructureId, id)
        }
    }

    override fun removeStructureFromWorld(locations: List<Location>) {
        // Remove the block meta at the specified location
        for (location in locations) {
            MetaManager.removeBlockData(location, BlockDataKey.StructureId)
        }
    }

}
