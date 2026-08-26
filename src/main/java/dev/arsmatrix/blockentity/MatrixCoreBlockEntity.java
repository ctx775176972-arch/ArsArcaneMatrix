package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@SuppressWarnings("removal")
public class MatrixCoreBlockEntity extends BlockEntity implements ISourceTile, ITooltipProvider {

    private static final int STRUCTURE_CHECK_INTERVAL = 20;
    private static final TagKey<Block> MATRIX_FRAME_BLOCKS = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "matrix_frame_blocks")
    );

    /**
     * 是否已经形成多方块结构
     */
    private boolean formed = false;

    /** 当前位于潮涌核心式框架有效位置上的魔源宝石块数量。 */
    private int frameBlockCount = 0;

    /** Arcane Amplifiers occupying the six axial vertices. */
    private int amplifierCount = 0;

    /** Ars Nouveau 标准内部 Source 缓存。 */
    private final SourceStorage sourceStorage = createSourceStorage();

    /** Tick计数 */
    private long tickCounter = 0;

    public MatrixCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MATRIX_CORE.get(), pos, state);
    }

    /**
     * 服务端Tick
     */
    public void serverTick() {
        tickCounter++;

        if (tickCounter % STRUCTURE_CHECK_INTERVAL != 0) {
            return;
        }

        updateStructureState();
        refreshSourceStorageLimits();

        if (formed) {
            generateSource();
            outputSource();
        }
    }

    private void generateSource() {
        sourceStorage.receiveSource(getSourceGenerationPerSecond(), false);
    }

    /**
     * 将内部 Source 输出至范围内的 Ars Nouveau Source Jar 或其他兼容目标。
     */
    private void outputSource() {
        if (level == null || getSource() <= 0) {
            return;
        }

        int remainingTransfer = Math.min(getSource(), getSourceTransferPerSecond());
        int transferredSource = 0;

        for (ISpecialSourceProvider provider
                : SourceUtil.canGiveSource(worldPosition, level, getSourceOutputRange())) {
            if (remainingTransfer <= 0) {
                break;
            }

            ISourceTile target = provider.getSource();
            if (target == null || !target.canAcceptSource()) {
                continue;
            }

            int acceptedSource = target.addSource(remainingTransfer, false);
            acceptedSource = Math.max(0, Math.min(acceptedSource, remainingTransfer));
            remainingTransfer -= acceptedSource;
            transferredSource += acceptedSource;
        }

        if (transferredSource > 0) {
            sourceStorage.extractSource(transferredSource, false);
        }
    }

    private SourceStorage createSourceStorage() {
        return new SourceStorage(
                getMaxSource(),
                Integer.MAX_VALUE,
                getSourceTransferPerSecond()
        ) {
            @Override
            public boolean canProvideSource(int amount) {
                return canOutputSource() && super.canProvideSource(amount);
            }

            @Override
            public int extractSource(int amount, boolean simulate) {
                return canOutputSource() ? super.extractSource(amount, simulate) : 0;
            }

            @Override
            public void onContentsChanged() {
                onSourceChanged();
            }
        };
    }

    private void refreshSourceStorageLimits() {
        int capacity = getMaxSource();
        int transferRate = getSourceTransferPerSecond();
        sourceStorage.setMaxSource(capacity);
        sourceStorage.setMaxReceive(Integer.MAX_VALUE);
        sourceStorage.setMaxExtract(transferRate);
        if (sourceStorage.getSource() > capacity) {
            sourceStorage.setSource(capacity);
        }
    }

    private boolean canOutputSource() {
        return formed;
    }

    private void onSourceChanged() {
        setChangedAndSyncClient();
    }

    private void setChangedAndSyncClient() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    /**
     * 统计潮涌核心式三轴 5x5 环上存在的框架方块，最多 42 个。
     */
    private int countFrameBlocks() {
        if (level == null) {
            return 0;
        }

        int count = 0;
        for (int xOffset = -2; xOffset <= 2; xOffset++) {
            for (int yOffset = -2; yOffset <= 2; yOffset++) {
                for (int zOffset = -2; zOffset <= 2; zOffset++) {
                    if (!isFramePosition(xOffset, yOffset, zOffset)) {
                        continue;
                    }

                    if (isValidFrameBlock(xOffset, yOffset, zOffset)) {
                        count++;
                    }
                }
            }
        }

        return Math.min(count, getMaximumFrameBlocks());
    }

    private boolean hasCompleteRing() {
        return isCompleteRing(0) || isCompleteRing(1) || isCompleteRing(2);
    }

    /**
     * Checks one 5x5 perimeter ring: XY (fixed Z), XZ (fixed Y), or YZ (fixed X).
     */
    private boolean isCompleteRing(int fixedAxis) {
        for (int first = -2; first <= 2; first++) {
            for (int second = -2; second <= 2; second++) {
                if (Math.abs(first) != 2 && Math.abs(second) != 2) {
                    continue;
                }
                int xOffset = fixedAxis == 2 ? first : fixedAxis == 1 ? first : 0;
                int yOffset = fixedAxis == 2 ? second : fixedAxis == 0 ? first : 0;
                int zOffset = fixedAxis == 1 ? second : fixedAxis == 0 ? second : 0;
                if (!isValidFrameBlock(xOffset, yOffset, zOffset)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean isValidFrameBlock(int xOffset, int yOffset, int zOffset) {
        if (level == null) {
            return false;
        }
        BlockState state = level.getBlockState(worldPosition.offset(xOffset, yOffset, zOffset));
        return state.is(MATRIX_FRAME_BLOCKS)
                || isAmplifierPosition(xOffset, yOffset, zOffset)
                && state.is(ModBlocks.ARCANE_AMPLIFIER.get());
    }

    private static boolean isFramePosition(int xOffset, int yOffset, int zOffset) {
        return xOffset == 0 && (Math.abs(yOffset) == 2 || Math.abs(zOffset) == 2)
                || yOffset == 0 && (Math.abs(xOffset) == 2 || Math.abs(zOffset) == 2)
                || zOffset == 0 && (Math.abs(xOffset) == 2 || Math.abs(yOffset) == 2);
    }

    private static boolean isAmplifierPosition(int xOffset, int yOffset, int zOffset) {
        return Math.abs(xOffset) == 2 && yOffset == 0 && zOffset == 0
                || xOffset == 0 && Math.abs(yOffset) == 2 && zOffset == 0
                || xOffset == 0 && yOffset == 0 && Math.abs(zOffset) == 2;
    }

    private int countAmplifiers() {
        if (level == null) {
            return 0;
        }
        int count = 0;
        int[][] vertices = {
                {2, 0, 0}, {-2, 0, 0},
                {0, 2, 0}, {0, -2, 0},
                {0, 0, 2}, {0, 0, -2}
        };
        for (int[] vertex : vertices) {
            if (level.getBlockState(worldPosition.offset(vertex[0], vertex[1], vertex[2]))
                    .is(ModBlocks.ARCANE_AMPLIFIER.get())) {
                count++;
            }
        }
        return count;
    }

    private void updateStructureState() {
        int newFrameBlockCount = countFrameBlocks();
        int newAmplifierCount = countAmplifiers();
        boolean structureFormed = newFrameBlockCount >= getMinimumFrameBlocks()
                && hasCompleteRing();
        if (formed == structureFormed
                && frameBlockCount == newFrameBlockCount
                && amplifierCount == newAmplifierCount) {
            return;
        }

        boolean wasFormed = formed;
        frameBlockCount = newFrameBlockCount;
        amplifierCount = newAmplifierCount;
        formed = structureFormed;

        setChangedAndSyncClient();

        if (!wasFormed && formed) {
            playStructureFormedEffect();
        }
    }

    /** 结构首次形成或重新形成时播放一次全客户端可见的核心激活特效。 */
    private void playStructureFormedEffect() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }

        double centerX = worldPosition.getX() + 0.5;
        double centerY = worldPosition.getY() + 0.5;
        double centerZ = worldPosition.getZ() + 0.5;

        serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                centerX, centerY, centerZ,
                120,
                2.0, 2.0, 2.0,
                0.2
        );
        serverLevel.sendParticles(
                ParticleTypes.END_ROD,
                centerX, centerY, centerZ,
                32,
                0.8, 0.8, 0.8,
                0.12
        );
        serverLevel.playSound(
                null,
                worldPosition,
                SoundEvents.CONDUIT_ACTIVATE,
                SoundSource.BLOCKS,
                1.5F,
                1.0F
        );
    }

    /*========================*/
    /*        Getter          */
    /*========================*/

    public boolean isFormed() {
        return formed;
    }

    public boolean isActive() {
        return formed;
    }

    public long getStoredSource() {
        return getSource();
    }

    @Override
    public int getMaxSource() {
        return MatrixConfig.SOURCE_CAPACITY.get();
    }

    public int getSourceGenerationPerSecond() {
        return MatrixConfig.matrixGenerationFor(frameBlockCount, amplifierCount);
    }

    public int getFrameBlockCount() {
        return frameBlockCount;
    }

    public int getAmplifierCount() {
        return amplifierCount;
    }

    public int getSourceTransferPerSecond() {
        return MatrixConfig.MAX_OUTPUT_PER_SECOND.get();
    }

    public int getSourceOutputRange() {
        return MatrixConfig.OUTPUT_RANGE.get();
    }

    public int getMinimumFrameBlocks() {
        return MatrixConfig.minimumFrameBlocks();
    }

    public int getMaximumFrameBlocks() {
        return MatrixConfig.maximumFrameBlocks();
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage;
    }

    @Override
    public int getTransferRate() {
        return getSourceTransferPerSecond();
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return canOutputSource() && getSource() > 0;
    }

    @Override
    public int getSource() {
        return sourceStorage.getSource();
    }

    @Override
    public int setSource(int source) {
        int previousSource = getSource();
        sourceStorage.setSource(source);
        if (previousSource != getSource()) {
            onSourceChanged();
        }
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        if (amount > 0) {
            setSource((int) Math.min((long) getSource() + amount, getMaxSource()));
        }
        return getSource();
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return sourceStorage.receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        if (amount > 0 && canProvideSource()) {
            setSource(Math.max(0, getSource() - amount));
        }
        return getSource();
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return sourceStorage.extractSource(amount, simulate);
    }

    @Override
    public void getTooltip(List<Component> tooltip) {
        if (ArsNouveauAPI.ENABLE_DEBUG_NUMBERS) {
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.matrix_core.source_exact",
                    getSource(),
                    getMaxSource()
            ));
        } else {
            int fullness = getMaxSource() == 0 ? 0 : getSource() * 100 / getMaxSource();
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.matrix_core.source_percent",
                    fullness
            ));
        }

        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.matrix_core.frame",
                frameBlockCount,
                getMaximumFrameBlocks(),
                getSourceGenerationPerSecond()
        ));
        tooltip.add(Component.translatable(
                "tooltip.ars_arcane_matrix.matrix_core.amplifiers",
                amplifierCount,
                MatrixConfig.MATRIX_AMPLIFIER_POSITIONS
        ));
    }

    /*========================*/
    /*        Setter          */
    /*========================*/

    public void setFormed(boolean formed) {
        if (this.formed == formed) {
            return;
        }

        this.formed = formed;
        setChanged();
    }

    public void setStoredSource(long source) {
        setSource((int) Math.max(0, Math.min(source, getMaxSource())));
    }

    public void addSource(long amount) {
        if (amount <= 0 || getSource() >= getMaxSource()) {
            return;
        }

        int acceptedAmount = (int) Math.min(amount, getMaxSource() - getSource());
        setSource(getSource() + acceptedAmount);
    }

    public boolean consumeSource(long amount) {
        if (amount <= 0 || amount > Integer.MAX_VALUE || getSource() < amount) {
            return false;
        }

        setSource(getSource() - (int) amount);
        return true;
    }

    /*========================*/
    /*         NBT            */
    /*========================*/

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);

        tag.putBoolean("Formed", formed);
        tag.putInt("FrameBlockCount", frameBlockCount);
        tag.putInt("AmplifierCount", amplifierCount);
        tag.putLong("StoredSource", getSource());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);

        formed = tag.getBoolean("Formed");
        frameBlockCount = Math.max(0, Math.min(tag.getInt("FrameBlockCount"), getMaximumFrameBlocks()));
        amplifierCount = Math.max(0, Math.min(
                tag.getInt("AmplifierCount"),
                MatrixConfig.MATRIX_AMPLIFIER_POSITIONS
        ));
        refreshSourceStorageLimits();
        sourceStorage.setSource((int) Math.max(0, Math.min(tag.getLong("StoredSource"), getMaxSource())));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable
    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
