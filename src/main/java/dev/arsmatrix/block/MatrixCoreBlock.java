package dev.arsmatrix.block;

import com.mojang.serialization.MapCodec;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.blockentity.MatrixCoreBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.source.SourceNetworkLinking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import org.jetbrains.annotations.Nullable;

public class MatrixCoreBlock extends BaseEntityBlock {

    public static final MapCodec<MatrixCoreBlock> CODEC = simpleCodec(MatrixCoreBlock::new);
    private static final TagKey<Block> MATRIX_FRAME_BLOCKS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "matrix_frame_blocks")
    );

    public MatrixCoreBlock(BlockBehaviour.Properties properties) {
        super(properties);
    }

    @Override
    protected MapCodec<? extends BaseEntityBlock> codec() {
        return CODEC;
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MatrixCoreBlockEntity(pos, state);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        super.animateTick(state, level, pos, random);
        if (!(level.getBlockEntity(pos) instanceof MatrixCoreBlockEntity core)) {
            return;
        }
        if (!core.isActive()) {
            return;
        }

        double centerX = pos.getX() + 0.5D;
        double centerY = pos.getY() + 0.5D;
        double centerZ = pos.getZ() + 0.5D;

        int particleCount = 1;
        if (core.getAmplifierCount() > 0
                && random.nextInt(6) < Math.min(core.getAmplifierCount(), 6)) {
            particleCount++;
        }

        for (int particle = 0; particle < particleCount; particle++) {
            BlockPos origin = findFrameParticleOrigin(level, pos, random);
            if (origin == null) {
                return;
            }

            double originX = origin.getX() + 0.5D + (random.nextDouble() - 0.5D) * 0.45D;
            double originY = origin.getY() + 0.5D + (random.nextDouble() - 0.5D) * 0.45D;
            double originZ = origin.getZ() + 0.5D + (random.nextDouble() - 0.5D) * 0.45D;

            // FlyTowardsPositionParticle ends 1.2 blocks below its target Y.
            // Raising the target by that amount makes the visible curve converge on the core center.
            double targetY = centerY + 1.2D;
            level.addParticle(
                    ParticleTypes.ENCHANT,
                    centerX, targetY, centerZ,
                    originX - centerX,
                    originY - targetY,
                    originZ - centerZ
            );
        }
    }

    @Nullable
    private static BlockPos findFrameParticleOrigin(
            Level level,
            BlockPos corePos,
            RandomSource random
    ) {
        for (int attempt = 0; attempt < 24; attempt++) {
            int first;
            int second;
            if (random.nextBoolean()) {
                first = random.nextBoolean() ? 2 : -2;
                second = random.nextInt(5) - 2;
            } else {
                first = random.nextInt(5) - 2;
                second = random.nextBoolean() ? 2 : -2;
            }

            BlockPos candidate = switch (random.nextInt(3)) {
                case 0 -> corePos.offset(first, second, 0);
                case 1 -> corePos.offset(first, 0, second);
                default -> corePos.offset(0, first, second);
            };
            BlockState candidateState = level.getBlockState(candidate);
            if (candidateState.is(MATRIX_FRAME_BLOCKS)
                    || candidateState.is(ModBlocks.ARCANE_AMPLIFIER.get())) {
                return candidate;
            }
        }
        return null;
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(
            Level level,
            BlockState state,
            BlockEntityType<T> type
    ) {
        return level.isClientSide ? null : (lvl, blockPos, blockState, blockEntity) -> {
            if (blockEntity instanceof MatrixCoreBlockEntity core) {
                core.serverTick();
            }
        };
    }

    @Override
    protected void onRemove(
            BlockState state,
            Level level,
            BlockPos pos,
            BlockState newState,
            boolean moving
    ) {
        if (!state.is(newState.getBlock())
                && level instanceof ServerLevel serverLevel
                && level.getBlockEntity(pos) instanceof MatrixCoreBlockEntity core) {
            SourceNetworkLinking.remove(serverLevel, core);
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return super.getStateForPlacement(context);
    }

    @Override
    protected boolean canSurvive(
            BlockState state,
            LevelReader level,
            BlockPos pos
    ) {
        return true;
    }

    @Override
    protected boolean isPathfindable(
            BlockState state,
            net.minecraft.world.level.pathfinder.PathComputationType type
    ) {
        return false;
    }
}
