package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.ArcaneFluidTankBlockEntity;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;

import java.util.List;

/** A placeable single-fluid reservoir that may also be installed in a controller. */
public final class ArcaneFluidTankBlock extends BaseEntityBlock {
    public static final MapCodec<ArcaneFluidTankBlock> CODEC = simpleCodec(ArcaneFluidTankBlock::new);

    public ArcaneFluidTankBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneFluidTankBlockEntity(pos, state);
    }

    @Override protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level,
                                                        BlockPos pos, Player player, InteractionHand hand,
                                                        BlockHitResult hit) {
        if (level.getBlockEntity(pos) instanceof ArcaneFluidTankBlockEntity tank
                && FluidUtil.interactWithFluidHandler(player, hand, tank.fluidHandler())) {
            return ItemInteractionResult.sidedSuccess(level.isClientSide);
        }
        return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
    }

    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack dropped = new ItemStack(this);
        BlockEntity blockEntity = builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ArcaneFluidTankBlockEntity tank) {
            BlockItem.setBlockEntityData(dropped, ModBlockEntities.ARCANE_FLUID_TANK.get(),
                    tank.saveWithoutMetadata(builder.getLevel().registryAccess()));
        }
        return List.of(dropped);
    }
}
