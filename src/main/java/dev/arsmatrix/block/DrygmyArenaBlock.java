package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.FeatureFlags;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
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

    /**
     * The hunting-ground model is intentionally lower and more open than a cube, which makes
     * aiming at its visual top unreliable. A Mob Jar is the one block players are expected to
     * place here, so let using it anywhere on the grounds place it in the required position.
     */
    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack,
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            InteractionHand hand,
            BlockHitResult hitResult
    ) {
        if (stack.is(BlockRegistry.MOB_JAR.asItem()) && stack.getItem() instanceof BlockItem blockItem) {
            BlockHitResult topHit = new BlockHitResult(
                    Vec3.atCenterOf(pos).add(0.0D, 0.5D, 0.0D),
                    Direction.UP,
                    pos,
                    false
            );
            InteractionResult placement = blockItem.place(new BlockPlaceContext(
                    new UseOnContext(player, hand, topHit)
            ));
            if (placement.consumesAction()) {
                return ItemInteractionResult.sidedSuccess(level.isClientSide);
            }
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
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
