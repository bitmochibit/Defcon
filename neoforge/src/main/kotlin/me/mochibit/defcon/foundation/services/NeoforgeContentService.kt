package me.mochibit.defcon.foundation.services

import net.minecraft.tags.BlockTags
import net.minecraft.world.level.block.state.BlockState
import net.neoforged.neoforge.common.Tags

class NeoforgeContentService: ContentService {
    override fun isGlass(state: BlockState): Boolean =
        state.`is`(Tags.Blocks.GLASS_BLOCKS) || state.`is`(Tags.Blocks.GLASS_PANES)

}