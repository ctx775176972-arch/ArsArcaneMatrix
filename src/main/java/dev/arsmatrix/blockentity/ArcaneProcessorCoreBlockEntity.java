package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.common.items.ExperienceGem;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.block.ArcaneProcessorCoreBlock;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.util.MixedItemBuffer;
import dev.arsmatrix.util.MultiblockClearance;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Tool-driven ore breaker. Food is time, magic food enables Enchanted Crystal rolls. */
public final class ArcaneProcessorCoreBlockEntity extends BlockEntity implements IWandable {
    public static final int MAX_BATCH = 5;
    public static final int CYCLE_TICKS = 100;
    private static final int OUTPUT_SLOTS = 18;
    private static final TagKey<Item> ORES = ItemTags.create(ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Block> ORE_BLOCKS = BlockTags.create(ResourceLocation.fromNamespaceAndPath("c", "ores"));
    private static final TagKey<Item> MAGIC_FOOD = ItemTags.create(
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "magic_food"));
    private static final TagKey<Block> FRAME = BlockTags.create(
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_processor_frame_blocks"));

    private final List<ItemStack> outputs = new ArrayList<>();
    private final MixedItemBuffer oreInputs = new MixedItemBuffer(MAX_BATCH);
    private GlobalPos inputContainer;
    private GlobalPos outputContainer;
    private Direction inputFace;
    private Direction outputFace;
    private int progressTicks;
    private int workTimeTicks;
    private int specialPity;
    private int specialWork;
    private int experienceRemainder;
    private boolean specialMode;
    private boolean structureFormed;
    private OperatingState state = OperatingState.UNFORMED;
    private int tickCounter;

    public ArcaneProcessorCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_PROCESSOR_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        if (tickCounter % 20 == 1) {
            structureFormed = isStructureFormed(serverLevel, worldPosition, facing());
        }
        pullBoundOres();
        pushBoundOutputs();
        if (!structureFormed) { setState(OperatingState.UNFORMED); return; }
        if (level.hasNeighborSignal(worldPosition)) { setState(OperatingState.REDSTONE_PAUSED); return; }

        // Magic food always takes over immediately; unused ordinary time is intentionally discarded.
        ArcanePedestalTile foodPedestal = foodPedestal();
        ItemStack offeredFood = foodPedestal == null ? ItemStack.EMPTY : foodPedestal.getStack();
        if (!specialMode && !offeredFood.isEmpty() && offeredFood.is(MAGIC_FOOD)) {
            workTimeTicks = 0;
            consumeOneFood(foodPedestal);
        }
        if (workTimeTicks <= 0 && !offeredFood.isEmpty()) consumeOneFood(foodPedestal);
        if (workTimeTicks <= 0) { progressTicks = 0; setState(OperatingState.NO_FOOD); return; }
        if (oreInputs.isEmpty()) { progressTicks = 0; setState(OperatingState.NO_ORE); return; }
        ArcanePedestalTile toolPedestal = toolPedestal();
        ItemStack tool = toolPedestal == null ? ItemStack.EMPTY : toolPedestal.getStack();
        if (!isUsableTool(serverLevel, tool, oreInputs.first())) {
            progressTicks = 0;
            setState(tool.isEmpty() ? OperatingState.NO_TOOL : OperatingState.INVALID_TOOL);
            return;
        }
        progressTicks++;
        workTimeTicks--;
        setState(specialMode ? OperatingState.MAGIC_RUNNING : OperatingState.NORMAL_RUNNING);
        if (progressTicks >= cycleTicks(serverLevel, tool)) {
            processBatch(serverLevel, toolPedestal, tool);
            progressTicks = 0;
        } else if (tickCounter % 20 == 0) sync();
    }

    private void consumeOneFood(@Nullable ArcanePedestalTile pedestal) {
        if (pedestal == null) return;
        ItemStack offered = pedestal.getStack();
        FoodProperties food = offered.get(DataComponents.FOOD);
        if (food == null) return;
        boolean magic = offered.is(MAGIC_FOOD);
        int seconds = Math.max(1, food.nutrition() * 10 + Math.round(food.saturation() * 5.0F));
        if (magic) seconds = Math.max(1, Math.round(seconds * 1.5F));
        specialMode = magic;
        workTimeTicks = Math.min(20 * 60 * 60, workTimeTicks + seconds * 20);
        ItemStack remainder = offered.getItem().hasCraftingRemainingItem(offered)
                ? offered.getItem().getCraftingRemainingItem(offered) : ItemStack.EMPTY;
        offered.shrink(1);
        // A single-item pedestal exposes this stack to pipes; bowls and bottles remain for extraction.
        pedestal.setStack(offered.isEmpty() ? remainder : offered);
        pedestal.setChanged();
        sync();
    }

    private void processBatch(ServerLevel serverLevel, ArcanePedestalTile pedestal, ItemStack tool) {
        int count = Math.min(MAX_BATCH, oreInputs.count());
        for (int index = 0; index < count && !tool.isEmpty(); index++) {
            ItemStack current = oreInputs.first();
            if (!isUsableTool(serverLevel, tool, current)) break;
            BlockState oreState = ((BlockItem) current.getItem()).getBlock().defaultBlockState();
            List<ItemStack> drops = Block.getDrops(oreState, serverLevel, worldPosition, null, null, tool);
            drops.forEach(this::storeOutput);
            int rawExperience = oreState.getBlock().getExpDrop(
                    oreState, serverLevel, worldPosition, null, null, tool);
            int experience = EnchantmentHelper.processBlockExperience(serverLevel, tool, rawExperience);
            addExperience(experience);
            if (specialMode) {
                specialWork++;
                if (specialWork >= MAX_BATCH) {
                    specialWork -= MAX_BATCH;
                    specialPity++;
                    if (specialPity >= 8 || serverLevel.random.nextFloat() < 0.125F) {
                        storeOutput(new ItemStack(ModItems.ENCHANTED_CRYSTAL.get()));
                        specialPity = 0;
                    }
                }
            }
            oreInputs.removeOne();
            tool.hurtAndBreak(1, serverLevel, (net.minecraft.world.entity.LivingEntity) null, ignored -> {});
        }
        pedestal.setChanged();
        sync();
    }

    private void addExperience(int earned) {
        int remaining = Math.max(0, experienceRemainder + earned);
        ExperienceGem greater = ItemsRegistry.GREATER_EXPERIENCE_GEM.get();
        int count = remaining / Math.max(1, greater.getValue());
        remaining %= Math.max(1, greater.getValue());
        addGems(greater, count);
        ExperienceGem normal = ItemsRegistry.EXPERIENCE_GEM.get();
        count = remaining / Math.max(1, normal.getValue());
        remaining %= Math.max(1, normal.getValue());
        addGems(normal, count);
        experienceRemainder = remaining;
    }

    private void addGems(Item item, int count) {
        while (count > 0) {
            int amount = Math.min(count, item.getDefaultMaxStackSize());
            storeOutput(new ItemStack(item, amount));
            count -= amount;
        }
    }

    private boolean isUsableTool(ServerLevel level, ItemStack tool, ItemStack ore) {
        if (tool.isEmpty() || !(ore.getItem() instanceof BlockItem blockItem)) return false;
        Holder<Enchantment> silk = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.SILK_TOUCH);
        if (EnchantmentHelper.getItemEnchantmentLevel(silk, tool) > 0) return false;
        BlockState state = blockItem.getBlock().defaultBlockState();
        return !state.requiresCorrectToolForDrops() || tool.isCorrectToolForDrops(state);
    }

    private int cycleTicks(ServerLevel level, ItemStack tool) {
        Holder<Enchantment> efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(Enchantments.EFFICIENCY);
        int levelValue = Math.max(0, EnchantmentHelper.getItemEnchantmentLevel(efficiency, tool));
        return Math.max(50, CYCLE_TICKS - levelValue * 10);
    }

    @Nullable private ArcanePedestalTile toolPedestal() {
        ArcanePedestalTile fallback = null;
        for (BlockPos pos : pedestalPositions(worldPosition, facing())) {
            if (!(level != null && level.getBlockEntity(pos) instanceof ArcanePedestalTile pedestal)) continue;
            ItemStack stack = pedestal.getStack();
            if (stack.isEmpty() || isFood(stack)) continue;
            if (stack.get(DataComponents.TOOL) != null) return pedestal;
            if (fallback == null) fallback = pedestal;
        }
        return fallback;
    }

    @Nullable private ArcanePedestalTile foodPedestal() {
        for (BlockPos pos : pedestalPositions(worldPosition, facing())) {
            if (level != null && level.getBlockEntity(pos) instanceof ArcanePedestalTile pedestal
                    && isFood(pedestal.getStack())) return pedestal;
        }
        return null;
    }

    private void pullBoundOres() {
        IItemHandler source = resolveHandler(inputContainer, inputFace);
        if (source == null || oreInputs.remaining() <= 0) return;
        boolean changed = false;
        for (int slot = 0; slot < source.getSlots() && oreInputs.remaining() > 0; slot++) {
            ItemStack available = source.getStackInSlot(slot);
            if (available.is(net.minecraft.world.item.Items.ANCIENT_DEBRIS)) {
                ItemStack bypassed = source.extractItem(slot, available.getCount(), false);
                if (!bypassed.isEmpty()) {
                    storeOutput(bypassed);
                    changed = true;
                }
                continue;
            }
            if (!isOre(available)) continue;
            ItemStack extracted = source.extractItem(slot, Math.min(available.getCount(), oreInputs.remaining()), false);
            if (extracted.isEmpty()) continue;
            oreInputs.insert(extracted);
            changed = true;
        }
        if (changed) sync();
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
            IItemHandler preferred = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, target.pos(), preferredFace);
            if (preferred != null) return preferred;
        }
        IItemHandler unsided = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            if (direction == preferredFace) continue;
            IItemHandler sided = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, target.pos(), direction);
            if (sided != null) return sided;
        }
        return null;
    }

    public static boolean isStructureFormed(Level level, BlockPos core, Direction facing) {
        for (BlockPos pos : framePositions(core, facing)) {
            if (!level.getBlockState(pos).is(FRAME)) return false;
        }
        for (BlockPos pos : pedestalPositions(core, facing)) {
            if (!(level.getBlockEntity(pos) instanceof ArcanePedestalTile)) return false;
        }
        return MultiblockClearance.isOpen(level, core.above(2))
                && MultiblockClearance.isOpen(level, core.relative(facing))
                && MultiblockClearance.isOpen(level, core.relative(facing.getOpposite()));
    }

    public static List<BlockPos> framePositions(BlockPos core, Direction facing) {
        Direction right = facing.getClockWise();
        return List.of(core.above(), core.relative(right), core.relative(right.getOpposite()),
                core.relative(right).relative(facing), core.relative(right).relative(facing.getOpposite()),
                core.relative(right.getOpposite()).relative(facing),
                core.relative(right.getOpposite()).relative(facing.getOpposite()));
    }

    /** Both positions are equivalent; their contents decide tool/food roles. */
    public static List<BlockPos> pedestalPositions(BlockPos core, Direction facing) {
        Direction right = facing.getClockWise();
        return List.of(core.relative(right).above(), core.relative(right.getOpposite()).above());
    }

    private Direction facing() { return getBlockState().getValue(ArcaneProcessorCoreBlock.FACING); }
    private boolean isOre(ItemStack stack) {
        return stack.getItem() instanceof BlockItem blockItem
                && (stack.is(ORES) || blockItem.getBlock().defaultBlockState().is(ORE_BLOCKS));
    }
    private boolean isFood(ItemStack stack) { return stack.get(DataComponents.FOOD) != null; }

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

    public OperatingState getState() { return state; }
    public int getInputCount() { return oreInputs.count(); }
    public int getProgressTicks() { return progressTicks; }
    public int getWorkTimeTicks() { return workTimeTicks; }
    public String getProgressSeconds() { return String.format(java.util.Locale.ROOT, "%.1f", progressTicks / 20.0D); }
    public String getCycleSeconds() {
        if (!(level instanceof ServerLevel serverLevel)) {
            ArcanePedestalTile pedestal = toolPedestal();
            ItemStack tool = pedestal == null ? ItemStack.EMPTY : pedestal.getStack();
            if (level != null && !tool.isEmpty()) {
                Holder<Enchantment> efficiency = level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT)
                        .getOrThrow(Enchantments.EFFICIENCY);
                int value = Math.max(0, EnchantmentHelper.getItemEnchantmentLevel(efficiency, tool));
                return String.format(java.util.Locale.ROOT, "%.1f", Math.max(50, CYCLE_TICKS - value * 10) / 20.0D);
            }
            return "5.0";
        }
        ArcanePedestalTile pedestal = toolPedestal();
        ItemStack tool = pedestal == null ? ItemStack.EMPTY : pedestal.getStack();
        return String.format(java.util.Locale.ROOT, "%.1f", cycleTicks(serverLevel, tool) / 20.0D);
    }
    public int getWorkTimeSeconds() { return (workTimeTicks + 19) / 20; }
    public int getSpecialPity() { return specialPity; }
    public int getSpecialWork() { return specialWork; }
    public int getBufferedItemCount() { return outputs.stream().mapToInt(ItemStack::getCount).sum(); }
    public boolean hasInputContainer() { return inputContainer != null; }
    public boolean hasOutputContainer() { return outputContainer != null; }
    private void setState(OperatingState next) { if (state != next) { state = next; sync(); } }
    private void sync() { setChanged(); if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS); }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        oreInputs.stacks().forEach(stack -> Containers.dropItemStack(
                level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack));
        outputs.forEach(stack -> Containers.dropItemStack(level, worldPosition.getX(), worldPosition.getY(), worldPosition.getZ(), stack));
        outputs.clear(); oreInputs.clear();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ListTag inputList = new ListTag();
        oreInputs.stacks().forEach(stack -> inputList.add(stack.saveOptional(registries)));
        tag.put("OreInputs", inputList);
        tag.putInt("Progress", progressTicks); tag.putInt("WorkTime", workTimeTicks);
        tag.putInt("SpecialPity", specialPity); tag.putInt("ExperienceRemainder", experienceRemainder);
        tag.putInt("SpecialWork", specialWork);
        tag.putBoolean("SpecialMode", specialMode);
        tag.putString("OperatingState", state.name());
        if (inputContainer != null) tag.put("InputContainer", saveGlobalPos(inputContainer));
        if (outputContainer != null) tag.put("OutputContainer", saveGlobalPos(outputContainer));
        if (inputFace != null) tag.putString("InputFace", inputFace.getSerializedName());
        if (outputFace != null) tag.putString("OutputFace", outputFace.getSerializedName());
        ListTag list = new ListTag(); outputs.forEach(stack -> list.add(stack.saveOptional(registries))); tag.put("Outputs", list);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        oreInputs.clear();
        ListTag inputList = tag.getList("OreInputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < inputList.size() && oreInputs.remaining() > 0; i++) {
            oreInputs.insert(ItemStack.parseOptional(registries, inputList.getCompound(i)));
        }
        if (oreInputs.isEmpty() && tag.contains("OreInput", Tag.TAG_COMPOUND)) {
            oreInputs.insert(ItemStack.parseOptional(registries, tag.getCompound("OreInput")));
        }
        progressTicks = tag.getInt("Progress"); workTimeTicks = tag.getInt("WorkTime");
        specialPity = tag.getInt("SpecialPity"); experienceRemainder = tag.getInt("ExperienceRemainder");
        specialWork = Math.max(0, Math.min(MAX_BATCH - 1, tag.getInt("SpecialWork")));
        specialMode = tag.getBoolean("SpecialMode"); outputs.clear();
        try { state = OperatingState.valueOf(tag.getString("OperatingState")); }
        catch (IllegalArgumentException ignored) { state = OperatingState.UNFORMED; }
        inputContainer = tag.contains("InputContainer") ? loadGlobalPos(tag.getCompound("InputContainer")) : null;
        outputContainer = tag.contains("OutputContainer") ? loadGlobalPos(tag.getCompound("OutputContainer")) : null;
        inputFace = Direction.byName(tag.getString("InputFace"));
        outputFace = Direction.byName(tag.getString("OutputFace"));
        ListTag list = tag.getList("Outputs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && outputs.size() < OUTPUT_SLOTS; i++) {
            ItemStack stack = ItemStack.parseOptional(registries, list.getCompound(i)); if (!stack.isEmpty()) outputs.add(stack);
        }
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    @Override public Result onFirstConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        outputContainer = target;
        outputFace = face;
        sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_processor.output_bound"));
        return Result.SUCCESS;
    }

    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                             @Nullable LivingEntity entity, Player player) {
        if (!isValidBinding(target)) return Result.FAIL;
        inputContainer = target;
        inputFace = face;
        sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_processor.input_bound"));
        return Result.SUCCESS;
    }

    @Override public Result onClearConnections(Player player) {
        inputContainer = null; outputContainer = null; inputFace = null; outputFace = null; sync();
        player.sendSystemMessage(Component.translatable("message.ars_arcane_matrix.arcane_processor.bindings_cleared"));
        return Result.SUCCESS;
    }

    private boolean isValidBinding(@Nullable GlobalPos target) {
        if (target == null || level == null || level.getServer() == null) return false;
        if (target.dimension().equals(level.dimension()) && target.pos().equals(worldPosition)) return false;
        return resolveHandler(target, null) != null;
    }

    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString()); tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }
    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (id == null) id = net.minecraft.world.level.Level.OVERWORLD.location();
        return GlobalPos.of(net.minecraft.resources.ResourceKey.create(Registries.DIMENSION, id), BlockPos.of(tag.getLong("Pos")));
    }

    public enum OperatingState {
        UNFORMED("message.ars_arcane_matrix.state.unformed"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        NO_FOOD("message.ars_arcane_matrix.arcane_processor.no_food"),
        NO_ORE("message.ars_arcane_matrix.arcane_processor.no_ore"),
        NO_TOOL("message.ars_arcane_matrix.arcane_processor.no_tool"),
        INVALID_TOOL("message.ars_arcane_matrix.arcane_processor.invalid_tool"),
        NORMAL_RUNNING("message.ars_arcane_matrix.arcane_processor.normal"),
        MAGIC_RUNNING("message.ars_arcane_matrix.arcane_processor.magic");
        private final String key; OperatingState(String key) { this.key = key; }
        public String translationKey() { return key; }
    }

}
