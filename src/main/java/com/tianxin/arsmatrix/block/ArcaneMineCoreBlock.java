package com.tianxin.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import com.tianxin.arsmatrix.blockentity.ArcaneMineCoreBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Controller block at the bottom point of the inverted-beacon Arcane Mine. */
public class ArcaneMineCoreBlock extends BaseEntityBlock {

    public static final MapCodec<ArcaneMineCoreBlock> CODEC = simpleCodec(ArcaneMineCoreBlock::new);

    public ArcaneMineCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneMineCoreBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof ArcaneMineCoreBlockEntity core) {
                core.serverTick();
            }
        };
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean isMoving
    ) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof ArcaneMineCoreBlockEntity core) {
            core.dropBufferedContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }

    @Override
    protected boolean isPathfindable(
            BlockState state,
            net.minecraft.world.level.pathfinder.PathComputationType type
    ) {
        return false;
    }
}
