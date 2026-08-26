package dev.arsmatrix.blockentity;

import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.compat.DynamicCraftingRecipeSupport;
import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.compat.ReversibleStorageConversionSupport;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public class WixieOrderTerminalBlockEntity extends BlockEntity implements MenuProvider {

    public static final int NETWORK_RADIUS = 16;
    private static final int BUFFER_SLOTS = 27;
    private static final int MAX_RECURSION_DEPTH = 24;

    private final ItemStackHandler buffer = new ItemStackHandler(BUFFER_SLOTS) {
        @Override
        protected void onContentsChanged(int slot) {
            sync();
        }
    };
    private int tickCounter;
    private BlockPos activePedestalPos;
    private ItemStack activeTarget = ItemStack.EMPTY;
    private int activeTargetCount;
    private int orderBaseline;
    private int orderProducedCount;
    private long orderStartGameTime;
    private int orderCraftOperations;
    private int orderSourceSpent;
    private final List<ItemStack> missingItems = new ArrayList<>();
    private final Map<ResourceLocation, Boolean> freeConversionCache = new HashMap<>();
    private int providerCount;
    private int activeWorkerCount;
    private TerminalState state = TerminalState.IDLE;
    private String detail = "";

    public WixieOrderTerminalBlockEntity(BlockPos pos, BlockState state) {
        this(ModBlockEntities.WIXIE_ORDER_TERMINAL.get(), pos, state);
    }

    protected WixieOrderTerminalBlockEntity(
            net.minecraft.world.level.block.entity.BlockEntityType<?> type,
            BlockPos pos,
            BlockState state
    ) {
        super(type, pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.wixie_order_terminal");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WixieOrderTerminalMenu(containerId, inventory, this);
    }

    /** Returns every unique ordinary crafting output encoded in a reachable provider. */
    public List<ItemStack> getCraftableOutputs() {
        return getCraftableRecipeInfos().stream()
                .map(info -> info.output().copy())
                .toList();
    }

    /** Returns the displayed output together with its actual encoded workstation and mode. */
    public List<CraftableRecipeInfo> getCraftableRecipeInfos() {
        if (level == null) {
            return List.of();
        }
        List<CraftableRecipeInfo> result = new ArrayList<>();
        Set<ResourceLocation> visitedRecipes = new HashSet<>();
        for (WixiePatternProviderBlockEntity provider : findProviders()) {
            if (!provider.hasWixieWorker()) {
                continue;
            }
            for (ResourceLocation recipeId : provider.getRecipeIds()) {
                if (!visitedRecipes.add(recipeId)) {
                    continue;
                }
                level.getRecipeManager().byKey(recipeId).ifPresent(holder -> {
                    Recipe<?> recipe = holder.value();
                    if (RecipeAutomationSupport.supports(recipe)
                            && (!(recipe instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe)
                            || fluidLectern() != null)) {
                        ItemStack output = RecipeAutomationSupport.result(recipe, level.registryAccess());
                        if (!output.isEmpty() && result.stream().noneMatch(existing ->
                                ItemStack.isSameItemSameComponents(existing.output(), output))) {
                            result.add(new CraftableRecipeInfo(
                                    output.copyWithCount(1), holder.id(),
                                    RecipeAutomationSupport.isCooking(recipe),
                                    provider.isFuzzyRecipe(holder.id())));
                        }
                    }
                });
            }
        }
        result.sort(Comparator.comparing(info -> info.output().getHoverName().getString()));
        return List.copyOf(result);
    }

    /** Changes every duplicate copy of one encoded guide to the same matching mode. */
    public boolean setRecipeFuzzy(ResourceLocation recipeId, boolean fuzzy) {
        if (recipeId == null) return false;
        boolean changed = false;
        for (WixiePatternProviderBlockEntity provider : findProviders()) {
            changed |= provider.setRecipeFuzzy(recipeId, fuzzy);
        }
        return changed;
    }

    /** Accepts orders only from this terminal's server-side menu. */
    public void requestFromTerminal(ItemStack requested, int count, Player player) {
        if (level == null || level.isClientSide || requested.isEmpty()) {
            return;
        }
        AutomaticRequestResult result = startOrder(requested, count, player.getUUID());
        if (result != AutomaticRequestResult.ACCEPTED) {
            String key = switch (result) {
                case RECIPE_UNAVAILABLE -> "message.ars_arcane_matrix.order_terminal.recipe_unavailable";
                case NO_PEDESTAL -> "message.ars_arcane_matrix.order_terminal.no_pedestal";
                default -> "message.ars_arcane_matrix.order_terminal.busy";
            };
            player.displayClientMessage(Component.translatable(key), true);
            return;
        }
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.order_terminal.submitted",
                activeTarget.getHoverName(), activeTargetCount), true);
    }

    /** Server-side entry used by stock requesters; no player messages are emitted. */
    public AutomaticRequestResult requestAutomatically(ItemStack requested, int count) {
        return requestAutomatically(requested, count, null);
    }

    /** Server-side entry used by stock requesters, retaining the configuring player for summaries. */
    public AutomaticRequestResult requestAutomatically(
            ItemStack requested, int count, UUID requester) {
        if (level == null || level.isClientSide || requested.isEmpty()) {
            return AutomaticRequestResult.RECIPE_UNAVAILABLE;
        }
        return startOrder(requested, count, requester);
    }

    public boolean hasActiveOrderFor(ItemStack requested) {
        return !activeTarget.isEmpty()
                && ItemStack.isSameItemSameComponents(activeTarget, requested);
    }

    /** Lets trusted network devices take one already-stored item without creating an order. */
    public ItemStack takeOneStoredForAutomation(ItemStack requested) {
        if (level == null || level.isClientSide || requested.isEmpty()) return ItemStack.EMPTY;
        List<WixiePatternProviderBlockEntity> providers = findProviders();
        return extractOne(requested.copyWithCount(1), combineInventories(
                collectWixieInventories(providers), collectWixieOutputInventories(providers)));
    }

    /** Moves an already-completed network output into a requester's bound inventory. */
    public AutomationTransferResult transferStoredForAutomation(
            ItemStack requested, int maximum, IItemHandler destination) {
        if (level == null || level.isClientSide || requested.isEmpty()
                || maximum <= 0 || destination == null) {
            return new AutomationTransferResult(0, false);
        }
        List<WixiePatternProviderBlockEntity> providers = findProviders();
        List<IItemHandler> sources = new ArrayList<>();
        sources.add(buffer);
        sources.addAll(combineInventories(
                collectWixieInventories(providers), collectWixieOutputInventories(providers)));
        int moved = 0;
        boolean available = false;
        for (IItemHandler source : sources) {
            if (source == destination) continue;
            for (int slot = 0; slot < source.getSlots() && moved < maximum; slot++) {
                ItemStack stored = source.getStackInSlot(slot);
                if (!ItemStack.isSameItemSameComponents(stored, requested)) continue;
                available = true;
                int wanted = Math.min(maximum - moved, stored.getCount());
                ItemStack simulated = source.extractItem(slot, wanted, true);
                if (simulated.isEmpty()) continue;
                ItemStack simulatedRemainder = ItemHandlerHelper.insertItemStacked(
                        destination, simulated.copy(), true);
                int accepted = simulated.getCount() - simulatedRemainder.getCount();
                if (accepted <= 0) continue;
                ItemStack extracted = source.extractItem(slot, accepted, false);
                ItemStack remainder = ItemHandlerHelper.insertItemStacked(destination, extracted, false);
                moved += extracted.getCount() - remainder.getCount();
                if (!remainder.isEmpty()) insertBufferOrDrop(remainder);
            }
            if (moved >= maximum) break;
        }
        return new AutomationTransferResult(moved, available);
    }

    public record AutomationTransferResult(int moved, boolean available) {}

    public record CraftableRecipeInfo(
            ItemStack output,
            ResourceLocation recipeId,
            boolean cooking,
            boolean fuzzy
    ) {}

    private AutomaticRequestResult startOrder(ItemStack requested, int count, UUID requester) {
        boolean isEncoded = getCraftableOutputs().stream().anyMatch(output ->
                ItemStack.isSameItemSameComponents(output, requested));
        if (!isEncoded) return AutomaticRequestResult.RECIPE_UNAVAILABLE;
        if (activePedestalPos != null || !activeTarget.isEmpty()) {
            return AutomaticRequestResult.BUSY;
        }
        ArcaneOrderPedestalBlockEntity pedestal = findIdlePedestal();
        if (pedestal == null) return AutomaticRequestResult.NO_PEDESTAL;

        activePedestalPos = pedestal.getBlockPos().immutable();
        activeTarget = requested.copyWithCount(1);
        activeTargetCount = Math.max(1, Math.min(9999, count));
        List<WixiePatternProviderBlockEntity> providers = findProviders();
        orderBaseline = countAvailable(
                activeTarget,
                combineInventories(
                        collectWixieInventories(providers),
                        collectWixieOutputInventories(providers)
                )
        );
        orderStartGameTime = level.getGameTime();
        orderCraftOperations = 0;
        orderSourceSpent = 0;
        orderProducedCount = 0;
        missingItems.clear();
        pedestal.assignFromTerminal(activeTarget, activeTargetCount, requester, worldPosition);
        setState(TerminalState.CRAFTING, "");
        sync();
        return AutomaticRequestResult.ACCEPTED;
    }

    public void cancelFromTerminal(Player player) {
        if (level == null || level.isClientSide) {
            return;
        }
        if (activePedestalPos != null
                && level.getBlockEntity(activePedestalPos) instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            pedestal.cancelFromTerminal();
        }
        activePedestalPos = null;
        activeTarget = ItemStack.EMPTY;
        activeTargetCount = 0;
        orderBaseline = 0;
        missingItems.clear();
        resetOrderStatistics();
        setState(TerminalState.IDLE, "");
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.order_terminal.cancelled"), true);
        sync();
    }

    private ArcaneOrderPedestalBlockEntity findIdlePedestal() {
        if (level == null) {
            return null;
        }
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-NETWORK_RADIUS, -NETWORK_RADIUS, -NETWORK_RADIUS),
                        worldPosition.offset(NETWORK_RADIUS, NETWORK_RADIUS, NETWORK_RADIUS)
                )
                .filter(level::hasChunkAt)
                .map(pos -> level.getBlockEntity(pos))
                .filter(ArcaneOrderPedestalBlockEntity.class::isInstance)
                .map(ArcaneOrderPedestalBlockEntity.class::cast)
                .filter(pedestal -> !pedestal.hasOrder())
                .min(Comparator.comparingDouble(pedestal -> pedestal.getBlockPos().distSqr(worldPosition)))
                .orElse(null);
    }

    public void serverTick() {
        if (level == null || level.isClientSide) {
            return;
        }
        tickCounter++;
        ArcaneOrderPedestalBlockEntity configuredPedestal = activePedestal();
        int dispatchInterval = configuredPedestal == null
                ? ArcaneOrderPedestalBlockEntity.dispatchIntervalTicks(0)
                : configuredPedestal.getDispatchIntervalTicks();
        if (tickCounter % dispatchInterval != 0) {
            return;
        }
        List<WixiePatternProviderBlockEntity> providers = findProviders();
        providerCount = providers.size();
        activeWorkerCount = providers.stream()
                .mapToInt(WixiePatternProviderBlockEntity::getActiveWorkerCount).sum();
        if (!loadActiveOrder()) {
            setState(TerminalState.IDLE, "");
            return;
        }
        configuredPedestal = activePedestal();
        int parallelLimit = configuredPedestal == null
                ? ArcaneOrderPedestalBlockEntity.maxParallelJobs(0)
                : configuredPedestal.getMaxParallelJobs();
        List<IItemHandler> storage = collectWixieInventories(providers);
        if (orderProducedCount >= activeTargetCount) {
            finishOrder();
            return;
        }
        Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> availablePatterns =
                collectAvailablePatterns(providers);
        if (availablePatterns.isEmpty()) {
            boolean hasWorker = providers.stream()
                    .anyMatch(WixiePatternProviderBlockEntity::hasWixieWorker);
            setPedestalState(
                    hasWorker
                            ? ArcaneOrderPedestalBlockEntity.OrderState.WAITING_PATTERNS
                            : ArcaneOrderPedestalBlockEntity.OrderState.WAITING_WORKSTATION,
                    ""
            );
            setState(hasWorker ? TerminalState.WAITING_PATTERNS : TerminalState.WAITING_WORKSTATION, "");
            return;
        }
        boolean dispatchedAny = false;
        missingItems.clear();
        int availableWorkers = providers.stream()
                .mapToInt(WixiePatternProviderBlockEntity::getAvailableWorkerCount).sum();
        int freeWorkers = Math.min(availableWorkers,
                Math.max(0, parallelLimit - activeWorkerCount));
        // Force the requested quantity to be produced by this order. Existing target items
        // remain usable storage, but must not count as newly crafted output.
        int requiredInInputPool = countAvailable(activeTarget, storage)
                + Math.max(1, activeTargetCount - orderProducedCount);
        for (int attempt = 0; attempt < freeWorkers; attempt++) {
            DispatchResult result = ensureOne(
                    activeTarget.copyWithCount(1),
                    requiredInInputPool,
                    storage,
                    providers,
                    availablePatterns,
                    new HashSet<>(),
                    0
            );
            if (result == DispatchResult.DISPATCHED) {
                dispatchedAny = true;
                continue;
            }
            break;
        }
        activeWorkerCount = providers.stream()
                .mapToInt(WixiePatternProviderBlockEntity::getActiveWorkerCount).sum();
        if (dispatchedAny || activeWorkerCount > 0) {
            setPedestalState(ArcaneOrderPedestalBlockEntity.OrderState.CRAFTING, "");
            setState(TerminalState.CRAFTING, "");
        } else if (detail.isBlank()) {
            setPedestalState(ArcaneOrderPedestalBlockEntity.OrderState.WAITING_WORKSTATION, "");
            setState(TerminalState.WAITING_WORKSTATION, "");
        }
    }

    private boolean loadActiveOrder() {
        if (activePedestalPos != null
                && level != null
                && level.hasChunkAt(activePedestalPos)
                && level.getBlockEntity(activePedestalPos) instanceof ArcaneOrderPedestalBlockEntity pedestal
                && pedestal.hasOrder()
                && pedestal.claimFor(worldPosition)) {
            activeTarget = pedestal.getVirtualTarget();
            activeTargetCount = pedestal.getVirtualTargetCount();
            ensureOrderStartTime();
            return true;
        }
        activePedestalPos = null;
        activeTarget = ItemStack.EMPTY;
        activeTargetCount = 0;
        orderBaseline = 0;
        if (level == null) {
            return false;
        }
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-NETWORK_RADIUS, -NETWORK_RADIUS, -NETWORK_RADIUS),
                        worldPosition.offset(NETWORK_RADIUS, NETWORK_RADIUS, NETWORK_RADIUS)
                )
                .filter(level::hasChunkAt)
                .map(pos -> level.getBlockEntity(pos))
                .filter(ArcaneOrderPedestalBlockEntity.class::isInstance)
                .map(ArcaneOrderPedestalBlockEntity.class::cast)
                .filter(ArcaneOrderPedestalBlockEntity::hasOrder)
                .filter(pedestal -> pedestal.canBeClaimedBy(worldPosition))
                .min(Comparator.comparingDouble(pedestal -> pedestal.getBlockPos().distSqr(worldPosition)))
                .map(pedestal -> {
                    if (!pedestal.claimFor(worldPosition)) return false;
                    activePedestalPos = pedestal.getBlockPos().immutable();
                    activeTarget = pedestal.getVirtualTarget();
                    activeTargetCount = pedestal.getVirtualTargetCount();
                    ensureOrderStartTime();
                    return true;
                })
                .orElse(false);
    }

    private ArcaneOrderPedestalBlockEntity activePedestal() {
        if (level == null || activePedestalPos == null || !level.hasChunkAt(activePedestalPos)) {
            return null;
        }
        return level.getBlockEntity(activePedestalPos) instanceof ArcaneOrderPedestalBlockEntity pedestal
                ? pedestal : null;
    }

    private void ensureOrderStartTime() {
        if (orderStartGameTime <= 0L && level != null) {
            orderStartGameTime = level.getGameTime();
            setChanged();
        }
    }

    private DispatchResult ensureOne(
            ItemStack target,
            int requiredCount,
            List<IItemHandler> storage,
            List<WixiePatternProviderBlockEntity> providers,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> availablePatterns,
            Set<Item> path,
            int depth
    ) {
        if (depth > MAX_RECURSION_DEPTH || !path.add(target.getItem())) {
            reportMissing(target.copyWithCount(Math.max(1, requiredCount)));
            return DispatchResult.MISSING;
        }
        try {
            int availableTarget = countAvailable(target, storage);
            if (availableTarget >= requiredCount) {
                return DispatchResult.AVAILABLE;
            }
            if (availableTarget + countPending(target, providers) >= requiredCount) {
                return DispatchResult.WAITING;
            }
            RecipeMatch match = findRecipeForOutput(
                    target, storage, providers, availablePatterns, path);
            if (match == null) {
                reportMissing(target.copyWithCount(Math.max(
                        1, requiredCount - availableTarget - countPending(target, providers))));
                return DispatchResult.MISSING;
            }
            Recipe<?> recipe = match.recipe().value();
            Map<Item, Integer> available = snapshotCounts(storage);
            AdvancedStorageLecternBlockEntity fluidLectern = fluidLectern();
            if (fluidLectern != null) {
                for (ItemStack virtualContainer : fluidLectern.getVirtualFluidContainers()) {
                    available.merge(virtualContainer.getItem(), virtualContainer.getCount(), Integer::sum);
                }
            }
            List<ItemStack> selected = selectIngredients(
                    recipe, available, match.fuzzy(), availablePatterns);
            boolean waiting = false;
            boolean missing = false;
            Map<Item, Integer> requiredPerItem = new HashMap<>();
            for (int index = 0; index < selected.size(); index++) {
                ItemStack selectedStack = selected.get(index);
                if (selectedStack.isEmpty()) {
                    continue;
                }
                int required = requiredPerItem.merge(selectedStack.getItem(), 1, Integer::sum);
                int stored = countAvailable(selectedStack, storage)
                        + virtualFluidContainerCount(selectedStack, fluidLectern);
                int pending = countPending(selectedStack, providers);
                if (stored >= required) {
                    continue;
                }
                if (stored + pending >= required) {
                    waiting = true;
                    continue;
                }
                DispatchResult child = ensureOne(
                        selectedStack,
                        required,
                        storage,
                        providers,
                        availablePatterns,
                        new HashSet<>(path),
                        depth + 1
                );
                if (child == DispatchResult.DISPATCHED) {
                    return DispatchResult.DISPATCHED;
                }
                if (child == DispatchResult.MISSING) {
                    missing = true;
                    continue;
                }
                waiting = true;
            }
            if (missing) {
                return DispatchResult.MISSING;
            }
            if (waiting) {
                return DispatchResult.WAITING;
            }
            ItemStack output = RecipeAutomationSupport.result(recipe, level.registryAccess());
            boolean finalOutput = ItemStack.isSameItemSameComponents(output, activeTarget);
            if (RecipeAutomationSupport.isCooking(recipe)) {
                List<WixiePatternProviderBlockEntity> workers = availableWorkers(providers);
                if (workers.isEmpty()) return DispatchResult.WAITING;
                for (WixiePatternProviderBlockEntity worker : workers) {
                    if (worker.startMachineJob(worldPosition, output, selected, finalOutput)) {
                        detail = "";
                        return DispatchResult.DISPATCHED;
                    }
                }
                return DispatchResult.WAITING;
            }
            if (!(recipe instanceof CraftingRecipe crafting)) return DispatchResult.MISSING;
            CraftingInput input = createInput(crafting, selected);
            if (!crafting.matches(input, level)) {
                reportMissing(target);
                return DispatchResult.MISSING;
            }
            output = crafting.assemble(input, level.registryAccess());
            NonNullList<ItemStack> remainders = crafting.getRemainingItems(input);
            if (remainders.stream().allMatch(ItemStack::isEmpty)
                    && isFreeStorageConversion(match.recipe())
                    && completeFreeStorageConversion(
                            output, selected, finalOutput, storage, providers)) {
                detail = "";
                return DispatchResult.DISPATCHED;
            }
            List<WixiePatternProviderBlockEntity> workers = availableWorkers(providers);
            if (workers.isEmpty()) return DispatchResult.WAITING;
            for (WixiePatternProviderBlockEntity worker : workers) {
                if (worker.startJob(worldPosition, output, selected, remainders, finalOutput)) {
                    detail = "";
                    return DispatchResult.DISPATCHED;
                }
            }
            return DispatchResult.WAITING;
        } finally {
            path.remove(target.getItem());
        }
    }

    private List<WixiePatternProviderBlockEntity> availableWorkers(
            List<WixiePatternProviderBlockEntity> providers
    ) {
        return providers.stream()
                .filter(WixiePatternProviderBlockEntity::isAvailable)
                .sorted(Comparator.comparingDouble(provider ->
                        provider.getBlockPos().distSqr(worldPosition)))
                .toList();
    }

    private boolean isFreeStorageConversion(RecipeHolder<?> recipe) {
        if (level == null) return false;
        return freeConversionCache.computeIfAbsent(recipe.id(), ignored ->
                ReversibleStorageConversionSupport.isReversible(
                        recipe, level.getRecipeManager(), level.registryAccess()));
    }

    /** Performs safe storage-form changes without starting a Source-consuming Wixie cycle. */
    private boolean completeFreeStorageConversion(
            ItemStack output,
            List<ItemStack> selected,
            boolean finalOutput,
            List<IItemHandler> storage,
            List<WixiePatternProviderBlockEntity> providers
    ) {
        List<ItemStack> extracted = extractSelected(selected, storage);
        if (extracted == null) return false;

        List<IItemHandler> destinations = finalOutput
                ? collectWixieOutputInventories(providers) : storage;
        ItemStack remaining = output.copy();
        for (IItemHandler destination : destinations) {
            remaining = ItemHandlerHelper.insertItemStacked(destination, remaining, false);
            if (remaining.isEmpty()) break;
        }
        if (!remaining.isEmpty()) insertBufferOrDrop(remaining);
        onWixieJobCompleted(0, output, finalOutput);
        return true;
    }

    private RecipeMatch findRecipeForOutput(
            ItemStack target,
            List<IItemHandler> storage,
            List<WixiePatternProviderBlockEntity> providers,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> patterns,
            Set<Item> path
    ) {
        if (level == null) {
            return null;
        }
        RecipeMatch firstMatch = null;
        RecipeMatch firstRouted = null;
        RecipeMatch firstDirect = null;
        for (Map.Entry<ResourceLocation, List<WixiePatternProviderBlockEntity>> entry : patterns.entrySet()) {
            var holder = level.getRecipeManager().byKey(entry.getKey());
            if (holder.isEmpty() || !RecipeAutomationSupport.supports(holder.get().value())) {
                continue;
            }
            Recipe<?> recipe = holder.get().value();
            if (RecipeAutomationSupport.isCooking(recipe) && fluidLectern() == null) continue;
            if (recipeLoopsIntoPath(recipe, path)) continue;
            ItemStack output = RecipeAutomationSupport.result(recipe, level.registryAccess());
            if (ItemStack.isSameItemSameComponents(output, target)) {
                WixiePatternProviderBlockEntity provider = entry.getValue().stream()
                        .filter(WixiePatternProviderBlockEntity::isAvailable)
                        .findFirst()
                        .orElse(entry.getValue().getFirst());
                RecipeMatch match = new RecipeMatch(
                        holder.get(), provider, provider.isFuzzyRecipe(entry.getKey()));
                if (firstMatch == null) firstMatch = match;
                if (!recipeHasIngredientRoutes(recipe, storage, providers, patterns)) continue;
                if (firstRouted == null) firstRouted = match;
                if (!recipeHasAllIngredientsStored(recipe, match.fuzzy(), storage, patterns)) continue;
                if (isFreeStorageConversion(holder.get())) return match;
                if (firstDirect == null) firstDirect = match;
            }
        }
        return firstDirect != null ? firstDirect : firstRouted != null ? firstRouted : firstMatch;
    }

    /** Avoids choosing nugget -> ingot while an order is already trying to make nuggets. */
    private static boolean recipeLoopsIntoPath(Recipe<?> recipe, Set<Item> path) {
        for (Ingredient ingredient : RecipeAutomationSupport.ingredients(recipe)) {
            ItemStack[] candidates = ingredient.getItems();
            if (candidates.length == 0) continue;
            boolean everyCandidateLoops = true;
            for (ItemStack candidate : candidates) {
                if (!path.contains(candidate.getItem())) {
                    everyCandidateLoops = false;
                    break;
                }
            }
            if (everyCandidateLoops) return true;
        }
        return false;
    }

    private boolean recipeHasAllIngredientsStored(
            Recipe<?> recipe,
            boolean fuzzy,
            List<IItemHandler> storage,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> patterns
    ) {
        Map<Item, Integer> counts = snapshotCounts(storage);
        AdvancedStorageLecternBlockEntity lectern = fluidLectern();
        if (lectern != null) {
            for (ItemStack virtual : lectern.getVirtualFluidContainers()) {
                counts.merge(virtual.getItem(), virtual.getCount(), Integer::sum);
            }
        }
        List<ItemStack> selected = selectIngredients(recipe, counts, fuzzy, patterns);
        Map<Item, Integer> required = new HashMap<>();
        for (ItemStack stack : selected) {
            if (stack.isEmpty()) continue;
            int wanted = required.merge(stack.getItem(), 1, Integer::sum);
            int stored = countAvailable(stack, storage)
                    + virtualFluidContainerCount(stack, lectern);
            if (stored < wanted) return false;
        }
        return true;
    }

    /** A prioritized pattern only blocks later alternatives when every ingredient has a route. */
    private boolean recipeHasIngredientRoutes(
            Recipe<?> recipe,
            List<IItemHandler> storage,
            List<WixiePatternProviderBlockEntity> providers,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> patterns
    ) {
        AdvancedStorageLecternBlockEntity lectern = fluidLectern();
        for (Ingredient ingredient : RecipeAutomationSupport.ingredients(recipe)) {
            if (ingredient.isEmpty()) continue;
            boolean routed = false;
            for (ItemStack candidate : ingredient.getItems()) {
                if (countAvailable(candidate, storage) > 0
                        || countPending(candidate, providers) > 0
                        || virtualFluidContainerCount(candidate, lectern) > 0
                        || patternProduces(candidate, patterns)) {
                    routed = true;
                    break;
                }
            }
            if (!routed) return false;
        }
        return true;
    }

    private boolean patternProduces(
            ItemStack target,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> patterns
    ) {
        if (level == null) return false;
        for (ResourceLocation recipeId : patterns.keySet()) {
            var holder = level.getRecipeManager().byKey(recipeId);
            if (holder.isEmpty() || !RecipeAutomationSupport.supports(holder.get().value())) continue;
            ItemStack output = RecipeAutomationSupport.result(
                    holder.get().value(), level.registryAccess());
            if (ItemStack.isSameItemSameComponents(output, target)) return true;
        }
        return false;
    }

    private Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> collectAvailablePatterns(
            List<WixiePatternProviderBlockEntity> providers
    ) {
        Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> result = new LinkedHashMap<>();
        for (WixiePatternProviderBlockEntity provider : providers) {
            if (!provider.hasWixieWorker()) {
                continue;
            }
            for (ResourceLocation recipe : provider.getRecipeIds()) {
                result.computeIfAbsent(recipe, ignored -> new ArrayList<>()).add(provider);
            }
        }
        return result;
    }

    private List<ItemStack> selectIngredients(
            Recipe<?> recipe,
            Map<Item, Integer> counts,
            boolean fuzzy,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> availablePatterns
    ) {
        List<ItemStack> selected = new ArrayList<>();
        for (Ingredient ingredient : RecipeAutomationSupport.ingredients(recipe)) {
            if (ingredient.isEmpty()) {
                selected.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack choice = ItemStack.EMPTY;
            if (!fuzzy && ingredient.getItems().length > 0) {
                for (ItemStack candidate : ingredient.getItems()) {
                    int count = counts.getOrDefault(candidate.getItem(), 0);
                    if (count > 0) {
                        choice = candidate.copyWithCount(1);
                        counts.put(candidate.getItem(), count - 1);
                        break;
                    }
                }
                if (choice.isEmpty()) {
                    choice = selectNetworkCraftableCandidate(
                            ingredient.getItems(), counts, availablePatterns);
                }
            } else {
                for (ItemStack candidate : ingredient.getItems()) {
                    int count = counts.getOrDefault(candidate.getItem(), 0);
                    if (count > 0) {
                        choice = candidate.copyWithCount(1);
                        counts.put(candidate.getItem(), count - 1);
                        break;
                    }
                }
                if (choice.isEmpty()) {
                    choice = ingredient.getItems().length == 0
                            ? ItemStack.EMPTY
                            : selectNetworkCraftableCandidate(
                                    ingredient.getItems(), counts, availablePatterns);
                }
            }
            if (choice.isEmpty() && ingredient.getItems().length > 0) {
                choice = ingredient.getItems()[0].copyWithCount(1);
            }
            selected.add(choice);
        }
        return selected;
    }

    private ItemStack selectNetworkCraftableCandidate(
            ItemStack[] candidates,
            Map<Item, Integer> counts,
            Map<ResourceLocation, List<WixiePatternProviderBlockEntity>> availablePatterns
    ) {
        if (level == null) {
            return ItemStack.EMPTY;
        }
        ItemStack fallback = ItemStack.EMPTY;
        ItemStack best = ItemStack.EMPTY;
        int bestScore = -1;
        for (ItemStack candidate : candidates) {
            for (ResourceLocation recipeId : availablePatterns.keySet()) {
                var holder = level.getRecipeManager().byKey(recipeId);
                if (holder.isEmpty() || !RecipeAutomationSupport.supports(holder.get().value())) {
                    continue;
                }
                Recipe<?> candidateRecipe = holder.get().value();
                if (RecipeAutomationSupport.isCooking(candidateRecipe) && fluidLectern() == null) continue;
                ItemStack output = RecipeAutomationSupport.result(candidateRecipe, level.registryAccess());
                if (!ItemStack.isSameItemSameComponents(output, candidate)) {
                    continue;
                }
                if (fallback.isEmpty()) {
                    fallback = candidate.copyWithCount(1);
                }
                int score = 0;
                for (Ingredient recipeIngredient : RecipeAutomationSupport.ingredients(candidateRecipe)) {
                    int ingredientScore = 0;
                    for (ItemStack ingredientCandidate : recipeIngredient.getItems()) {
                        ingredientScore = Math.max(
                                ingredientScore,
                                counts.getOrDefault(ingredientCandidate.getItem(), 0));
                    }
                    score += ingredientScore;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = candidate.copyWithCount(1);
                }
            }
        }
        return !best.isEmpty() ? best : fallback;
    }

    private List<ItemStack> extractSelected(List<ItemStack> selected, List<IItemHandler> storage) {
        List<ItemStack> extracted = new ArrayList<>();
        for (ItemStack choice : selected) {
            if (choice.isEmpty()) {
                extracted.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack taken = extractOne(choice, storage);
            if (taken.isEmpty()) {
                extracted.stream().filter(stack -> !stack.isEmpty()).forEach(this::insertBufferOrDrop);
                return null;
            }
            extracted.add(taken);
        }
        return extracted;
    }

    private CraftingInput createInput(CraftingRecipe recipe, List<ItemStack> selected) {
        if (recipe instanceof ShapedRecipe shaped) {
            return CraftingInput.of(shaped.getWidth(), shaped.getHeight(), selected);
        }
        List<ItemStack> grid = new ArrayList<>(selected);
        while (grid.size() < 9) {
            grid.add(ItemStack.EMPTY);
        }
        return CraftingInput.of(3, 3, grid);
    }

    private ItemStack extractOne(ItemStack template, List<IItemHandler> storage) {
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            if (ItemStack.isSameItemSameComponents(buffer.getStackInSlot(slot), template)) {
                return buffer.extractItem(slot, 1, false);
            }
        }
        for (IItemHandler handler : storage) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), template)) {
                    ItemStack extracted = handler.extractItem(slot, 1, false);
                    if (!extracted.isEmpty()) {
                        return extracted;
                    }
                }
            }
        }
        return ItemStack.EMPTY;
    }

    private Map<Item, Integer> snapshotCounts(List<IItemHandler> storage) {
        Map<Item, Integer> result = new HashMap<>();
        addCounts(result, buffer);
        storage.forEach(handler -> addCounts(result, handler));
        return result;
    }

    private AdvancedStorageLecternBlockEntity fluidLectern() {
        if (level == null) return null;
        return level.getBlockEntity(worldPosition) instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern : null;
    }

    private static int virtualFluidContainerCount(
            ItemStack template, AdvancedStorageLecternBlockEntity lectern
    ) {
        if (lectern == null || template.isEmpty()) return 0;
        int count = 0;
        for (ItemStack available : lectern.getVirtualFluidContainers()) {
            if (ItemStack.isSameItemSameComponents(available, template)) count += available.getCount();
        }
        return count;
    }

    private static void addCounts(Map<Item, Integer> counts, IItemHandler handler) {
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                counts.merge(stack.getItem(), stack.getCount(), Integer::sum);
            }
        }
    }

    private int countAvailable(ItemStack template, List<IItemHandler> storage) {
        int count = countInBuffer(template);
        for (IItemHandler handler : storage) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (ItemStack.isSameItemSameComponents(stack, template)) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    private int countInBuffer(ItemStack template) {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, template)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static int countPending(ItemStack template, List<WixiePatternProviderBlockEntity> providers) {
        return providers.stream()
                .flatMap(provider -> provider.getPendingOutputs().stream())
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, template))
                .mapToInt(ItemStack::getCount)
                .sum();
    }

    public void onWixieJobCompleted(int sourceCost, ItemStack output, boolean finalOutput) {
        orderCraftOperations++;
        orderSourceSpent += Math.max(0, sourceCost);
        if (finalOutput && ItemStack.isSameItemSameComponents(activeTarget, output)) {
            orderProducedCount = Math.min(activeTargetCount,
                    orderProducedCount + Math.max(0, output.getCount()));
        }
        sync();
    }

    private void insertBufferOrDrop(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack remaining = stack.copy();
        for (int slot = 0; slot < buffer.getSlots() && !remaining.isEmpty(); slot++) {
            remaining = buffer.insertItem(slot, remaining, false);
        }
        if (!remaining.isEmpty() && level != null && !level.isClientSide) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.75D, worldPosition.getZ() + 0.5D, remaining);
        }
    }

    private void finishOrder() {
        UUID requester = null;
        if (level != null
                && activePedestalPos != null
                && level.getBlockEntity(activePedestalPos) instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            requester = pedestal.getRequester();
            pedestal.complete();
        }
        if (level instanceof ServerLevel serverLevel && requester != null) {
            ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(requester);
            if (player != null) {
                long elapsedTicks = orderStartGameTime <= 0L ? 0L
                        : Math.max(0L, level.getGameTime() - orderStartGameTime);
                long elapsedSeconds = Math.max(1L, (elapsedTicks + 19L) / 20L);
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.order_terminal.completed_summary",
                        activeTarget.getHoverName(), activeTargetCount, elapsedSeconds,
                        orderSourceSpent, orderCraftOperations), false);
            }
        }
        activePedestalPos = null;
        activeTarget = ItemStack.EMPTY;
        activeTargetCount = 0;
        orderBaseline = 0;
        orderProducedCount = 0;
        missingItems.clear();
        resetOrderStatistics();
        setState(TerminalState.IDLE, "");
        sync();
    }

    private void resetOrderStatistics() {
        orderProducedCount = 0;
        orderStartGameTime = 0L;
        orderCraftOperations = 0;
        orderSourceSpent = 0;
    }

    public List<WixiePatternProviderBlockEntity> findProviders() {
        if (level == null) {
            return List.of();
        }
        return BlockPos.betweenClosedStream(
                        worldPosition.offset(-NETWORK_RADIUS, -NETWORK_RADIUS, -NETWORK_RADIUS),
                        worldPosition.offset(NETWORK_RADIUS, NETWORK_RADIUS, NETWORK_RADIUS)
                )
                .filter(level::hasChunkAt)
                .map(pos -> level.getBlockEntity(pos))
                .filter(WixiePatternProviderBlockEntity.class::isInstance)
                .map(WixiePatternProviderBlockEntity.class::cast)
                .sorted(Comparator
                        .comparingDouble((WixiePatternProviderBlockEntity provider) ->
                                provider.getBlockPos().distSqr(worldPosition))
                        .thenComparingLong(provider -> provider.getBlockPos().asLong()))
                .toList();
    }

    public boolean hasEncodedRecipe(ResourceLocation recipeId) {
        return recipeId != null && findProviders().stream()
                .anyMatch(provider -> provider.getRecipeIds().contains(recipeId));
    }

    public boolean hasGuideDestination() {
        return findProviders().stream().anyMatch(WixiePatternProviderBlockEntity::hasGuideSpace);
    }

    /** Sends a guide to the closest provider with capacity. */
    public boolean distributeEncodedGuide(ItemStack guide) {
        return findProviders().stream()
                .filter(WixiePatternProviderBlockEntity::hasGuideSpace)
                .sorted(Comparator.comparingDouble(provider ->
                        provider.getBlockPos().distSqr(worldPosition)))
                .anyMatch(provider -> provider.insertEncodedGuide(guide));
    }

    private List<IItemHandler> collectWixieInventories(
            List<WixiePatternProviderBlockEntity> providers
    ) {
        List<IItemHandler> result = new ArrayList<>();
        for (WixiePatternProviderBlockEntity provider : providers) {
            for (IItemHandler handler : provider.getWixieInventories()) {
                if (!result.contains(handler)) {
                    result.add(handler);
                }
            }
        }
        return List.copyOf(result);
    }

    private List<IItemHandler> collectWixieOutputInventories(
            List<WixiePatternProviderBlockEntity> providers
    ) {
        List<IItemHandler> result = new ArrayList<>();
        for (WixiePatternProviderBlockEntity provider : providers) {
            for (IItemHandler handler : provider.getWixieOutputInventories()) {
                if (!result.contains(handler)) {
                    result.add(handler);
                }
            }
        }
        return List.copyOf(result);
    }

    private static List<IItemHandler> combineInventories(
            List<IItemHandler> first,
            List<IItemHandler> second
    ) {
        List<IItemHandler> result = new ArrayList<>(first);
        for (IItemHandler handler : second) {
            if (!result.contains(handler)) {
                result.add(handler);
            }
        }
        return List.copyOf(result);
    }

    private void reportMissing(ItemStack missing) {
        ItemStack existing = missingItems.stream()
                .filter(stack -> ItemStack.isSameItemSameComponents(stack, missing))
                .findFirst().orElse(ItemStack.EMPTY);
        if (existing.isEmpty()) {
            missingItems.add(missing.copyWithCount(Math.max(1, missing.getCount())));
        } else {
            existing.setCount(Math.max(existing.getCount(), missing.getCount()));
        }
        detail = missingItems.getFirst().getHoverName().getString();
        setPedestalState(ArcaneOrderPedestalBlockEntity.OrderState.WAITING_MATERIALS, detail);
        setState(TerminalState.WAITING_MATERIALS, detail);
        sync();
    }

    private void setPedestalState(ArcaneOrderPedestalBlockEntity.OrderState newState, String newDetail) {
        if (level != null
                && activePedestalPos != null
                && level.getBlockEntity(activePedestalPos) instanceof ArcaneOrderPedestalBlockEntity pedestal) {
            pedestal.setState(newState, newDetail);
        }
    }

    private void setState(TerminalState newState, String newDetail) {
        if (state != newState || !detail.equals(newDetail)) {
            state = newState;
            detail = newDetail;
            sync();
        }
    }

    public TerminalState getState() {
        return state;
    }

    public String getDetail() {
        return detail;
    }

    public int getProviderCount() {
        return providerCount;
    }

    public int getActiveWorkerCount() {
        return activeWorkerCount;
    }

    public int getBufferedItemCount() {
        int count = 0;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            count += buffer.getStackInSlot(slot).getCount();
        }
        return count;
    }

    public List<ItemStack> getMissingItems() {
        return missingItems.stream().map(ItemStack::copy).toList();
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) {
            return;
        }
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack stack = buffer.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                        worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack.copy());
                buffer.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Buffer", buffer.serializeNBT(registries));
        tag.put("ActiveTarget", activeTarget.copyWithCount(1).saveOptional(registries));
        tag.putInt("ActiveTargetCount", activeTargetCount);
        tag.putInt("OrderBaseline", orderBaseline);
        tag.putInt("OrderProducedCount", orderProducedCount);
        tag.putLong("OrderStartGameTime", orderStartGameTime);
        tag.putInt("OrderCraftOperations", orderCraftOperations);
        tag.putInt("OrderSourceSpent", orderSourceSpent);
        CompoundTag missingTag = new CompoundTag();
        CompoundTag missingCounts = new CompoundTag();
        for (int index = 0; index < missingItems.size(); index++) {
            ItemStack missing = missingItems.get(index);
            String key = Integer.toString(index);
            missingTag.put(key, missing.copyWithCount(1).saveOptional(registries));
            missingCounts.putInt(key, Math.max(1, missing.getCount()));
        }
        tag.put("MissingItems", missingTag);
        tag.put("MissingItemCounts", missingCounts);
        if (activePedestalPos != null) {
            tag.putLong("ActivePedestal", activePedestalPos.asLong());
        }
        tag.putString("TerminalState", state.name());
        tag.putString("Detail", detail);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        buffer.deserializeNBT(registries, tag.getCompound("Buffer"));
        activeTarget = ItemStack.parseOptional(registries, tag.getCompound("ActiveTarget"));
        activeTargetCount = tag.contains("ActiveTargetCount")
                ? Math.max(0, tag.getInt("ActiveTargetCount")) : activeTarget.getCount();
        if (!activeTarget.isEmpty()) activeTarget.setCount(1);
        orderBaseline = Math.max(0, tag.getInt("OrderBaseline"));
        orderProducedCount = Math.max(0, tag.getInt("OrderProducedCount"));
        orderStartGameTime = Math.max(0L, tag.getLong("OrderStartGameTime"));
        orderCraftOperations = Math.max(0, tag.getInt("OrderCraftOperations"));
        orderSourceSpent = Math.max(0, tag.getInt("OrderSourceSpent"));
        missingItems.clear();
        CompoundTag missingTag = tag.getCompound("MissingItems");
        CompoundTag missingCounts = tag.getCompound("MissingItemCounts");
        missingTag.getAllKeys().stream().sorted(Comparator.comparingInt(Integer::parseInt)).forEach(key -> {
            ItemStack stack = ItemStack.parseOptional(registries, missingTag.getCompound(key));
            if (!stack.isEmpty()) {
                int count = missingCounts.contains(key) ? missingCounts.getInt(key) : stack.getCount();
                stack.setCount(Math.max(1, count));
                missingItems.add(stack);
            }
        });
        activePedestalPos = tag.contains("ActivePedestal") ? BlockPos.of(tag.getLong("ActivePedestal")) : null;
        detail = tag.getString("Detail");
        try {
            state = TerminalState.valueOf(tag.getString("TerminalState"));
        } catch (IllegalArgumentException ignored) {
            state = TerminalState.IDLE;
        }
    }

    /** Saves this terminal when it is used as the order engine inside another block entity. */
    public CompoundTag saveEmbedded(HolderLookup.Provider registries) {
        CompoundTag tag = new CompoundTag();
        saveAdditional(tag, registries);
        return tag;
    }

    /** Loads an order engine embedded inside another block entity. */
    public void loadEmbedded(CompoundTag tag, HolderLookup.Provider registries) {
        loadAdditional(tag, registries);
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
            BlockState blockState = getBlockState();
            level.sendBlockUpdated(worldPosition, blockState, blockState, Block.UPDATE_CLIENTS);
        }
    }

    private record RecipeMatch(
            RecipeHolder<?> recipe,
            WixiePatternProviderBlockEntity provider,
            boolean fuzzy
    ) {
    }

    private enum DispatchResult {
        AVAILABLE,
        WAITING,
        DISPATCHED,
        MISSING
    }

    public enum TerminalState {
        IDLE("message.ars_arcane_matrix.order_terminal.state.idle"),
        WAITING_MATERIALS("message.ars_arcane_matrix.order_terminal.state.waiting_materials"),
        WAITING_PATTERNS("message.ars_arcane_matrix.order_terminal.state.waiting_patterns"),
        WAITING_WORKSTATION("message.ars_arcane_matrix.order_terminal.state.waiting_workstation"),
        CRAFTING("message.ars_arcane_matrix.order_terminal.state.crafting");

        private final String translationKey;

        TerminalState(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }

    public enum AutomaticRequestResult {
        ACCEPTED,
        BUSY,
        RECIPE_UNAVAILABLE,
        NO_PEDESTAL
    }
}
