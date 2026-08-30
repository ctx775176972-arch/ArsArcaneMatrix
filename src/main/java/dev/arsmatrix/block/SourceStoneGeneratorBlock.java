package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.SourceStoneGeneratorBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
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

/** Single arcane controller for bulk, pedestal-selected block generation. */
public final class SourceStoneGeneratorBlock extends BaseEntityBlock {

    public static final MapCodec<SourceStoneGeneratorBlock> CODEC =
            simpleCodec(SourceStoneGeneratorBlock::new);

    public SourceStoneGeneratorBlock(BlockBehaviour.Properties properties) {
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
        return new SourceStoneGeneratorBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof SourceStoneGeneratorBlockEntity generator) {
                generator.serverTick();
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
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof SourceStoneGeneratorBlockEntity generator) {
            ItemStack output = generator.getCurrentOutput();
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.source_stone_generator.status",
                    Component.translatable(generator.getOperatingState().translationKey()),
                    generator.getOutputDescription(),
                    output.getCount(),
                    generator.getProgress(),
                    generator.getProcessingCost(),
                    generator.getBufferedItemCount()
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
                && level.getBlockEntity(pos) instanceof SourceStoneGeneratorBlockEntity generator) {
            generator.dropBufferedContents();
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
