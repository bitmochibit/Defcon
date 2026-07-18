package me.mochibit.defcon.mixin;


import me.mochibit.defcon.content.explosion.BlastZone;
import me.mochibit.defcon.content.explosion.BlastZoneSavedData;
import me.mochibit.defcon.content.explosion.processor.PostApocalypticTerrain;
import me.mochibit.defcon.content.explosion.processor.TreeBurnCore;
import me.mochibit.defcon.content.explosion.processor.carver.WorldGenColumnCarveContext;
import me.mochibit.defcon.explosion.processor.Shockwave;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldGenRegion.class)
public abstract class WorldGenRegionMixin {

    @Inject(
            method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("TAIL")
    )
    private void defcon$reburnOrphanTreeBlock(
            BlockPos pos,
            BlockState state,
            int flags,
            int recursionLeft,
            CallbackInfoReturnable<Boolean> cir
    ) {
        if (!cir.getReturnValue()) return;

        if (!TreeBurnCore.INSTANCE.isTreeBlockType(state.getBlock())) return;

        WorldGenRegion self = (WorldGenRegion) (Object) this;
        ServerLevel serverLevel = self.getLevel();

        int chunkX = pos.getX() >> 4;
        int chunkZ = pos.getZ() >> 4;

        BlastZoneSavedData savedData = BlastZoneSavedData.get(serverLevel);

        // Se il chunk non ha ancora fatto la sua passata di carving, si autorisolverà
        // normalmente quando la farà: non serve intervenire qui.
        if (!savedData.isChunkWorldgenProcessed(chunkX, chunkZ)) return;

        BlastZone zone = savedData.zoneForChunk(
                chunkX, chunkZ,
                z -> PostApocalypticTerrain.INSTANCE.maxFeatherMargin(z.getRadius())
        );
        if (zone == null) return;

        double dx = pos.getX() - zone.getCenterX();
        double dz = pos.getZ() - zone.getCenterZ();
        double dist = Math.sqrt(dx * dx + dz * dz);
        float effectiveRange = zone.getRadius() - zone.getRadiusStart();
        float radiusProgress = (float) ((dist - zone.getRadiusStart()) / effectiveRange);
        float power = Shockwave.Companion.calculateShockwavePower(radiusProgress);
        if (power <= 0f) return;

        ChunkAccess targetChunk = self.getChunk(chunkX, chunkZ);
        WorldGenColumnCarveContext ctx = new WorldGenColumnCarveContext(
                self,
                targetChunk,
                new BlockPos(zone.getCenterX(), zone.getCenterY(), zone.getCenterZ())
        );

        int trunkBase = ctx.findLocalTrunkBase(pos.getX(), pos.getY(), pos.getZ());
        ctx.burnTreeBlock(pos.getX(), pos.getY(), pos.getZ(), power, null, trunkBase);
    }
}