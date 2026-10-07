package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity.CraftableRecipeInfo;
import dev.arsmatrix.blockentity.AdvancedStorageLecternBlockEntity;
import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModMenus;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.item.CraftingGuideItem;
import dev.arsmatrix.compat.DynamicCraftingRecipeSupport;
import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.network.StorageEntriesDeltaPayload;
import dev.arsmatrix.network.OrderDiagnosticsPayload;
import dev.arsmatrix.util.RemoteMenuAccess;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.ResultContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.inventory.TransientCraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** A slotless request menu; all displayed stacks are recipe previews, never inventory contents. */
public final class WixieOrderTerminalMenu extends AbstractContainerMenu {

    public static final int MAX_LINKED_FLUID_TYPES = 16;
    private static final int NETWORK_DATA_SIZE = 10 + MAX_LINKED_FLUID_TYPES * 2;

    public static final int BUTTON_MINUS_ONE = 0;
    public static final int BUTTON_PLUS_ONE = 1;
    public static final int BUTTON_MINUS_SIXTEEN = 2;
    public static final int BUTTON_PLUS_SIXTEEN = 3;
    public static final int BUTTON_SUBMIT = 4;
    public static final int BUTTON_CANCEL = 5;
    public static final int BUTTON_SHOW_STORAGE = 6;
    public static final int BUTTON_SHOW_ORDERS = 7;
    public static final int BUTTON_TOGGLE_CRAFTING_GRID = 8;
    public static final int BUTTON_STORAGE_DEPOSIT = 9;
    public static final int BUTTON_MANAGE_PATTERNS = 10;
    public static final int BUTTON_TOGGLE_MATCH_MODE = 11;
    public static final int BUTTON_RETURN_CRAFTING_GRID = 12;
    public static final int BUTTON_SELECT_OFFSET = 1000;
    public static final int BUTTON_STORAGE_ONE_OFFSET = 100000;
    public static final int BUTTON_STORAGE_STACK_OFFSET = 200000;
    public static final int BUTTON_STORAGE_ALL_OFFSET = 300000;
    public static final int BUTTON_SET_COUNT_FLAG = 0x20000000;
    private static final int BUTTON_SET_COUNT_MASK = 0x1FFFFFFF;
    private static final int BUTTON_ENCODE_FLAG = 0x40000000;
    private static final int BUTTON_RECIPE_HASH_MASK = 0x3FFFFFFF;

    private final WixieOrderTerminalBlockEntity terminal;
    private final AdvancedStorageLecternBlockEntity advancedLectern;
    private final BlockPos terminalPos;
    private final ResourceKey<Level> terminalDimension;
    private final List<ItemStack> craftableOutputs;
    private final List<CraftableRecipeInfo> craftableRecipeInfos;
    private final List<StorageEntry> storedEntries;
    /** Server-only baseline of what was actually sent to this client's storage view. */
    private final List<StorageEntry> lastSyncedStoredEntries = new ArrayList<>();
    private final boolean advancedStorage;
    private final Player menuPlayer;
    private final ContainerData sourceData;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private boolean storageCraftingActive;
    private boolean storagePageActive;
    private int selectedIndex = -1;
    private int requestedCount = 1;
    private long lastStorageRefreshTime = Long.MIN_VALUE;
    private long lastDiagnosticsRefreshTime = Long.MIN_VALUE;
    private OrderDiagnosticsPayload diagnostics = emptyDiagnostics();

    private static OrderDiagnosticsPayload emptyDiagnostics() {
        return new OrderDiagnosticsPayload(-1, ItemStack.EMPTY, 0, 0,
                "message.ars_arcane_matrix.order_terminal.state.idle", "",
                0, 0, 0, 0L, 0, 0, List.of(), List.of());
    }

    public WixieOrderTerminalMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, readOpeningData(data));
    }

    private WixieOrderTerminalMenu(
            int containerId,
            Inventory inventory,
            OpeningData opening
    ) {
        super(ModMenus.WIXIE_ORDER_TERMINAL.get(), containerId);
        terminalPos = opening.pos();
        terminalDimension = opening.dimension();
        Level targetLevel = targetLevel(inventory.player);
        var foundBlockEntity = targetLevel == null ? null : targetLevel.getBlockEntity(opening.pos());
        terminal = foundBlockEntity instanceof WixieOrderTerminalBlockEntity found
                ? found
                : foundBlockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern.getOrderEngine() : null;
        advancedLectern = foundBlockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern : null;
        craftableRecipeInfos = new ArrayList<>(opening.recipes());
        craftableOutputs = craftableRecipeInfos.stream().map(CraftableRecipeInfo::output).toList();
        storedEntries = new ArrayList<>(opening.storage());
        advancedStorage = opening.advanced();
        menuPlayer = inventory.player;
        sourceData = new SimpleContainerData(NETWORK_DATA_SIZE);
        storageCraftingActive = advancedStorage;
        storagePageActive = advancedStorage;
        addCraftingAndInventorySlots(inventory);
        loadPersistentCraftingGrid();
    }

    public WixieOrderTerminalMenu(
            int containerId,
            Inventory inventory,
            WixieOrderTerminalBlockEntity terminal
    ) {
        super(ModMenus.WIXIE_ORDER_TERMINAL.get(), containerId);
        this.terminal = terminal;
        advancedLectern = null;
        terminalPos = terminal.getBlockPos().immutable();
        terminalDimension = terminal.getLevel() == null
                ? inventory.player.level().dimension() : terminal.getLevel().dimension();
        craftableRecipeInfos = new ArrayList<>(terminal.getCraftableRecipeInfos());
        craftableOutputs = craftableRecipeInfos.stream().map(CraftableRecipeInfo::output).toList();
        storedEntries = new ArrayList<>();
        advancedStorage = false;
        menuPlayer = inventory.player;
        sourceData = new SimpleContainerData(NETWORK_DATA_SIZE);
        storageCraftingActive = false;
        storagePageActive = false;
        addCraftingAndInventorySlots(inventory);
    }

    public WixieOrderTerminalMenu(
            int containerId,
            Inventory inventory,
            AdvancedStorageLecternBlockEntity terminal,
            List<AdvancedStorageLecternBlockEntity.StoredStack> storage
    ) {
        super(ModMenus.WIXIE_ORDER_TERMINAL.get(), containerId);
        this.terminal = terminal.getOrderEngine();
        advancedLectern = terminal;
        terminalPos = terminal.getBlockPos().immutable();
        terminalDimension = terminal.getLevel() == null
                ? inventory.player.level().dimension() : terminal.getLevel().dimension();
        craftableRecipeInfos = new ArrayList<>(terminal.getCraftableRecipeInfos());
        craftableOutputs = craftableRecipeInfos.stream().map(CraftableRecipeInfo::output).toList();
        storedEntries = storage.stream()
                .map(entry -> new StorageEntry(entry.stack().copy(), entry.count()))
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        advancedStorage = true;
        menuPlayer = inventory.player;
        sourceData = sourceData(terminal);
        storageCraftingActive = true;
        storagePageActive = true;
        addCraftingAndInventorySlots(inventory);
        loadPersistentCraftingGrid();
    }

    private void loadPersistentCraftingGrid() {
        if (advancedLectern == null || menuPlayer.level().isClientSide) return;
        List<ItemStack> saved = advancedLectern.getCraftingGrid();
        for (int slot = 0; slot < Math.min(saved.size(), craftSlots.getContainerSize()); slot++) {
            craftSlots.setItem(slot, saved.get(slot));
        }
        slotsChanged(craftSlots);
    }

    private void savePersistentCraftingGrid() {
        if (advancedLectern == null || menuPlayer.level().isClientSide) return;
        List<ItemStack> saved = new ArrayList<>(craftSlots.getContainerSize());
        for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
            saved.add(craftSlots.getItem(slot).copy());
        }
        advancedLectern.setCraftingGrid(saved);
    }

    private void addCraftingAndInventorySlots(Inventory inventory) {
        addDataSlots(sourceData);
        addSlot(new ResultSlot(menuPlayer, craftSlots, resultSlots, 0, 128, 192) {
            @Override public boolean isActive() { return isStorageCraftingActive(); }
        });
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 3; column++) {
                addSlot(new Slot(craftSlots, column + row * 3, 26 + column * 18, 174 + row * 18) {
                    @Override public boolean isActive() { return isStorageCraftingActive(); }
                });
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 240 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 298));
        }
    }

    private static ContainerData sourceData(AdvancedStorageLecternBlockEntity lectern) {
        return new ContainerData() {
            private long lastPatternRefresh = Long.MIN_VALUE;
            private int providerCount;
            private int usedPatterns;
            private int patternCapacity;
            private int blankGuides;

            private void refreshPatternData() {
                Level level = lectern.getLevel();
                long refreshBucket = level == null ? 0L : level.getGameTime() / 20L;
                if (lastPatternRefresh == refreshBucket) return;
                lastPatternRefresh = refreshBucket;
                List<WixiePatternProviderBlockEntity> providers = lectern.getOrderEngine().findProviders();
                providerCount = Math.min(32767, providers.size());
                usedPatterns = Math.min(32767, providers.stream()
                        .mapToInt(WixiePatternProviderBlockEntity::getGuideCount).sum());
                patternCapacity = Math.min(32767, providers.stream()
                        .mapToInt(WixiePatternProviderBlockEntity::getGuideCapacity).sum());
                blankGuides = Math.min(32767, lectern.getStoredStacks().stream()
                        .filter(entry -> isBlankGuide(entry.stack()))
                        .mapToInt(AdvancedStorageLecternBlockEntity.StoredStack::count).sum());
            }

            @Override public int get(int index) {
                long stored = lectern.getNetworkSource();
                long capacity = lectern.getNetworkCapacity();
                if (index >= 6 + MAX_LINKED_FLUID_TYPES * 2) refreshPatternData();
                return switch (index) {
                    case 0 -> (int) stored;
                    case 1 -> (int) (stored >>> 32);
                    case 2 -> (int) capacity;
                    case 3 -> (int) (capacity >>> 32);
                    case 4 -> lectern.getLinkedSourceJarCount();
                    case 5 -> lectern.getLinkedSourceRelayCount();
                    case 6 + MAX_LINKED_FLUID_TYPES * 2 -> providerCount;
                    case 7 + MAX_LINKED_FLUID_TYPES * 2 -> usedPatterns;
                    case 8 + MAX_LINKED_FLUID_TYPES * 2 -> patternCapacity;
                    case 9 + MAX_LINKED_FLUID_TYPES * 2 -> blankGuides;
                    default -> index >= 6 && index < 6 + MAX_LINKED_FLUID_TYPES * 2
                            ? (index & 1) == 0
                            ? lectern.getLinkedFluidType((index - 6) / 2)
                            : lectern.getLinkedFluidAmount((index - 7) / 2)
                            : 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return NETWORK_DATA_SIZE; }
        };
    }

    private static List<CraftableRecipeInfo> readRecipes(RegistryFriendlyByteBuf data) {
        int size = Math.min(256, data.readVarInt());
        List<CraftableRecipeInfo> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(new CraftableRecipeInfo(
                    ItemStack.STREAM_CODEC.decode(data),
                    data.readResourceLocation(), data.readResourceLocation(), data.readBoolean()));
        }
        return result;
    }

    private static OpeningData readOpeningData(RegistryFriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        ResourceKey<Level> dimension = ResourceKey.create(
                Registries.DIMENSION, data.readResourceLocation());
        List<CraftableRecipeInfo> recipes = readRecipes(data);
        boolean advanced = data.readBoolean();
        List<StorageEntry> storage = new ArrayList<>();
        if (advanced) {
            int size = Math.min(512, data.readVarInt());
            for (int index = 0; index < size; index++) {
                storage.add(new StorageEntry(ItemStack.STREAM_CODEC.decode(data), data.readVarInt()));
            }
        }
        return new OpeningData(pos, dimension, recipes, storage, advanced);
    }

    public static void writeOpeningData(
            RegistryFriendlyByteBuf data,
            BlockPos pos,
            ResourceKey<Level> dimension,
            List<CraftableRecipeInfo> recipes
    ) {
        data.writeBlockPos(pos);
        data.writeResourceLocation(dimension.location());
        writeRecipes(data, recipes);
        data.writeBoolean(false);
    }

    public static void writeOpeningData(
            RegistryFriendlyByteBuf data,
            BlockPos pos,
            ResourceKey<Level> dimension,
            List<CraftableRecipeInfo> recipes,
            List<AdvancedStorageLecternBlockEntity.StoredStack> storage
    ) {
        data.writeBlockPos(pos);
        data.writeResourceLocation(dimension.location());
        writeRecipes(data, recipes);
        data.writeBoolean(true);
        data.writeVarInt(Math.min(512, storage.size()));
        storage.stream().limit(512).forEach(entry -> {
            ItemStack.STREAM_CODEC.encode(data, entry.stack());
            data.writeVarInt(entry.count());
        });
    }

    private static void writeRecipes(
            RegistryFriendlyByteBuf data, List<CraftableRecipeInfo> recipes
    ) {
        data.writeVarInt(Math.min(256, recipes.size()));
        recipes.stream().limit(256).forEach(info -> {
            ItemStack.STREAM_CODEC.encode(data, info.output());
            data.writeResourceLocation(info.recipeId());
            data.writeResourceLocation(info.workstation());
            data.writeBoolean(info.fuzzy());
        });
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if ((id & BUTTON_ENCODE_FLAG) != 0) {
            return encodeGuideFromJei(player, id & BUTTON_RECIPE_HASH_MASK);
        }
        if ((id & BUTTON_SET_COUNT_FLAG) != 0) {
            requestedCount = Math.max(1, Math.min(9999, id & BUTTON_SET_COUNT_MASK));
            return true;
        }
        if (id >= BUTTON_STORAGE_ALL_OFFSET) {
            return extractStorage(player, id - BUTTON_STORAGE_ALL_OFFSET, StoragePull.ALL_FITTING);
        }
        if (id >= BUTTON_STORAGE_STACK_OFFSET) {
            return extractStorage(player, id - BUTTON_STORAGE_STACK_OFFSET, StoragePull.STACK);
        }
        if (id >= BUTTON_STORAGE_ONE_OFFSET) {
            return extractStorage(player, id - BUTTON_STORAGE_ONE_OFFSET, StoragePull.ONE);
        }
        if (id >= BUTTON_SELECT_OFFSET) {
            int requestedIndex = id - BUTTON_SELECT_OFFSET;
            if (requestedIndex < craftableOutputs.size()) {
                selectedIndex = requestedIndex;
                requestedCount = 1;
                return true;
            }
            return false;
        }
        switch (id) {
            case BUTTON_MINUS_ONE -> requestedCount = Math.max(1, requestedCount - 1);
            case BUTTON_PLUS_ONE -> requestedCount = Math.min(9999, requestedCount + 1);
            case BUTTON_MINUS_SIXTEEN -> requestedCount = requestedCount <= 16
                    ? 1
                    : Math.max(16, ((requestedCount - 1) / 16) * 16);
            case BUTTON_PLUS_SIXTEEN -> requestedCount = Math.min(
                    9999, ((requestedCount / 16) + 1) * 16);
            case BUTTON_SUBMIT -> {
                if (terminal != null && selectedIndex >= 0 && selectedIndex < craftableOutputs.size()) {
                    terminal.requestFromTerminal(craftableOutputs.get(selectedIndex), requestedCount, player);
                }
            }
            case BUTTON_CANCEL -> {
                if (terminal != null) {
                    terminal.cancelFromTerminal(player);
                }
            }
            case BUTTON_SHOW_STORAGE -> storagePageActive = advancedStorage;
            case BUTTON_SHOW_ORDERS -> storagePageActive = false;
            case BUTTON_TOGGLE_CRAFTING_GRID -> {
                if (advancedStorage && storagePageActive) storageCraftingActive = !storageCraftingActive;
            }
            case BUTTON_STORAGE_DEPOSIT -> {
                if (!advancedStorage || !storagePageActive) return false;
                return depositCarriedStack(player);
            }
            case BUTTON_MANAGE_PATTERNS -> {
                if (!advancedStorage || player.level().isClientSide) return advancedStorage;
                WixiePatternProviderBlockEntity provider = terminal == null
                        ? null : terminal.findProviders().stream().findFirst().orElse(null);
                if (provider == null) {
                    player.displayClientMessage(Component.translatable(
                            "message.ars_arcane_matrix.pattern_provider.none_nearby"), true);
                    return true;
                }
                if (player instanceof ServerPlayer serverPlayer) {
                    serverPlayer.openMenu(provider, data -> {
                        data.writeBlockPos(provider.getBlockPos());
                        data.writeVarInt(provider.getGuideCapacity());
                        data.writeBlockPos(terminalPos);
                    });
                }
            }
            case BUTTON_TOGGLE_MATCH_MODE -> {
                if (selectedIndex < 0 || selectedIndex >= craftableRecipeInfos.size()) return false;
                CraftableRecipeInfo selected = craftableRecipeInfos.get(selectedIndex);
                boolean fuzzy = !selected.fuzzy();
                craftableRecipeInfos.set(selectedIndex, new CraftableRecipeInfo(
                        selected.output(), selected.recipeId(), selected.workstation(), fuzzy));
                if (!player.level().isClientSide && terminal != null) {
                    terminal.setRecipeFuzzy(selected.recipeId(), fuzzy);
                }
            }
            case BUTTON_RETURN_CRAFTING_GRID -> {
                if (!advancedStorage || !storagePageActive) return false;
                // The client-side lectern does not own the complete Bookwyrm network. Running
                // the return locally would therefore replace the catalogue with an empty item
                // snapshot until the menu was reopened. Let the authoritative server update
                // both the real storage and these crafting slots.
                if (player.level().isClientSide) return true;
                if (advancedLectern == null) return false;
                returnCraftingGrid(advancedLectern, player);
                refreshStoredEntries(advancedLectern);
            }
            default -> {
                return false;
            }
        }
        return true;
    }

    private boolean depositCarriedStack(Player player) {
        ItemStack carried = getCarried();
        if (carried.isEmpty()) return true;
        ItemStack original = carried.copy();
        if (player.level().isClientSide) {
            mergeStoredPreview(original, original.getCount());
            setCarried(ItemStack.EMPTY);
            return true;
        }
        Level targetLevel = targetLevel(player);
        if (targetLevel == null || !(targetLevel.getBlockEntity(terminalPos)
                instanceof AdvancedStorageLecternBlockEntity lectern)) {
            return false;
        }
        ItemStack remainder = lectern.insertStored(original);
        int accepted = original.getCount() - remainder.getCount();
        setCarried(remainder);
        if (accepted > 0) refreshStoredEntries(lectern);
        else player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.advanced_storage_lectern.storage_full"), true);
        return true;
    }

    /** Fills only the advanced lectern's manual 3x3 grid; this never submits an order. */
    public boolean fillStorageCraftingFromJei(
            Player player, ResourceLocation recipeId, boolean maxTransfer
    ) {
        // JEI may invoke a transfer after rebuilding its overlay while the menu's
        // client-only folding state is one packet ahead of the server. The request
        // itself is only emitted by our advanced-lectern Storage-page handler, so
        // make the server authoritative and restore that state before filling.
        if (!advancedStorage) return false;
        storagePageActive = true;
        storageCraftingActive = true;
        if (player.level().isClientSide) return true;

        Level targetLevel = targetLevel(player);
        if (targetLevel == null || !(targetLevel.getBlockEntity(terminalPos)
                instanceof AdvancedStorageLecternBlockEntity lectern)) return false;
        RecipeHolder<CraftingRecipe> holder = player.level().getRecipeManager()
                .getAllRecipesFor(RecipeType.CRAFTING).stream()
                .filter(candidate -> candidate.id().equals(recipeId))
                .filter(candidate -> !candidate.value().isSpecial())
                .findFirst().orElse(null);
        if (holder == null) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.advanced_storage_lectern.recipe_missing"), true);
            return true;
        }

        returnCraftingGrid(lectern, player);
        List<AvailableStack> available = availableCraftingStacks(lectern, player);
        List<AvailableStack> planning = copyAvailable(available);
        List<ItemStack> selected = new ArrayList<>();
        for (Ingredient ingredient : holder.value().getIngredients()) {
            if (ingredient.isEmpty()) {
                selected.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack choice = maxTransfer
                    ? selectMostAvailableIngredient(ingredient, planning)
                    : selectAvailableIngredient(ingredient, planning);
            if (choice.isEmpty()) {
                ItemStack[] examples = ingredient.getItems();
                ItemStack example = examples.length == 0 ? ItemStack.EMPTY : examples[0];
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.advanced_storage_lectern.crafting_missing",
                        example.isEmpty() ? Component.literal("?") : example.getHoverName()), true);
                refreshStoredEntries(lectern);
                return true;
            }
            selected.add(choice);
        }

        int craftCount = maxTransfer ? maximumCraftCount(selected, available) : 1;

        List<ItemStack> extracted = new ArrayList<>();
        for (ItemStack choice : selected) {
            if (choice.isEmpty()) {
                extracted.add(ItemStack.EMPTY);
                continue;
            }
            ItemStack taken = lectern.extractStoredInternal(choice, craftCount);
            int remaining = craftCount - taken.getCount();
            if (remaining > 0) {
                ItemStack fromPlayer = extractFromPlayer(player, choice, remaining);
                if (taken.isEmpty()) taken = fromPlayer;
                else taken.grow(fromPlayer.getCount());
            }
            if (taken.getCount() < craftCount) {
                if (!taken.isEmpty()) extracted.add(taken);
                returnExtracted(lectern, player, extracted);
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.advanced_storage_lectern.crafting_changed"), true);
                refreshStoredEntries(lectern);
                return true;
            }
            extracted.add(taken);
        }

        if (holder.value() instanceof ShapedRecipe shaped) {
            for (int recipeSlot = 0; recipeSlot < extracted.size(); recipeSlot++) {
                int row = recipeSlot / shaped.getWidth();
                int column = recipeSlot % shaped.getWidth();
                if (row < 3 && column < 3) {
                    craftSlots.setItem(row * 3 + column, extracted.get(recipeSlot));
                }
            }
        } else {
            int targetSlot = 0;
            for (ItemStack stack : extracted) {
                if (!stack.isEmpty() && targetSlot < 9) craftSlots.setItem(targetSlot++, stack);
            }
        }
        slotsChanged(craftSlots);
        refreshStoredEntries(lectern);
        return true;
    }

    private void returnCraftingGrid(AdvancedStorageLecternBlockEntity lectern, Player player) {
        for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
            ItemStack stack = craftSlots.removeItemNoUpdate(slot);
            if (stack.isEmpty()) continue;
            ItemStack remainder = lectern.insertStored(stack);
            if (!remainder.isEmpty()) {
                net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, remainder);
            }
        }
        slotsChanged(craftSlots);
    }

    private static List<AvailableStack> availableCraftingStacks(
            AdvancedStorageLecternBlockEntity lectern, Player player
    ) {
        List<AvailableStack> result = new ArrayList<>();
        lectern.getStoredStacks().forEach(entry ->
                mergeAvailable(result, entry.stack(), entry.count()));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) mergeAvailable(result, stack, stack.getCount());
        }
        return result;
    }

    private static void mergeAvailable(List<AvailableStack> available, ItemStack stack, int count) {
        for (AvailableStack entry : available) {
            if (!ItemStack.isSameItemSameComponents(entry.stack, stack)) continue;
            entry.count = (int) Math.min(Integer.MAX_VALUE, (long) entry.count + count);
            return;
        }
        available.add(new AvailableStack(stack.copyWithCount(1), count));
    }

    private static ItemStack selectAvailableIngredient(
            Ingredient ingredient, List<AvailableStack> available
    ) {
        for (ItemStack preferred : ingredient.getItems()) {
            for (AvailableStack entry : available) {
                if (entry.count > 0 && ItemStack.isSameItemSameComponents(entry.stack, preferred)) {
                    entry.count--;
                    return entry.stack.copyWithCount(1);
                }
            }
        }
        for (AvailableStack entry : available) {
            if (entry.count <= 0 || !ingredient.test(entry.stack)) continue;
            entry.count--;
            return entry.stack.copyWithCount(1);
        }
        return ItemStack.EMPTY;
    }

    private static ItemStack selectMostAvailableIngredient(
            Ingredient ingredient, List<AvailableStack> available
    ) {
        AvailableStack best = null;
        for (AvailableStack entry : available) {
            if (entry.count <= 0 || !ingredient.test(entry.stack)) continue;
            if (best == null || entry.count > best.count) best = entry;
        }
        if (best == null) return ItemStack.EMPTY;
        best.count--;
        return best.stack.copyWithCount(1);
    }

    private static List<AvailableStack> copyAvailable(List<AvailableStack> source) {
        List<AvailableStack> result = new ArrayList<>(source.size());
        source.forEach(entry -> result.add(new AvailableStack(entry.stack, entry.count)));
        return result;
    }

    private static int maximumCraftCount(
            List<ItemStack> selected, List<AvailableStack> available
    ) {
        int maximum = 64;
        for (ItemStack choice : selected) {
            if (choice.isEmpty()) continue;
            int occurrences = 0;
            for (ItemStack other : selected) {
                if (ItemStack.isSameItemSameComponents(choice, other)) occurrences++;
            }
            int count = 0;
            for (AvailableStack entry : available) {
                if (ItemStack.isSameItemSameComponents(choice, entry.stack)) count += entry.count;
            }
            maximum = Math.min(maximum,
                    Math.min(choice.getMaxStackSize(), count / Math.max(1, occurrences)));
        }
        return Math.max(1, maximum);
    }

    private static ItemStack extractFromPlayer(Player player, ItemStack template, int requested) {
        ItemStack result = template.copyWithCount(0);
        for (int slot = 0; slot < player.getInventory().getContainerSize()
                && result.getCount() < requested; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!ItemStack.isSameItemSameComponents(stack, template)) continue;
            int moved = Math.min(requested - result.getCount(), stack.getCount());
            stack.shrink(moved);
            result.grow(moved);
        }
        if (!result.isEmpty()) player.getInventory().setChanged();
        return result;
    }

    private static void returnExtracted(
            AdvancedStorageLecternBlockEntity lectern, Player player, List<ItemStack> extracted
    ) {
        for (ItemStack stack : extracted) {
            if (stack.isEmpty()) continue;
            ItemStack remainder = lectern.insertStored(stack);
            if (!remainder.isEmpty()) {
                net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, remainder);
            }
        }
    }

    private static final class AvailableStack {
        private final ItemStack stack;
        private int count;

        private AvailableStack(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }

    private void refreshStoredEntries(AdvancedStorageLecternBlockEntity lectern) {
        storedEntries.clear();
        lectern.getStoredStacks().stream().limit(512).forEach(entry ->
                storedEntries.add(new StorageEntry(entry.stack().copy(), entry.count())));
        // Do not treat a refreshed server-side working list as if the client had
        // already received it. Force the next menu tick to publish the delta.
        if (!menuPlayer.level().isClientSide) lastStorageRefreshTime = Long.MIN_VALUE;
    }

    /** Applies server-sent changes without replacing unchanged storage entries. */
    public void applyStorageDelta(List<StorageEntry> changes) {
        for (StorageEntry change : changes) {
            int existing = findStoredEntry(storedEntries, change.stack());
            if (change.count() <= 0) {
                if (existing >= 0) storedEntries.remove(existing);
            } else if (existing >= 0) {
                storedEntries.set(existing, new StorageEntry(change.stack().copy(), change.count()));
            } else {
                storedEntries.add(new StorageEntry(change.stack().copy(), change.count()));
            }
        }
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (menuPlayer.level().isClientSide || !(menuPlayer instanceof ServerPlayer serverPlayer)) return;
        long gameTime = menuPlayer.level().getGameTime();
        if (terminal != null && (lastDiagnosticsRefreshTime == Long.MIN_VALUE
                || gameTime - lastDiagnosticsRefreshTime >= 20L)) {
            lastDiagnosticsRefreshTime = gameTime;
            PacketDistributor.sendToPlayer(serverPlayer, new OrderDiagnosticsPayload(
                    containerId,
                    terminal.getActiveTarget(),
                    terminal.getActiveTargetCount(),
                    terminal.getOrderProducedCount(),
                    terminal.getState().translationKey(),
                    terminal.getDetail(),
                    terminal.getProviderCount(),
                    terminal.getActiveWorkerCount(),
                    terminal.getBufferedItemCount(),
                    terminal.getOrderElapsedTicks(),
                    terminal.getOrderCraftOperations(),
                    terminal.getOrderSourceSpent(),
                    terminal.getMissingItems(),
                    terminal.getActiveJobDiagnostics()));
        }
        if (!advancedStorage) return;
        if (lastStorageRefreshTime != Long.MIN_VALUE
                && gameTime - lastStorageRefreshTime < 10L) return;
        lastStorageRefreshTime = gameTime;
        Level targetLevel = targetLevel(menuPlayer);
        if (targetLevel == null || !(targetLevel.getBlockEntity(terminalPos)
                instanceof AdvancedStorageLecternBlockEntity lectern)) return;

        List<StorageEntry> snapshot = lectern.getStoredStacks().stream().limit(512)
                .map(entry -> new StorageEntry(entry.stack().copy(), entry.count()))
                .toList();
        List<StorageEntry> changes = storageDelta(lastSyncedStoredEntries, snapshot);
        if (changes.isEmpty()) return;
        storedEntries.clear();
        storedEntries.addAll(snapshot);
        lastSyncedStoredEntries.clear();
        lastSyncedStoredEntries.addAll(snapshot);
        PacketDistributor.sendToPlayer(serverPlayer,
                new StorageEntriesDeltaPayload(containerId, changes));
    }

    private static List<StorageEntry> storageDelta(
            List<StorageEntry> previous, List<StorageEntry> current
    ) {
        List<StorageEntry> changes = new ArrayList<>();
        for (StorageEntry entry : current) {
            int oldIndex = findStoredEntry(previous, entry.stack());
            if (oldIndex < 0 || previous.get(oldIndex).count() != entry.count()) {
                changes.add(entry);
            }
        }
        for (StorageEntry entry : previous) {
            if (findStoredEntry(current, entry.stack()) < 0) {
                changes.add(new StorageEntry(entry.stack().copy(), 0));
            }
        }
        return changes;
    }

    private static int findStoredEntry(List<StorageEntry> entries, ItemStack stack) {
        for (int index = 0; index < entries.size(); index++) {
            if (ItemStack.isSameItemSameComponents(entries.get(index).stack(), stack)) return index;
        }
        return -1;
    }

    private void mergeStoredPreview(ItemStack stack, int amount) {
        for (int index = 0; index < storedEntries.size(); index++) {
            StorageEntry entry = storedEntries.get(index);
            if (!ItemStack.isSameItemSameComponents(entry.stack(), stack)) continue;
            storedEntries.set(index, new StorageEntry(entry.stack(),
                    (int) Math.min(Integer.MAX_VALUE, (long) entry.count() + amount)));
            return;
        }
        storedEntries.add(new StorageEntry(stack.copyWithCount(1), amount));
    }

    private boolean extractStorage(Player player, int index, StoragePull pull) {
        Level targetLevel = targetLevel(player);
        if (targetLevel == null
                || !(targetLevel.getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity lectern)
                || index < 0 || index >= storedEntries.size()) {
            return false;
        }
        StorageEntry entry = storedEntries.get(index);
        int requested = switch (pull) {
            case ONE -> 1;
            case STACK -> Math.min(entry.stack().getMaxStackSize(), entry.count());
            case ALL_FITTING -> Math.min(entry.count(), inventoryRoomFor(player, entry.stack()));
        };
        if (requested <= 0) return true;
        if (player.level().isClientSide) {
            storedEntries.set(index, new StorageEntry(entry.stack(), Math.max(0, entry.count() - requested)));
            return true;
        }
        int extracted = lectern.extractStored(entry.stack(), requested, player);
        if (extracted > 0) {
            storedEntries.set(index, new StorageEntry(entry.stack(), Math.max(0, entry.count() - extracted)));
        }
        return true;
    }

    /** Server-authoritative extraction keyed by the clicked stack, never by a stale UI index. */
    public boolean extractStorageMatching(Player player, ItemStack template, int mode) {
        if (player.level().isClientSide || !advancedStorage || template.isEmpty()) return false;
        Level targetLevel = targetLevel(player);
        if (targetLevel == null
                || !(targetLevel.getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity lectern)) {
            return false;
        }
        AdvancedStorageLecternBlockEntity.StoredStack current = lectern.getStoredStacks().stream()
                .filter(entry -> ItemStack.isSameItemSameComponents(entry.stack(), template))
                .findFirst().orElse(null);
        if (current == null || current.count() <= 0) return true;
        int requested = switch (mode) {
            case dev.arsmatrix.network.StorageExtractionPayload.ONE -> 1;
            case dev.arsmatrix.network.StorageExtractionPayload.ALL_FITTING ->
                    Math.min(current.count(), inventoryRoomFor(player, current.stack()));
            default -> Math.min(current.stack().getMaxStackSize(), current.count());
        };
        if (requested > 0) lectern.extractStored(current.stack(), requested, player);
        refreshStoredEntries(lectern);
        return true;
    }

    private static int inventoryRoomFor(Player player, ItemStack template) {
        long room = 0L;
        int stackLimit = template.getMaxStackSize();
        for (ItemStack existing : player.getInventory().items) {
            if (existing.isEmpty()) {
                room += stackLimit;
            } else if (ItemStack.isSameItemSameComponents(existing, template)) {
                room += Math.max(0, Math.min(stackLimit, existing.getMaxStackSize()) - existing.getCount());
            }
            if (room >= Integer.MAX_VALUE) return Integer.MAX_VALUE;
        }
        return (int) room;
    }

    private enum StoragePull { ONE, STACK, ALL_FITTING }

    public static int recipeEncodingButton(ResourceLocation recipeId) {
        return BUTTON_ENCODE_FLAG | (recipeId.hashCode() & BUTTON_RECIPE_HASH_MASK);
    }

    private boolean encodeGuideFromJei(Player player, int recipeHash) {
        if (terminal == null || player.level().isClientSide) {
            return true;
        }
        RecipeHolder<?> selected = RecipeAutomationSupport.all(player.level().getRecipeManager()).stream()
                .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                .filter(holder -> (holder.id().hashCode() & BUTTON_RECIPE_HASH_MASK) == recipeHash)
                .findFirst().orElse(null);
        if (selected == null) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_recipe_missing"), true);
            return false;
        }
        Level targetLevel = targetLevel(player);
        AdvancedStorageLecternBlockEntity lectern = advancedStorage && targetLevel != null
                && targetLevel.getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity found
                ? found : null;
        if (lectern != null && terminal.hasEncodedRecipe(selected.id())) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_already_encoded"), true);
            return false;
        }
        if (lectern != null && !terminal.hasGuideDestination()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_provider_full"), true);
            return false;
        }

        ItemStack blankTemplate = new ItemStack(ModItems.CRAFTING_GUIDE.get());
        ItemStack consumedBlank = lectern == null
                ? ItemStack.EMPTY : lectern.extractOneStoredInternal(blankTemplate);
        boolean fromStorage = !consumedBlank.isEmpty();
        int blankSlot = fromStorage ? -1 : findBlankGuideSlot(player);
        if (!fromStorage && blankSlot < 0) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_need_blank"), true);
            return false;
        }
        if (!fromStorage) player.getInventory().getItem(blankSlot).shrink(1);
        ItemStack encoded = new ItemStack(ModItems.CRAFTING_GUIDE.get());
        ItemStack result = RecipeAutomationSupport.result(selected.value(), player.level().registryAccess());
        CraftingGuideItem.encodeRecipe(encoded, selected, result, player.level().registryAccess());

        if (lectern != null) {
            if (!terminal.distributeEncodedGuide(encoded)) {
                ItemStack refund = fromStorage ? lectern.insertStored(blankTemplate) : blankTemplate;
                if (!refund.isEmpty() && !player.getInventory().add(refund)) player.drop(refund, false);
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.crafting_guide.jei_provider_full"), true);
                refreshStoredEntries(lectern);
                return false;
            }
            refreshStoredEntries(lectern);
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_recorded_distributed",
                    result.getHoverName()), true);
        } else {
            if (!player.getInventory().add(encoded)) player.drop(encoded, false);
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_recorded",
                    result.getHoverName()), true);
        }
        player.getInventory().setChanged();
        return true;
    }

    /**
     * Atomically records a client-previewed recipe chain into this lectern's provider network.
     * Every recipe, blank guide and destination slot is checked before anything is consumed.
     */
    public boolean encodeGuideChain(Player player, List<ResourceLocation> requestedRecipeIds) {
        if (player.level().isClientSide) return true;
        Level targetLevel = targetLevel(player);
        if (!advancedStorage || terminal == null || targetLevel == null
                || !(targetLevel.getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity lectern)) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.chain_requires_lectern"), true);
            return false;
        }

        Set<ResourceLocation> uniqueIds = new LinkedHashSet<>(requestedRecipeIds);
        List<RecipeHolder<?>> recipes = new ArrayList<>();
        for (ResourceLocation recipeId : uniqueIds) {
            RecipeHolder<?> holder = RecipeAutomationSupport.find(
                    player.level().getRecipeManager(), recipeId).orElse(null);
            if (holder == null || !RecipeAutomationSupport.supports(holder.value())) {
                player.displayClientMessage(Component.translatable(
                        "message.ars_arcane_matrix.crafting_guide.chain_changed"), true);
                return false;
            }
            if (!terminal.hasEncodedRecipe(recipeId)) recipes.add(holder);
        }
        if (recipes.isEmpty()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.chain_nothing_new"), true);
            return true;
        }

        List<GuideDestination> destinations = new ArrayList<>();
        for (WixiePatternProviderBlockEntity provider : terminal.findProviders()) {
            for (int slot = 0; slot < provider.getGuideCapacity(); slot++) {
                if (provider.getGuideHandler().getStackInSlot(slot).isEmpty()) {
                    destinations.add(new GuideDestination(provider, slot));
                }
            }
        }
        if (destinations.size() < recipes.size()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.chain_provider_space",
                    recipes.size(), destinations.size()), true);
            return false;
        }

        ItemStack blankTemplate = new ItemStack(ModItems.CRAFTING_GUIDE.get());
        int networkBlanks = lectern.getStoredStacks().stream()
                .filter(entry -> isBlankGuide(entry.stack()))
                .mapToInt(AdvancedStorageLecternBlockEntity.StoredStack::count).sum();
        int playerBlanks = countBlankGuides(player);
        if (networkBlanks + playerBlanks < recipes.size()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.chain_need_blanks",
                    recipes.size(), networkBlanks + playerBlanks), true);
            return false;
        }

        int fromNetwork = Math.min(recipes.size(), networkBlanks);
        ItemStack extracted = fromNetwork <= 0 ? ItemStack.EMPTY
                : lectern.extractStoredInternal(blankTemplate, fromNetwork);
        int consumedFromNetwork = extracted.getCount();
        int requestedFromPlayer = recipes.size() - consumedFromNetwork;
        int consumedFromPlayer = consumeBlankGuides(player, requestedFromPlayer);
        if (consumedFromNetwork != fromNetwork || consumedFromPlayer != requestedFromPlayer) {
            refundBlankGuides(lectern, player, consumedFromNetwork + consumedFromPlayer);
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.chain_changed"), true);
            return false;
        }

        List<GuideDestination> written = new ArrayList<>();
        try {
            for (int index = 0; index < recipes.size(); index++) {
                RecipeHolder<?> recipe = recipes.get(index);
                ItemStack encoded = new ItemStack(ModItems.CRAFTING_GUIDE.get());
                ItemStack result = RecipeAutomationSupport.result(
                        recipe.value(), player.level().registryAccess());
                CraftingGuideItem.encodeRecipe(
                        encoded, recipe, result, player.level().registryAccess());
                GuideDestination destination = destinations.get(index);
                destination.provider().getGuideHandler().setStackInSlot(destination.slot(), encoded);
                written.add(destination);
            }
        } catch (RuntimeException exception) {
            written.forEach(destination -> destination.provider().getGuideHandler()
                    .setStackInSlot(destination.slot(), ItemStack.EMPTY));
            refundBlankGuides(lectern, player, recipes.size());
            return false;
        }
        refreshStoredEntries(lectern);
        player.getInventory().setChanged();
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.crafting_guide.chain_recorded", recipes.size()), true);
        return true;
    }

    private static boolean isBlankGuide(ItemStack stack) {
        return stack.is(ModItems.CRAFTING_GUIDE.get()) && CraftingGuideItem.getRecipeId(stack) == null;
    }

    private static int countBlankGuides(Player player) {
        int count = 0;
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (isBlankGuide(stack)) count += stack.getCount();
        }
        return count;
    }

    private static int consumeBlankGuides(Player player, int requested) {
        if (requested <= 0) return 0;
        int remaining = requested;
        for (int slot = 0; slot < player.getInventory().getContainerSize() && remaining > 0; slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!isBlankGuide(stack)) continue;
            int consumed = Math.min(remaining, stack.getCount());
            stack.shrink(consumed);
            remaining -= consumed;
        }
        return requested - remaining;
    }

    private static void refundBlankGuides(
            AdvancedStorageLecternBlockEntity lectern, Player player, int count
    ) {
        if (count <= 0) return;
        ItemStack refund = new ItemStack(ModItems.CRAFTING_GUIDE.get(), count);
        ItemStack remainder = lectern.insertStored(refund);
        if (!remainder.isEmpty() && !player.getInventory().add(remainder)) player.drop(remainder, false);
    }

    private record GuideDestination(WixiePatternProviderBlockEntity provider, int slot) {}

    public static boolean hasBlankGuide(Player player) {
        return findBlankGuideSlot(player) >= 0;
    }

    private static int findBlankGuideSlot(Player player) {
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (stack.is(ModItems.CRAFTING_GUIDE.get()) && CraftingGuideItem.getRecipeId(stack) == null) {
                return slot;
            }
        }
        return -1;
    }

    public List<ItemStack> getCraftableOutputs() {
        return craftableOutputs;
    }

    public CraftableRecipeInfo getCraftableRecipeInfo(int index) {
        return index < 0 || index >= craftableRecipeInfos.size()
                ? null : craftableRecipeInfos.get(index);
    }

    public CraftableRecipeInfo getSelectedRecipeInfo() {
        return getCraftableRecipeInfo(selectedIndex);
    }

    public int getSelectedIndex() {
        return selectedIndex;
    }

    public int getRequestedCount() {
        return requestedCount;
    }

    public long getNetworkSource() {
        return Integer.toUnsignedLong(sourceData.get(0)) | (long) sourceData.get(1) << 32;
    }

    public long getNetworkSourceCapacity() {
        return Integer.toUnsignedLong(sourceData.get(2)) | (long) sourceData.get(3) << 32;
    }

    public int getNetworkSourceJars() { return sourceData.get(4); }
    public int getNetworkSourceRelays() { return sourceData.get(5); }

    public int getPatternProviderCount() {
        return Math.max(0, sourceData.get(6 + MAX_LINKED_FLUID_TYPES * 2));
    }

    public int getUsedPatternSlots() {
        return Math.max(0, sourceData.get(7 + MAX_LINKED_FLUID_TYPES * 2));
    }

    public int getPatternCapacity() {
        return Math.max(0, sourceData.get(8 + MAX_LINKED_FLUID_TYPES * 2));
    }

    public int getStoredBlankGuideCount() {
        return Math.max(0, sourceData.get(9 + MAX_LINKED_FLUID_TYPES * 2));
    }

    public int getLinkedFluidType(int tank) {
        return tank < 0 || tank >= MAX_LINKED_FLUID_TYPES ? -1 : sourceData.get(6 + tank * 2);
    }

    public int getLinkedFluidAmount(int tank) {
        return tank < 0 || tank >= MAX_LINKED_FLUID_TYPES ? 0 : Math.max(0, sourceData.get(7 + tank * 2));
    }

    public boolean isAdvancedStorage() {
        return advancedStorage;
    }

    /** True while JEI should treat this menu as a normal 3x3 crafting table. */
    public boolean isStorageCraftingActive() {
        return advancedStorage && storagePageActive && storageCraftingActive;
    }

    public List<StorageEntry> getStoredEntries() {
        return List.copyOf(storedEntries);
    }

    public List<ItemStack> getMissingItems() {
        return diagnostics.containerId() < 0
                ? terminal == null ? List.of() : terminal.getMissingItems()
                : diagnostics.missingItems();
    }

    public OrderDiagnosticsPayload getDiagnostics() {
        return diagnostics;
    }

    public void applyDiagnostics(OrderDiagnosticsPayload update) {
        diagnostics = update;
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (container != craftSlots || menuPlayer.level().isClientSide) return;
        savePersistentCraftingGrid();
        var input = craftSlots.asCraftInput();
        var recipe = menuPlayer.level().getRecipeManager()
                .getRecipeFor(RecipeType.CRAFTING, input, menuPlayer.level()).orElse(null);
        ItemStack output = recipe == null
                ? ItemStack.EMPTY
                : recipe.value().assemble(input, menuPlayer.level().registryAccess());
        resultSlots.setRecipeUsed(recipe);
        resultSlots.setItem(0, output);
        broadcastChanges();
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide && advancedStorage) {
            savePersistentCraftingGrid();
        } else if (!player.level().isClientSide) {
            for (int slot = 0; slot < craftSlots.getContainerSize(); slot++) {
                ItemStack stack = craftSlots.removeItemNoUpdate(slot);
                if (!stack.isEmpty()) net.neoforged.neoforge.items.ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
        resultSlots.clearContent();
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (index == 0) {
            if (!moveItemStackTo(source, 10, 46, true)) return ItemStack.EMPTY;
            slot.onQuickCraft(source, copy);
        } else if (index < 10) {
            if (!moveItemStackTo(source, 10, 46, false)) return ItemStack.EMPTY;
        } else if (advancedStorage && storagePageActive) {
            if (player.level().isClientSide) {
                mergeStoredPreview(copy, copy.getCount());
                source.setCount(0);
            } else if (targetLevel(player) != null && targetLevel(player).getBlockEntity(terminalPos)
                    instanceof AdvancedStorageLecternBlockEntity lectern) {
                ItemStack remainder = lectern.insertStored(source);
                int accepted = source.getCount() - remainder.getCount();
                source.setCount(remainder.getCount());
                if (accepted <= 0) return ItemStack.EMPTY;
                refreshStoredEntries(lectern);
            } else {
                return ItemStack.EMPTY;
            }
        } else if (advancedStorage && storageCraftingActive) {
            if (!moveItemStackTo(source, 1, 10, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        if (source.getCount() == copy.getCount()) return ItemStack.EMPTY;
        slot.onTake(player, source);
        return copy;
    }

    @Override
    public boolean stillValid(Player player) {
        Level targetLevel = targetLevel(player);
        // A cross-dimensional client has no ClientLevel for the remote dimension;
        // the server performs the authoritative checks.
        if (targetLevel == null) return player.level().isClientSide;
        if (!targetLevel.hasChunkAt(terminalPos)) return false;
        boolean terminalPresent = targetLevel.getBlockState(terminalPos)
                .is(ModBlocks.WIXIE_ORDER_TERMINAL.get());
        boolean lecternPresent = targetLevel.getBlockState(terminalPos)
                .is(ModBlocks.ADVANCED_STORAGE_LECTERN.get());
        if (!terminalPresent && !lecternPresent) return false;
        // Container opening is server-authoritative. Advanced storage deliberately
        // remains usable when a compatible addon opens it remotely.
        return targetLevel != player.level() || advancedStorage
                || RemoteMenuAccess.isWithinUseRange(player, terminalPos);
    }

    private Level targetLevel(Player player) {
        if (player.level().dimension().equals(terminalDimension)) return player.level();
        return player instanceof ServerPlayer serverPlayer
                ? serverPlayer.getServer().getLevel(terminalDimension) : null;
    }

    public record StorageEntry(ItemStack stack, int count) {
    }

    private record OpeningData(
            BlockPos pos, ResourceKey<Level> dimension, List<CraftableRecipeInfo> recipes,
            List<StorageEntry> storage, boolean advanced
    ) {
    }
}
