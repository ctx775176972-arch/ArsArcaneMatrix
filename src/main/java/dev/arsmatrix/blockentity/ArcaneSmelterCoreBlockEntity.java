package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.block.ArcaneSmelterCoreBlock;
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
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
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

/**
 * High-throughput mineral furnace. It accepts ore, raw material, and dust smelting
 * recipes, consumes real vanilla fuel values, and uses enchanted archwood charcoal
 * to roll the Casting Crystal required by the Crusher stage.
 */
public final class ArcaneSmelterCoreBlockEntity extends BlockEntity implements IWandable {
    public static final int MAX_BATCH = 64;
    public static final int CYCLE_TICKS = 100;
    private static final int FUEL_PER_ITEM = 200;
    private static final int OUTPUT_SLOTS = 72;
    private static final int SPECIAL_ROLL_ITEMS = 16;
    private static final int SPECIAL_PITY_ROLLS = 8;
    private static final TagKey<Item> ORES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Item> RAW_MATERIALS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "raw_materials"));
    private static final TagKey<Item> DUSTS = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "dusts"));
    private static final TagKey<Block> FRAME = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_smelter_frame_blocks"));

    private final List<ItemStack> outputs = new ArrayList<>();
    private final MixedItemBuffer inputs = new MixedItemBuffer(MAX_BATCH);
    private GlobalPos inputContainer;
    private GlobalPos outputContainer;
    private Direction inputFace;
    private Direction outputFace;
    private int progressTicks;
    private int fuelUnits;
    private int specialWork;
    private int specialPity;
    private int tickCounter;
    private boolean specialMode;
    private boolean structureFormed;
    private OperatingState state = OperatingState.UNFORMED;

    public ArcaneSmelterCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_SMELTER_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        if (tickCounter % 20 == 1) structureFormed = isStructureFormed(serverLevel, worldPosition, facing());
        pullBoundInputs(serverLevel);
        pushBoundOutputs();
        if (!structureFormed) { progressTicks = 0; setState(OperatingState.UNFORMED); setLit(false); return; }
        if (level.hasNeighborSignal(worldPosition)) { setState(OperatingState.REDSTONE_PAUSED); setLit(false); return; }

        Optional<RecipeHolder<SmeltingRecipe>> recipe = findRecipe(serverLevel, inputs.first());
        if (inputs.isEmpty()) { progressTicks = 0; setState(OperatingState.NO_INPUT); setLit(false); return; }
        if (recipe.isEmpty()) { progressTicks = 0; setState(OperatingState.INVALID_INPUT); setLit(false); return; }

        IItemHandler fuelContainer = fuelContainer();
        int enchantedSlot = StructureInventoryAccess.firstSlot(fuelContainer, ArcaneSmelterCoreBlockEntity::isEnchantedFuel);
        if (enchantedSlot >= 0 && !specialMode) {
            fuelUnits = 0;
            consumeFuel(fuelContainer, enchantedSlot);
        }
        int requestedFuel = Math.min(MAX_BATCH, inputs.count()) * FUEL_PER_ITEM;
        fillFuelForBatch(fuelContainer, requestedFuel);
        if (fuelUnits < FUEL_PER_ITEM) {
            progressTicks = 0;
            setState(fuelContainer == null || !StructureInventoryAccess.hasAnyItem(fuelContainer)
                    ? OperatingState.NO_FUEL : OperatingState.INVALID_FUEL);
            setLit(false);
            return;
        }

        ItemStack result = recipe.get().value().getResultItem(serverLevel.registryAccess());
        if (!canStore(result)) { setState(OperatingState.OUTPUT_BLOCKED); setLit(false); return; }
        progressTicks++;
        setState(specialMode ? OperatingState.ENCHANTED_RUNNING : OperatingState.NORMAL_RUNNING);
        setLit(true);
        if (progressTicks >= CYCLE_TICKS) {
            processBatch(serverLevel);
            progressTicks = 0;
        } else if (tickCounter % 20 == 0) sync();
    }

    private void fillFuelForBatch(@Nullable IItemHandler container, int requestedFuel) {
        while (container != null && fuelUnits < requestedFuel) {
            int slot = StructureInventoryAccess.firstSlot(container, stack ->
                    stack.getBurnTime(RecipeType.SMELTING) > 0 && (!specialMode || isEnchantedFuel(stack)));
            // Finish the remaining enchanted work before accepting ordinary fuel.
            if (slot < 0 || !consumeFuel(container, slot)) return;
        }
    }

    private boolean consumeFuel(@Nullable IItemHandler container, int slot) {
        if (container == null || slot < 0) return false;
        ItemStack offered = container.getStackInSlot(slot);
        int burn = offered.getBurnTime(RecipeType.SMELTING);
        if (burn <= 0) return false;
        ItemStack consumed = container.extractItem(slot, 1, false);
        if (consumed.isEmpty()) return false;
        specialMode = isEnchantedFuel(consumed);
        fuelUnits = Math.min(Integer.MAX_VALUE - burn, fuelUnits) + burn;
        ItemStack remainder = consumed.getItem().hasCraftingRemainingItem(consumed)
                ? consumed.getItem().getCraftingRemainingItem(consumed) : ItemStack.EMPTY;
        if (!remainder.isEmpty()) {
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(container, remainder, false);
            if (!leftover.isEmpty()) storeOutput(leftover);
        }
        sync();
        return true;
    }

    private void processBatch(ServerLevel serverLevel) {
        int available = Math.min(MAX_BATCH, Math.min(inputs.count(), fuelUnits / FUEL_PER_ITEM));
        int completed = 0;
        for (int index = 0; index < available; index++) {
            ItemStack current = inputs.first();
            Optional<RecipeHolder<SmeltingRecipe>> recipe = findRecipe(serverLevel, current.copyWithCount(1));
            if (recipe.isEmpty()) break;
            ItemStack result = recipe.get().value().getResultItem(serverLevel.registryAccess()).copy();
            if (!canStore(result)) break;
            storeOutput(result);
            inputs.removeOne();
            fuelUnits -= FUEL_PER_ITEM;
            completed++;
        }
        if (specialMode && completed > 0) advanceSpecialRolls(serverLevel, completed);
        if (fuelUnits < FUEL_PER_ITEM) specialMode = false;
        sync();
    }

    private void advanceSpecialRolls(ServerLevel level, int processed) {
        specialWork += processed;
        while (specialWork >= SPECIAL_ROLL_ITEMS) {
            specialWork -= SPECIAL_ROLL_ITEMS;
            specialPity++;
            if (specialPity >= SPECIAL_PITY_ROLLS || level.random.nextFloat() < 0.125F) {
                ItemStack crystal = new ItemStack(ModItems.CASTING_CRYSTAL.get());
                if (!canStore(crystal)) {
                    specialWork += SPECIAL_ROLL_ITEMS;
                    specialPity = Math.max(specialPity, SPECIAL_PITY_ROLLS);
                    return;
                }
                storeOutput(crystal);
                specialPity = 0;
            }
        }
    }

    private Optional<RecipeHolder<SmeltingRecipe>> findRecipe(ServerLevel level, ItemStack stack) {
        if (stack.isEmpty() || !isMineralInput(stack)) return Optional.empty();
        return level.getRecipeManager().getRecipeFor(
                RecipeType.SMELTING, new SingleRecipeInput(stack.copyWithCount(1)), level);
    }

    private boolean isMineralInput(ItemStack stack) {
        return stack.is(ORES) || stack.is(RAW_MATERIALS) || stack.is(DUSTS)
                || isRawMetalBlock(stack);
    }

    private void pullBoundInputs(ServerLevel serverLevel) {
        IItemHandler source = resolveHandler(inputContainer, inputFace);
        if (source == null) return;
        boolean changed = false;
        int selectedPriority = inputs.isEmpty()
                ? highestSmeltingPriority(source, serverLevel)
                : smeltingInputPriority(inputs.first());
        if (selectedPriority <= 0) return;
        // One internal batch belongs to exactly one tier. Do not admit raw
        // materials or ores while a dust batch is still being drained.
        for (int slot = 0; slot < source.getSlots() && inputs.remaining() > 0; slot++) {
            ItemStack available = source.getStackInSlot(slot);
            if (smeltingInputPriority(available) != selectedPriority
                    || findRecipe(serverLevel, available).isEmpty()) continue;
            ItemStack extracted = source.extractItem(slot,
                    Math.min(available.getCount(), inputs.remaining()), false);
            if (extracted.isEmpty()) continue;
            inputs.insert(extracted);
            changed = true;
        }
        if (changed) sync();
    }

    private int highestSmeltingPriority(IItemHandler source, ServerLevel serverLevel) {
        int highest = 0;
        for (int slot = 0; slot < source.getSlots(); slot++) {
            ItemStack stack = source.getStackInSlot(slot);
            if (findRecipe(serverLevel, stack).isPresent()) {
                highest = Math.max(highest, smeltingInputPriority(stack));
            }
        }
        return highest;
    }

    private static int smeltingInputPriority(ItemStack stack) {
        if (stack.is(DUSTS)) return 3;
        if (stack.is(RAW_MATERIALS) || isRawMetalBlock(stack)) return 2;
        return stack.is(ORES) ? 1 : 0;
    }

    private static boolean isRawMetalBlock(ItemStack stack) {
        return stack.is(Items.RAW_COPPER_BLOCK)
                || stack.is(Items.RAW_IRON_BLOCK)
                || stack.is(Items.RAW_GOLD_BLOCK);
    }

    private static boolean isEnchantedFuel(ItemStack stack) {
        return stack.is(ModItems.ENCHANTED_ARCHWOOD_CHARCOAL.get())
                || stack.is(ModItems.ENCHANTED_ARCHWOOD_CHARCOAL_BLOCK.get());
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

    @Nullable private IItemHandler fuelContainer() {
        return StructureInventoryAccess.at(level, consumableContainerPosition(worldPosition));
    }

    public static boolean isStructureFormed(Level level, BlockPos core, Direction facing) {
        for (BlockPos pos : framePositions(core, facing)) if (!level.getBlockState(pos).is(FRAME)) return false;
        return StructureInventoryAccess.at(level, consumableContainerPosition(core)) != null;
    }

    public static BlockPos consumableContainerPosition(BlockPos core) { return core.below(); }

    public static List<BlockPos> framePositions(BlockPos core, Direction facing) {
        Direction right = facing.getClockWise();
        Direction back = facing.getOpposite();
        List<BlockPos> result = new ArrayList<>();
        for (int depth = 0; depth <= 2; depth++) {
            for (int y = -1; y <= 1; y++) {
                for (int x = -1; x <= 1; x++) {
                    if (x == 0 && depth == 0 && (y == -1 || y == 0)) continue;
                    // The central chamber is intentionally ignored rather than
                    // requiring air, so utility blocks never invalidate the structure.
                    if (x == 0 && depth == 1 && y == 0) continue;
                    result.add(core.relative(right, x).relative(back, depth).offset(0, y, 0));
                }
            }
        }
        return List.copyOf(result);
    }

    private Direction facing() { return getBlockState().getValue(ArcaneSmelterCoreBlock.FACING); }

    private boolean canStore(ItemStack incoming) {
        if (incoming.isEmpty()) return true;
        int remaining = incoming.getCount();
        int freeSlots = OUTPUT_SLOTS - outputs.size();
        for (ItemStack existing : outputs) {
            if (ItemStack.isSameItemSameComponents(existing, incoming)) {
                remaining -= Math.max(0, existing.getMaxStackSize() - existing.getCount());
                if (remaining <= 0) return true;
            }
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

    private void setLit(boolean lit) {
        if (level == null || getBlockState().getValue(ArcaneSmelterCoreBlock.LIT) == lit) return;
        level.setBlock(worldPosition, getBlockState().setValue(ArcaneSmelterCoreBlock.LIT, lit), Block.UPDATE_CLIENTS);
    }
    private void setState(OperatingState next) { if (state != next) { state = next; sync(); } }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }

    public OperatingState getState() { return state; }
    public int getInputCount() { return inputs.count(); }
    public int getProgressTicks() { return progressTicks; }
    public int getFuelItemsRemaining() { return fuelUnits / FUEL_PER_ITEM; }
    public int getSpecialWork() { return specialWork; }
    public int getSpecialPity() { return specialPity; }
    public int getBufferedItemCount() { return outputs.stream().mapToInt(ItemStack::getCount).sum(); }
    public boolean hasInputContainer() { return inputContainer != null; }
    public boolean hasOutputContainer() { return outputContainer != null; }
    public boolean hasConsumableContainer() {
        return StructureInventoryAccess.at(level, consumableContainerPosition(worldPosition)) != null;
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
        tag.putInt("Progress", progressTicks); tag.putInt("FuelUnits", fuelUnits);
        tag.putInt("SpecialWork", specialWork); tag.putInt("SpecialPity", specialPity);
        tag.putBoolean("SpecialMode", specialMode); tag.putString("OperatingState", state.name());
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
        progressTicks = tag.getInt("Progress"); fuelUnits = Math.max(0, tag.getInt("FuelUnits"));
        specialWork = Math.max(0, tag.getInt("SpecialWork")); specialPity = Math.max(0, tag.getInt("SpecialPity"));
        specialMode = tag.getBoolean("SpecialMode");
        try { state = OperatingState.valueOf(tag.getString("OperatingState")); }
        catch (IllegalArgumentException ignored) { state = OperatingState.UNFORMED; }
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
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_smelter.output_bound"));
        return Result.SUCCESS;
    }
    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face, @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        inputContainer = target; inputFace = face; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_smelter.input_bound"));
        return Result.SUCCESS;
    }
    @Override public Result onClearConnections(Player player) {
        inputContainer = null; outputContainer = null; inputFace = null; outputFace = null; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_smelter.bindings_cleared"));
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

    public enum OperatingState {
        UNFORMED("message.ars_arcane_matrix.state.unformed"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        NO_INPUT("message.ars_arcane_matrix.arcane_smelter.no_input"),
        INVALID_INPUT("message.ars_arcane_matrix.arcane_smelter.invalid_input"),
        NO_FUEL("message.ars_arcane_matrix.arcane_smelter.no_fuel"),
        INVALID_FUEL("message.ars_arcane_matrix.arcane_smelter.invalid_fuel"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.state.output_blocked"),
        NORMAL_RUNNING("message.ars_arcane_matrix.arcane_smelter.normal"),
        ENCHANTED_RUNNING("message.ars_arcane_matrix.arcane_smelter.enchanted");
        private final String key; OperatingState(String key) { this.key = key; }
        public String translationKey() { return key; }
    }
}
