package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.server.level.ServerPlayer;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Stores physical crafting guides and turns a nearby Wixie station into a network worker. */
public final class WixiePatternProviderBlock extends BaseEntityBlock {

    public static final MapCodec<WixiePatternProviderBlock> CODEC = simpleCodec(WixiePatternProviderBlock::new);

    public WixiePatternProviderBlock(BlockBehaviour.Properties properties) {
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
        return new WixiePatternProviderBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(
            Level level, BlockPos pos, BlockState state,
            @Nullable LivingEntity placer, ItemStack stack
    ) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof WixiePatternProviderBlockEntity provider) {
            provider.setUpgradeTier(stack.getOrDefault(
                    ModDataComponents.PATTERN_PROVIDER_TIER.get(), 0));
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof WixiePatternProviderBlockEntity provider) {
            for (ItemStack drop : drops) {
                if (drop.is(dev.arsmatrix.registry.ModItems.WIXIE_PATTERN_PROVIDER.get())) {
                    drop.set(ModDataComponents.PATTERN_PROVIDER_TIER.get(), provider.getUpgradeTier());
                }
            }
        }
        return drops;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof WixiePatternProviderBlockEntity provider) {
                provider.serverTick();
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
        if (!(level.getBlockEntity(pos) instanceof WixiePatternProviderBlockEntity provider)) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (player.isShiftKeyDown()) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.pattern_provider.status",
                        provider.getGuideCount(),
                        provider.getGuideCapacity(),
                        provider.getWixieWorkers().size(),
                        provider.getWixieInventories().size()
                ), false);
            } else if (player instanceof ServerPlayer serverPlayer) {
                serverPlayer.openMenu(provider, data -> {
                    data.writeBlockPos(pos);
                    data.writeVarInt(provider.getGuideCapacity());
                    data.writeBlockPos(pos);
                });
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
                && level.getBlockEntity(pos) instanceof WixiePatternProviderBlockEntity provider) {
            provider.dropGuides();
        }
        super.onRemove(state, level, pos, newState, isMoving);
    }
}
