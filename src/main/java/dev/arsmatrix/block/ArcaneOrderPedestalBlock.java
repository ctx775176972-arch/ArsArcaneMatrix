package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import dev.arsmatrix.registry.ModDataComponents;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A request endpoint whose displayed stack is virtual and vanishes when its order completes. */
public final class ArcaneOrderPedestalBlock extends BaseEntityBlock {

    public static final MapCodec<ArcaneOrderPedestalBlock> CODEC = simpleCodec(ArcaneOrderPedestalBlock::new);

    public ArcaneOrderPedestalBlock(BlockBehaviour.Properties properties) {
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
        return new ArcaneOrderPedestalBlockEntity(pos, state);
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state,
                            @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (level.getBlockEntity(pos) instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            pedestal.setUpgradeTier(stack.getOrDefault(
                    ModDataComponents.ORDER_PEDESTAL_TIER.get(), 0));
        }
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        List<ItemStack> drops = super.getDrops(state, builder);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            for (ItemStack drop : drops) {
                if (drop.is(ModItems.ARCANE_ORDER_PEDESTAL.get())) {
                    drop.set(ModDataComponents.ORDER_PEDESTAL_TIER.get(), pedestal.getUpgradeTier());
                }
            }
        }
        return drops;
    }

    @Override
    protected InteractionResult useWithoutItem(
            BlockState state,
            Level level,
            BlockPos pos,
            Player player,
            BlockHitResult hitResult
    ) {
        if (level.getBlockEntity(pos) instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            if (!level.isClientSide) {
                player.displayClientMessage(pedestal.getStatusMessage(), false);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        return InteractionResult.PASS;
    }
}
