package dev.arsmatrix.world;

import com.hollingsworth.arsnouveau.common.block.MageBlock;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.neoforge.event.level.ChunkEvent;

/** Only brand-new chunks. Loaded player builds must NEVER pass through this cleanup. */
public final class SkyblockFinalCleanup {
    private SkyblockFinalCleanup() {}

    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!event.isNewChunk() || !(event.getChunk() instanceof LevelChunk chunk)
                || !(event.getLevel() instanceof ServerLevel level)
                || !MatrixSkyblock.isSkyblock(level.getChunkSource().getGenerator())) return;
        boolean nether = level.dimension() == Level.NETHER;
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        var fortresses = nether ? level.structureManager().startsForStructure(chunk.getPos(), structure ->
                ResourceLocation.withDefaultNamespace("fortress").equals(registry.getKey(structure)))
                : java.util.List.<net.minecraft.world.level.levelgen.structure.StructureStart>of();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x0 = chunk.getPos().getMinBlockX(), z0 = chunk.getPos().getMinBlockZ();
        for (int y = chunk.getMinBuildHeight(); y < chunk.getMaxBuildHeight(); y++) {
            for (int x = x0; x < x0 + 16; x++) for (int z = z0; z < z0 + 16; z++) {
                pos.set(x,y,z);
                var state = chunk.getBlockState(pos);
                if (state.isAir() || !nether && state.is(Blocks.END_PORTAL_FRAME)) continue;
                if (nether && (state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.NETHER_BRICK_FENCE)
                        || state.is(Blocks.NETHER_BRICK_STAIRS) || state.is(Blocks.SOUL_SAND)
                        || state.is(Blocks.NETHER_WART) || state.is(Blocks.CHEST) || state.is(Blocks.SPAWNER)
                        || state.is(Blocks.LAVA)) && fortresses.stream().anyMatch(s -> s.getBoundingBox().isInside(pos))) continue;
                if (!nether && x >= -2 && x <= 2 && z >= -2 && z <= 2 && (y == 79 || y == 80)) continue;
                chunk.removeBlockEntity(pos);
                chunk.setBlockState(pos, Blocks.AIR.defaultBlockState(), false);
                level.getChunkSource().getLightEngine().checkBlock(pos.immutable());
            }
        }
        // Also restore the starter layers if a late worldgen feature overwrote them.
        if (!nether) for(int x=-2; x<=2; x++) for(int z=-2; z<=2; z++) {
            pos.set(x,80,z);
            if (!chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(pos))) continue;
            chunk.removeBlockEntity(pos);
            chunk.setBlockState(pos,Blocks.DIRT.defaultBlockState(),false);
            level.getChunkSource().getLightEngine().checkBlock(pos.immutable());
            pos.set(x,79,z);
            chunk.removeBlockEntity(pos);
            chunk.setBlockState(pos,BlockRegistry.MAGE_BLOCK.get().defaultBlockState().setValue(MageBlock.TEMPORARY,false),false);
            level.getChunkSource().getLightEngine().checkBlock(pos.immutable());
        }
        if (!nether && chunk.getPos().equals(new net.minecraft.world.level.ChunkPos(0,0))) {
            BlockPos saplingPos = new BlockPos(0,81,0);
            var sapling = net.minecraft.core.registries.BuiltInRegistries.BLOCK.get(
                    ResourceLocation.fromNamespaceAndPath("ars_nouveau", "green_archwood_sapling"));
            chunk.removeBlockEntity(saplingPos);
            chunk.setBlockState(saplingPos,sapling.defaultBlockState(),false);
            level.getChunkSource().getLightEngine().checkBlock(saplingPos);
        }
        chunk.setUnsaved(true);
    }
}
