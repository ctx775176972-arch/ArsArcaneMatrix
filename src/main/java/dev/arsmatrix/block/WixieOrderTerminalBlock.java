package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
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

/** Planner, intermediate buffer, and dispatcher for the first Wixie crafting-network prototype. */
public final class WixieOrderTerminalBlock extends BaseEntityBlock {

    public static final MapCodec<WixieOrderTerminalBlock> CODEC = simpleCodec(WixieOrderTerminalBlock::new);

    public WixieOrderTerminalBlock(BlockBehaviour.Properties properties) {
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
        return new WixieOrderTerminalBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof WixieOrderTerminalBlockEntity terminal) {
                terminal.serverTick();
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
                && player instanceof ServerPlayer serverPlayer
                && level.getBlockEntity(pos) instanceof WixieOrderTerminalBlockEntity terminal) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.order_terminal.status",
                        Component.translatable(terminal.getState().translationKey()),
                        terminal.getProviderCount(),
                        terminal.getActiveWorkerCount(),
                        terminal.getBufferedItemCount(),
                        terminal.getDetail().isBlank() ? "-" : terminal.getDetail()
                ), false);
            } else {
                serverPlayer.openMenu(terminal, data -> WixieOrderTerminalMenu.writeOpeningData(
                        data, pos, terminal.getCraftableRecipeInfos()));
            }
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
                && level.getBlockEntity(pos) instanceof WixieOrderTerminalBlockEntity terminal) {
            terminal.dropBufferedContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
