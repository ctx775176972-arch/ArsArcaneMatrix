package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.source.SourceNetworkLinking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.item.context.BlockPlaceContext;
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
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.jetbrains.annotations.Nullable;

import java.util.List;

public final class SuperSourceJarCoreBlock extends BaseEntityBlock {
    public static final MapCodec<SuperSourceJarCoreBlock> CODEC = simpleCodec(SuperSourceJarCoreBlock::new);
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public SuperSourceJarCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, net.minecraft.core.Direction.NORTH));
    }
    @Override protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }
    @Override public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }
    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }
    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new SuperSourceJarCoreBlockEntity(pos, state);
    }
    @Nullable @Override public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof SuperSourceJarCoreBlockEntity jar) jar.serverTick();
        };
    }
    @Override protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack dropped = new ItemStack(this);
        if (builder.getOptionalParameter(LootContextParams.BLOCK_ENTITY)
                instanceof SuperSourceJarCoreBlockEntity jar) {
            CompoundTag data = new CompoundTag();
            data.putInt("Source", jar.getSource());
            BlockItem.setBlockEntityData(dropped, ModBlockEntities.SUPER_SOURCE_JAR_CORE.get(), data);
        }
        return List.of(dropped);
    }
    @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                                      BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof SuperSourceJarCoreBlockEntity jar) {
            SourceNetworkLinking.remove(serverLevel, jar);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
