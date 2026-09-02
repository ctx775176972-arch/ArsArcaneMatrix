package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import dev.arsmatrix.registry.ModItems;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class AutomaticStockRequesterBlock extends BaseEntityBlock {
    public static final MapCodec<AutomaticStockRequesterBlock> CODEC =
            simpleCodec(AutomaticStockRequesterBlock::new);

    public AutomaticStockRequesterBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new AutomaticStockRequesterBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide
                && level.getBlockEntity(pos) instanceof AutomaticStockRequesterBlockEntity requester) {
            requester.setUpgradeTier(stack.getOrDefault(
                    ModDataComponents.STOCK_REQUESTER_TIER.get(), 0));
            if (placer instanceof Player player) requester.setNotificationPlayer(player);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof AutomaticStockRequesterBlockEntity requester) {
            for (ItemStack drop : drops) {
                if (drop.is(ModItems.AUTOMATIC_STOCK_REQUESTER.get())) {
                    drop.set(ModDataComponents.STOCK_REQUESTER_TIER.get(), requester.getUpgradeTier());
                }
            }
        }
        return drops;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof AutomaticStockRequesterBlockEntity requester) {
                requester.serverTick();
            }
        };
    }

    @Override
    protected ItemInteractionResult useItemOn(
            ItemStack stack, BlockState state, Level level, BlockPos pos,
            Player player, InteractionHand hand, BlockHitResult hitResult
    ) {
        if (stack.is(ItemsRegistry.DOMINION_ROD.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
        // The monitored item is a virtual GUI slot. Do not capture arbitrary held
        // items here: doing so makes normal tool and item interactions error-prone.
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state, Level level, BlockPos pos,
            Player player, BlockHitResult hitResult
    ) {
        if (player.getMainHandItem().is(ItemsRegistry.DOMINION_ROD.get())
                || player.getOffhandItem().is(ItemsRegistry.DOMINION_ROD.get())) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof AutomaticStockRequesterBlockEntity requester)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                requester.clearTarget();
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.ars_arcane_matrix.stock_requester.target_cleared"), true);
            } else if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(requester, data -> data.writeBlockPos(pos));
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(
            BlockState state, Level level, BlockPos pos,
            BlockState newState, boolean isMoving
    ) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof AutomaticStockRequesterBlockEntity requester) {
            requester.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
