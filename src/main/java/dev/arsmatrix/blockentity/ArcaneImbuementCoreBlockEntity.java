package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.LinkedHashSet;

/**
 * Standalone bulk Source Gem converter. Input and output inventories are bound
 * with a Dominion Wand; a future upgraded chamber remains a separate machine.
 */
public final class ArcaneImbuementCoreBlockEntity extends BlockEntity
        implements ISourceTile, IWandable {

    private static final ResourceLocation SOURCE_GEM_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem");
    private static final ResourceLocation SOURCE_GEM_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem_block");

    // Kept only as a zero-capacity capability adapter for Ars Nouveau APIs.
    // Operations pay Source directly from nearby providers and never buffer it here.
    private final SourceStorage sourceStorage = new SourceStorage(0, 0, 0);
    private ItemStack input = ItemStack.EMPTY;
    private ItemStack gemOutput = ItemStack.EMPTY;
    private ItemStack gemBlockOutput = ItemStack.EMPTY;
    @Nullable private GlobalPos inputContainer;
    @Nullable private GlobalPos outputContainer;
    @Nullable private Direction inputFace;
    @Nullable private Direction outputFace;
    private BatchKind activeBatch = BatchKind.NONE;
    private int activeBatchSize;
    private int progressTicks;
    private int tickCounter;
    private boolean redstonePaused;
    private OperatingState operatingState = OperatingState.IDLE;
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
        pullBoundInput();
        pushBoundOutputs();

        if (tickCounter % 20 == 1) {
            redstonePaused = currentLevel.hasNeighborSignal(worldPosition);
            setChangedAndSyncClient();
        }
        if (redstonePaused) {
            setOperatingState(OperatingState.REDSTONE_PAUSED);
            return;
        }
        if (progressTicks > 0) {
            progressTicks--;
            setOperatingState(OperatingState.PROCESSING);
            if (tickCounter % 10 == 0) {
                playProcessingParticles();
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
        int requestedSource = maxConfiguredBatch * kind.sourcePerInput;
        int maxBySource = availableOperationSource(requestedSource) / kind.sourcePerInput;
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
        if (!consumeOperationSource(batchSize * kind.sourcePerInput)) {
            setOperatingState(OperatingState.SOURCE_STARVED);
            return;
        }
        input.shrink(batchSize);
        if (input.isEmpty()) {
            input = ItemStack.EMPTY;
        }
        activeBatch = kind;
        activeBatchSize = batchSize;
        progressTicks = MatrixConfig.IMBUEMENT_CYCLE_TICKS.get();
        setOperatingState(OperatingState.PROCESSING);
        playProcessingParticles();
        setChangedAndSyncClient();
    }

    /** Atomically pays one batch from nearby providers without storing Source in the core. */
    private boolean consumeOperationSource(int cost) {
        if (cost <= 0) return true;
        List<ISourceTile> sources = operationSourceProviders();
        if (availableOperationSource(cost, sources) < cost) return false;
        int remaining = cost;
        for (ISourceTile source : sources) {
            if (remaining <= 0) break;
            int extracted = Math.max(0, Math.min(
                    remaining, source.removeSource(remaining, false)));
            remaining -= extracted;
        }
        return remaining == 0;
    }

    private int availableOperationSource(int cost) {
        return availableOperationSource(cost, operationSourceProviders());
    }

    private static int availableOperationSource(int cost, List<ISourceTile> sources) {
        int available = 0;
        for (ISourceTile source : sources) {
            int wanted = cost - Math.min(cost, available);
            if (wanted <= 0) break;
            available += Math.max(0, Math.min(wanted, source.removeSource(wanted, true)));
        }
        return available;
    }

    private List<ISourceTile> operationSourceProviders() {
        if (level == null) return List.of();
        LinkedHashSet<ISourceTile> result = new LinkedHashSet<>();
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(
                worldPosition, level, MatrixConfig.IMBUEMENT_SOURCE_INPUT_RANGE.get())) {
            ISourceTile source = provider.getSource();
            if (source != null && source != this && source.canProvideSource()) result.add(source);
        }
        return List.copyOf(result);
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
        playProcessingParticles();
        setChangedAndSyncClient();
    }

    private boolean canStoreBatchResult(BatchKind kind, int batchSize) {
        if (batchSize <= 0) {
            return true;
        }
        int produced = batchSize * kind.outputPerInput;
        if (outputMode == OutputMode.LOOSE) {
            return produced <= gemRoom();
        }
        int totalGems = gemOutput.getCount() + produced;
        return totalGems / 4 <= gemBlockRoom();
    }

    private void storeBatchResult(BatchKind kind, int batchSize) {
        int produced = batchSize * kind.outputPerInput;
        addGems(produced);
        if (outputMode == OutputMode.COMPACT) {
            compactBufferedGems();
        }
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

    private void pullBoundInput() {
        IItemHandler source = resolveHandler(inputContainer, inputFace);
        if (source == null || input.getCount() >= 64) {
            return;
        }
        ItemStack preferredInput = input;
        if (preferredInput.isEmpty()) {
            // Select a material for this internal batch before extracting. Storage
            // blocks (and Amplifiers) always win over loose Lapis or Shards,
            // regardless of the source inventory's slot order.
            int bestPriority = 0;
            for (int slot = 0; slot < source.getSlots(); slot++) {
                ItemStack candidate = source.getStackInSlot(slot);
                int priority = inputPriority(candidate);
                if (priority > bestPriority) {
                    preferredInput = candidate.copy();
                    bestPriority = priority;
                }
            }
            if (preferredInput.isEmpty()) {
                return;
            }
        }
        for (int slot = 0; slot < source.getSlots() && input.getCount() < 64; slot++) {
            ItemStack available = source.getStackInSlot(slot);
            if (!isBulkInput(available)
                    || !ItemStack.isSameItemSameComponents(preferredInput, available)
                    || !canMergeInput(available)) continue;
            int amount = Math.min(available.getCount(), 64 - input.getCount());
            ItemStack extracted = source.extractItem(slot, amount, false);
            if (extracted.isEmpty()) continue;
            if (input.isEmpty()) input = extracted;
            else input.grow(extracted.getCount());
            setChangedAndSyncClient();
        }
    }

    private void pushBoundOutputs() {
        IItemHandler target = resolveHandler(outputContainer, outputFace);
        if (target == null || (gemOutput.isEmpty() && gemBlockOutput.isEmpty())) return;
        // Compact mode keeps fewer than four loose gems inside the core until a
        // complete Source Gem Block can be formed.
        boolean changed = outputMode == OutputMode.LOOSE && pushStack(target, true);
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

    private void playProcessingParticles() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        serverLevel.sendParticles(
                net.minecraft.core.particles.ParticleTypes.ENCHANT,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.75D,
                worldPosition.getZ() + 0.5D,
                4,
                0.2D,
                0.2D,
                0.2D,
                0.01D
        );
    }

    private boolean canMergeInput(ItemStack stack) {
        return input.isEmpty() || ItemStack.isSameItemSameComponents(input, stack);
    }

    private static boolean isBulkInput(ItemStack stack) {
        return stack.is(Items.LAPIS_LAZULI)
                || stack.is(Items.AMETHYST_SHARD)
                || stack.is(Items.LAPIS_BLOCK)
                || stack.is(Items.AMETHYST_BLOCK)
                || stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                        "ars_arcane_matrix", "arcane_amplifier")));
    }

    private static int inputPriority(ItemStack stack) {
        if (stack.is(Items.LAPIS_BLOCK)
                || stack.is(Items.AMETHYST_BLOCK)
                || stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                        "ars_arcane_matrix", "arcane_amplifier")))) {
            return 2;
        }
        return stack.is(Items.LAPIS_LAZULI) || stack.is(Items.AMETHYST_SHARD) ? 1 : 0;
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        dropStack(input);
        dropStack(gemOutput);
        dropStack(gemBlockOutput);
        if (activeBatch != BatchKind.NONE && activeBatchSize > 0) {
            dropStack(new ItemStack(activeBatch.inputItem(), activeBatchSize));
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

    /** Actual paid cost while running, or the planned cost of the next possible batch. */
    public int getDisplayedBatchSourceCost() {
        if (activeBatch != BatchKind.NONE && activeBatchSize > 0) {
            return activeBatchSize * activeBatch.sourcePerInput;
        }
        BatchKind kind = BatchKind.from(input);
        if (kind == BatchKind.NONE) return 0;
        int maxBatch = Math.min(input.getCount(), MatrixConfig.IMBUEMENT_MAX_COMPRESSED_INPUTS.get());
        int storable = 0;
        for (int candidate = 1; candidate <= maxBatch; candidate++) {
            if (!canStoreBatchResult(kind, candidate)) break;
            storable = candidate;
        }
        return storable * kind.sourcePerInput;
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

    public boolean hasInputContainer() { return inputContainer != null; }
    public boolean hasOutputContainer() { return outputContainer != null; }

    private void setOperatingState(OperatingState state) {
        if (operatingState != state) {
            operatingState = state;
            setChangedAndSyncClient();
        }
    }

    private void refreshSourceLimits() {
        sourceStorage.setMaxSource(0);
        sourceStorage.setMaxReceive(0);
        sourceStorage.setMaxExtract(0);
        sourceStorage.setSource(0);
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
        return false;
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
        return 0;
    }

    @Override
    public int setSource(int source) {
        sourceStorage.setSource(0);
        return 0;
    }

    @Override
    public int addSource(int amount) {
        return 0;
    }

    @Override
    public int addSource(int amount, boolean simulate) {
        return 0;
    }

    @Override
    public int removeSource(int amount) {
        return 0;
    }

    @Override
    public int removeSource(int amount, boolean simulate) {
        return 0;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Input", input.saveOptional(registries));
        tag.put("Output", gemOutput.saveOptional(registries));
        tag.put("GemBlockOutput", gemBlockOutput.saveOptional(registries));
        tag.putString("OutputMode", outputMode.serializedName);
        tag.putString("ActiveBatch", activeBatch.serializedName);
        tag.putInt("ActiveBatchSize", activeBatchSize);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putInt("OperatingState", operatingState.ordinal());
        tag.putBoolean("RedstonePaused", redstonePaused);
        if (inputContainer != null) tag.put("InputContainer", saveGlobalPos(inputContainer));
        if (outputContainer != null) tag.put("OutputContainer", saveGlobalPos(outputContainer));
        if (inputFace != null) tag.putString("InputFace", inputFace.getSerializedName());
        if (outputFace != null) tag.putString("OutputFace", outputFace.getSerializedName());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        // Legacy builds stored up to one million Source here. The core is now a
        // direct-payment machine, so old cached values are intentionally discarded.
        refreshSourceLimits();
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
                : OperatingState.IDLE;
        redstonePaused = tag.getBoolean("RedstonePaused");
        inputContainer = tag.contains("InputContainer", Tag.TAG_COMPOUND)
                ? loadGlobalPos(tag.getCompound("InputContainer")) : null;
        outputContainer = tag.contains("OutputContainer", Tag.TAG_COMPOUND)
                ? loadGlobalPos(tag.getCompound("OutputContainer")) : null;
        inputFace = Direction.byName(tag.getString("InputFace"));
        outputFace = Direction.byName(tag.getString("OutputFace"));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    @Override
    public Result onFirstConnection(GlobalPos target, @Nullable Direction face,
                                    @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        outputContainer = target;
        outputFace = face;
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_imbuement_core.output_bound"));
        return Result.SUCCESS;
    }

    @Override
    public Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                   @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        inputContainer = target;
        inputFace = face;
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_imbuement_core.input_bound"));
        return Result.SUCCESS;
    }

    @Override
    public Result onClearConnections(Player player) {
        inputContainer = null;
        outputContainer = null;
        inputFace = null;
        outputFace = null;
        setChangedAndSyncClient();
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_imbuement_core.bindings_cleared"));
        return Result.SUCCESS;
    }

    private boolean isValidBinding(@Nullable GlobalPos target) {
        if (target == null || level == null || level.getServer() == null) return false;
        if (target.dimension().equals(level.dimension()) && target.pos().equals(worldPosition)) return false;
        return resolveHandler(target, null) != null;
    }

    @Nullable
    private IItemHandler resolveHandler(@Nullable GlobalPos target, @Nullable Direction preferredFace) {
        if (target == null || level == null || level.getServer() == null) return null;
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())) return null;
        if (preferredFace != null) {
            IItemHandler preferred = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, target.pos(), preferredFace);
            if (preferred != null) return preferred;
        }
        IItemHandler unsided = targetLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, target.pos(), null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            if (direction == preferredFace) continue;
            IItemHandler sided = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, target.pos(), direction);
            if (sided != null) return sided;
        }
        return null;
    }

    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }

    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (id == null) id = Level.OVERWORLD.location();
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, id),
                BlockPos.of(tag.getLong("Pos")));
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
        LAPIS_ITEM("lapis_item", Items.LAPIS_LAZULI, 500, 1, SOURCE_GEM_ID),
        AMETHYST_SHARD("amethyst_shard", Items.AMETHYST_SHARD, 500, 1, SOURCE_GEM_ID),
        LAPIS("lapis", Items.LAPIS_BLOCK, 4_500, 9, SOURCE_GEM_ID),
        AMETHYST("amethyst", Items.AMETHYST_BLOCK, 2_000, 4, SOURCE_GEM_ID),
        AMPLIFIER("amplifier", Items.AIR, 2_000, 16, SOURCE_GEM_ID);

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

        private Item inputItem() {
            return this == AMPLIFIER
                    ? BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                            "ars_arcane_matrix", "arcane_amplifier"))
                    : inputItem;
        }

        private static BatchKind from(ItemStack stack) {
            if (stack.is(Items.LAPIS_LAZULI)) {
                return LAPIS_ITEM;
            }
            if (stack.is(Items.AMETHYST_SHARD)) {
                return AMETHYST_SHARD;
            }
            if (stack.is(Items.LAPIS_BLOCK)) {
                return LAPIS;
            }
            if (stack.is(Items.AMETHYST_BLOCK)) {
                return AMETHYST;
            }
            if (stack.is(BuiltInRegistries.ITEM.get(ResourceLocation.fromNamespaceAndPath(
                    "ars_arcane_matrix", "arcane_amplifier")))) {
                return AMPLIFIER;
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

}
