package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.List;

/** Finds real Source producers without relying on SourceManager's receiver-only registry. */
final class NearbySourceProducerScanner {
    private NearbySourceProducerScanner() {}

    static List<BlockPos> scan(ServerLevel level, BlockPos center, int range) {
        List<BlockPos> result = new ArrayList<>();
        double rangeSquared = (double) range * range;
        int minChunkX = Math.floorDiv(center.getX() - range, 16);
        int maxChunkX = Math.floorDiv(center.getX() + range, 16);
        int minChunkZ = Math.floorDiv(center.getZ() - range, 16);
        int maxChunkZ = Math.floorDiv(center.getZ() + range, 16);

        for (int chunkX = minChunkX; chunkX <= maxChunkX; chunkX++) {
            for (int chunkZ = minChunkZ; chunkZ <= maxChunkZ; chunkZ++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null) continue;
                for (BlockEntity blockEntity : chunk.getBlockEntities().values()) {
                    if (!isProducer(blockEntity)) continue;
                    BlockPos pos = blockEntity.getBlockPos();
                    if (center.distSqr(pos) <= rangeSquared) result.add(pos.immutable());
                }
            }
        }
        return result;
    }

    static ISourceTile resolve(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return isProducer(blockEntity) ? (ISourceTile) blockEntity : null;
    }

    /** Keeps Ars Nouveau's client-side Source HUD current after an external extraction. */
    static void syncAfterExtraction(ServerLevel level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        if (!isProducer(blockEntity)) return;
        blockEntity.setChanged();
        level.sendBlockUpdated(pos, blockEntity.getBlockState(), blockEntity.getBlockState(), Block.UPDATE_CLIENTS);
    }

    private static boolean isProducer(BlockEntity blockEntity) {
        return blockEntity instanceof SourcelinkTile || blockEntity instanceof MatrixCoreBlockEntity;
    }
}
