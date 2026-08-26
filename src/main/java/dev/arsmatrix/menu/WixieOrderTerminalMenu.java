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
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
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
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.ArrayList;
import java.util.List;

/** A slotless request menu; all displayed stacks are recipe previews, never inventory contents. */
public final class WixieOrderTerminalMenu extends AbstractContainerMenu {

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
    public static final int BUTTON_SELECT_OFFSET = 1000;
    public static final int BUTTON_STORAGE_ONE_OFFSET = 100000;
    public static final int BUTTON_STORAGE_STACK_OFFSET = 200000;
    public static final int BUTTON_SET_COUNT_FLAG = 0x20000000;
    private static final int BUTTON_SET_COUNT_MASK = 0x1FFFFFFF;
    private static final int BUTTON_ENCODE_FLAG = 0x40000000;
    private static final int BUTTON_RECIPE_HASH_MASK = 0x3FFFFFFF;

    private final WixieOrderTerminalBlockEntity terminal;
    private final BlockPos terminalPos;
    private final List<ItemStack> craftableOutputs;
    private final List<CraftableRecipeInfo> craftableRecipeInfos;
    private final List<StorageEntry> storedEntries;
    private final boolean advancedStorage;
    private final Player menuPlayer;
    private final ContainerData sourceData;
    private final CraftingContainer craftSlots = new TransientCraftingContainer(this, 3, 3);
    private final ResultContainer resultSlots = new ResultContainer();
    private boolean storageCraftingActive;
    private boolean storagePageActive;
    private int selectedIndex = -1;
    private int requestedCount = 1;

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
        var foundBlockEntity = inventory.player.level().getBlockEntity(opening.pos());
        terminal = foundBlockEntity instanceof WixieOrderTerminalBlockEntity found
                ? found
                : foundBlockEntity instanceof AdvancedStorageLecternBlockEntity lectern
                ? lectern.getOrderEngine() : null;
        craftableRecipeInfos = new ArrayList<>(opening.recipes());
        craftableOutputs = craftableRecipeInfos.stream().map(CraftableRecipeInfo::output).toList();
        storedEntries = new ArrayList<>(opening.storage());
        advancedStorage = opening.advanced();
        menuPlayer = inventory.player;
        sourceData = new SimpleContainerData(12);
        storageCraftingActive = advancedStorage;
        storagePageActive = advancedStorage;
        addCraftingAndInventorySlots(inventory);
    }

    public WixieOrderTerminalMenu(
            int containerId,
            Inventory inventory,
            WixieOrderTerminalBlockEntity terminal
    ) {
        super(ModMenus.WIXIE_ORDER_TERMINAL.get(), containerId);
        this.terminal = terminal;
        terminalPos = terminal.getBlockPos().immutable();
        craftableRecipeInfos = new ArrayList<>(terminal.getCraftableRecipeInfos());
        craftableOutputs = craftableRecipeInfos.stream().map(CraftableRecipeInfo::output).toList();
        storedEntries = new ArrayList<>();
        advancedStorage = false;
        menuPlayer = inventory.player;
        sourceData = new SimpleContainerData(12);
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
        terminalPos = terminal.getBlockPos().immutable();
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
            @Override public int get(int index) {
                long stored = lectern.getNetworkSource();
                long capacity = lectern.getNetworkCapacity();
                return switch (index) {
                    case 0 -> (int) stored;
                    case 1 -> (int) (stored >>> 32);
                    case 2 -> (int) capacity;
                    case 3 -> (int) (capacity >>> 32);
                    case 4 -> lectern.getLinkedSourceJarCount();
                    case 5 -> lectern.getLinkedSourceRelayCount();
                    case 6, 8, 10 -> lectern.getLinkedFluidType((index - 6) / 2);
                    case 7, 9, 11 -> lectern.getLinkedFluidAmount((index - 7) / 2);
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 12; }
        };
    }

    private static List<CraftableRecipeInfo> readRecipes(RegistryFriendlyByteBuf data) {
        int size = Math.min(256, data.readVarInt());
        List<CraftableRecipeInfo> result = new ArrayList<>(size);
        for (int index = 0; index < size; index++) {
            result.add(new CraftableRecipeInfo(
                    ItemStack.STREAM_CODEC.decode(data),
                    data.readResourceLocation(), data.readBoolean(), data.readBoolean()));
        }
        return result;
    }

    private static OpeningData readOpeningData(RegistryFriendlyByteBuf data) {
        BlockPos pos = data.readBlockPos();
        List<CraftableRecipeInfo> recipes = readRecipes(data);
        boolean advanced = data.readBoolean();
        List<StorageEntry> storage = new ArrayList<>();
        if (advanced) {
            int size = Math.min(512, data.readVarInt());
            for (int index = 0; index < size; index++) {
                storage.add(new StorageEntry(ItemStack.STREAM_CODEC.decode(data), data.readVarInt()));
            }
        }
        return new OpeningData(pos, recipes, storage, advanced);
    }

    public static void writeOpeningData(
            RegistryFriendlyByteBuf data,
            BlockPos pos,
            List<CraftableRecipeInfo> recipes
    ) {
        data.writeBlockPos(pos);
        writeRecipes(data, recipes);
        data.writeBoolean(false);
    }

    public static void writeOpeningData(
            RegistryFriendlyByteBuf data,
            BlockPos pos,
            List<CraftableRecipeInfo> recipes,
            List<AdvancedStorageLecternBlockEntity.StoredStack> storage
    ) {
        data.writeBlockPos(pos);
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
            data.writeBoolean(info.cooking());
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
        if (id >= BUTTON_STORAGE_STACK_OFFSET) {
            return extractStorage(player, id - BUTTON_STORAGE_STACK_OFFSET, true);
        }
        if (id >= BUTTON_STORAGE_ONE_OFFSET) {
            return extractStorage(player, id - BUTTON_STORAGE_ONE_OFFSET, false);
        }
        if (id >= BUTTON_SELECT_OFFSET) {
            int requestedIndex = id - BUTTON_SELECT_OFFSET;
            if (requestedIndex >= 0 && requestedIndex < craftableOutputs.size()) {
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
                        selected.output(), selected.recipeId(), selected.cooking(), fuzzy));
                if (!player.level().isClientSide && terminal != null) {
                    terminal.setRecipeFuzzy(selected.recipeId(), fuzzy);
                }
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
        if (!(player.level().getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity lectern)) {
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

    private void refreshStoredEntries(AdvancedStorageLecternBlockEntity lectern) {
        storedEntries.clear();
        lectern.getStoredStacks().forEach(entry ->
                storedEntries.add(new StorageEntry(entry.stack().copy(), entry.count())));
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

    private boolean extractStorage(Player player, int index, boolean fullStack) {
        if (!(player.level().getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity lectern)
                || index < 0 || index >= storedEntries.size()) {
            return false;
        }
        StorageEntry entry = storedEntries.get(index);
        int requested = fullStack ? Math.min(entry.stack().getMaxStackSize(), entry.count()) : 1;
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

    public static int recipeEncodingButton(ResourceLocation recipeId) {
        return BUTTON_ENCODE_FLAG | (recipeId.hashCode() & BUTTON_RECIPE_HASH_MASK);
    }

    private boolean encodeGuideFromJei(Player player, int recipeHash) {
        if (terminal == null || player.level().isClientSide) {
            return true;
        }
        RecipeHolder<?> selected = player.level().getRecipeManager().getRecipes().stream()
                .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                .filter(holder -> (holder.id().hashCode() & BUTTON_RECIPE_HASH_MASK) == recipeHash)
                .filter(holder -> !(holder.value() instanceof net.minecraft.world.item.crafting.AbstractCookingRecipe)
                        || advancedStorage)
                .findFirst().orElse(null);
        if (selected == null) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.crafting_guide.jei_recipe_missing"), true);
            return false;
        }
        AdvancedStorageLecternBlockEntity lectern = advancedStorage
                && player.level().getBlockEntity(terminalPos) instanceof AdvancedStorageLecternBlockEntity found
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
        CraftingGuideItem.encodeRecipe(encoded, selected, result);

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

    public int getLinkedFluidType(int tank) {
        return tank < 0 || tank >= 3 ? -1 : sourceData.get(6 + tank * 2);
    }

    public int getLinkedFluidAmount(int tank) {
        return tank < 0 || tank >= 3 ? 0 : Math.max(0, sourceData.get(7 + tank * 2));
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
        return terminal == null ? List.of() : terminal.getMissingItems();
    }

    @Override
    public void slotsChanged(net.minecraft.world.Container container) {
        super.slotsChanged(container);
        if (container != craftSlots || menuPlayer.level().isClientSide) return;
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
        if (!player.level().isClientSide) {
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
        } else if (index >= 1 && index < 10) {
            if (!moveItemStackTo(source, 10, 46, false)) return ItemStack.EMPTY;
        } else if (advancedStorage && storagePageActive) {
            if (player.level().isClientSide) {
                mergeStoredPreview(copy, copy.getCount());
                source.setCount(0);
            } else if (player.level().getBlockEntity(terminalPos)
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
        return (player.level().getBlockState(terminalPos).is(ModBlocks.WIXIE_ORDER_TERMINAL.get())
                || player.level().getBlockState(terminalPos).is(ModBlocks.ADVANCED_STORAGE_LECTERN.get()))
                && player.distanceToSqr(
                        terminalPos.getX() + 0.5D,
                        terminalPos.getY() + 0.5D,
                        terminalPos.getZ() + 0.5D
                ) <= 64.0D;
    }

    public record StorageEntry(ItemStack stack, int count) {
    }

    private record OpeningData(
            BlockPos pos, List<CraftableRecipeInfo> recipes,
            List<StorageEntry> storage, boolean advanced
    ) {
    }
}
