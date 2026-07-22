package me.mochibit.defcon.mixin;

import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

import java.util.Map;

@Mixin(ChunkAccess.class)
public interface ChunkAccessAccessor {

    @Accessor("heightmaps")
    Map<Heightmap.Types, Heightmap> getHeightmaps();

    @Accessor("unsaved")
    void setUnsaved(boolean unsaved);
}

