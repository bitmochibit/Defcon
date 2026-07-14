package me.mochibit.defcon.foundation.services

import net.minecraft.world.level.block.state.BlockState

interface ContentService {
    fun isGlass(state: BlockState): Boolean

}

val contentService: ContentService by lazy {
    loadService<ContentService>()
}