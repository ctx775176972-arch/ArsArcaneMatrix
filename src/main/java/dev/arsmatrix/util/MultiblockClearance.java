package dev.arsmatrix.util;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/** Shared clearance rule for decorative controls, pipes, and other non-solid attachments. */
public final class MultiblockClearance {
    private MultiblockClearance() {}

    public static boolean isOpen(Level level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        return state.isAir() || !state.isCollisionShapeFullBlock(level, pos);
    }
}
