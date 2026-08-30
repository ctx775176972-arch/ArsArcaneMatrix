package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.block.ArcaneCrusherCoreBlock;
import dev.arsmatrix.data.CrusherRecipeResolver;
import dev.arsmatrix.data.CrusherRecipeRule;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.util.MixedItemBuffer;
import dev.arsmatrix.util.StructureInventoryAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Essence-driven automatic crusher with common-tag ore compatibility. */
public final class ArcaneCrusherCoreBlockEntity extends BlockEntity implements IWandable {
    public static final int MAX_BATCH = 16;
    public static final int CYCLE_TICKS = 100;
    private static final int OUTPUT_SLOTS = 36;
    private static final int WATER_ROLL_ITEMS = 16;
    private static final int WATER_PITY_ROLLS = 8;
    private static final ResourceLocation AIR_ESSENCE = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "air_essence");
    private static final ResourceLocation WATER_ESSENCE = ResourceLocation.fromNamespaceAndPath("ars_nouveau", "water_essence");
    private static final TagKey<Block> FRAME = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_crusher_frame_blocks"));

    private final List<ItemStack> outputs = new ArrayList<>();
    private final MixedItemBuffer inputs = new MixedItemBuffer(MAX_BATCH);
    private GlobalPos inputContainer;
    private GlobalPos outputContainer;
    private Direction inputFace;
    private Direction outputFace;
    private int progressTicks;
    private int waterWork;
    private int waterPity;
    private int tickCounter;
    private boolean structureFormed;
    private OperatingState state = OperatingState.UNFORMED;
    private Mode mode = Mode.NONE;

    public ArcaneCrusherCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_CRUSHER_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        if (tickCounter % 20 == 1) structureFormed = isStructureFormed(serverLevel, worldPosition, facing());
        pullBoundInputs();
        pushBoundOutputs();
        if (!structureFormed) { progressTicks = 0; setState(OperatingState.UNFORMED); return; }
        if (level.hasNeighborSignal(worldPosition)) { setState(OperatingState.REDSTONE_PAUSED); return; }

        Optional<CrusherRecipeRule> recipe = CrusherRecipeResolver.find(inputs.first());
        if (inputs.isEmpty()) { progressTicks = 0; setState(OperatingState.NO_INPUT); return; }
        if (recipe.isEmpty()) { progressTicks = 0; setState(OperatingState.INVALID_INPUT); return; }

        IItemHandler essenceContainer = essenceContainer();
        int essenceSlot = StructureInventoryAccess.firstSlot(essenceContainer,
                stack -> modeOf(stack) != Mode.NONE);
        ItemStack catalyst = essenceSlot < 0 ? ItemStack.EMPTY : essenceContainer.getStackInSlot(essenceSlot);
        mode = modeOf(catalyst);
        if (mode == Mode.NONE) {
            progressTicks = 0;
            setState(essenceContainer == null || !StructureInventoryAccess.hasAnyItem(essenceContainer)
                    ? OperatingState.NO_ESSENCE : OperatingState.INVALID_ESSENCE);
            return;
        }
        ItemStack oneOutput = mode == Mode.AIR ? recipe.get().airOutput() : recipe.get().baseOutput();
        if (!canStore(oneOutput)) { setState(OperatingState.OUTPUT_BLOCKED); return; }

        progressTicks++;
        setState(mode == Mode.AIR ? OperatingState.AIR_RUNNING : OperatingState.WATER_RUNNING);
        if (progressTicks >= CYCLE_TICKS) {
            processBatch(serverLevel, essenceContainer, essenceSlot, mode);
            progressTicks = 0;
        } else if (tickCounter % 20 == 0) sync();
    }

    private void processBatch(ServerLevel serverLevel, @Nullable IItemHandler container, int essenceSlot, Mode activeMode) {
        if (container == null || essenceSlot < 0) return;
        ItemStack consumed = container.extractItem(essenceSlot, 1, false);
        if (consumed.isEmpty() || modeOf(consumed) != activeMode) {
            if (!consumed.isEmpty()) ItemHandlerHelper.insertItemStacked(container, consumed, false);
            return;
        }
        int available = Math.min(MAX_BATCH, inputs.count());
        int completed = 0;
        int waterProgress = 0;
        for (int index = 0; index < available; index++) {
            ItemStack input = inputs.first().copyWithCount(1);
            Optional<CrusherRecipeRule> recipe = CrusherRecipeResolver.find(input);
            if (recipe.isEmpty()) break;
            ItemStack result = activeMode == Mode.AIR ? recipe.get().airOutput() : recipe.get().baseOutput();
            if (!canStore(result)) break;
            storeOutput(result);
            inputs.removeOne();
            completed++;
            if (activeMode == Mode.WATER) {
                waterProgress += input.is(Items.ANCIENT_DEBRIS) ? WATER_ROLL_ITEMS : 1;
            }
        }
        if (completed <= 0) {
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(container, consumed, false);
            if (!leftover.isEmpty()) storeOutput(leftover);
        }
        if (activeMode == Mode.WATER && waterProgress > 0) advanceWaterRolls(serverLevel, waterProgress);
        sync();
    }

    private void advanceWaterRolls(ServerLevel level, int processed) {
        waterWork += processed;
        while (waterWork >= WATER_ROLL_ITEMS) {
            waterWork -= WATER_ROLL_ITEMS;
            waterPity++;
            if (waterPity >= WATER_PITY_ROLLS || level.random.nextFloat() < 0.125F) {
                ItemStack crystal = new ItemStack(ModItems.ENRICHED_MINERAL_CRYSTAL.get());
                if (!canStore(crystal)) {
                    waterWork += WATER_ROLL_ITEMS;
                    waterPity = Math.max(waterPity, WATER_PITY_ROLLS);
                    return;
                }
                storeOutput(crystal);
                waterPity = 0;
            }
        }
    }

    private void pullBoundInputs() {
        IItemHandler source = resolveHandler(inputContainer, inputFace);
        if (source == null) return;
        boolean changed = false;
        int selectedPriority = inputs.isEmpty()
                ? highestCrushingPriority(source)
                : crushingInputPriority(inputs.first());
        if (selectedPriority <= 0) return;
        // Finish the raw-mineral tier completely before admitting ore blocks.
        for (int slot = 0; slot < source.getSlots() && inputs.remaining() > 0; slot++) {
            ItemStack available = source.getStackInSlot(slot);
            if (crushingInputPriority(available) != selectedPriority
                    || CrusherRecipeResolver.find(available).isEmpty()) continue;
            ItemStack extracted = source.extractItem(slot,
                    Math.min(available.getCount(), inputs.remaining()), false);
            if (extracted.isEmpty()) continue;
            inputs.insert(extracted);
            changed = true;
        }
        if (changed) sync();
    }

    private static int highestCrushingPriority(IItemHandler source) {
        int highest = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (CrusherRecipeResolver.find(stack).isPresent()) {
                highest = Math.max(highest, crushingInputPriority(stack));
            }
        }
        return highest;
    }

    private static int crushingInputPriority(ItemStack stack) {
        boolean raw = stack.getTags().map(TagKey::location).anyMatch(id ->
                id.getNamespace().equals("c") && id.getPath().startsWith("raw_materials/"));
        return raw ? 2 : CrusherRecipeResolver.find(stack).isPresent() ? 1 : 0;
    }

    private void pushBoundOutputs() {
        IItemHandler target = resolveHandler(outputContainer, outputFace);
        if (target == null || outputs.isEmpty()) return;
        boolean changed = false;
        for (int index = 0; index < outputs.size();) {
            ItemStack original = outputs.get(index);
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, original.copy(), false);
            if (remainder.getCount() != original.getCount()) changed = true;
            if (remainder.isEmpty()) outputs.remove(index);
            else { outputs.set(index, remainder); index++; }
        }
        if (changed) sync();
    }

    @Nullable private IItemHandler resolveHandler(@Nullable GlobalPos target, @Nullable Direction preferredFace) {
        if (target == null || level == null || level.getServer() == null) return null;
        ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())) return null;
        if (preferredFace != null) {
            IItemHandler preferred = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), preferredFace);
            if (preferred != null) return preferred;
        }
        IItemHandler unsided = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            IItemHandler sided = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), direction);
            if (sided != null) return sided;
        }
        return null;
    }

    @Nullable private IItemHandler essenceContainer() {
        return StructureInventoryAccess.at(level, consumableContainerPosition(worldPosition, facing()));
    }

    private static Mode modeOf(ItemStack stack) {
        if (stack.isEmpty()) return Mode.NONE;
        ResourceLocation id = net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(stack.getItem());
        if (id.equals(AIR_ESSENCE)) return Mode.AIR;
        if (id.equals(WATER_ESSENCE)) return Mode.WATER;
        return Mode.NONE;
    }

    public static boolean isStructureFormed(Level level, BlockPos core, Direction facing) {
        for (BlockPos pos : framePositions(core, facing)) if (!level.getBlockState(pos).is(FRAME)) return false;
        return StructureInventoryAccess.at(level, consumableContainerPosition(core, facing)) != null;
    }

    public static BlockPos consumableContainerPosition(BlockPos core, Direction facing) {
        return core.below();
    }

    public static List<BlockPos> framePositions(BlockPos core, Direction facing) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        List<BlockPos> result = new ArrayList<>();
        // The core sits at the front midpoint of the rim, directly above the
        // Essence container, so every interaction remains reachable from ground level.
        for (int depth = 1; depth <= 3; depth++) {
            for (int x = -1; x <= 1; x++) {
                result.add(core.relative(right, x).relative(back, depth).below());
                result.add(core.relative(right, x).relative(back, depth).above(2));
            }
        }
        for (int depth = 0; depth <= 4; depth++) {
            for (int x = -2; x <= 2; x++) {
                if (Math.abs(x) == 2 || depth == 0 || depth == 4) {
                    if (x == 0 && depth == 0) continue;
                    result.add(core.relative(right, x).relative(back, depth));
                }
            }
        }
        return List.copyOf(result);
    }

    private Direction facing() { return getBlockState().getValue(ArcaneCrusherCoreBlock.FACING); }

    private boolean canStore(ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        int remaining = incoming.getCount();
        int freeSlots = OUTPUT_SLOTS - outputs.size();
        for (ItemStack existing : outputs) if (ItemStack.isSameItemSameComponents(existing, incoming)) {
            remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
            if (remaining <= 0) return true;
        }
        return remaining <= freeSlots * incoming.getMaxStackSize();
    }

    private void storeOutput(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : outputs) {
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(moved); remaining.shrink(moved);
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty() && outputs.size() < OUTPUT_SLOTS) {
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            outputs.add(remaining.copyWithCount(amount)); remaining.shrink(amount);
        }
    }

    private void setState(OperatingState next) { if (state != next) { state = next; sync(); } }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }

    public OperatingState getState() { return state; }
    public Mode getMode() { return mode; }
    public int getInputCount() { return inputs.count(); }
    public int getProgressTicks() { return progressTicks; }
    public int getWaterWork() { return waterWork; }
    public int getWaterPity() { return waterPity; }
    public int getBufferedItemCount() { return outputs.stream().mapToInt(ItemStack::getCount).sum(); }
    public boolean hasInputContainer() { return inputContainer != null; }
    public boolean hasOutputContainer() { return outputContainer != null; }
    public boolean hasConsumableContainer() {
        return StructureInventoryAccess.at(level, consumableContainerPosition(worldPosition, facing())) != null;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        inputs.stacks().forEach(stack -> Containers.dropItemStack(
                level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack));
        outputs.forEach(stack -> Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack));
        inputs.clear(); outputs.clear();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag inputList = new ListTag();
        inputs.stacks().forEach(stack -> inputList.add(stack.saveOptional(registries)));
        tag.put("Inputs", inputList);
        tag.putInt("Progress", progressTicks); tag.putInt("WaterWork", waterWork); tag.putInt("WaterPity", waterPity);
        tag.putString("OperatingState", state.name()); tag.putString("Mode", mode.name());
        if (inputContainer != null) tag.put("InputContainer", saveGlobalPos(inputContainer));
        if (outputContainer != null) tag.put("OutputContainer", saveGlobalPos(outputContainer));
        if (inputFace != null) tag.putString("InputFace", inputFace.getSerializedName());
        if (outputFace != null) tag.putString("OutputFace", outputFace.getSerializedName());
        ListTag list = new ListTag(); outputs.forEach(stack -> list.add(stack.saveOptional(registries))); tag.put("Outputs", list);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inputs.clear();
        ListTag inputList = tag.getList("Inputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputList.size() && inputs.remaining() > 0; i++) {
            inputs.insert(ItemStack.parseOptional(registries, inputList.getCompound(i)));
        }
        if (inputs.isEmpty() && tag.contains("Input", Tag.TAG_COMPOUND)) {
            inputs.insert(ItemStack.parseOptional(registries, tag.getCompound("Input")));
        }
        progressTicks = tag.getInt("Progress"); waterWork = Math.max(0, tag.getInt("WaterWork")); waterPity = Math.max(0, tag.getInt("WaterPity"));
        try { state = OperatingState.valueOf(tag.getString("OperatingState")); } catch (IllegalArgumentException ignored) { state = OperatingState.UNFORMED; }
        try { mode = Mode.valueOf(tag.getString("Mode")); } catch (IllegalArgumentException ignored) { mode = Mode.NONE; }
        inputContainer = tag.contains("InputContainer") ? loadGlobalPos(tag.getCompound("InputContainer")) : null;
        outputContainer = tag.contains("OutputContainer") ? loadGlobalPos(tag.getCompound("OutputContainer")) : null;
        inputFace = Direction.byName(tag.getString("InputFace")); outputFace = Direction.byName(tag.getString("OutputFace"));
        outputs.clear(); ListTag list = tag.getList("Outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && outputs.size() < OUTPUT_SLOTS; i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i)); if (!stack.isEmpty()) outputs.add(stack);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Result onFirstConnection(GlobalPos target, @Nullable Direction face, @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        outputContainer = target; outputFace = face; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_crusher.output_bound"));
        return Result.SUCCESS;
    }
    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face, @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        inputContainer = target; inputFace = face; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_crusher.input_bound"));
        return Result.SUCCESS;
    }
    @Override public Result onClearConnections(Player player) {
        inputContainer = null; outputContainer = null; inputFace = null; outputFace = null; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_crusher.bindings_cleared"));
        return Result.SUCCESS;
    }
    private boolean isValidBinding(@Nullable GlobalPos target) {
        return target != null && level != null && level.getServer() != null
                && !(target.dimension().equals(level.dimension()) && target.pos().equals(worldPosition))
                && resolveHandler(target, null) != null;
    }
    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag(); tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong()); return tag;
    }
    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (id == null) id = Level.OVERWORLD.location();
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong("Pos")));
    }

    public enum Mode { NONE, AIR, WATER }
    public enum OperatingState {
        UNFORMED("message.ars_arcane_matrix.state.unformed"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        NO_INPUT("message.ars_arcane_matrix.arcane_crusher.no_input"),
        INVALID_INPUT("message.ars_arcane_matrix.arcane_crusher.invalid_input"),
        NO_ESSENCE("message.ars_arcane_matrix.arcane_crusher.no_essence"),
        INVALID_ESSENCE("message.ars_arcane_matrix.arcane_crusher.invalid_essence"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.state.output_blocked"),
        AIR_RUNNING("message.ars_arcane_matrix.arcane_crusher.air"),
        WATER_RUNNING("message.ars_arcane_matrix.arcane_crusher.water");
        private final String key; OperatingState(String key) { this.key = key; }
        public String translationKey() { return key; }
    }
}
