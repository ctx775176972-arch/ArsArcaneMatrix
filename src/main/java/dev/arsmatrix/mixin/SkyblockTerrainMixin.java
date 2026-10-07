package dev.arsmatrix.mixin;

import dev.arsmatrix.world.MatrixSkyblock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Runs only during generation of the explicitly selected skyblock preset, never on loaded chunks. */
@Mixin(ChunkGenerator.class)
public abstract class SkyblockTerrainMixin {
    // A feature can write into neighboring chunks AFTER their own cleanup finished.
    // Disable decoration at its source, but keep vanilla structure placement for portals/fortresses.
    @Redirect(method = "applyBiomeDecoration", at = @At(value = "INVOKE", target =
            "Lnet/minecraft/world/level/levelgen/placement/PlacedFeature;placeWithBiomeCheck(Lnet/minecraft/world/level/WorldGenLevel;Lnet/minecraft/world/level/chunk/ChunkGenerator;Lnet/minecraft/util/RandomSource;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean matrix$skipFeatures(net.minecraft.world.level.levelgen.placement.PlacedFeature feature,
            WorldGenLevel level, ChunkGenerator generator, net.minecraft.util.RandomSource random, BlockPos pos) {
        return !MatrixSkyblock.isSkyblock(generator) && feature.placeWithBiomeCheck(level, generator, random, pos);
    }

    @Inject(method = "applyBiomeDecoration", at = @At("TAIL"))
    private void matrix$clearTerrain(WorldGenLevel level, ChunkAccess chunk, StructureManager structures, CallbackInfo ci) {
        if (!MatrixSkyblock.isSkyblock((ChunkGenerator)(Object)this)) return;
        boolean nether = level.getLevel().dimension() == net.minecraft.world.level.Level.NETHER;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var fortress = nether ? structures.startsForStructure(chunk.getPos(), s ->
                ResourceLocation.withDefaultNamespace("fortress").equals(registry.getKey(s))) : java.util.List.<net.minecraft.world.level.levelgen.structure.StructureStart>of();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                pos.set(x,y,z);
                var state = chunk.getBlockState(pos);
                if (state.isAir()) continue;
                if (!nether && state.is(Blocks.END_PORTAL_FRAME)) continue;
                if (nether && (state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.NETHER_BRICK_FENCE)
                        || state.is(Blocks.NETHER_BRICK_STAIRS) || state.is(Blocks.SOUL_SAND)
                        || state.is(Blocks.NETHER_WART) || state.is(Blocks.CHEST) || state.is(Blocks.SPAWNER)
                        || state.is(Blocks.LAVA)) && fortress.stream().anyMatch(s -> s.getBoundingBox().isInside(pos))) continue;
                chunk.removeBlockEntity(pos);
                chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
            }
        }
        // Generate the starter island in its owning chunks, so later chunk generation cannot erase it.
        if (!nether) for(int x = -2; x <= 2; x++) for(int z = -2; z <= 2; z++) {
            pos.set(x, 80, z);
            if (chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(pos))) {
                chunk.setBlockState(pos, Blocks.DIRT.defaultBlockState(), false);
                pos.set(x, 79, z);
                chunk.setBlockState(pos, com.hollingsworth.arsnouveau.setup.registry.BlockRegistry.MAGE_BLOCK.get()
                        .defaultBlockState().setValue(com.hollingsworth.arsnouveau.common.block.MageBlock.TEMPORARY, false), false);
            }
        }
    }
}
