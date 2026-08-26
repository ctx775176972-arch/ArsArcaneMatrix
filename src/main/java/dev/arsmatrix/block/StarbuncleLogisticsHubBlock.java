package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Safely converts nearby owned Starbuncles back into their data-preserving charms. */
public final class StarbuncleLogisticsHubBlock extends BaseEntityBlock {
    public static final MapCodec<StarbuncleLogisticsHubBlock> CODEC =
            simpleCodec(StarbuncleLogisticsHubBlock::new);

    public StarbuncleLogisticsHubBlock(BlockBehaviour.Properties properties) { super(properties); }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new StarbuncleLogisticsHubBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof StarbuncleLogisticsHubBlockEntity hub) hub.serverTick();
        };
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity hub) {
            hub.setUpgradeTier(stack.getOrDefault(ModDataComponents.LOGISTICS_HUB_TIER.get(), 0));
            if (placer instanceof Player player) hub.setOwner(player);
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack drop = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof StarbuncleLogisticsHubBlockEntity hub) {
            drop.set(ModDataComponents.LOGISTICS_HUB_TIER.get(), hub.getUpgradeTier());
            CompoundTag configuration = hub.saveWithoutMetadata(builder.getLevel().registryAccess());
            // Real buffer items are dropped by onRemove. Keep only management state
            // in the portable block data so upgrades cannot duplicate inventory.
            configuration.remove("Inventory");
            BlockItem.setBlockEntityData(drop, ModBlockEntities.STARBUNCLE_LOGISTICS_HUB.get(), configuration);
        }
        return List.of(drop);
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos,
                                                Player player, BlockHitResult hitResult) {
        if (!(level.getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity hub)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer) {
            if (!hub.canAccess(player)) {
                player.displayClientMessage(net.minecraft.network.chat.Component.translatable(
                        "message.ars_arcane_matrix.starbuncle_hub.not_owner"), true);
            } else {
                serverPlayer.openMenu(hub, data -> {
                    data.writeBlockPos(pos);
                    hub.writeRouteData(data);
                });
            }
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    protected void onRemove(BlockState state, Level level, BlockPos pos,
                            BlockState newState, boolean isMoving) {
        if (!state.is(newState.getBlock())
                && level.getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity hub) {
            hub.dropContents();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
