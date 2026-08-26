package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.data.SourceStoneGeneratorRecipeManager;
import dev.arsmatrix.data.SourceStoneGeneratorRule;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * A GUI-free bulk block generator. Pedestals select an exact data-driven recipe;
 * passive imbuement progress keeps it useful without Source, while four cardinal
 * Arcane Amplifiers raise its Source throughput to one default batch per second.
 */
public final class SourceStoneGeneratorBlockEntity extends BlockEntity {

    private static final int OUTPUT_SLOTS = 9;
    private static final int CONTEXT_INTERVAL = 5;
    private static final Direction[] HORIZONTAL_DIRECTIONS = {
            Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST
    };

    private final List<ItemStack> bufferedOutputs = new ArrayList<>();
    private final IItemHandler outputHandler = new OutputHandler();

    private SourceStoneGeneratorRule activeRecipe = SourceStoneGeneratorRecipeManager.defaultRule();
    private int progress;
    private int passiveRemainder;
    private int sourceRateRemainder;
    private int passiveProgressSinceSourcePull;
    private int tickCounter;
    private int amplifierCount;
    private int pedestalItemCount;
    private int sourceAcceleratedTicks;
    private int progressGainWindow;
    private int currentEfficiency;
    private OperatingState operatingState = OperatingState.SOURCE_MISSING;

    public SourceStoneGeneratorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SOURCE_STONE_GENERATOR.get(), pos, state);
    }

    public void serverTick() {
        Level currentLevel = level;
        if (currentLevel == null || currentLevel.isClientSide) {
            return;
        }
        tickCounter++;
        if (tickCounter % 20 == 1) {
            currentEfficiency = progressGainWindow;
            progressGainWindow = 0;
            if (tickCounter > 1) {
                setChangedAndSyncClient();
            }
        }
        pushOutputDown();

        if (tickCounter % CONTEXT_INTERVAL == 1) {
            refreshContext();
        }
        if (currentLevel.hasNeighborSignal(worldPosition)) {
            setOperatingState(OperatingState.REDSTONE_PAUSED);
            return;
        }

        ItemStack output = activeRecipe.createOutput();
        if (output.isEmpty() || !canStore(output)) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }

        addPassiveProgress();
        if (tickCounter % CONTEXT_INTERVAL == 0 && progress < activeRecipe.processingCost()) {
            addSourceProgress();
        }
        if (sourceAcceleratedTicks > 0) {
            sourceAcceleratedTicks--;
        }
        setOperatingState(sourceAcceleratedTicks > 0
                ? OperatingState.NORMAL_RUNNING
                : OperatingState.SOURCE_MISSING);

        if (progress >= activeRecipe.processingCost()) {
            // Recheck pedestals at commit time so removing a catalyst cannot finish an old recipe.
            SourceStoneGeneratorRule current = SourceStoneGeneratorRecipeManager.findMatch(scanPedestals());
            if (!current.id().equals(activeRecipe.id())) {
                switchRecipe(current);
                return;
            }
            if (!canStore(output)) {
                setOperatingState(OperatingState.OUTPUT_BLOCKED);
                return;
            }
            storeOutput(output);
            progress = 0;
            passiveRemainder = 0;
            passiveProgressSinceSourcePull = 0;
            playCompletionEffect();
            setChangedAndSyncClient();
        } else if (tickCounter % 20 == 0) {
            setChangedAndSyncClient();
        }
    }

    private void refreshContext() {
        int newAmplifierCount = countCardinalAmplifiers();
        if (newAmplifierCount != amplifierCount) {
            amplifierCount = newAmplifierCount;
            sourceRateRemainder = 0;
        }
        List<ItemStack> pedestalStacks = scanPedestals();
        pedestalItemCount = pedestalStacks.stream().mapToInt(ItemStack::getCount).sum();
        switchRecipe(SourceStoneGeneratorRecipeManager.findMatch(pedestalStacks));
    }

    private void switchRecipe(SourceStoneGeneratorRule recipe) {
        if (activeRecipe.id().equals(recipe.id())) {
            return;
        }
        activeRecipe = recipe;
        progress = 0;
        passiveRemainder = 0;
        sourceRateRemainder = 0;
        passiveProgressSinceSourcePull = 0;
        sourceAcceleratedTicks = 0;
        progressGainWindow = 0;
        currentEfficiency = 0;
        setChangedAndSyncClient();
    }

    private List<ItemStack> scanPedestals() {
        if (level == null) {
            return List.of();
        }
        List<ItemStack> stacks = new ArrayList<>();
        for (int x = -1; x <= 1; x++) {
            for (int y = -1; y <= 1; y++) {
                for (int z = -1; z <= 1; z++) {
                    if (x == 0 && y == 0 && z == 0) {
                        continue;
                    }
                    BlockPos candidate = worldPosition.offset(x, y, z);
                    if (!level.hasChunkAt(candidate)
                            || !(level.getBlockEntity(candidate) instanceof ArcanePedestalTile pedestal)) {
                        continue;
                    }
                    ItemStack stack = pedestal.getStack();
                    if (!stack.isEmpty()) {
                        stacks.add(stack.copy());
                    }
                }
            }
        }
        return List.copyOf(stacks);
    }

    private int countCardinalAmplifiers() {
        if (level == null) {
            return 0;
        }
        int count = 0;
        for (Direction direction : HORIZONTAL_DIRECTIONS) {
            BlockPos candidate = worldPosition.relative(direction);
            if (level.hasChunkAt(candidate)
                    && level.getBlockState(candidate).is(ModBlocks.ARCANE_AMPLIFIER.get())) {
                count++;
            }
        }
        return count;
    }

    private void addPassiveProgress() {
        long accumulated = (long) passiveRemainder
                + MatrixConfig.GENERATOR_PASSIVE_PROGRESS_PER_SECOND.get();
        int gained = (int) (accumulated / 20L);
        passiveRemainder = (int) (accumulated % 20L);
        int accepted = Math.min(gained, activeRecipe.processingCost() - progress);
        progress += accepted;
        passiveProgressSinceSourcePull += accepted;
        progressGainWindow += accepted;
    }

    private void addSourceProgress() {
        int targetTicks = getPoweredDurationSeconds() * 20;
        long accumulated = (long) sourceRateRemainder
                + (long) activeRecipe.processingCost() * CONTEXT_INTERVAL;
        int totalProgressAllowance = (int) (accumulated / targetTicks);
        sourceRateRemainder = (int) (accumulated % targetTicks);
        int sourceAllowance = Math.max(0, totalProgressAllowance - passiveProgressSinceSourcePull);
        passiveProgressSinceSourcePull = 0;
        int requested = Math.min(sourceAllowance, activeRecipe.processingCost() - progress);
        if (requested <= 0) {
            return;
        }
        int extracted = pullNearbySource(requested);
        if (extracted <= 0) {
            return;
        }
        progress = Math.min(activeRecipe.processingCost(), progress + extracted);
        progressGainWindow += extracted;
        sourceAcceleratedTicks = CONTEXT_INTERVAL + 1;
        playSourceParticles(extracted);
    }

    private int pullNearbySource(int requested) {
        if (level == null || requested <= 0) {
            return 0;
        }
        int remaining = requested;
        for (ISpecialSourceProvider provider : SourceUtil.canTakeSource(
                worldPosition,
                level,
                MatrixConfig.GENERATOR_SOURCE_INPUT_RANGE.get()
        )) {
            if (remaining <= 0) {
                break;
            }
            ISourceTile source = provider.getSource();
            if (source == null || !source.canProvideSource()) {
                continue;
            }
            int available = Math.max(0, Math.min(remaining, source.removeSource(remaining, true)));
            if (available <= 0) {
                continue;
            }
            int extracted = Math.max(0, Math.min(available, source.removeSource(available, false)));
            remaining -= extracted;
        }
        return requested - remaining;
    }

    private boolean canStore(ItemStack incoming) {
        int remaining = incoming.getCount();
        for (ItemStack existing : bufferedOutputs) {
            if (!ItemStack.isSameItemSameComponents(existing, incoming)) {
                continue;
            }
            remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (remaining <= 0) {
                return true;
            }
        }
        int emptySlots = OUTPUT_SLOTS - bufferedOutputs.size();
        return remaining <= (long) emptySlots * incoming.getMaxStackSize();
    }

    private void storeOutput(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : bufferedOutputs) {
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) {
                continue;
            }
            int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(moved);
            remaining.shrink(moved);
            if (remaining.isEmpty()) {
                return;
            }
        }
        while (!remaining.isEmpty() && bufferedOutputs.size() < OUTPUT_SLOTS) {
            int count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            bufferedOutputs.add(remaining.copyWithCount(count));
            remaining.shrink(count);
        }
    }

    private void pushOutputDown() {
        if (level == null || bufferedOutputs.isEmpty() || !level.hasChunkAt(worldPosition.below())) {
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
        boolean changed = false;
        for (int index = 0; index < bufferedOutputs.size();) {
            ItemStack stack = bufferedOutputs.get(index);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, stack.copy(), false);
            if (remainder.getCount() != stack.getCount()) {
                changed = true;
            }
            if (remainder.isEmpty()) {
                bufferedOutputs.remove(index);
            } else {
                bufferedOutputs.set(index, remainder);
                index++;
            }
        }
        if (changed) {
            setChangedAndSyncClient();
        }
    }

    private void playSourceParticles(int extracted) {
        if (level instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(
                    ParticleTypes.ENCHANT,
                    worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.7D,
                    worldPosition.getZ() + 0.5D,
                    Math.min(8, Math.max(1, extracted / 10)),
                    0.35D, 0.25D, 0.35D,
                    0.02D
            );
        }
    }

    private void playCompletionEffect() {
        if (!(level instanceof ServerLevel serverLevel)) {
            return;
        }
        serverLevel.sendParticles(
                ParticleTypes.HAPPY_VILLAGER,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.65D,
                worldPosition.getZ() + 0.5D,
                8,
                0.3D, 0.2D, 0.3D,
                0.03D
        );
        serverLevel.playSound(
                null,
                worldPosition,
                SoundEvents.AMETHYST_BLOCK_RESONATE,
                SoundSource.BLOCKS,
                0.35F,
                1.15F
        );
    }

    private void setOperatingState(OperatingState state) {
        if (operatingState != state) {
            operatingState = state;
            setChangedAndSyncClient();
        }
    }

    private void setChangedAndSyncClient() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public IItemHandler getItemHandler() {
        return outputHandler;
    }

    public OperatingState getOperatingState() {
        return operatingState;
    }

    public int getProgress() {
        return progress;
    }

    public int getProcessingCost() {
        return activeRecipe.processingCost();
    }

    public ItemStack getCurrentOutput() {
        return activeRecipe.createOutput();
    }

    public int getBufferedItemCount() {
        return bufferedOutputs.stream().mapToInt(ItemStack::getCount).sum();
    }

    public int getAmplifierCount() {
        return amplifierCount;
    }

    public int getPedestalItemCount() {
        return pedestalItemCount;
    }

    public int getPoweredDurationSeconds() {
        return MatrixConfig.generatorPoweredDurationSeconds(amplifierCount);
    }

    public int getPassiveProgressPerSecond() {
        return MatrixConfig.GENERATOR_PASSIVE_PROGRESS_PER_SECOND.get();
    }

    public int getCurrentEfficiency() {
        return currentEfficiency;
    }

    public Component getOutputDescription() {
        ItemStack output = getCurrentOutput();
        return output.isEmpty() ? Component.literal("?") : output.getHoverName();
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        bufferedOutputs.forEach(stack -> Containers.dropItemStack(
                level,
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D,
                stack.copy()
        ));
        bufferedOutputs.clear();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putString("ActiveRecipe", activeRecipe.id().toString());
        tag.put("CurrentOutput", activeRecipe.createOutput().saveOptional(registries));
        tag.putInt("CurrentProcessingCost", activeRecipe.processingCost());
        tag.putInt("Progress", progress);
        tag.putInt("PassiveRemainder", passiveRemainder);
        tag.putInt("SourceRateRemainder", sourceRateRemainder);
        tag.putInt("PassiveSinceSourcePull", passiveProgressSinceSourcePull);
        tag.putInt("Amplifiers", amplifierCount);
        tag.putInt("PedestalItems", pedestalItemCount);
        tag.putInt("CurrentEfficiency", currentEfficiency);
        tag.putString("OperatingState", operatingState.name());
        ListTag output = new ListTag();
        bufferedOutputs.forEach(stack -> output.add(stack.saveOptional(registries)));
        tag.put("BufferedOutputs", output);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ResourceLocation recipeId = ResourceLocation.tryParse(tag.getString("ActiveRecipe"));
        ItemStack savedOutput = ItemStack.parseOptional(registries, tag.getCompound("CurrentOutput"));
        if (recipeId != null && !savedOutput.isEmpty()) {
            // The explicit snapshot keeps Jade correct when a server data pack overrides a bundled recipe.
            activeRecipe = new SourceStoneGeneratorRule(
                    recipeId,
                    List.of(),
                    BuiltInRegistries.ITEM.getKey(savedOutput.getItem()),
                    savedOutput.getCount(),
                    Math.max(1, tag.getInt("CurrentProcessingCost")),
                    true
            );
        } else {
            activeRecipe = recipeId == null
                    ? SourceStoneGeneratorRecipeManager.defaultRule()
                    : SourceStoneGeneratorRecipeManager.find(recipeId)
                            .orElseGet(SourceStoneGeneratorRecipeManager::defaultRule);
        }
        progress = Math.max(0, Math.min(tag.getInt("Progress"), activeRecipe.processingCost()));
        passiveRemainder = Math.max(0, tag.getInt("PassiveRemainder"));
        sourceRateRemainder = Math.max(0, tag.getInt("SourceRateRemainder"));
        passiveProgressSinceSourcePull = Math.max(0, tag.getInt("PassiveSinceSourcePull"));
        amplifierCount = Math.max(0, Math.min(4, tag.getInt("Amplifiers")));
        pedestalItemCount = Math.max(0, tag.getInt("PedestalItems"));
        currentEfficiency = Math.max(0, tag.getInt("CurrentEfficiency"));
        try {
            operatingState = OperatingState.valueOf(tag.getString("OperatingState"));
        } catch (IllegalArgumentException ignored) {
            operatingState = OperatingState.SOURCE_MISSING;
        }
        bufferedOutputs.clear();
        ListTag output = tag.getList("BufferedOutputs", Tag.TAG_COMPOUND);
        for (int index = 0; index < output.size() && bufferedOutputs.size() < OUTPUT_SLOTS; index++) {
            ItemStack stack = ItemStack.parseOptional(registries, output.getCompound(index));
            if (!stack.isEmpty()) {
                bufferedOutputs.add(stack);
            }
        }
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
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.state.output_blocked"),
        SOURCE_MISSING("message.ars_arcane_matrix.source_stone_generator.source_missing"),
        NORMAL_RUNNING("message.ars_arcane_matrix.source_stone_generator.normal_running");

        private final String translationKey;

        OperatingState(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    private final class OutputHandler implements IItemHandler {
        @Override
        public int getSlots() {
            return OUTPUT_SLOTS;
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < bufferedOutputs.size()
                    ? bufferedOutputs.get(slot)
                    : ItemStack.EMPTY;
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
                    bufferedOutputs.remove(slot);
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
