//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

package dev.arsmatrix.block;

import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import dev.arsmatrix.registry.ModBlockEntities;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.Property;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.BlockHitResult;
import net.neoforged.neoforge.fluids.FluidUtil;
import org.jetbrains.annotations.Nullable;

public final class ArcaneFluidReservoirBlock extends BaseEntityBlock {
    public static final DirectionProperty FACING;
    public static final MapCodec<ArcaneFluidReservoirBlock> CODEC;

    public ArcaneFluidReservoirBlock(BlockBehaviour.Properties properties) {
        super(properties);
        this.registerDefaultState((BlockState)((BlockState)this.stateDefinition.any()).setValue(FACING, Direction.NORTH));
    }

    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(new Property[]{FACING});
    }

    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return (BlockState)this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneFluidReservoirBlockEntity(pos, state);
    }

    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof ArcaneFluidReservoirBlockEntity reservoir) {
                reservoir.serverTick();
            }

        };
    }

    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty()) {
            if (!level.isClientSide) {
                BlockEntity var7 = level.getBlockEntity(pos);
                if (var7 instanceof ArcaneFluidReservoirBlockEntity) {
                    ArcaneFluidReservoirBlockEntity reservoir = (ArcaneFluidReservoirBlockEntity)var7;
                    if (player instanceof ServerPlayer) {
                        ServerPlayer serverPlayer = (ServerPlayer)player;
                        serverPlayer.openMenu(reservoir, (data) -> data.writeBlockPos(pos));
                    }
                }
            }

            return InteractionResult.sidedSuccess(level.isClientSide);
        } else {
            return InteractionResult.PASS;
        }
    }

    protected ItemInteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.is((Item)ItemsRegistry.DOMINION_ROD.get())) {
            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        } else {
            BlockEntity var9 = level.getBlockEntity(pos);
            if (var9 instanceof ArcaneFluidReservoirBlockEntity) {
                ArcaneFluidReservoirBlockEntity reservoir = (ArcaneFluidReservoirBlockEntity)var9;
                if (FluidUtil.interactWithFluidHandler(player, hand, reservoir.getFluidHandler(hit.getDirection()))) {
                    return ItemInteractionResult.sidedSuccess(level.isClientSide);
                }
            }

            return ItemInteractionResult.PASS_TO_DEFAULT_BLOCK_INTERACTION;
        }
    }

    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack dropped = new ItemStack(this);
        BlockEntity blockEntity = (BlockEntity)builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY);
        if (blockEntity instanceof ArcaneFluidReservoirBlockEntity reservoir) {
            BlockItem.setBlockEntityData(dropped, (BlockEntityType)ModBlockEntities.ARCANE_FLUID_RESERVOIR.get(), reservoir.saveWithoutMetadata(builder.getLevel().registryAccess()));
        }

        return List.of(dropped);
    }

    static {
        FACING = HorizontalDirectionalBlock.FACING;
        CODEC = simpleCodec(ArcaneFluidReservoirBlock::new);
    }
}
