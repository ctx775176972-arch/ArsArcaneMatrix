package dev.arsmatrix.blockentity;

import dev.arsmatrix.ArsArcaneMatrix;
import com.hollingsworth.arsnouveau.api.recipe.CraftingManager;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.ArcaneCore;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.EnchantingApparatusTile;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import dev.arsmatrix.item.CraftingGuideItem;
import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.menu.WixiePatternProviderMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.Containers;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class WixiePatternProviderBlockEntity extends BlockEntity implements MenuProvider {

    public static final int GUIDE_SLOTS_PER_TIER = 27;
    public static final int MAX_UPGRADE_TIER = 3;
    private static final int[] GUIDE_CAPACITIES = {27, 81, 243, 729};
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
    private final Map<BlockPos, PendingApparatusJob> apparatusJobs = new LinkedHashMap<>();
    private final Map<BlockPos, PendingImbuementJob> imbuementJobs = new LinkedHashMap<>();
    private final Map<BlockPos, PendingReactionJob> reactionJobs = new LinkedHashMap<>();
    private final Map<BlockPos, PendingCookingPotJob> cookingPotJobs = new LinkedHashMap<>();
    private final Map<BlockPos, PendingCompressorJob> compressorJobs = new LinkedHashMap<>();

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
        return GUIDE_CAPACITIES[Math.max(0, Math.min(MAX_UPGRADE_TIER, upgradeTier))];
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
        if (level == null || level.isClientSide) return;
        recoverOrphanedNetworkManagers();
        if (activeJobs.isEmpty() && machineJobs.isEmpty() && apparatusJobs.isEmpty()
                && imbuementJobs.isEmpty() && reactionJobs.isEmpty()
                && cookingPotJobs.isEmpty() && compressorJobs.isEmpty()) return;
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
            if (completed.isEmpty()) {
                if (!furnace.inventory().getStackInSlot(0).isEmpty()) continue;
                List<ItemStack> recovered = furnace.cancelNetworkJob();
                recovered.forEach(stack -> routeToWixieInput(wixie, stack));
                ArsArcaneMatrix.LOGGER.warn(
                        "Cancelled stale Source Stone Furnace job at {}: expected {}, recovered {} stack(s)",
                        job.furnacePos, job.output, recovered.size());
                machineJobs.remove(entry.getKey());
                sync();
                continue;
            }
            routeStack(wixie, completed, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos,
                    SourceStoneFurnaceBlockEntity.SOURCE_COST * job.operations,
                    completed, job.finalOutput, job.operations);
            machineJobs.remove(entry.getKey());
            sync();
        }
        for (Map.Entry<BlockPos, PendingApparatusJob> entry : new ArrayList<>(apparatusJobs.entrySet())) {
            PendingApparatusJob job = entry.getValue();
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            EnchantingApparatusTile apparatus = level.getBlockEntity(job.apparatusPos)
                    instanceof EnchantingApparatusTile found ? found : null;
            if (wixie == null || apparatus == null) {
                if (apparatus != null) {
                    returnRecovered(wixie, apparatus.removeItem(0, apparatus.getItem(0).getCount()));
                }
                cleanupApparatusPedestals(wixie, job.pedestalPositions);
                apparatusJobs.remove(entry.getKey());
                sync();
                continue;
            }
            if (apparatus.isCrafting) continue;
            ItemStack completed = apparatus.getItem(0);
            if (completed.isEmpty() || completed.getItem() != job.output.getItem()) {
                cleanupApparatusPedestals(wixie, job.pedestalPositions);
                apparatusJobs.remove(entry.getKey());
                sync();
                continue;
            }
            ItemStack actualOutput = apparatus.removeItemNoUpdate(0);
            cleanupApparatusPedestals(wixie, job.pedestalPositions);
            routeStack(wixie, actualOutput, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos, job.sourceCost, job.output, job.finalOutput);
            apparatusJobs.remove(entry.getKey());
            sync();
        }
        for (Map.Entry<BlockPos, PendingImbuementJob> entry : new ArrayList<>(imbuementJobs.entrySet())) {
            PendingImbuementJob job = entry.getValue();
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            ImbuementTile chamber = level.getBlockEntity(job.chamberPos)
                    instanceof ImbuementTile found ? found : null;
            if (wixie == null || chamber == null) {
                if (chamber != null) {
                    returnRecovered(wixie, chamber.removeItem(0, chamber.getItem(0).getCount()));
                }
                cleanupImbuementPedestals(wixie, job.pedestalPositions);
                imbuementJobs.remove(entry.getKey());
                sync();
                continue;
            }
            ItemStack completed = chamber.getItem(0);
            if (!ItemStack.isSameItemSameComponents(completed, job.output)) continue;
            // ImbuementTile#removeItemNoUpdate only marks the server tile dirty.
            // Using the normal container extraction also calls updateBlock(), so
            // clients immediately stop rendering the completed item.
            ItemStack actualOutput = chamber.removeItem(0, completed.getCount());
            cleanupImbuementPedestals(wixie, job.pedestalPositions);
            routeStack(wixie, actualOutput, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos, job.sourceCost, actualOutput, job.finalOutput);
            imbuementJobs.remove(entry.getKey());
            sync();
        }
        for (Map.Entry<BlockPos, PendingReactionJob> entry : new ArrayList<>(reactionJobs.entrySet())) {
            PendingReactionJob job = entry.getValue();
            if (!(level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie)
                    || !(level.getBlockEntity(job.vesselPos) instanceof ArcaneReactionVesselBlockEntity vessel)) {
                reactionJobs.remove(entry.getKey());
                sync();
                continue;
            }
            if (vessel.isOrphanedNetworkJob()) {
                vessel.releaseOrphanedNetworkJob();
                reactionJobs.remove(entry.getKey());
                ArsArcaneMatrix.LOGGER.warn(
                        "Released orphaned Arcane Reaction Vessel job at {} for {}",
                        job.vesselPos, job.output);
                sync();
                continue;
            }
            ItemStack completed = vessel.takeNetworkResult(job.output);
            if (completed.isEmpty()) continue;
            routeStack(wixie, completed, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos, job.sourceCost, completed,
                    job.finalOutput, job.operations);
            reactionJobs.remove(entry.getKey());
            sync();
        }
        for (Map.Entry<BlockPos, PendingCookingPotJob> entry
                : new ArrayList<>(cookingPotJobs.entrySet())) {
            PendingCookingPotJob job = entry.getValue();
            if (!(level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie)) {
                cookingPotJobs.remove(entry.getKey());
                sync();
                continue;
            }
            BlockEntity pot = level.getBlockEntity(job.potPos);
            ItemStackHandler inventory = farmersDelightPotInventory(pot);
            if (inventory == null) {
                cookingPotJobs.remove(entry.getKey());
                sync();
                continue;
            }
            int outputSlot = matchingPotOutputSlot(inventory, job.output);
            if (outputSlot < 0) continue;
            if (inventory.getStackInSlot(outputSlot).getCount() < job.output.getCount()) continue;
            ItemStack actualOutput = inventory.extractItem(
                    outputSlot, job.output.getCount(), false);
            routeStack(wixie, actualOutput, job.finalOutput
                    ? getWixieOutputInventories() : getWixieInventories());
            notifyTerminal(job.terminalPos, 0, actualOutput, job.finalOutput, job.operations);
            cookingPotJobs.remove(entry.getKey());
            pot.setChanged();
            sync();
        }
        for (Map.Entry<BlockPos, PendingCompressorJob> entry
                : new ArrayList<>(compressorJobs.entrySet())) {
            PendingCompressorJob job = entry.getValue();
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            Container compressor = level.getBlockEntity(job.compressorPos)
                    instanceof Container found ? found : null;
            if (wixie == null || compressor == null || compressor.getContainerSize() < 2) {
                returnCompressorQueue(wixie, job);
                compressorJobs.remove(entry.getKey());
                sync();
                continue;
            }
            ItemStack completed = compressor.getItem(0);
            if (ItemStack.isSameItemSameComponents(completed, job.output)) {
                ItemStack actualOutput = compressor.removeItem(0, completed.getCount());
                returnCompressorQueue(wixie, job);
                routeStack(wixie, actualOutput, job.finalOutput
                        ? getWixieOutputInventories() : getWixieInventories());
                notifyTerminal(job.terminalPos, 0, actualOutput, job.finalOutput);
                compressor.setChanged();
                compressorJobs.remove(entry.getKey());
                sync();
                continue;
            }
            feedCompressor(compressor, job);
        }
    }

    /**
     * A client/server crash can save the Wixie's crafting manager without saving
     * this provider's matching PendingJob. Such a worker looks permanently busy
     * while the terminal reports zero active jobs. Only managers created by this
     * provider are eligible for recovery; native Wixie work is left untouched.
     */
    private void recoverOrphanedNetworkManagers() {
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos();
            if (!(wixie.craftManager instanceof NetworkWixieCraftingManager)
                    || activeJobs.containsKey(wixiePos)) continue;
            ArsArcaneMatrix.LOGGER.warn(
                    "Released orphaned Wixie network task at {} for provider {}",
                    wixiePos, worldPosition);
            // Ars Nouveau assumes this field is always non-null in both the
            // cauldron tick and the Wixie's CompleteCraftingGoal. Restore the
            // same empty manager used by a newly placed Wixie cauldron rather
            // than clearing the field outright.
            wixie.craftManager = new CraftingManager();
            wixie.hasSource = false;
            wixie.setChanged();
        }
    }

    /** Reserves one Wixie and one real Source Stone Furnace for a cooking step. */
    public boolean startMachineJob(
            BlockPos terminal, ItemStack output, List<ItemStack> ingredients,
            boolean finalOutput, int operations) {
        if (level == null || output.isEmpty() || ingredients.size() != 1
                || ingredients.getFirst().isEmpty()) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (workerBusy(wixie)) continue;
            for (SourceStoneFurnaceBlockEntity furnace : getSourceStoneFurnaces()) {
                if (furnace.isNetworkReserved()) continue;
                furnace.takeUnreservedContents().forEach(stack -> routeToWixieInput(wixie, stack));
                if (!furnace.isAvailableForNetworkJob()) continue;
                ExtractionResult extraction = extractIngredients(wixie, terminal, ingredients);
                if (extraction == null || extraction.stacks.isEmpty()) return false;
                ItemStack input = extraction.stacks.getFirst().copy();
                boolean compatibleBatch = true;
                for (int index = 1; index < extraction.stacks.size(); index++) {
                    ItemStack part = extraction.stacks.get(index);
                    if (!ItemStack.isSameItemSameComponents(input, part)
                            || input.getCount() + part.getCount() > input.getMaxStackSize()) {
                        compatibleBatch = false;
                        break;
                    }
                    input.grow(part.getCount());
                }
                if (!compatibleBatch) {
                    extraction.stacks.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                if (!furnace.startNetworkJob(input)) {
                    extraction.stacks.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                machineJobs.put(wixiePos, new PendingMachineJob(
                        terminal.immutable(), furnace.getBlockPos().immutable(),
                        output.copy(), finalOutput, Math.max(1, operations)));
                ArsArcaneMatrix.LOGGER.info(
                        "Started Wixie cooking batch: provider={}, wixie={}, furnace={}, input={}, expected={}",
                        worldPosition, wixiePos, furnace.getBlockPos(), input, output);
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

    /** Reserves one Wixie and drives a real Ars Nouveau Enchanting Apparatus. */
    public boolean startApparatusJob(
            BlockPos terminal,
            EnchantingApparatusRecipe recipe,
            ItemStack output,
            List<ItemStack> ingredients,
            boolean finalOutput
    ) {
        if (level == null || output.isEmpty()
                || ingredients.size() != recipe.pedestalItems().size() + 1) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (workerBusy(wixie)) continue;
            for (EnchantingApparatusTile apparatus : getEnchantingApparatuses()) {
                if (!apparatusAvailable(apparatus, recipe)) continue;
                List<BlockPos> apparatusPedestals = apparatus.pedestalList().stream()
                        .filter(pos -> level.getBlockEntity(pos) instanceof ArcanePedestalTile)
                        .map(BlockPos::immutable)
                        .toList();
                if (apparatusPedestals.stream().anyMatch(reservedPedestalPositions()::contains)) continue;
                List<BlockPos> availablePedestals = apparatusPedestals.stream()
                        .limit(recipe.pedestalItems().size())
                        .toList();
                if (availablePedestals.size() != recipe.pedestalItems().size()) continue;

                // Return old pedestal contents before checking the new recipe's
                // inventory. The returned stacks may themselves be ingredients.
                cleanupApparatusPedestals(wixie, apparatusPedestals);
                List<ItemStack> extracted = extractPhysicalIngredients(wixie, ingredients);
                if (extracted == null) return false;
                ItemStack reagent = extracted.getFirst();
                List<ItemStack> pedestalStacks = extracted.subList(1, extracted.size());
                for (int index = 0; index < availablePedestals.size(); index++) {
                    ArcanePedestalTile pedestal = (ArcanePedestalTile) level.getBlockEntity(
                            availablePedestals.get(index));
                    pedestal.setStack(pedestalStacks.get(index));
                }
                if (!recipe.matches(new ApparatusRecipeInput(
                        reagent, pedestalStacks, null), level, null)) {
                    routeToWixieInput(wixie, reagent);
                    cleanupApparatusPedestals(wixie, availablePedestals);
                    continue;
                }

                apparatus.setItem(0, reagent);
                if (!apparatus.isCrafting) {
                    routeToWixieInput(wixie, apparatus.removeItemNoUpdate(0));
                    cleanupApparatusPedestals(wixie, availablePedestals);
                    continue;
                }
                apparatusJobs.put(wixiePos, new PendingApparatusJob(
                        terminal.immutable(), apparatus.getBlockPos().immutable(),
                        availablePedestals, output.copy(), recipe.sourceCost(), finalOutput));
                sync();
                return true;
            }
        }
        return false;
    }

    /** Reserves one Wixie and drives a real ordinary or Advanced Imbuement Chamber. */
    public boolean startImbuementJob(
            BlockPos terminal,
            ImbuementRecipe recipe,
            ItemStack output,
            List<ItemStack> ingredients,
            boolean finalOutput
    ) {
        if (level == null || output.isEmpty()
                || ingredients.size() != recipe.getPedestalItems().size() + 1) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (workerBusy(wixie)) continue;
            List<ImbuementTile> chambers = getImbuementChambers();
            if (chambers.isEmpty()) {
                ArsArcaneMatrix.LOGGER.debug(
                        "Wixie imbuement job found no ordinary or advanced chamber near provider {}",
                        worldPosition);
            }
            for (ImbuementTile chamber : chambers) {
                List<BlockPos> chamberPedestals = chamber.getNearbyPedestals().stream()
                        .filter(pos -> level.getBlockEntity(pos) instanceof ArcanePedestalTile)
                        .map(BlockPos::immutable)
                        .toList();
                if (chamberPedestals.stream().anyMatch(reservedPedestalPositions()::contains)) continue;
                ItemStack existingInput = chamber.getItem(0);
                if (!existingInput.isEmpty()) {
                    // A failed manual recipe check can leave an input in the chamber.
                    // Reclaim only invalid residue; never interrupt a valid manual craft.
                    if (chamber.getRecipeNow() != null) continue;
                    ItemStack recovered = chamber.removeItem(0, existingInput.getCount());
                    if (!recovered.isEmpty()) routeToWixieInput(wixie, recovered);
                }
                if (!chamber.getItem(0).isEmpty()) continue;
                boolean reusePedestals = EnchantingApparatusRecipe.doItemsMatch(
                        chamber.getPedestalItems(), recipe.getPedestalItems());
                if (!reusePedestals && !chamber.getPedestalItems().isEmpty()) {
                    cleanupImbuementPedestals(wixie, chamberPedestals);
                }
                List<BlockPos> emptyPedestals = chamberPedestals.stream()
                        .filter(pos -> level.getBlockEntity(pos) instanceof ArcanePedestalTile pedestal
                                && pedestal.getItem(0).isEmpty())
                        .limit(recipe.getPedestalItems().size())
                        .toList();
                if (!reusePedestals && emptyPedestals.size() != recipe.getPedestalItems().size()) {
                    ArsArcaneMatrix.LOGGER.debug(
                            "Wixie imbuement chamber {} has {}/{} empty pedestals for recipe {}",
                            chamber.getBlockPos(), emptyPedestals.size(),
                            recipe.getPedestalItems().size(), recipe);
                    continue;
                }

                List<ItemStack> extracted = extractPhysicalIngredients(wixie,
                        reusePedestals ? List.of(ingredients.getFirst()) : ingredients);
                if (extracted == null) return false;
                ItemStack reagent = extracted.getFirst();
                if (!reusePedestals) {
                    List<ItemStack> pedestalStacks = extracted.subList(1, extracted.size());
                    for (int index = 0; index < emptyPedestals.size(); index++) {
                        ArcanePedestalTile pedestal = (ArcanePedestalTile) level.getBlockEntity(
                                emptyPedestals.get(index));
                        pedestal.setStack(pedestalStacks.get(index));
                    }
                }
                chamber.setItem(0, reagent);
                if (!recipe.matches(chamber, level)) {
                    routeToWixieInput(wixie,
                            chamber.removeItem(0, chamber.getItem(0).getCount()));
                    cleanupImbuementPedestals(wixie, emptyPedestals);
                    continue;
                }
                imbuementJobs.put(wixiePos, new PendingImbuementJob(
                        terminal.immutable(), chamber.getBlockPos().immutable(),
                        reusePedestals ? chamberPedestals : emptyPedestals,
                        output.copy(), recipe.getSource(), finalOutput));
                sync();
                return true;
            }
        }
        return false;
    }

    private boolean workerBusy(WixieCauldronTile wixie) {
        BlockPos pos = wixie.getBlockPos();
        return activeJobs.containsKey(pos) || machineJobs.containsKey(pos)
                || apparatusJobs.containsKey(pos) || imbuementJobs.containsKey(pos)
                || reactionJobs.containsKey(pos) || cookingPotJobs.containsKey(pos)
                || compressorJobs.containsKey(pos)
                || wixie.craftManager != null && !wixie.craftManager.isCraftCompleted();
    }

    /** Reserves one Wixie and one real Arcane Reaction Vessel until its timed process completes. */
    public boolean startReactionJob(
            BlockPos terminal, ArcaneReactionRule recipe, ItemStack output,
            List<ItemStack> ingredients, boolean finalOutput, int operations
    ) {
        if (level == null || output.isEmpty() || ingredients.isEmpty()) return false;
        // Reaction automation is deliberately an Advanced Storage Lectern feature:
        // its linked fluid network supplies the exact batch amount directly, without
        // fabricating buckets or requiring a pre-filled vessel.
        AdvancedStorageLecternBlockEntity lectern =
                level.getBlockEntity(terminal) instanceof AdvancedStorageLecternBlockEntity found
                        ? found : null;
        if (lectern == null) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (workerBusy(wixie)) continue;
            for (ArcaneReactionVesselBlockEntity vessel : getReactionVessels()) {
                if (!vessel.canPrepareNetworkJob(recipe)) continue;
                int safeOperations = Math.max(1, operations);
                int missingFluid = vessel.missingInputFluid(recipe, safeOperations);
                if (missingFluid < 0) continue;
                List<ItemStack> extracted = extractPhysicalIngredients(wixie, ingredients);
                if (extracted == null) return false;
                if (missingFluid > 0 && !lectern.supplyLinkedFluid(
                        vessel.fluidHandler(null), recipe.inputFluid(), missingFluid)) {
                    extracted.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                if (!vessel.isAvailableForNetworkJob(recipe, safeOperations)) {
                    extracted.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                if (!vessel.startNetworkJob(recipe, extracted, safeOperations)) {
                    extracted.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                reactionJobs.put(wixiePos, new PendingReactionJob(
                        terminal.immutable(), vessel.getBlockPos().immutable(), output.copy(),
                        recipe.sourceCost() * safeOperations, finalOutput, safeOperations));
                sync();
                return true;
            }
        }
        return false;
    }

    private List<ArcaneReactionVesselBlockEntity> getReactionVessels() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .map(level::getBlockEntity)
                .filter(ArcaneReactionVesselBlockEntity.class::isInstance)
                .map(ArcaneReactionVesselBlockEntity.class::cast)
                .sorted(Comparator.comparingDouble(vessel ->
                        vessel.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    private List<EnchantingApparatusTile> getEnchantingApparatuses() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .map(level::getBlockEntity)
                .filter(EnchantingApparatusTile.class::isInstance)
                .map(EnchantingApparatusTile.class::cast)
                .sorted(Comparator.comparingDouble(apparatus ->
                        apparatus.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    private List<ImbuementTile> getImbuementChambers() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .map(level::getBlockEntity)
                .filter(ImbuementTile.class::isInstance)
                .map(ImbuementTile.class::cast)
                .sorted(Comparator.comparingDouble(chamber ->
                        chamber.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    /** Counts catalysts already installed on an idle chamber for this exact recipe. */
    public int countReusableImbuementCatalyst(ImbuementRecipe recipe, ItemStack template) {
        int count = 0;
        Set<BlockPos> reserved = reservedPedestalPositions();
        for (ImbuementTile chamber : getImbuementChambers()) {
            if (!chamber.getItem(0).isEmpty()
                    || chamber.getNearbyPedestals().stream().anyMatch(reserved::contains)
                    || !EnchantingApparatusRecipe.doItemsMatch(
                    chamber.getPedestalItems(), recipe.getPedestalItems())) continue;
            for (ItemStack pedestal : chamber.getPedestalItems()) {
                if (ItemStack.isSameItemSameComponents(pedestal, template)) {
                    count += pedestal.getCount();
                }
            }
        }
        return count;
    }

    /** Adds installed catalysts to ingredient selection without exposing them to other recipes. */
    public List<ItemStack> getReusableImbuementCatalysts(ImbuementRecipe recipe) {
        List<ItemStack> result = new ArrayList<>();
        Set<BlockPos> reserved = reservedPedestalPositions();
        for (ImbuementTile chamber : getImbuementChambers()) {
            if (!chamber.getItem(0).isEmpty()
                    || chamber.getNearbyPedestals().stream().anyMatch(reserved::contains)
                    || !EnchantingApparatusRecipe.doItemsMatch(
                    chamber.getPedestalItems(), recipe.getPedestalItems())) continue;
            chamber.getPedestalItems().forEach(stack -> result.add(stack.copy()));
        }
        return List.copyOf(result);
    }

    private Set<BlockPos> reservedPedestalPositions() {
        Set<BlockPos> reserved = new HashSet<>();
        apparatusJobs.values().forEach(job -> reserved.addAll(job.pedestalPositions));
        imbuementJobs.values().forEach(job -> reserved.addAll(job.pedestalPositions));
        return reserved;
    }

    private boolean apparatusAvailable(
            EnchantingApparatusTile apparatus, EnchantingApparatusRecipe recipe
    ) {
        if (level == null || apparatus.isCrafting || !apparatus.getItem(0).isEmpty()) return false;
        BlockState apparatusState = apparatus.getBlockState();
        if (!apparatusState.hasProperty(BlockStateProperties.FACING)) return false;
        var facing = apparatusState.getValue(BlockStateProperties.FACING);
        BlockState coreState = level.getBlockState(apparatus.getBlockPos().relative(facing.getOpposite()));
        if (!(coreState.getBlock() instanceof ArcaneCore)
                || !coreState.hasProperty(BlockStateProperties.FACING)
                || !coreState.getValue(BlockStateProperties.FACING).getAxis().test(facing)) return false;
        return !recipe.consumesSource() || SourceUtil.hasSourceNearby(
                apparatus.getBlockPos(), level, 10, recipe.sourceCost());
    }

    private List<ItemStack> extractPhysicalIngredients(
            WixieCauldronTile wixie, List<ItemStack> ingredients
    ) {
        // Planning treats every input bound to this provider's Wixies as one
        // network. Extraction must use the same view or a worker can be selected
        // whose personal binding does not contain all planned ingredients.
        List<IItemHandler> inventories = getWixieInventories();
        List<ItemStack> extracted = new ArrayList<>();
        for (ItemStack ingredient : ingredients) {
            ItemStack combined = ItemStack.EMPTY;
            int remaining = ingredient.getCount();
            for (IItemHandler inventory : inventories) {
                for (int slot = 0; slot < inventory.getSlots() && remaining > 0; slot++) {
                    ItemStack available = inventory.getStackInSlot(slot);
                    if (!ItemStack.isSameItemSameComponents(available, ingredient)) continue;
                    ItemStack taken = inventory.extractItem(slot, remaining, false);
                    if (taken.isEmpty()) continue;
                    if (combined.isEmpty()) combined = taken.copy();
                    else combined.grow(taken.getCount());
                    remaining -= taken.getCount();
                }
            }
            if (remaining > 0) {
                extracted.forEach(stack -> routeToWixieInput(wixie, stack));
                if (!combined.isEmpty()) routeToWixieInput(wixie, combined);
                return null;
            }
            extracted.add(combined);
        }
        return extracted;
    }

    private void cleanupApparatusPedestals(
            WixieCauldronTile wixie, List<BlockPos> pedestalPositions
    ) {
        if (level == null) return;
        for (BlockPos pos : pedestalPositions) {
            if (level.getBlockEntity(pos) instanceof ArcanePedestalTile pedestal) {
                returnRecovered(wixie, pedestal.removeItemNoUpdate(0));
            }
        }
    }

    private void cleanupImbuementPedestals(
            WixieCauldronTile wixie, List<BlockPos> pedestalPositions
    ) {
        cleanupApparatusPedestals(wixie, pedestalPositions);
    }

    private void returnRecovered(WixieCauldronTile wixie, ItemStack stack) {
        if (level == null || stack.isEmpty()) return;
        if (wixie != null) {
            routeToWixieInput(wixie, stack);
        } else {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack);
        }
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
            if (workerBusy(wixie)) {
                continue;
            }
            PendingJob job = new PendingJob(
                    terminal.immutable(), output.copy(), copyStacks(ingredients),
                    copyStacks(remainders), finalOutput, 1, null);
            activeJobs.put(wixiePos, job);
            if (installAndConsumeWixieJob(wixie, job, job.ingredients)) {
                return true;
            }
            activeJobs.remove(wixiePos);
        }
        return false;
    }

    /** Reserves one Wixie and one real vanilla Stonecutter for a stonecutting step. */
    public boolean startStonecutterJob(
            BlockPos terminal, ItemStack output, List<ItemStack> ingredients, boolean finalOutput
    ) {
        if (level == null || output.isEmpty() || ingredients.size() != 1
                || ingredients.getFirst().isEmpty()) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            if (workerBusy(wixie)) continue;
            BlockPos stonecutter = getAvailableStonecutters().stream().findFirst().orElse(null);
            if (stonecutter == null) return false;
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            PendingJob job = new PendingJob(
                    terminal.immutable(), output.copy(), copyStacks(ingredients),
                    new ArrayList<>(), finalOutput, 1, stonecutter);
            activeJobs.put(wixiePos, job);
            if (installAndConsumeWixieJob(wixie, job, job.ingredients)) return true;
            activeJobs.remove(wixiePos);
        }
        return false;
    }

    private List<BlockPos> getAvailableStonecutters() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .filter(pos -> level.getBlockState(pos).is(Blocks.STONECUTTER))
                .map(BlockPos::immutable)
                .filter(pos -> activeJobs.values().stream()
                        .noneMatch(job -> pos.equals(job.workstationPos)))
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(worldPosition)))
                .toList();
    }

    /** Uses a real heated Farmer's Delight Cooking Pot and its native cook time. */
    public boolean startCookingPotJob(
            BlockPos terminal, Recipe<?> recipe, ItemStack output,
            List<ItemStack> ingredients, boolean finalOutput, int operations
    ) {
        if (level == null || output.isEmpty() || ingredients.isEmpty()) return false;
        int foodCount = (int) recipe.getIngredients().stream()
                .filter(ingredient -> !ingredient.isEmpty()).count();
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            if (workerBusy(wixie)) continue;
            for (BlockEntity pot : getAvailableCookingPots()) {
                ItemStackHandler inventory = farmersDelightPotInventory(pot);
                if (inventory == null || !potIsEmpty(inventory)
                        || !farmersDelightPotHeated(pot)) continue;
                List<ItemStack> extracted = extractPhysicalIngredients(wixie, ingredients);
                if (extracted == null) return false;
                List<ItemStack> supplied = extracted.stream()
                        .filter(stack -> !stack.isEmpty()).toList();
                if (supplied.size() < foodCount || foodCount > 6) {
                    extracted.forEach(stack -> routeToWixieInput(wixie, stack));
                    continue;
                }
                for (int index = 0; index < foodCount; index++) {
                    inventory.setStackInSlot(index, supplied.get(index).copy());
                }
                if (supplied.size() > foodCount) {
                    inventory.setStackInSlot(7, supplied.get(foodCount).copy());
                }
                BlockPos wixiePos = wixie.getBlockPos().immutable();
                cookingPotJobs.put(wixiePos, new PendingCookingPotJob(
                        terminal.immutable(), pot.getBlockPos().immutable(),
                        output.copy(), finalOutput, Math.max(1, operations)));
                pot.setChanged();
                sync();
                return true;
            }
        }
        return false;
    }

    /** Uses a real Re-Avaritia table of the recipe's tier or a higher tier. */
    public boolean startAvaritiaJob(
            BlockPos terminal, Recipe<?> recipe, ItemStack output,
            List<ItemStack> ingredients, boolean finalOutput
    ) {
        int tier = RecipeAutomationSupport.avaritiaTier(recipe);
        if (level == null || tier < 0 || output.isEmpty()) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            if (workerBusy(wixie)) continue;
            BlockPos table = getAvailableAvaritiaTables(tier).stream().findFirst().orElse(null);
            if (table == null) return false;
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            PendingJob job = new PendingJob(
                    terminal.immutable(), output.copy(), copyStacks(ingredients),
                    new ArrayList<>(), finalOutput, 1, table);
            activeJobs.put(wixiePos, job);
            if (installAndConsumeWixieJob(wixie, job, job.ingredients)) return true;
            activeJobs.remove(wixiePos);
        }
        return false;
    }

    /** Reserves a real Re-Avaritia compressor and feeds its large input in safe stack-sized chunks. */
    public boolean startAvaritiaCompressorJob(
            BlockPos terminal, Recipe<?> recipe, ItemStack output, List<ItemStack> ingredients,
            boolean finalOutput
    ) {
        if (level == null || output.isEmpty() || ingredients.size() != 1
                || ingredients.getFirst().isEmpty()) return false;
        for (WixieCauldronTile wixie : getWixieWorkers()) {
            BlockPos wixiePos = wixie.getBlockPos().immutable();
            if (workerBusy(wixie)) continue;
            for (Container compressor : getAvailableAvaritiaCompressors()) {
                ResourceLocation compressorId = compressorBlockId(compressor);
                int declaredInput = ingredients.getFirst().getCount();
                if (compressorInputForBase(compressorId,
                        RecipeAutomationSupport.avaritiaCompressorInputCount(recipe))
                        != declaredInput) continue;
                if (compressorMaterialCount(compressor) > 0 && !recoverCompressorMaterials(compressor, wixie)) continue;
                ExtractionResult extraction = extractIngredients(wixie, terminal, ingredients);
                if (extraction == null || extraction.stacks.isEmpty()) return false;
                ItemStack expectedOutput = output.copyWithCount(
                        output.getCount() * compressorOutputMultiplier(compressorId));
                PendingCompressorJob job = new PendingCompressorJob(
                        terminal.immutable(), ((BlockEntity) compressor).getBlockPos().immutable(),
                        expectedOutput, new ArrayList<>(extraction.stacks), finalOutput);
                compressorJobs.put(wixiePos, job);
                feedCompressor(compressor, job);
                sync();
                return true;
            }
        }
        return false;
    }

    private List<Container> getAvailableAvaritiaCompressors() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .filter(pos -> isAvaritiaCompressorBlock(BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(pos).getBlock())))
                .filter(pos -> compressorJobs.values().stream()
                        .noneMatch(job -> pos.equals(job.compressorPos)))
                .map(level::getBlockEntity)
                .filter(blockEntity -> blockEntity instanceof Container)
                .map(blockEntity -> (Container) blockEntity)
                .filter(compressor -> compressor.getContainerSize() >= 2
                        && compressor.getItem(0).isEmpty() && compressor.getItem(1).isEmpty()
                        && !compressorLocked(compressor))
                .sorted(Comparator.comparingInt((Container compressor) -> compressorMaterialCount(compressor) == 0 ? 0 : 1)
                        .thenComparingDouble(compressor ->
                        ((BlockEntity) compressor).getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    private static boolean isAvaritiaCompressorBlock(ResourceLocation id) {
        if (id == null || !"avaritia".equals(id.getNamespace())) return false;
        return switch (id.getPath()) {
            case "neutron_compressor", "dense_neutron_compressor",
                    "denser_neutron_compressor", "densest_neutron_compressor" -> true;
            default -> false;
        };
    }

    private static boolean compressorLocked(Container compressor) {
        try {
            return Boolean.TRUE.equals(compressor.getClass().getMethod("isRecipeLocked").invoke(compressor));
        } catch (ReflectiveOperationException exception) { return true; }
    }

    private boolean recoverCompressorMaterials(Container compressor, WixieCauldronTile wixie) {
        try {
            ItemStack material = (ItemStack) compressor.getClass().getMethod("getMaterialStack").invoke(compressor);
            int count = compressorMaterialCount(compressor);
            if (material.isEmpty() || count <= 0 || compressorLocked(compressor)) return false;
            Object packed = Class.forName("committee.nova.mods.avaritia.common.item.resources.MatterClusterItem")
                    .getMethod("makeClusters", java.util.Collection.class)
                    .invoke(null, List.of(material.copyWithCount(count)));
            if (!(packed instanceof List<?> clusters) || clusters.size() != 1
                    || !(clusters.getFirst() instanceof ItemStack cluster) || cluster.isEmpty()) return false;
            for (IItemHandler handler : getWixieInventories(wixie)) {
                if (!ItemHandlerHelper.insertItemStacked(handler, cluster.copy(), true).isEmpty()) continue;
                // Use the native clear operation, keeping no oversized vanilla stack in storage.
                compressor.getClass().getMethod("clearMaterials").invoke(compressor);
                ItemStack remaining = ItemHandlerHelper.insertItemStacked(handler, cluster.copy(), false);
                if (!remaining.isEmpty()) routeToWixieInput(wixie, remaining);
                return true;
            }
        } catch (ReflectiveOperationException | LinkageError | RuntimeException exception) {
            return false;
        }
        return false;
    }

    private static ResourceLocation compressorBlockId(Container compressor) {
        return compressor instanceof BlockEntity blockEntity
                ? BuiltInRegistries.BLOCK.getKey(blockEntity.getBlockState().getBlock()) : null;
    }

    private static int compressorInputForBase(ResourceLocation id, int baseInput) {
        if (id == null) return baseInput;
        return switch (id.getPath()) {
            case "denser_neutron_compressor" -> (baseInput * 3 + 3) / 4;
            case "densest_neutron_compressor" -> (baseInput + 1) / 2;
            default -> baseInput;
        };
    }

    private static int compressorOutputMultiplier(ResourceLocation id) {
        return id != null && "densest_neutron_compressor".equals(id.getPath()) ? 2 : 1;
    }

    public int getMinimumAvailableAvaritiaCompressorInput(int baseInput) {
        return getAvailableAvaritiaCompressors().stream()
                .map(WixiePatternProviderBlockEntity::compressorBlockId)
                .mapToInt(id -> compressorInputForBase(id, baseInput))
                .min().orElse(Math.max(1, baseInput));
    }

    public int getMaximumAvailableAvaritiaCompressorOutputMultiplier() {
        return getAvailableAvaritiaCompressors().stream()
                .map(WixiePatternProviderBlockEntity::compressorBlockId)
                .mapToInt(WixiePatternProviderBlockEntity::compressorOutputMultiplier)
                .max().orElse(1);
    }

    private static int compressorMaterialCount(Container compressor) {
        try {
            Object value = compressor.getClass().getMethod("getMaterialCount").invoke(compressor);
            return value instanceof Number number ? Math.max(0, number.intValue()) : -1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    private static void feedCompressor(Container compressor, PendingCompressorJob job) {
        if (!compressor.getItem(1).isEmpty()) return;
        while (!job.queuedInputs.isEmpty() && job.queuedInputs.getFirst().isEmpty()) {
            job.queuedInputs.removeFirst();
        }
        if (job.queuedInputs.isEmpty()) return;
        ItemStack queued = job.queuedInputs.getFirst();
        int amount = Math.min(queued.getCount(), queued.getMaxStackSize());
        compressor.setItem(1, queued.copyWithCount(amount));
        queued.shrink(amount);
        if (queued.isEmpty()) job.queuedInputs.removeFirst();
        compressor.setChanged();
    }

    private void returnCompressorQueue(WixieCauldronTile wixie, PendingCompressorJob job) {
        for (ItemStack queued : job.queuedInputs) {
            returnRecovered(wixie, queued);
        }
        job.queuedInputs.clear();
    }

    private void recoverCompressorMaterials(WixieCauldronTile wixie, Container compressor) {
        try {
            int count = compressorMaterialCount(compressor);
            Object value = compressor.getClass().getMethod("getMaterialStack").invoke(compressor);
            if (count > 0 && value instanceof ItemStack material && !material.isEmpty()) {
                int remaining = count;
                while (remaining > 0) {
                    int amount = Math.min(remaining, material.getMaxStackSize());
                    returnRecovered(wixie, material.copyWithCount(amount));
                    remaining -= amount;
                }
            }
            compressor.getClass().getMethod("clearMaterials").invoke(compressor);
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // The compressor remains untouched if an incompatible optional-mod version is present.
        }
    }

    private List<BlockEntity> getAvailableCookingPots() {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .filter(pos -> RecipeAutomationSupport.FARMERS_DELIGHT_COOKING_POT.equals(
                        BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos).getBlock())))
                .filter(pos -> cookingPotJobs.values().stream()
                        .noneMatch(job -> pos.equals(job.potPos)))
                .map(level::getBlockEntity)
                .filter(java.util.Objects::nonNull)
                .sorted(Comparator.comparingDouble(pot -> pot.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    private List<BlockPos> getAvailableAvaritiaTables(int minimumTier) {
        if (level == null) return List.of();
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-WORKSTATION_RADIUS, -WORKSTATION_VERTICAL_RADIUS,
                                -WORKSTATION_RADIUS),
                        worldPosition.offset(WORKSTATION_RADIUS, WORKSTATION_VERTICAL_RADIUS,
                                WORKSTATION_RADIUS))
                .filter(level::hasChunkAt)
                .filter(pos -> avaritiaTableTier(BuiltInRegistries.BLOCK.getKey(
                        level.getBlockState(pos).getBlock())) >= minimumTier)
                .filter(pos -> activeJobs.values().stream()
                        .noneMatch(job -> pos.equals(job.workstationPos)))
                .map(BlockPos::immutable)
                .sorted(Comparator.comparingDouble(pos -> pos.distSqr(worldPosition)))
                .toList();
    }

    private static int avaritiaTableTier(ResourceLocation id) {
        if (RecipeAutomationSupport.AVARITIA_SCULK_CRAFTING_TABLE.equals(id)) return 1;
        if (RecipeAutomationSupport.AVARITIA_NETHER_CRAFTING_TABLE.equals(id)) return 2;
        if (RecipeAutomationSupport.AVARITIA_END_CRAFTING_TABLE.equals(id)) return 3;
        if (RecipeAutomationSupport.AVARITIA_EXTREME_CRAFTING_TABLE.equals(id)) return 4;
        return -1;
    }

    private static ItemStackHandler farmersDelightPotInventory(BlockEntity pot) {
        if (pot == null || !pot.getClass().getName().equals(
                "vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity")) return null;
        try {
            Object inventory = pot.getClass().getMethod("getInventory").invoke(pot);
            return inventory instanceof ItemStackHandler handler ? handler : null;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return null;
        }
    }

    private static boolean farmersDelightPotHeated(BlockEntity pot) {
        try {
            return Boolean.TRUE.equals(pot.getClass().getMethod("isHeated").invoke(pot));
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return false;
        }
    }

    private static boolean potIsEmpty(ItemStackHandler inventory) {
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            if (!inventory.getStackInSlot(slot).isEmpty()) return false;
        }
        return true;
    }

    private static int matchingPotOutputSlot(ItemStackHandler inventory, ItemStack expected) {
        for (int slot : new int[]{8, 6}) {
            if (ItemStack.isSameItemSameComponents(inventory.getStackInSlot(slot), expected)) {
                return slot;
            }
        }
        return -1;
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
        // All Wixies attached to one provider cooperate on the same order network.
        // Keep physical extraction consistent with the terminal's inventory scan.
        List<IItemHandler> inventories = getWixieInventories();
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
        notifyTerminal(terminalPos, sourceCost, output, finalOutput, 1);
    }

    private void notifyTerminal(BlockPos terminalPos, int sourceCost,
                                ItemStack output, boolean finalOutput, int operations) {
        if (level == null || !level.hasChunkAt(terminalPos)) return;
        BlockEntity blockEntity = level.getBlockEntity(terminalPos);
        WixieOrderTerminalBlockEntity terminal = blockEntity instanceof WixieOrderTerminalBlockEntity direct
                ? direct : blockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern.getOrderEngine() : null;
        if (terminal != null) terminal.onWixieJobCompleted(
                sourceCost, output, finalOutput, operations);
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
                .comparing((ItemStack guide) -> resolvedGuideWorkstation(guide).toString())
                .thenComparing(this::guideResultName, String.CASE_INSENSITIVE_ORDER)
                .thenComparing(guide -> String.valueOf(CraftingGuideItem.getRecipeId(guide))));
    }

    private ResourceLocation resolvedGuideWorkstation(ItemStack guide) {
        return level == null
                ? CraftingGuideItem.getWorkstationId(guide)
                : CraftingGuideItem.getWorkstationId(guide, level.getRecipeManager());
    }

    private String guideResultName(ItemStack guide) {
        ItemStack result = level == null
                ? ItemStack.EMPTY
                : CraftingGuideItem.getRecordedResult(guide, level.registryAccess());
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
        return !activeJobs.isEmpty() || !machineJobs.isEmpty() || !apparatusJobs.isEmpty()
                || !imbuementJobs.isEmpty() || !reactionJobs.isEmpty()
                || !cookingPotJobs.isEmpty() || !compressorJobs.isEmpty();
    }

    public int getActiveWorkerCount() {
        return activeJobs.size() + machineJobs.size() + apparatusJobs.size()
                + imbuementJobs.size() + reactionJobs.size() + cookingPotJobs.size()
                + compressorJobs.size();
    }

    /** Compact, stable diagnostics intended for the order terminal's copyable report. */
    public List<String> getActiveJobDiagnostics(BlockPos terminalPos) {
        List<String> result = new ArrayList<>();
        activeJobs.forEach((wixie, job) -> {
            if (terminalPos.equals(job.terminalPos)) result.add(diagnosticLine(
                    "crafting", job.workstationPos == null ? wixie : job.workstationPos, job.output));
        });
        machineJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> result.add(diagnosticLine("source_stone_furnace", job.furnacePos, job.output)));
        apparatusJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> result.add(diagnosticLine("enchanting_apparatus", job.apparatusPos, job.output)));
        imbuementJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> result.add(diagnosticLine("imbuement_chamber", job.chamberPos, job.output)));
        reactionJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> {
                    String line = diagnosticLine("arcane_reaction_vessel", job.vesselPos, job.output);
                    if (level != null && level.getBlockEntity(job.vesselPos)
                            instanceof ArcaneReactionVesselBlockEntity vessel) {
                        line += " [" + vessel.networkDiagnostic() + "]";
                    }
                    result.add(line);
                });
        cookingPotJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> result.add(diagnosticLine("cooking_pot", job.potPos, job.output)));
        compressorJobs.values().stream().filter(job -> terminalPos.equals(job.terminalPos))
                .forEach(job -> result.add(diagnosticLine(
                        "neutron_compressor", job.compressorPos, job.output)));
        return List.copyOf(result);
    }

    private static String diagnosticLine(String type, BlockPos pos, ItemStack output) {
        return type + " @ " + pos.toShortString() + " -> "
                + BuiltInRegistries.ITEM.getKey(output.getItem());
    }

    public int getAvailableWorkerCount() {
        return (int) getWixieWorkers().stream()
                .filter(wixie -> !activeJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !machineJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !apparatusJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !imbuementJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !reactionJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !cookingPotJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> !compressorJobs.containsKey(wixie.getBlockPos()))
                .filter(wixie -> wixie.craftManager == null || wixie.craftManager.isCraftCompleted())
                .count();
    }

    public int getAvailableAvaritiaCompressorJobCount() {
        return Math.min(getAvailableWorkerCount(), getAvailableAvaritiaCompressors().size());
    }

    /** Number of cooking jobs this provider can start concurrently right now. */
    public int getAvailableMachineJobCount() {
        int workers = getAvailableWorkerCount();
        if (workers <= 0) return 0;
        int furnaces = (int) getSourceStoneFurnaces().stream()
                .filter(furnace -> !furnace.isNetworkReserved())
                .count();
        return Math.min(workers, furnaces);
    }

    /** Number of heated, empty Cooking Pots that can receive parallel batches now. */
    public int getAvailableCookingPotJobCount() {
        int workers = getAvailableWorkerCount();
        if (workers <= 0) return 0;
        int pots = (int) getAvailableCookingPots().stream()
                .filter(pot -> {
                    ItemStackHandler inventory = farmersDelightPotInventory(pot);
                    return inventory != null && potIsEmpty(inventory) && farmersDelightPotHeated(pot);
                })
                .count();
        return Math.min(workers, pots);
    }

    /** Number of empty Reaction Vessels that can receive this recipe in parallel now. */
    public int getAvailableReactionJobCount(ArcaneReactionRule recipe) {
        int workers = getAvailableWorkerCount();
        if (workers <= 0) return 0;
        int vessels = (int) getReactionVessels().stream()
                .filter(vessel -> vessel.canPrepareNetworkJob(recipe))
                .count();
        return Math.min(workers, vessels);
    }

    /**
     * Releases jobs owned by one order terminal.  The terminal and pedestal can be
     * cancelled independently from this block, so keeping these reservations would
     * leave a Wixie permanently busy and prevent the replacement order from ever
     * reaching its workstation.
     */
    public void cancelJobsFromTerminal(BlockPos terminalPos) {
        if (level == null || terminalPos == null) return;
        boolean changed = false;

        for (Map.Entry<BlockPos, PendingJob> entry : new ArrayList<>(activeJobs.entrySet())) {
            PendingJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            if (level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie) {
                // Only reset managers installed by this provider's network jobs.
                if (wixie.craftManager instanceof NetworkWixieCraftingManager) {
                    wixie.craftManager = new CraftingManager();
                    wixie.hasSource = false;
                    wixie.setChanged();
                }
                job.ingredients.forEach(stack -> routeToWixieInput(wixie, stack));
            }
            activeJobs.remove(entry.getKey());
            changed = true;
        }

        for (Map.Entry<BlockPos, PendingMachineJob> entry : new ArrayList<>(machineJobs.entrySet())) {
            PendingMachineJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            if (level.getBlockEntity(job.furnacePos) instanceof SourceStoneFurnaceBlockEntity furnace) {
                List<ItemStack> recovered = furnace.cancelNetworkJob();
                if (level.getBlockEntity(entry.getKey()) instanceof WixieCauldronTile wixie) {
                    recovered.forEach(stack -> routeToWixieInput(wixie, stack));
                } else {
                    recovered.forEach(stack -> Containers.dropItemStack(level,
                            worldPosition.getX() + 0.5D, worldPosition.getY() + 0.5D,
                            worldPosition.getZ() + 0.5D, stack));
                }
            }
            machineJobs.remove(entry.getKey());
            changed = true;
        }

        for (Map.Entry<BlockPos, PendingApparatusJob> entry : new ArrayList<>(apparatusJobs.entrySet())) {
            PendingApparatusJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            if (level.getBlockEntity(job.apparatusPos) instanceof EnchantingApparatusTile apparatus) {
                returnRecovered(wixie, apparatus.removeItem(0, apparatus.getItem(0).getCount()));
                cleanupApparatusPedestals(wixie, job.pedestalPositions);
            }
            apparatusJobs.remove(entry.getKey());
            changed = true;
        }

        for (Map.Entry<BlockPos, PendingImbuementJob> entry : new ArrayList<>(imbuementJobs.entrySet())) {
            PendingImbuementJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            if (level.getBlockEntity(job.chamberPos) instanceof ImbuementTile chamber) {
                returnRecovered(wixie, chamber.removeItem(0, chamber.getItem(0).getCount()));
                cleanupImbuementPedestals(wixie, job.pedestalPositions);
            }
            imbuementJobs.remove(entry.getKey());
            changed = true;
        }

        for (Map.Entry<BlockPos, PendingCookingPotJob> entry
                : new ArrayList<>(cookingPotJobs.entrySet())) {
            PendingCookingPotJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            ItemStackHandler inventory = farmersDelightPotInventory(
                    level.getBlockEntity(job.potPos));
            if (inventory != null) {
                for (int slot = 0; slot < inventory.getSlots(); slot++) {
                    ItemStack recovered = inventory.extractItem(
                            slot, inventory.getStackInSlot(slot).getCount(), false);
                    if (recovered.isEmpty()) continue;
                    if (wixie != null) routeToWixieInput(wixie, recovered);
                    else Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                            worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, recovered);
                }
            }
            cookingPotJobs.remove(entry.getKey());
            changed = true;
        }

        for (Map.Entry<BlockPos, PendingCompressorJob> entry
                : new ArrayList<>(compressorJobs.entrySet())) {
            PendingCompressorJob job = entry.getValue();
            if (!terminalPos.equals(job.terminalPos)) continue;
            WixieCauldronTile wixie = level.getBlockEntity(entry.getKey())
                    instanceof WixieCauldronTile found ? found : null;
            Container compressor = level.getBlockEntity(job.compressorPos)
                    instanceof Container found ? found : null;
            if (compressor != null && compressor.getContainerSize() >= 2) {
                returnRecovered(wixie, compressor.removeItem(1,
                        compressor.getItem(1).getCount()));
                recoverCompressorMaterials(wixie, compressor);
            }
            returnCompressorQueue(wixie, job);
            compressorJobs.remove(entry.getKey());
            changed = true;
        }

        if (changed) sync();
    }

    public List<ItemStack> getPendingOutputs() {
        List<ItemStack> result = new ArrayList<>();
        activeJobs.values().forEach(job -> result.add(job.output.copy()));
        machineJobs.values().forEach(job -> result.add(job.output.copy()));
        apparatusJobs.values().forEach(job -> result.add(job.output.copy()));
        imbuementJobs.values().forEach(job -> result.add(job.output.copy()));
        reactionJobs.values().forEach(job -> result.add(job.output.copy()));
        cookingPotJobs.values().forEach(job -> result.add(job.output.copy()));
        compressorJobs.values().forEach(job -> result.add(job.output.copy()));
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
            if (job.workstationPos != null) {
                jobTag.putLong("WorkstationPos", job.workstationPos.asLong());
            }
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
            jobTag.putInt("Operations", job.operations);
            machineTag.put(Integer.toString(machineIndex++), jobTag);
        }
        tag.put("MachineJobs", machineTag);
        CompoundTag apparatusTag = new CompoundTag();
        int apparatusIndex = 0;
        for (Map.Entry<BlockPos, PendingApparatusJob> entry : apparatusJobs.entrySet()) {
            PendingApparatusJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("ApparatusPos", job.apparatusPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putInt("SourceCost", job.sourceCost);
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            CompoundTag pedestalsTag = new CompoundTag();
            for (int index = 0; index < job.pedestalPositions.size(); index++) {
                pedestalsTag.putLong(Integer.toString(index),
                        job.pedestalPositions.get(index).asLong());
            }
            jobTag.put("Pedestals", pedestalsTag);
            apparatusTag.put(Integer.toString(apparatusIndex++), jobTag);
        }
        tag.put("ApparatusJobs", apparatusTag);
        CompoundTag imbuementTag = new CompoundTag();
        int imbuementIndex = 0;
        for (Map.Entry<BlockPos, PendingImbuementJob> entry : imbuementJobs.entrySet()) {
            PendingImbuementJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("ChamberPos", job.chamberPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putInt("SourceCost", job.sourceCost);
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            CompoundTag pedestalsTag = new CompoundTag();
            for (int index = 0; index < job.pedestalPositions.size(); index++) {
                pedestalsTag.putLong(Integer.toString(index),
                        job.pedestalPositions.get(index).asLong());
            }
            jobTag.put("Pedestals", pedestalsTag);
            imbuementTag.put(Integer.toString(imbuementIndex++), jobTag);
        }
        tag.put("ImbuementJobs", imbuementTag);
        CompoundTag reactionTag = new CompoundTag();
        int reactionIndex = 0;
        for (Map.Entry<BlockPos, PendingReactionJob> entry : reactionJobs.entrySet()) {
            PendingReactionJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("VesselPos", job.vesselPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putInt("SourceCost", job.sourceCost);
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            jobTag.putInt("Operations", job.operations);
            reactionTag.put(Integer.toString(reactionIndex++), jobTag);
        }
        tag.put("ReactionJobs", reactionTag);
        CompoundTag cookingPotTag = new CompoundTag();
        int cookingPotIndex = 0;
        for (Map.Entry<BlockPos, PendingCookingPotJob> entry : cookingPotJobs.entrySet()) {
            PendingCookingPotJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("PotPos", job.potPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            jobTag.putInt("Operations", job.operations);
            cookingPotTag.put(Integer.toString(cookingPotIndex++), jobTag);
        }
        tag.put("CookingPotJobs", cookingPotTag);
        CompoundTag compressorTag = new CompoundTag();
        int compressorIndex = 0;
        for (Map.Entry<BlockPos, PendingCompressorJob> entry : compressorJobs.entrySet()) {
            PendingCompressorJob job = entry.getValue();
            CompoundTag jobTag = new CompoundTag();
            jobTag.putLong("WixiePos", entry.getKey().asLong());
            jobTag.putLong("TerminalPos", job.terminalPos.asLong());
            jobTag.putLong("CompressorPos", job.compressorPos.asLong());
            jobTag.put("Output", job.output.saveOptional(registries));
            jobTag.put("QueuedInputs", saveStacks(job.queuedInputs, registries));
            jobTag.putBoolean("FinalOutput", job.finalOutput);
            compressorTag.put(Integer.toString(compressorIndex++), jobTag);
        }
        tag.put("CompressorJobs", compressorTag);
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
                                Math.max(1, jobTag.getInt("WorkTicks")),
                                jobTag.contains("WorkstationPos")
                                        ? BlockPos.of(jobTag.getLong("WorkstationPos")) : null)
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
                        jobTag.getBoolean("FinalOutput"),
                        Math.max(1, jobTag.getInt("Operations"))));
            }
        }
        apparatusJobs.clear();
        CompoundTag apparatusTag = tag.getCompound("ApparatusJobs");
        for (String key : apparatusTag.getAllKeys()) {
            CompoundTag jobTag = apparatusTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (output.isEmpty() || !jobTag.contains("WixiePos")
                    || !jobTag.contains("TerminalPos") || !jobTag.contains("ApparatusPos")) {
                continue;
            }
            List<BlockPos> pedestals = new ArrayList<>();
            CompoundTag pedestalsTag = jobTag.getCompound("Pedestals");
            pedestalsTag.getAllKeys().stream()
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .forEach(index -> pedestals.add(BlockPos.of(pedestalsTag.getLong(index))));
            apparatusJobs.put(BlockPos.of(jobTag.getLong("WixiePos")),
                    new PendingApparatusJob(
                            BlockPos.of(jobTag.getLong("TerminalPos")),
                            BlockPos.of(jobTag.getLong("ApparatusPos")),
                            List.copyOf(pedestals), output,
                            Math.max(0, jobTag.getInt("SourceCost")),
                            jobTag.getBoolean("FinalOutput")));
        }
        imbuementJobs.clear();
        CompoundTag imbuementTag = tag.getCompound("ImbuementJobs");
        for (String key : imbuementTag.getAllKeys()) {
            CompoundTag jobTag = imbuementTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (output.isEmpty() || !jobTag.contains("WixiePos")
                    || !jobTag.contains("TerminalPos") || !jobTag.contains("ChamberPos")) {
                continue;
            }
            List<BlockPos> pedestals = new ArrayList<>();
            CompoundTag pedestalsTag = jobTag.getCompound("Pedestals");
            pedestalsTag.getAllKeys().stream()
                    .sorted(Comparator.comparingInt(Integer::parseInt))
                    .forEach(index -> pedestals.add(BlockPos.of(pedestalsTag.getLong(index))));
            imbuementJobs.put(BlockPos.of(jobTag.getLong("WixiePos")),
                    new PendingImbuementJob(
                            BlockPos.of(jobTag.getLong("TerminalPos")),
                            BlockPos.of(jobTag.getLong("ChamberPos")),
                            List.copyOf(pedestals), output,
                            Math.max(0, jobTag.getInt("SourceCost")),
                            jobTag.getBoolean("FinalOutput")));
        }
        reactionJobs.clear();
        CompoundTag reactionTag = tag.getCompound("ReactionJobs");
        for (String key : reactionTag.getAllKeys()) {
            CompoundTag jobTag = reactionTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (output.isEmpty() || !jobTag.contains("WixiePos")
                    || !jobTag.contains("TerminalPos") || !jobTag.contains("VesselPos")) continue;
            reactionJobs.put(BlockPos.of(jobTag.getLong("WixiePos")), new PendingReactionJob(
                    BlockPos.of(jobTag.getLong("TerminalPos")),
                    BlockPos.of(jobTag.getLong("VesselPos")), output,
                    Math.max(0, jobTag.getInt("SourceCost")),
                    jobTag.getBoolean("FinalOutput"),
                    Math.max(1, jobTag.getInt("Operations"))));
        }
        cookingPotJobs.clear();
        CompoundTag cookingPotTag = tag.getCompound("CookingPotJobs");
        for (String key : cookingPotTag.getAllKeys()) {
            CompoundTag jobTag = cookingPotTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (output.isEmpty() || !jobTag.contains("WixiePos")
                    || !jobTag.contains("TerminalPos") || !jobTag.contains("PotPos")) continue;
            cookingPotJobs.put(BlockPos.of(jobTag.getLong("WixiePos")),
                    new PendingCookingPotJob(
                            BlockPos.of(jobTag.getLong("TerminalPos")),
                            BlockPos.of(jobTag.getLong("PotPos")), output,
                            jobTag.getBoolean("FinalOutput"),
                        Math.max(1, jobTag.getInt("Operations"))));
        }
        compressorJobs.clear();
        CompoundTag compressorTag = tag.getCompound("CompressorJobs");
        for (String key : compressorTag.getAllKeys()) {
            CompoundTag jobTag = compressorTag.getCompound(key);
            ItemStack output = ItemStack.parseOptional(registries, jobTag.getCompound("Output"));
            if (output.isEmpty() || !jobTag.contains("WixiePos")
                    || !jobTag.contains("TerminalPos") || !jobTag.contains("CompressorPos")) {
                continue;
            }
            compressorJobs.put(BlockPos.of(jobTag.getLong("WixiePos")),
                    new PendingCompressorJob(
                            BlockPos.of(jobTag.getLong("TerminalPos")),
                            BlockPos.of(jobTag.getLong("CompressorPos")), output,
                            new ArrayList<>(loadStacks(
                                    jobTag.getCompound("QueuedInputs"), registries)),
                            jobTag.getBoolean("FinalOutput")));
        }
    }

    private static CompoundTag saveStacks(
            List<ItemStack> stacks, HolderLookup.Provider registries
    ) {
        CompoundTag result = new CompoundTag();
        for (int index = 0; index < stacks.size(); index++) {
            ItemStack stack = stacks.get(index);
            CompoundTag entry = new CompoundTag();
            entry.put("Stack", stack.isEmpty() ? new CompoundTag()
                    : stack.copyWithCount(1).saveOptional(registries));
            entry.putInt("Amount", stack.getCount());
            result.put(Integer.toString(index), entry);
        }
        return result;
    }

    private static List<ItemStack> loadStacks(
            CompoundTag tag, HolderLookup.Provider registries
    ) {
        List<ItemStack> result = new ArrayList<>();
        tag.getAllKeys().stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            CompoundTag entry = tag.getCompound(key);
            ItemStack stack = ItemStack.parseOptional(registries,
                    entry.contains("Stack") ? entry.getCompound("Stack") : entry);
            if (entry.contains("Stack") && !stack.isEmpty()) stack.setCount(Math.max(0, entry.getInt("Amount")));
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
        private final BlockPos workstationPos;
        private int workTicks;

        private PendingJob(
                BlockPos terminalPos,
                ItemStack output,
                List<ItemStack> ingredients,
                List<ItemStack> remainders,
                boolean finalOutput,
                int workTicks,
                BlockPos workstationPos
        ) {
            this.terminalPos = terminalPos;
            this.output = output;
            this.ingredients = ingredients;
            this.remainders = remainders;
            this.finalOutput = finalOutput;
            this.workTicks = workTicks;
            this.workstationPos = workstationPos;
        }
    }

    private record PendingMachineJob(
            BlockPos terminalPos, BlockPos furnacePos, ItemStack output,
            boolean finalOutput, int operations) {}

    private record PendingApparatusJob(
            BlockPos terminalPos,
            BlockPos apparatusPos,
            List<BlockPos> pedestalPositions,
            ItemStack output,
            int sourceCost,
            boolean finalOutput
    ) {}

    private record PendingImbuementJob(
            BlockPos terminalPos,
            BlockPos chamberPos,
            List<BlockPos> pedestalPositions,
            ItemStack output,
            int sourceCost,
            boolean finalOutput
    ) {}

    private record PendingReactionJob(
            BlockPos terminalPos, BlockPos vesselPos, ItemStack output,
            int sourceCost, boolean finalOutput, int operations
    ) {}

    private record PendingCookingPotJob(
            BlockPos terminalPos, BlockPos potPos, ItemStack output,
            boolean finalOutput, int operations
    ) {}

    private record PendingCompressorJob(
            BlockPos terminalPos, BlockPos compressorPos, ItemStack output,
            List<ItemStack> queuedInputs, boolean finalOutput
    ) {}
}
