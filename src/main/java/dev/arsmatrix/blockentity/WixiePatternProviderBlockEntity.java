package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import dev.arsmatrix.item.CraftingGuideItem;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.menu.WixiePatternProviderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class WixiePatternProviderBlockEntity extends BlockEntity implements MenuProvider {

    public static final int GUIDE_SLOTS_PER_TIER = 27;
    public static final int MAX_UPGRADE_TIER = 3;
    public static final int WORKSTATION_RADIUS = 8;
    public static final int WORKSTATION_VERTICAL_RADIUS = 8;
    private static final int SOURCE_COST_PER_CRAFT = 50;
    private static final int NETWORK_CRAFT_TICKS = 1;
    private int upgradeTier;
    private final ItemStackHandler guides = new ItemStackHandler(GUIDE_SLOTS_PER_TIER) {
        @Override
        public int getSlotLimit(int slot) {
            return 1;
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(dev.arsmatrix.registry.ModItems.CRAFTING_GUIDE.get())
                    && CraftingGuideItem.getRecipeId(stack) != null;
        }

        @Override
        protected void onContentsChanged(int slot) {
            sync();
        }
    };
    private final Map<BlockPos, PendingJob> activeJobs = new LinkedHashMap<>();
    private final Map<BlockPos, PendingMachineJob> machineJobs = new LinkedHashMap<>();

    public WixiePatternProviderBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.WIXIE_PATTERN_PROVIDER.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.wixie_pattern_provider");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WixiePatternProviderMenu(containerId, inventory, this);
    }

    public ItemStackHandler getGuideHandler() {
        return guides;
    }

    public int getUpgradeTier() {
        return upgradeTier;
    }

    public int getGuideCapacity() {
        return GUIDE_SLOTS_PER_TIER * (upgradeTier + 1);
    }

    public void setUpgradeTier(int tier) {
        int clamped = Math.max(0, Math.min(MAX_UPGRADE_TIER, tier));
        upgradeTier = clamped;
        ensureGuideCapacity();
        sync();
    }

    private void ensureGuideCapacity() {
        int capacity = getGuideCapacity();
        if (guides.getSlots() >= capacity) return;
        List<ItemStack> existing = new ArrayList<>();
        for (int slot = 0; slot < guides.getSlots(); slot++) {
            existing.add(guides.getStackInSlot(slot).copy());
        }
        guides.setSize(capacity);
        for (int slot = 0; slot < existing.size(); slot++) {
            guides.setStackInSlot(slot, existing.get(slot));
        }
    }

    public void serverTick() {
        if (level == null || level.isClientSide || activeJobs.isEmpty() && machineJobs.isEmpty()) {
            return;
        }
        for (Map.Entry<BlockPos, PendingJob> entry : new ArrayList<>(activeJobs.entrySet())) {
            if (!(level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie)) {
                continue;
            }
            PendingJob job = entry.getValue();
            if (wixie.craftManager instanceof NetworkWixieCraftingManager networkManager) {
                job.workTicks++;
                if (job.workTicks >= NETWORK_CRAFT_TICKS) {
                    networkManager.markReadyToComplete();
                    if (wixie.hasSource) {
                        wixie.attemptFinish();
                    }
                }
            } else {
                List<ItemStack> remaining = wixie.craftManager != null
                        && ItemStack.isSameItemSameComponents(wixie.craftManager.outputStack, job.output)
                        && !wixie.craftManager.neededItems.isEmpty()
                        ? wixie.craftManager.neededItems
                        : job.ingredients;
                installAndConsumeWixieJob(wixie, job, remaining);
            }
        }
        for (Map.Entry<BlockPos, PendingMachineJob> entry : new ArrayList<>(machineJobs.entrySet())) {
            PendingMachineJob job = entry.getValue();
            if (!(level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie)
                    || !(level.getBlockEntity(job.furnacePos) instanceof SourceStoneFurnaceBlockEntity furnace)) {
                continue;
            }
            ItemStack completed = furnace.takeNetworkResult(job.output);
            if (completed.isEmpty()) continue;
            routeStack(wixie, completed, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos, SourceStoneFurnaceBlockEntity.SOURCE_COST,
                    completed, job.finalOutput);
            machineJobs.remove(entry.getKey());
            sync();
        }
    }

    /** Reserves one Wixie and one real Source Stone Furnace for a cooking step. */
    public boolean startMachineJob(
            BlockPos terminal, ItemStack output, List<ItemStack> ingredients, boolean finalOutput) {
        if (level == null || output.isEmpty() || ingredients.size() != 1
                || ingredients.getFirst().isEmpty()) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (activeJobs.containsKey(wixiePos) || machineJobs.containsKey(wixiePos)
                    || wixie.craftManager != null && !wixie.craftManager.isCraftCompleted()) continue;
            for (SourceStoneFurnaceBlockEntity furnace : getSourceStoneFurnaces()) {
                if (!furnace.isAvailableForNetworkJob()) continue;
                ExtractionResult extraction = extractIngredients(wixie, terminal, ingredients);
                if (extraction == null || extraction.stacks.isEmpty()) return false;
                ItemStack input = extraction.stacks.getFirst();
                if (!furnace.startNetworkJob(input)) {
                    extraction.stacks.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                machineJobs.put(wixiePos, new PendingMachineJob(
                        terminal.immutable(), furnace.getBlockPos().immutable(),
                        output.copy(), finalOutput));
                sync();
                return true;
            }
        }
        return false;
    }

    private List<SourceStoneFurnaceBlockEntity> getSourceStoneFurnaces() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .map(level::getBlockEntity)
                .filter(SourceStoneFurnaceBlockEntity.class::isInstance)
                .map(SourceStoneFurnaceBlockEntity.class::cast)
                .sorted(Comparator.comparingDouble(furnace ->
                        furnace.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    public boolean startJob(
            BlockPos terminal,
            ItemStack output,
            List<ItemStack> ingredients,
            List<ItemStack> remainders,
            boolean finalOutput
    ) {
        if (output.isEmpty()) {
            return false;
        }
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (activeJobs.containsKey(wixiePos)
                    || (wixie.craftManager != null && !wixie.craftManager.isCraftCompleted())) {
                continue;
            }
            PendingJob job = new PendingJob(
                    terminal.immutable(), output.copy(), copyStacks(ingredients),
                    copyStacks(remainders), finalOutput, 1);
            activeJobs.put(wixiePos, job);
            if (installAndConsumeWixieJob(wixie, job, job.ingredients)) {
                return true;
            }
            activeJobs.remove(wixiePos);
        }
        return false;
    }

    private boolean installAndConsumeWixieJob(
            WixieCauldronTile wixie, PendingJob job, List<ItemStack> ingredients
    ) {
        ExtractionResult extraction = extractIngredients(wixie, job.terminalPos, ingredients);
        if (extraction == null) {
            return false;
        }
        // A reservoir supplies the fluid itself rather than a physical bucket/bottle. Remove
        // the matching crafting remainder so virtual containers cannot create free empties.
        for (ItemStack virtualContainer : extraction.virtualContainers) {
            removeCraftingRemainder(job.remainders, virtualContainer);
        }
        wixie.craftManager = new NetworkWixieCraftingManager(
                worldPosition, job.output, extraction.stacks, job.remainders);
        wixie.onCraftStart();
        for (ItemStack stack : extraction.stacks) {
            for (int count = 0; count < stack.getCount(); count++) {
                wixie.craftManager.giveItem(stack.getItem());
            }
        }
        job.ingredients.clear();
        wixie.setChanged();
        sync();
        return true;
    }

    /**
     * Transfers the planned ingredients directly into the Wixie job. Ars normally
     * spawns a flying-item entity for every fetched ingredient; direct delivery
     * preserves its crafting/source cycle without rendering those entities.
     */
    private ExtractionResult extractIngredients(
            WixieCauldronTile wixie, BlockPos terminalPos, List<ItemStack> ingredients
    ) {
        List<IItemHandler> inventories = getWixieInventories(wixie);
        List<ItemStack> physical = new ArrayList<>();
        List<ItemStack> virtual = new ArrayList<>();
        AdvancedStorageLecternBlockEntity lectern = level != null
                && level.getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity found
                ? found : null;
        for (ItemStack ingredient : ingredients) {
            int remaining = ingredient.getCount();
            for (IItemHandler inventory : inventories) {
                for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                    ItemStack available = inventory.getStackInSlot(slot);
                    if (!ItemStack.isSameItemSameComponents(available, ingredient)) {
                        continue;
                    }
                    ItemStack taken = inventory.extractItem(slot, remaining, false);
                    if (!taken.isEmpty()) {
                        physical.add(taken);
                        remaining -= taken.getCount();
                    }
                }
                if (remaining <= 0) {
                    break;
                }
            }
            while (remaining > 0 && lectern != null
                    && lectern.consumeVirtualFluidContainer(ingredient)) {
                virtual.add(ingredient.copyWithCount(1));
                remaining--;
            }
            if (remaining > 0) {
                physical.forEach(stack -> routeToWixieInput(wixie, stack));
                if (lectern != null) virtual.forEach(lectern::restoreVirtualFluidContainer);
                return null;
            }
        }
        List<ItemStack> combined = new ArrayList<>(physical);
        combined.addAll(virtual);
        return new ExtractionResult(combined, virtual);
    }

    private static void removeCraftingRemainder(List<ItemStack> remainders, ItemStack ingredient) {
        ItemStack expected = ingredient.getCraftingRemainingItem();
        for (int index = 0; index < remainders.size(); index++) {
            ItemStack remainder = remainders.get(index);
            // Ordinary recipes return an empty container; Farmer's Delight's dynamic dough
            // recipe returns the filled water bucket itself. Both must disappear when the
            // reservoir supplied only virtual fluid rather than a physical container.
            boolean matchesExpected = !expected.isEmpty()
                    && ItemStack.isSameItemSameComponents(remainder, expected);
            boolean matchesIngredient = ItemStack.isSameItemSameComponents(remainder, ingredient);
            if (!matchesExpected && !matchesIngredient) continue;
            remainder.shrink(1);
            if (remainder.isEmpty()) remainders.remove(index);
            return;
        }
    }

    private record ExtractionResult(List<ItemStack> stacks, List<ItemStack> virtualContainers) {}

    public void completeWixieJob(BlockPos wixiePos) {
        if (level == null || level.isClientSide) {
            return;
        }
        PendingJob job = activeJobs.remove(wixiePos);
        if (job == null || !(level.getBlockEntity(wixiePos) instanceof WixieCauldronTile wixie)) {
            return;
        }
        if (job.finalOutput) {
            routeStack(wixie, job.output, getWixieOutputInventories());
        } else {
            routeStack(wixie, job.output, getWixieInventories());
        }
        job.remainders.forEach(stack -> routeStack(wixie, stack, getWixieInventories()));
        if (level.hasChunkAt(job.terminalPos)) {
            var blockEntity = level.getBlockEntity(job.terminalPos);
            WixieOrderTerminalBlockEntity terminal = blockEntity instanceof WixieOrderTerminalBlockEntity direct
                    ? direct : blockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                    ? lectern.getOrderEngine() : null;
            if (terminal != null) terminal.onWixieJobCompleted(
                    SOURCE_COST_PER_CRAFT, job.output, job.finalOutput);
        }
        sync();
    }

    private void notifyTerminal(BlockPos terminalPos, int sourceCost,
                                ItemStack output, boolean finalOutput) {
        if (level == null || !level.hasChunkAt(terminalPos)) return;
        BlockEntity blockEntity = level.getBlockEntity(terminalPos);
        WixieOrderTerminalBlockEntity terminal = blockEntity instanceof WixieOrderTerminalBlockEntity direct
                ? direct : blockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern.getOrderEngine() : null;
        if (terminal != null) terminal.onWixieJobCompleted(sourceCost, output, finalOutput);
    }

    private static List<ItemStack> copyStacks(List<ItemStack> stacks) {
        return stacks.stream().filter(stack -> !stack.isEmpty()).map(ItemStack::copy)
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    public List<ResourceLocation> getRecipeIds() {
        List<ResourceLocation> result = new ArrayList<>();
        for (int slot = 0; slot < guides.getSlots(); slot++) {
            ResourceLocation id = CraftingGuideItem.getRecipeId(guides.getStackInSlot(slot));
            if (id != null) {
                result.add(id);
            }
        }
        return List.copyOf(result);
    }

    public boolean isFuzzyRecipe(ResourceLocation recipeId) {
        for (int slot = 0; slot < guides.getSlots(); slot++) {
            ItemStack guide = guides.getStackInSlot(slot);
            if (recipeId.equals(CraftingGuideItem.getRecipeId(guide))) {
                return CraftingGuideItem.isFuzzy(guide);
            }
        }
        return true;
    }

    public int getGuideCount() {
        return getRecipeIds().size();
    }

    public boolean hasGuideSpace() {
        for (int slot = 0; slot < getGuideCapacity(); slot++) {
            if (guides.getStackInSlot(slot).isEmpty()) return true;
        }
        return false;
    }

    /** Inserts one already encoded guide into the first unlocked empty slot. */
    public boolean insertEncodedGuide(ItemStack guide) {
        if (guide.isEmpty() || CraftingGuideItem.getRecipeId(guide) == null) return false;
        for (int slot = 0; slot < getGuideCapacity(); slot++) {
            if (!guides.getStackInSlot(slot).isEmpty()) continue;
            ItemStack remainder = guides.insertItem(slot, guide.copyWithCount(1), false);
            if (remainder.isEmpty()) {
                sync();
                return true;
            }
        }
        return false;
    }

    /** Physically orders the guide inventory; slot order is also recipe priority. */
    public void sortGuidesByName() {
        sortGuides(Comparator
                .comparing((ItemStack guide) -> guideResultName(guide), String.CASE_INSENSITIVE_ORDER)
                .thenComparing(guide -> String.valueOf(CraftingGuideItem.getRecipeId(guide))));
    }

    /** Groups workstation recipes, then orders each group by its produced item. */
    public void sortGuidesByWorkstation() {
        sortGuides(Comparator
                .comparing((ItemStack guide) -> CraftingGuideItem.getWorkstationId(guide).toString())
                .thenComparing(this::guideResultName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(guide -> String.valueOf(CraftingGuideItem.getRecipeId(guide))));
    }

    private String guideResultName(ItemStack guide) {
        ItemStack result = CraftingGuideItem.getRecordedResult(guide);
        return result.isEmpty() ? "" : result.getHoverName().getString();
    }

    private void sortGuides(Comparator<ItemStack> comparator) {
        List<ItemStack> ordered = new ArrayList<>();
        for (int slot = 0; slot < getGuideCapacity(); slot++) {
            ItemStack guide = guides.getStackInSlot(slot);
            if (!guide.isEmpty()) ordered.add(guide.copy());
        }
        ordered.sort(comparator);
        for (int slot = 0; slot < getGuideCapacity(); slot++) {
            guides.setStackInSlot(slot, slot < ordered.size() ? ordered.get(slot) : ItemStack.EMPTY);
        }
        sync();
    }

    public boolean toggleGuideMode(int slot) {
        if (slot < 0 || slot >= getGuideCapacity()) return false;
        ItemStack guide = guides.getStackInSlot(slot);
        if (guide.isEmpty() || CraftingGuideItem.getRecipeId(guide) == null) return false;
        CraftingGuideItem.setFuzzy(guide, !CraftingGuideItem.isFuzzy(guide));
        guides.setStackInSlot(slot, guide);
        sync();
        return true;
    }

    public boolean setRecipeFuzzy(ResourceLocation recipeId, boolean fuzzy) {
        boolean changed = false;
        for (int slot = 0; slot < getGuideCapacity(); slot++) {
            ItemStack guide = guides.getStackInSlot(slot);
            if (!recipeId.equals(CraftingGuideItem.getRecipeId(guide))
                    || CraftingGuideItem.isFuzzy(guide) == fuzzy) continue;
            CraftingGuideItem.setFuzzy(guide, fuzzy);
            guides.setStackInSlot(slot, guide);
            changed = true;
        }
        if (changed) sync();
        return changed;
    }

    public boolean isAvailable() {
        return getAvailableWorkerCount() > 0;
    }

    public boolean isWorking() {
        return !activeJobs.isEmpty() || !machineJobs.isEmpty();
    }

    public int getActiveWorkerCount() {
        return activeJobs.size() + machineJobs.size();
    }

    public int getAvailableWorkerCount() {
        return (int) getWixieWorkers().stream()
                .filter(wixie -> !activeJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !machineJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> wixie.craftManager == null || wixie.craftManager.isCraftCompleted())
                .count();
    }

    public List<ItemStack> getPendingOutputs() {
        List<ItemStack> result = new ArrayList<>();
        activeJobs.values().forEach(job -> result.add(job.output.copy()));
        machineJobs.values().forEach(job -> result.add(job.output.copy()));
        return List.copyOf(result);
    }

    public boolean hasWixieWorker() {
        return !getWixieWorkers().isEmpty();
    }

    public List<WixieCauldronTile> getWixieWorkers() {
        if (level == null) {
            return List.of();
        }
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS)
                )
                .map(level::getBlockEntity)
                .filter(WixieCauldronTile.class::isInstance)
                .map(WixieCauldronTile.class::cast)
                .sorted(Comparator
                        .comparingDouble((WixieCauldronTile wixie) ->
                                wixie.getBlockPos().distSqr(worldPosition))
                        .thenComparingLong(wixie -> wixie.getBlockPos().asLong()))
                .toList();
    }

    /** Combines the inventories bound to every nearby Wixie with a Dominion Wand. */
    public List<IItemHandler> getWixieInventories() {
        List<IItemHandler> result = new ArrayList<>();
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            for (IItemHandler handler : getWixieInventories(wixie)) {
                if (!result.contains(handler)) {
                    result.add(handler);
                }
            }
        }
        return List.copyOf(result);
    }

    private List<IItemHandler> getWixieInventories(WixieCauldronTile wixie) {
        if (level == null) {
            return List.of();
        }
        List<IItemHandler> result = new ArrayList<>();
        for (BlockPos inventoryPos : wixie.getInventories()) {
            if (!level.hasChunkAt(inventoryPos)) {
                continue;
            }
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, inventoryPos, null);
            if (handler != null && !result.contains(handler)) {
                result.add(handler);
            }
        }
        return List.copyOf(result);
    }

    private void routeToWixieInput(WixieCauldronTile wixie, ItemStack stack) {
        routeStack(wixie, stack, getWixieInventories(wixie));
    }

    private void routeStack(WixieCauldronTile wixie, ItemStack stack, List<IItemHandler> destinations) {
        if (stack.isEmpty() || level == null) {
            return;
        }
        ItemStack remaining = stack.copy();
        for (IItemHandler handler : destinations) {
            remaining = ItemHandlerHelper.insertItemStacked(handler, remaining, false);
            if (remaining.isEmpty()) {
                return;
            }
        }
        BlockPos dropPos = wixie.getBlockPos();
        Containers.dropItemStack(level, dropPos.getX() + 0.5D,
                dropPos.getY() + 1.0D, dropPos.getZ() + 0.5D, remaining);
    }

    public List<IItemHandler> getWixieOutputInventories() {
        List<IItemHandler> result = new ArrayList<>();
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            for (IItemHandler handler : getExplicitWixieOutputInventories(wixie)) {
                if (!result.contains(handler)) {
                    result.add(handler);
                }
            }
        }
        return result.isEmpty() ? getWixieInventories() : List.copyOf(result);
    }

    private List<IItemHandler> getExplicitWixieOutputInventories(WixieCauldronTile wixie) {
        if (level == null) {
            return List.of();
        }
        List<IItemHandler> result = new ArrayList<>();
        CompoundTag data = wixie.saveWithoutMetadata(level.registryAccess());
        if (data.contains("FinishedStorage", Tag.TAG_INT_ARRAY)) {
            int[] coordinates = data.getIntArray("FinishedStorage");
            if (coordinates.length == 3) {
                BlockPos outputPos = new BlockPos(coordinates[0], coordinates[1], coordinates[2]);
                IItemHandler output = level.getCapability(
                        Capabilities.ItemHandler.BLOCK, outputPos, null);
                if (output != null) {
                    result.add(output);
                }
            }
        }
        return List.copyOf(result);
    }

    public void dropGuides() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < guides.getSlots(); slot++) {
            ItemStack stack = guides.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack.copy());
                guides.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("UpgradeTier", upgradeTier);
        tag.put("Guides", guides.serializeNBT(registries));
        CompoundTag jobsTag = new CompoundTag();
        int jobIndex = 0;
        for (Map.Entry<BlockPos, PendingJob> entry : activeJobs.entrySet()) {
            PendingJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            jobTag.putInt("WorkTicks", job.workTicks);
            jobTag.put("Ingredients", saveStacks(job.ingredients, registries));
            jobTag.put("Remainders", saveStacks(job.remainders, registries));
            jobsTag.put(Integer.toString(jobIndex++), jobTag);
        }
        tag.put("ActiveJobs", jobsTag);
        CompoundTag machineTag = new CompoundTag();
        int machineIndex = 0;
        for (Map.Entry<BlockPos, PendingMachineJob> entry : machineJobs.entrySet()) {
            PendingMachineJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("FurnacePos", job.furnacePos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            machineTag.put(Integer.toString(machineIndex++), jobTag);
        }
        tag.put("MachineJobs", machineTag);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tag.getInt("UpgradeTier")));
        guides.deserializeNBT(registries, tag.getCompound("Guides"));
        ensureGuideCapacity();
        activeJobs.clear();
        CompoundTag jobsTag = tag.getCompound("ActiveJobs");
        for (String key : jobsTag.getAllKeys()) {
            CompoundTag jobTag = jobsTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (!output.isEmpty() && jobTag.contains("WixiePos") && jobTag.contains("TerminalPos")) {
                activeJobs.put(
                        BlockPos.of(jobTag.getLong("WixiePos")),
                        new PendingJob(
                                BlockPos.of(jobTag.getLong("TerminalPos")), output,
                                loadStacks(jobTag.getCompound("Ingredients"), registries),
                                loadStacks(jobTag.getCompound("Remainders"), registries),
                                jobTag.getBoolean("FinalOutput"),
                                Math.max(1, jobTag.getInt("WorkTicks")))
                );
            }
        }
        machineJobs.clear();
        CompoundTag machineTag = tag.getCompound("MachineJobs");
        for (String key : machineTag.getAllKeys()) {
            CompoundTag jobTag = machineTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (!output.isEmpty() && jobTag.contains("WixiePos")
                    && jobTag.contains("TerminalPos") && jobTag.contains("FurnacePos")) {
                machineJobs.put(BlockPos.of(jobTag.getLong("WixiePos")), new PendingMachineJob(
                        BlockPos.of(jobTag.getLong("TerminalPos")),
                        BlockPos.of(jobTag.getLong("FurnacePos")), output,
                        jobTag.getBoolean("FinalOutput")));
            }
        }
    }

    private static CompoundTag saveStacks(
            List<ItemStack> stacks, HolderLookup.Provider registries
    ) {
        CompoundTag result = new CompoundTag();
        for (int index = 0; index < stacks.size(); index++) {
            result.put(Integer.toString(index), stacks.get(index).saveOptional(registries));
        }
        return result;
    }

    private static List<ItemStack> loadStacks(
            CompoundTag tag, HolderLookup.Provider registries
    ) {
        List<ItemStack> result = new ArrayList<>();
        tag.getAllKeys().stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            ItemStack stack = ItemStack.parseOptional(registries, tag.getCompound(key));
            if (!stack.isEmpty()) {
                result.add(stack);
            }
        });
        return result;
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    private static final class PendingJob {
        private final BlockPos terminalPos;
        private final ItemStack output;
        private final List<ItemStack> ingredients;
        private final List<ItemStack> remainders;
        private final boolean finalOutput;
        private int workTicks;

        private PendingJob(
                BlockPos terminalPos,
                ItemStack output,
                List<ItemStack> ingredients,
                List<ItemStack> remainders,
                boolean finalOutput,
                int workTicks
        ) {
            this.terminalPos = terminalPos;
            this.output = output;
            this.ingredients = ingredients;
            this.remainders = remainders;
            this.finalOutput = finalOutput;
            this.workTicks = workTicks;
        }
    }

    private record PendingMachineJob(
            BlockPos terminalPos, BlockPos furnacePos, ItemStack output, boolean finalOutput) {}
}
