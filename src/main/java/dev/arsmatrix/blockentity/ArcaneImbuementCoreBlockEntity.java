package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.ArsNouveauAPI;
import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.core.particles.ParticleTypes;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Strengthens an Imbuement Chamber in the same vertical column. Bulk inputs
 * live in this controller so the vanilla chamber can retain its one-item,
 * GUI-free interaction model.
 */
public final class ArcaneImbuementCoreBlockEntity extends BlockEntity
        implements ISourceTile, ITooltipProvider {

    private static final ResourceLocation SOURCE_GEM_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem");
    private static final ResourceLocation SOURCE_GEM_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem_block");

    private final SourceStorage sourceStorage = new SourceStorage(
            MatrixConfig.IMBUEMENT_SOURCE_CAPACITY.get(),
            Integer.MAX_VALUE,
            0
    );
    private final IItemHandler inputHandler = new InputHandler();
    private final IItemHandler outputHandler = new OutputHandler();

    private ItemStack input = ItemStack.EMPTY;
    private ItemStack gemOutput = ItemStack.EMPTY;
    private ItemStack gemBlockOutput = ItemStack.EMPTY;
    private BlockPos chamberPos;
    private BatchKind activeBatch = BatchKind.NONE;
    private int activeBatchSize;
    private int progressTicks;
    private int tickCounter;
    private boolean redstonePaused;
    private OperatingState operatingState = OperatingState.UNLINKED;
    private OutputMode outputMode = OutputMode.LOOSE;

    public ArcaneImbuementCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_IMBUEMENT_CORE.get(), pos, state);
    }

    public void serverTick() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        tickCounter++;
        refreshSourceLimits();
        pushOutputDown();

        if (tickCounter % 20 == 1) {
            chamberPos = findChamber();
            redstonePaused = currentLevel.hasNeighborSignal(worldPosition);
            setChangedAndSyncClient();
        }

        ImbuementTile chamber = getConnectedChamber();
        if (chamber == null) {
            setOperatingState(OperatingState.UNLINKED);
            return;
        }
        if (redstonePaused) {
            setOperatingState(OperatingState.REDSTONE_PAUSED);
            return;
        }

        ItemStack chamberStack = chamber.getStack();
        if (chamberStack.isEmpty()
                || isBulkInput(chamberStack)) {
            reclaimChamberSource(chamber);
        }
        absorbCompressedInput(chamber);
        if (tickCounter % 20 == 0) {
            pullNearbySource();
            supplyConnectedChamber(chamber);
        }

        if (progressTicks > 0) {
            progressTicks--;
            setOperatingState(OperatingState.PROCESSING);
            if (tickCounter % 10 == 0) {
                playLinkParticles();
            }
            if (progressTicks == 0) {
                finishBatch();
            } else if (tickCounter % 20 == 0) {
                setChangedAndSyncClient();
            }
            return;
        }

        tryStartBatch();
    }

    @Nullable
    private BlockPos findChamber() {
        if (level == null) {
            return null;
        }
        int minimum = Math.max(1, MatrixConfig.IMBUEMENT_MINIMUM_CHAMBER_DISTANCE.get());
        int maximum = Math.max(minimum, MatrixConfig.IMBUEMENT_MAXIMUM_CHAMBER_DISTANCE.get());
        for (int distance = minimum; distance <= maximum; distance++) {
            BlockPos candidate = worldPosition.above(distance);
            if (level.hasChunkAt(candidate)
                    && level.getBlockEntity(candidate) instanceof ImbuementTile
                    && isNearestCoreFor(candidate, minimum, maximum)) {
                return candidate.immutable();
            }
        }
        return null;
    }

    private boolean isNearestCoreFor(BlockPos chamber, int minimum, int maximum) {
        if (level == null) {
            return false;
        }
        for (int distance = minimum; distance <= maximum; distance++) {
            BlockPos candidate = chamber.below(distance);
            if (level.getBlockEntity(candidate) instanceof ArcaneImbuementCoreBlockEntity) {
                return candidate.equals(worldPosition);
            }
        }
        return false;
    }

    @Nullable
    private ImbuementTile getConnectedChamber() {
        if (level == null || chamberPos == null || !level.hasChunkAt(chamberPos)) {
            return null;
        }
        return level.getBlockEntity(chamberPos) instanceof ImbuementTile chamber ? chamber : null;
    }

    private void absorbCompressedInput(ImbuementTile chamber) {
        ItemStack chamberStack = chamber.getStack();
        if (!isBulkInput(chamberStack) || !canMergeInput(chamberStack)) {
            return;
        }
        int accepted = Math.min(
                chamberStack.getCount(),
                chamberStack.getMaxStackSize() - input.getCount()
        );
        if (accepted <= 0) {
            return;
        }
        if (input.isEmpty()) {
            input = chamberStack.copyWithCount(accepted);
        } else {
            input.grow(accepted);
        }
        reclaimChamberSource(chamber);
        ItemStack remainder = chamberStack.copy();
        remainder.shrink(accepted);
        chamber.setItem(0, remainder);
        setChangedAndSyncClient();
    }

    private void reclaimChamberSource(ImbuementTile chamber) {
        int room = getMaxSource() - getSource();
        if (room <= 0 || chamber.getSource() <= 0) {
            return;
        }
        int offered = Math.max(0, Math.min(room, chamber.removeSource(room, true)));
        int accepted = sourceStorage.receiveSource(offered, true);
        if (accepted <= 0) {
            return;
        }
        int extracted = Math.max(0, Math.min(accepted, chamber.removeSource(accepted, false)));
        sourceStorage.receiveSource(extracted, false);
        chamber.updateBlock();
    }

    private void pullNearbySource() {
        if (level == null || getSource() >= getMaxSource()) {
            return;
        }
        int remaining = Math.min(
                getMaxSource() - getSource(),
                MatrixConfig.IMBUEMENT_MAX_SOURCE_INPUT_PER_SECOND.get()
        );
        int range = MatrixConfig.IMBUEMENT_SOURCE_INPUT_RANGE.get();
        Set<ISpecialSourceProvider> providers = new LinkedHashSet<>(
                SourceUtil.canTakeSource(worldPosition, level, range)
        );
        if (chamberPos != null) {
            providers.addAll(SourceUtil.canTakeSource(chamberPos, level, range));
        }
        for (ISpecialSourceProvider provider : providers) {
            if (remaining <= 0) {
                break;
            }
            ISourceTile source = provider.getSource();
            if (source == null || source == this || !source.canProvideSource()) {
                continue;
            }
            int offered = Math.max(0, Math.min(remaining, source.removeSource(remaining, true)));
            int accepted = sourceStorage.receiveSource(offered, true);
            if (accepted <= 0) {
                continue;
            }
            int extracted = Math.max(0, Math.min(accepted, source.removeSource(accepted, false)));
            int stored = sourceStorage.receiveSource(extracted, false);
            remaining -= stored;
        }
    }

    private void supplyConnectedChamber(ImbuementTile chamber) {
        ItemStack chamberStack = chamber.getStack();
        if (chamberStack.isEmpty()
                || isBulkInput(chamberStack)
                || getSource() <= 0) {
            return;
        }
        var recipe = chamber.getRecipeNow();
        if (recipe == null) {
            return;
        }
        int required = Math.max(0, recipe.value().getSourceCost(chamber));
        int needed = Math.max(0, required - chamber.getSource());
        if (needed <= 0) {
            return;
        }
        SourceStorage chamberStorage = chamber.getSourceStorage();
        int offered = Math.min(getSource(), needed);
        int accepted = chamberStorage.receiveSource(offered, true);
        if (accepted <= 0) {
            return;
        }
        int transferred = chamberStorage.receiveSource(accepted, false);
        setSource(getSource() - transferred);
        chamber.updateBlock();
        setChangedAndSyncClient();
    }

    private void tryStartBatch() {
        BatchKind kind = BatchKind.from(input);
        if (kind == BatchKind.NONE) {
            setOperatingState(OperatingState.IDLE);
            return;
        }

        int maxConfiguredBatch = Math.min(
                input.getCount(),
                MatrixConfig.IMBUEMENT_MAX_COMPRESSED_INPUTS.get()
        );
        int maxByOutput = 0;
        for (int candidate = 1; candidate <= maxConfiguredBatch; candidate++) {
            if (!canStoreBatchResult(kind, candidate)) {
                break;
            }
            maxByOutput = candidate;
        }
        int maxBySource = getSource() / kind.sourcePerInput;
        int batchSize = Math.min(
                maxConfiguredBatch,
                Math.min(maxByOutput, maxBySource)
        );
        if (maxByOutput <= 0) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }
        if (maxBySource <= 0) {
            setOperatingState(OperatingState.SOURCE_STARVED);
            return;
        }
        input.shrink(batchSize);
        if (input.isEmpty()) {
            input = ItemStack.EMPTY;
        }
        setSource(getSource() - batchSize * kind.sourcePerInput);
        activeBatch = kind;
        activeBatchSize = batchSize;
        progressTicks = MatrixConfig.IMBUEMENT_CYCLE_TICKS.get();
        setOperatingState(OperatingState.PROCESSING);
        playLinkParticles();
        setChangedAndSyncClient();
    }

    private void finishBatch() {
        if (activeBatch == BatchKind.NONE || activeBatchSize <= 0) {
            return;
        }
        storeBatchResult(activeBatch, activeBatchSize);
        activeBatch = BatchKind.NONE;
        activeBatchSize = 0;
        if (level != null) {
            level.playSound(
                    null,
                    worldPosition,
                    SoundEvents.AMETHYST_BLOCK_RESONATE,
                    SoundSource.BLOCKS,
                    0.8F,
                    1.1F
            );
        }
        playLinkParticles();
        setChangedAndSyncClient();
    }

    private boolean canStoreBatchResult(BatchKind kind, int batchSize) {
        if (batchSize <= 0) {
            return true;
        }
        int produced = batchSize * kind.outputPerInput;
        if (kind == BatchKind.LAPIS) {
            if (outputMode == OutputMode.LOOSE) {
                return produced <= gemRoom();
            }
            int totalGems = gemOutput.getCount() + produced;
            return totalGems / 4 <= gemBlockRoom();
        }
        return produced <= gemBlockRoom();
    }

    private void storeBatchResult(BatchKind kind, int batchSize) {
        int produced = batchSize * kind.outputPerInput;
        if (kind == BatchKind.LAPIS) {
            addGems(produced);
            if (outputMode == OutputMode.COMPACT) {
                compactBufferedGems();
            }
            return;
        }
        addGemBlocks(produced);
    }

    private int gemRoom() {
        return gemOutput.isEmpty() ? 64 : Math.max(0, gemOutput.getMaxStackSize() - gemOutput.getCount());
    }

    private int gemBlockRoom() {
        return gemBlockOutput.isEmpty()
                ? 64
                : Math.max(0, gemBlockOutput.getMaxStackSize() - gemBlockOutput.getCount());
    }

    private void addGems(int count) {
        if (count <= 0) {
            return;
        }
        if (gemOutput.isEmpty()) {
            gemOutput = new ItemStack(BuiltInRegistries.ITEM.get(SOURCE_GEM_ID), count);
        } else {
            gemOutput.grow(count);
        }
    }

    private void addGemBlocks(int count) {
        if (count <= 0) {
            return;
        }
        if (gemBlockOutput.isEmpty()) {
            gemBlockOutput = new ItemStack(BuiltInRegistries.ITEM.get(SOURCE_GEM_BLOCK_ID), count);
        } else {
            gemBlockOutput.grow(count);
        }
    }

    private void compactBufferedGems() {
        int groups = Math.min(gemOutput.getCount() / 4, gemBlockRoom());
        if (groups <= 0) {
            return;
        }
        gemOutput.shrink(groups * 4);
        if (gemOutput.isEmpty()) {
            gemOutput = ItemStack.EMPTY;
        }
        addGemBlocks(groups);
    }

    /**
     * Output is actively pushed so simple inventories and pipes do not need to
     * poll the core correctly. The bottom-face extraction capability remains
     * available for networks that prefer pulling.
     */
    private void pushOutputDown() {
        if (level == null
                || (gemOutput.isEmpty() && gemBlockOutput.isEmpty())
                || !level.hasChunkAt(worldPosition.below())) {
            return;
        }
        IItemHandler target = level.getCapability(
                Capabilities.ItemHandler.BLOCK,
                worldPosition.below(),
                Direction.UP
        );
        if (target == null) {
            return;
        }
        boolean changed = pushStack(target, true);
        changed |= pushStack(target, false);
        if (changed) {
            setChangedAndSyncClient();
        }
    }

    private boolean pushStack(IItemHandler target, boolean gems) {
        ItemStack stack = gems ? gemOutput : gemBlockOutput;
        if (stack.isEmpty()) {
            return false;
        }
        int previousCount = stack.getCount();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
        if (remainder.getCount() == previousCount) {
            return false;
        }
        if (gems) {
            gemOutput = remainder;
        } else {
            gemBlockOutput = remainder;
        }
        return true;
    }

    private void playLinkParticles() {
        if (!(level instanceof ServerLevel serverLevel) || chamberPos == null) {
            return;
        }
        double height = chamberPos.getY() - worldPosition.getY();
        serverLevel.sendParticles(
                ParticleTypes.ENCHANT,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 1.0D,
                worldPosition.getZ() + 0.5D,
                Math.max(1, (int) height),
                0.04D,
                height * 0.35D,
                0.04D,
                0.01D
        );
    }

    private boolean canMergeInput(ItemStack stack) {
        return input.isEmpty() || ItemStack.isSameItemSameComponents(input, stack);
    }

    private static boolean isBulkInput(ItemStack stack) {
        return stack.is(Items.LAPIS_BLOCK) || stack.is(Items.AMETHYST_BLOCK);
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        dropStack(input);
        dropStack(gemOutput);
        dropStack(gemBlockOutput);
        if (activeBatch != BatchKind.NONE && activeBatchSize > 0) {
            dropStack(new ItemStack(activeBatch.inputItem, activeBatchSize));
        }
        input = ItemStack.EMPTY;
        gemOutput = ItemStack.EMPTY;
        gemBlockOutput = ItemStack.EMPTY;
        activeBatch = BatchKind.NONE;
        activeBatchSize = 0;
    }

    private void dropStack(ItemStack stack) {
        if (!stack.isEmpty() && level != null) {
            Containers.dropItemStack(
                    level,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D,
                    worldPosition.getZ() + 0.5D,
                    stack.copy()
            );
        }
    }

    public IItemHandler getItemHandler(@Nullable Direction direction) {
        return direction == Direction.DOWN ? outputHandler : inputHandler;
    }

    public SourceStorage getSourceStorage() {
        return sourceStorage;
    }

    public OperatingState getOperatingState() {
        return operatingState;
    }

    public int getProgressTicks() {
        return progressTicks;
    }

    public int getInputCount() {
        return input.getCount();
    }

    public int getOutputCount() {
        return gemOutput.getCount() + gemBlockOutput.getCount();
    }

    public OutputMode getOutputMode() {
        return outputMode;
    }

    public OutputMode toggleOutputMode() {
        outputMode = outputMode == OutputMode.LOOSE ? OutputMode.COMPACT : OutputMode.LOOSE;
        if (outputMode == OutputMode.COMPACT) {
            compactBufferedGems();
        }
        setChangedAndSyncClient();
        return outputMode;
    }

    public int getConnectedDistance() {
        return chamberPos == null ? 0 : chamberPos.getY() - worldPosition.getY();
    }

    private void setOperatingState(OperatingState state) {
        if (operatingState != state) {
            operatingState = state;
            setChangedAndSyncClient();
        }
    }

    private void refreshSourceLimits() {
        sourceStorage.setMaxSource(getMaxSource());
        sourceStorage.setMaxReceive(Integer.MAX_VALUE);
        sourceStorage.setMaxExtract(0);
        if (sourceStorage.getSource() > getMaxSource()) {
            sourceStorage.setSource(getMaxSource());
        }
    }

    private void setChangedAndSyncClient() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public int getTransferRate() {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canAcceptSource() {
        return getSource() < getMaxSource();
    }

    @Override
    public boolean canProvideSource() {
        return false;
    }

    @Override
    public int getSource() {
        return sourceStorage.getSource();
    }

    @Override
    public int getMaxSource() {
        return MatrixConfig.IMBUEMENT_SOURCE_CAPACITY.get();
    }

    @Override
    public int setSource(int source) {
        sourceStorage.setSource(Math.max(0, Math.min(source, getMaxSource())));
        return getSource();
    }

    @Override
    public int addSource(int amount) {
        return setSource((int) Math.min((long) getSource() + Math.max(0, amount), getMaxSource()));
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return sourceStorage.receiveSource(amount, simulate);
    }

    @Override
    public int removeSource(int amount) {
        return getSource();
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public void getTooltip(List<Component> tooltip) {
        if (ArsNouveauAPI.ENABLE_DEBUG_NUMBERS) {
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.arcane_imbuement_core.source_exact",
                    getSource(),
                    getMaxSource()
            ));
        } else {
            int percent = getMaxSource() == 0 ? 0 : getSource() * 100 / getMaxSource();
            tooltip.add(Component.translatable(
                    "tooltip.ars_arcane_matrix.arcane_imbuement_core.source_percent",
                    percent
            ));
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Source", getSource());
        tag.put("Input", input.saveOptional(registries));
        tag.put("Output", gemOutput.saveOptional(registries));
        tag.put("GemBlockOutput", gemBlockOutput.saveOptional(registries));
        tag.putString("OutputMode", outputMode.serializedName);
        tag.putString("ActiveBatch", activeBatch.serializedName);
        tag.putInt("ActiveBatchSize", activeBatchSize);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putInt("OperatingState", operatingState.ordinal());
        tag.putBoolean("RedstonePaused", redstonePaused);
        if (chamberPos != null) {
            tag.putLong("ChamberPos", chamberPos.asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        setSource(tag.getInt("Source"));
        input = tag.contains("Input", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Input"))
                : ItemStack.EMPTY;
        ItemStack legacyOutput = tag.contains("Output", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("Output"))
                : ItemStack.EMPTY;
        gemBlockOutput = tag.contains("GemBlockOutput", Tag.TAG_COMPOUND)
                ? ItemStack.parseOptional(registries, tag.getCompound("GemBlockOutput"))
                : ItemStack.EMPTY;
        if (legacyOutput.is(BuiltInRegistries.ITEM.get(SOURCE_GEM_BLOCK_ID))) {
            if (gemBlockOutput.isEmpty()) {
                gemBlockOutput = legacyOutput;
            } else {
                gemBlockOutput.grow(legacyOutput.getCount());
            }
            gemOutput = ItemStack.EMPTY;
        } else {
            gemOutput = legacyOutput;
        }
        outputMode = OutputMode.fromSerializedName(tag.getString("OutputMode"));
        activeBatch = BatchKind.fromSerializedName(tag.getString("ActiveBatch"));
        activeBatchSize = Math.max(0, tag.getInt("ActiveBatchSize"));
        progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        int stateOrdinal = tag.getInt("OperatingState");
        operatingState = stateOrdinal >= 0 && stateOrdinal < OperatingState.values().length
                ? OperatingState.values()[stateOrdinal]
                : OperatingState.UNLINKED;
        redstonePaused = tag.getBoolean("RedstonePaused");
        chamberPos = tag.contains("ChamberPos", Tag.TAG_LONG)
                ? BlockPos.of(tag.getLong("ChamberPos"))
                : null;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum OperatingState {
        UNLINKED,
        REDSTONE_PAUSED,
        IDLE,
        SOURCE_STARVED,
        OUTPUT_BLOCKED,
        PROCESSING
    }

    public enum OutputMode {
        LOOSE("loose", "message.ars_arcane_matrix.arcane_imbuement_core.mode.loose"),
        COMPACT("compact", "message.ars_arcane_matrix.arcane_imbuement_core.mode.compact");

        private final String serializedName;
        private final String translationKey;

        OutputMode(String serializedName, String translationKey) {
            this.serializedName = serializedName;
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }

        private static OutputMode fromSerializedName(String name) {
            return COMPACT.serializedName.equals(name) ? COMPACT : LOOSE;
        }
    }

    private enum BatchKind {
        NONE("", Items.AIR, 0, 0, null),
        LAPIS("lapis", Items.LAPIS_BLOCK, 4_500, 9, SOURCE_GEM_ID),
        AMETHYST("amethyst", Items.AMETHYST_BLOCK, 2_000, 1, SOURCE_GEM_BLOCK_ID);

        private final String serializedName;
        private final Item inputItem;
        private final int sourcePerInput;
        private final int outputPerInput;
        private final ResourceLocation resultId;

        BatchKind(
                String serializedName,
                Item inputItem,
                int sourcePerInput,
                int outputPerInput,
                @Nullable ResourceLocation resultId
        ) {
            this.serializedName = serializedName;
            this.inputItem = inputItem;
            this.sourcePerInput = sourcePerInput;
            this.outputPerInput = outputPerInput;
            this.resultId = resultId;
        }

        private Item resultItem() {
            return resultId == null ? Items.AIR : BuiltInRegistries.ITEM.get(resultId);
        }

        private static BatchKind from(ItemStack stack) {
            if (stack.is(Items.LAPIS_BLOCK)) {
                return LAPIS;
            }
            if (stack.is(Items.AMETHYST_BLOCK)) {
                return AMETHYST;
            }
            return NONE;
        }

        private static BatchKind fromSerializedName(String name) {
            for (BatchKind value : values()) {
                if (value.serializedName.equals(name)) {
                    return value;
                }
            }
            return NONE;
        }
    }

    private final class InputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 1;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot == 0 ? input : ItemStack.EMPTY;
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (slot != 0 || !isBulkInput(stack) || !canMergeInput(stack)) {
                return stack;
            }
            int accepted = Math.min(stack.getCount(), stack.getMaxStackSize() - input.getCount());
            if (accepted <= 0) {
                return stack;
            }
            if (!simulate) {
                if (input.isEmpty()) {
                    input = stack.copyWithCount(accepted);
                } else {
                    input.grow(accepted);
                }
                setChangedAndSyncClient();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(accepted);
            return remainder;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return slot == 0 && isBulkInput(stack);
        }
    }

    private final class OutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return 2;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return switch (slot) {
                case 0 -> gemOutput;
                case 1 -> gemBlockOutput;
                default -> ItemStack.EMPTY;
            };
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return stack;
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = getStackInSlot(slot);
            if (amount <= 0 || stack.isEmpty()) {
                return ItemStack.EMPTY;
            }
            int extractedCount = Math.min(amount, stack.getCount());
            ItemStack extracted = stack.copyWithCount(extractedCount);
            if (!simulate) {
                stack.shrink(extractedCount);
                if (stack.isEmpty()) {
                    if (slot == 0) {
                        gemOutput = ItemStack.EMPTY;
                    } else if (slot == 1) {
                        gemBlockOutput = ItemStack.EMPTY;
                    }
                }
                setChangedAndSyncClient();
            }
            return extracted;
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return false;
        }
    }
}
