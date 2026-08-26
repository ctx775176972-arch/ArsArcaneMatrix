package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.FeatureFlags;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** Catalyst-powered single-jar special reward producer created with a Drygmy Charm. */
public final class DrygmyArenaBlock extends BaseEntityBlock {

    public static final MapCodec<DrygmyArenaBlock> CODEC = simpleCodec(DrygmyArenaBlock::new);

    public DrygmyArenaBlock(BlockBehaviour.Properties properties) {
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

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new DrygmyArenaBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide || !FeatureFlags.ARCANE_ARENA
                ? null
                : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof DrygmyArenaBlockEntity arena) {
                arena.serverTick();
            }
        };
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (!FeatureFlags.ARCANE_ARENA) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof DrygmyArenaBlockEntity arena) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.drygmy_arena.status",
                    Component.translatable(arena.getOperatingState().translationKey()),
                    arena.getTargetDescription(),
                    Math.min(arena.getProgressTicks(), arena.getCycleTicks()) / 20,
                    Math.ceilDiv(arena.getCycleTicks(), 20),
                    arena.getBufferedItemCount(),
                    arena.getCatalystPoints(),
                    arena.getRequiredPoints()
            ), false);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
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
                && level.getBlockEntity(pos) instanceof DrygmyArenaBlockEntity arena) {
            arena.dropBufferedContents();
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
