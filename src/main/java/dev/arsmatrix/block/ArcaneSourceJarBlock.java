package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.blockentity.ArcaneSourceJarBlockEntity;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.source.SourceNetworkLinking;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** A compact pre-Matrix Source buffer that actively gathers from nearby producers. */
public final class ArcaneSourceJarBlock extends BaseEntityBlock {
    public static final MapCodec<ArcaneSourceJarBlock> CODEC = simpleCodec(ArcaneSourceJarBlock::new);

    public ArcaneSourceJarBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override protected MapCodec<? extends BaseEntityBlock> codec() { return CODEC; }
    @Override public RenderShape getRenderShape(BlockState state) { return RenderShape.MODEL; }

    @Override public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new ArcaneSourceJarBlockEntity(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level, BlockState state, BlockEntityType<T> type) {
        return level.isClientSide ? null : (tickLevel, tickPos, tickState, blockEntity) -> {
            if (blockEntity instanceof ArcaneSourceJarBlockEntity jar) jar.serverTick();
        };
    }

    @Override
    protected List<ItemStack> getDrops(BlockState state, LootParams.Builder builder) {
        ItemStack dropped = new ItemStack(this);
        if (builder.getOptionalParameter(net.minecraft.world.level.storage.loot.parameters.LootContextParams.BLOCK_ENTITY)
                instanceof ArcaneSourceJarBlockEntity jar) {
            CompoundTag data = new CompoundTag();
            data.putInt("Source", jar.getSource());
            BlockItem.setBlockEntityData(dropped, ModBlockEntities.ARCANE_SOURCE_JAR.get(), data);
        }
        return List.of(dropped);
    }

    @Override protected void onRemove(BlockState state, Level level, BlockPos pos,
                                      BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof ArcaneSourceJarBlockEntity jar) {
            SourceNetworkLinking.remove(serverLevel, jar);
        }
        super.onRemove(state, level, pos, newState, moving);
    }
}
