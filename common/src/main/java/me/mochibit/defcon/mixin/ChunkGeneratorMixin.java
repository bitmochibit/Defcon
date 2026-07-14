package me.mochibit.defcon.mixin;


import me.mochibit.defcon.content.explosion.processor.worldgen.BlastZone;
import me.mochibit.defcon.content.explosion.processor.worldgen.BlastZoneSavedData;
import me.mochibit.defcon.content.explosion.processor.worldgen.PostApocalypticTerrain;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.ChunkPos;

import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class ChunkGeneratorMixin {

    @Inject(
            method = "applyBiomeDecoration(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkAccess;Lnet/minecraft/world/level/StructureManager;)V",
            at = @At("TAIL")
    )
    private void defcon$postDecorationTerrainOverlay(
            WorldGenLevel level,
            ChunkAccess chunk,
            net.minecraft.world.level.StructureManager structureManager,
            CallbackInfo ci
    ) {
        if (!(level instanceof WorldGenRegion worldGenRegion)) {
            return;
        }

        if (chunk.getInhabitedTime() > 0) {
            return;
        }
        ServerLevel serverLevel = worldGenRegion.getLevel();
        ChunkPos chunkPos = chunk.getPos();

        BlastZone blastZone = BlastZoneSavedData.get(serverLevel)
                .zoneForChunk(
                        chunkPos.x,
                        chunkPos.z,
                        zone -> PostApocalypticTerrain.INSTANCE.maxFeatherMargin(zone.getRadius())
                );

        if (blastZone != null) {
            BlastZoneSavedData.get(serverLevel).markChunkWorldgenProcessed(chunkPos.x, chunkPos.z);
            PostApocalypticTerrain.INSTANCE.apply(level, chunk, blastZone);
        }
    }
}