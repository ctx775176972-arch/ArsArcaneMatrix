package dev.arsmatrix.client;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity.CraftableRecipeInfo;
import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.compat.jecharacters.JustEnoughCharactersCompat;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.network.OrderDiagnosticsPayload;
import dev.arsmatrix.network.StorageExtractionPayload;
import dev.arsmatrix.network.GuideChainEncodePayload;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/** Storage browser and Wixie order catalogue with one compact side control rail. */
public final class WixieOrderTerminalScreen extends AbstractContainerScreen<WixieOrderTerminalMenu> {

    private static final int COLUMNS = 9;
    private static final int ORDER_ROWS = 6;
    private static final int MIN_STORAGE_ROWS = 3;
    private static final int EXPANDED_MAX_ROWS = 6;
    private static final int COLLAPSED_MAX_ROWS = 10;
    private static final int GRID_X = 8;
    private static final int GRID_Y = 54;
    private static final int DEPOSIT_X = 180;
    private static final int DEPOSIT_Y = 146;
    /** Fluid resources occupy -1 through -3; source is the next virtual storage entry. */
    private static final int SOURCE_INDEX = -4;

    private int scrollRow;
    private int storageRows = 6;
    private int expandedStorageRows = 6;
    private boolean storageTab;
    private static final java.util.Map<java.util.UUID, Boolean> rememberedStoragePages = new java.util.HashMap<>();
    private boolean craftingExpanded = true;
    private boolean draggingScrollbar;
    private int storageDragButton = -1;
    private int lastStorageDragIndex = Integer.MIN_VALUE;
    private SortMode sortMode = SortMode.NAME;
    private EditBox search;
    private EditBox countInput;
    private Button storageModeButton;
    private Button craftingModeButton;
    private Button sortButton;
    private Button foldButton;
    private Button returnCraftingButton;
    private Button patternManagementButton;
    private Button matchModeButton;
    private Button diagnosticsButton;
    private Button copyDiagnosticsButton;
    private Button planBackButton;
    private Button planStartButton;
    private Button statusBackButton;
    private Button statusCancelButton;
    private Button statusCopyButton;
    private Button chainBackButton;
    private Button chainCreateButton;
    private DetailPage detailPage = DetailPage.NONE;
    private int detailScroll;
    private ResourceLocation chainRootRecipeId;
    private List<GuideChainRow> guideChainRows = List.of();
    private final Set<ResourceLocation> skippedGuideRecipes = new HashSet<>();
    private final List<Button> orderControls = new ArrayList<>();
    private final List<Button> storageControls = new ArrayList<>();

    private static java.lang.ref.WeakReference<WixieOrderTerminalScreen> lastScreen =
            new java.lang.ref.WeakReference<>(null);

    public static WixieOrderTerminalScreen forMenu(WixieOrderTerminalMenu menu) {
        WixieOrderTerminalScreen screen = lastScreen.get();
        return screen != null && screen.getMenu() == menu ? screen : null;
    }

    public WixieOrderTerminalScreen(WixieOrderTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        lastScreen = new java.lang.ref.WeakReference<>(this);
        imageWidth = 205;
        imageHeight = 322;
        storageTab = menu.isAdvancedStorage();
        var player = Minecraft.getInstance().player;
        if (menu.isAdvancedStorage() && player != null) {
            storageTab = rememberedStoragePages.getOrDefault(player.getUUID(), true);
        }
    }

    @Override
    protected void init() {
        super.init();
        if (menu.isAdvancedStorage()) sendMenuButton(storageTab
                ? WixieOrderTerminalMenu.BUTTON_SHOW_STORAGE : WixieOrderTerminalMenu.BUTTON_SHOW_ORDERS);
        search = new EditBox(font, leftPos + 8, topPos + 29, 162, 18,
                Component.translatable("screen.ars_arcane_matrix.order_terminal.search"));
        search.setHint(Component.translatable("screen.ars_arcane_matrix.order_terminal.search"));
        search.setResponder(value -> scrollRow = 0);
        addRenderableWidget(search);

        if (menu.isAdvancedStorage()) {
            storageModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> setMode(true))
                    .bounds(leftPos + 177, topPos + 7, 24, 20).build());
            craftingModeButton = addRenderableWidget(Button.builder(Component.empty(), button -> setMode(false))
                    .bounds(leftPos + 177, topPos + 29, 24, 20).build());
        }

        sortButton = addStorageControl(Button.builder(Component.literal(sortMode.label), button -> {
                    sortMode = sortMode.next();
                    sortButton.setMessage(Component.literal(sortMode.label));
                    scrollRow = 0;
                }).bounds(leftPos + 177, topPos + 54, 24, 14).build());
        addStorageControl(Button.builder(Component.literal("▲"), button -> changeRows(1))
                .bounds(leftPos + 177, topPos + 72, 24, 14).build());
        addStorageControl(Button.builder(Component.literal("▼"), button -> changeRows(-1))
                .bounds(leftPos + 177, topPos + 90, 24, 14).build());
        foldButton = addStorageControl(Button.builder(Component.literal("▦"), button -> toggleCraftingGrid())
                .bounds(leftPos + 177, topPos + 108, 24, 14).build());
        returnCraftingButton = addStorageControl(Button.builder(Component.literal("↩"), button ->
                        sendMenuButton(WixieOrderTerminalMenu.BUTTON_RETURN_CRAFTING_GRID))
                .bounds(leftPos + 86, topPos + 173, 18, 14).build());
        if (menu.isAdvancedStorage()) {
            patternManagementButton = addOrderControl(Button.builder(Component.literal("G"), button ->
                            sendMenuButton(WixieOrderTerminalMenu.BUTTON_MANAGE_PATTERNS))
                    .bounds(leftPos + 177, topPos + 180, 24, 14).build());
        }
        diagnosticsButton = addOrderControl(Button.builder(Component.literal("i"), button -> openStatusPage())
                .bounds(leftPos + 177, topPos + 198, 24, 14).build());
        copyDiagnosticsButton = addOrderControl(Button.builder(Component.literal("C"), button -> copyDiagnostics())
                .bounds(leftPos + 177, topPos + 216, 24, 14).build());

        countInput = new EditBox(font, leftPos + 111, topPos + 204, 60, 18,
                Component.translatable("screen.ars_arcane_matrix.order_terminal.count"));
        countInput.setValue(Integer.toString(menu.getRequestedCount()));
        countInput.setFilter(value -> value.isEmpty()
                || value.length() <= 4 && value.chars().allMatch(Character::isDigit));
        addRenderableWidget(countInput);
        matchModeButton = addOrderControl(Button.builder(Component.literal("—"), button -> {
                    sendMenuButton(WixieOrderTerminalMenu.BUTTON_TOGGLE_MATCH_MODE);
                    updateMatchModeButton();
                }).bounds(leftPos + 177, topPos + 54, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("-16"), button -> adjustCount(WixieOrderTerminalMenu.BUTTON_MINUS_SIXTEEN))
                .bounds(leftPos + 177, topPos + 72, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("-1"), button -> adjustCount(WixieOrderTerminalMenu.BUTTON_MINUS_ONE))
                .bounds(leftPos + 177, topPos + 90, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("+1"), button -> adjustCount(WixieOrderTerminalMenu.BUTTON_PLUS_ONE))
                .bounds(leftPos + 177, topPos + 108, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("+16"), button -> adjustCount(WixieOrderTerminalMenu.BUTTON_PLUS_SIXTEEN))
                .bounds(leftPos + 177, topPos + 126, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("✓"),
                        button -> submitOrder()).bounds(leftPos + 177, topPos + 144, 24, 14).build());
        addOrderControl(Button.builder(Component.literal("×"),
                        button -> sendMenuButton(WixieOrderTerminalMenu.BUTTON_CANCEL))
                .bounds(leftPos + 177, topPos + 162, 24, 14).build());
        planBackButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.back"), button -> closeDetailPage())
                .bounds(leftPos + 8, topPos + 292, 54, 18).build());
        planStartButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.start"), button -> startPlannedOrder())
                .bounds(leftPos + 143, topPos + 292, 54, 18).build());
        statusBackButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.back"), button -> closeDetailPage())
                .bounds(leftPos + 8, topPos + 292, 54, 18).build());
        statusCancelButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.status.cancel"), button -> {
                    sendMenuButton(WixieOrderTerminalMenu.BUTTON_CANCEL);
                    closeDetailPage();
                }).bounds(leftPos + 75, topPos + 292, 54, 18).build());
        statusCopyButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.status.copy"), button -> copyDiagnostics())
                .bounds(leftPos + 143, topPos + 292, 54, 18).build());
        chainBackButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.back"), button -> closeDetailPage())
                .bounds(leftPos + 8, topPos + 292, 54, 18).build());
        chainCreateButton = addRenderableWidget(Button.builder(Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.guide_chain.create"), button -> createGuideChain())
                .bounds(leftPos + 125, topPos + 292, 72, 18).build());
        updateControlVisibility();
    }

    private void setMode(boolean showStorage) {
        if (storageTab == showStorage) return;
        storageTab = showStorage;
        if (menu.isAdvancedStorage() && minecraft != null && minecraft.player != null) {
            rememberedStoragePages.put(minecraft.player.getUUID(), storageTab);
        }
        scrollRow = 0;
        sendMenuButton(storageTab
                ? WixieOrderTerminalMenu.BUTTON_SHOW_STORAGE
                : WixieOrderTerminalMenu.BUTTON_SHOW_ORDERS);
        updateControlVisibility();
    }

    private Button addOrderControl(Button button) {
        orderControls.add(button);
        return addRenderableWidget(button);
    }

    private Button addStorageControl(Button button) {
        storageControls.add(button);
        return addRenderableWidget(button);
    }

    private void updateControlVisibility() {
        boolean catalogue = detailPage == DetailPage.NONE;
        orderControls.forEach(button -> button.visible = catalogue && !storageTab);
        storageControls.forEach(button -> button.visible = catalogue && storageTab);
        if (returnCraftingButton != null) {
            returnCraftingButton.visible = catalogue && storageTab && craftingExpanded;
        }
        if (countInput != null) countInput.setVisible(catalogue && !storageTab);
        if (search != null) search.setVisible(catalogue);
        if (storageModeButton != null) storageModeButton.visible = catalogue;
        if (craftingModeButton != null) craftingModeButton.visible = catalogue;
        if (planBackButton != null) planBackButton.visible = detailPage == DetailPage.PLAN;
        if (planStartButton != null) planStartButton.visible = detailPage == DetailPage.PLAN;
        if (statusBackButton != null) statusBackButton.visible = detailPage == DetailPage.STATUS;
        if (statusCancelButton != null) statusCancelButton.visible = detailPage == DetailPage.STATUS;
        if (statusCopyButton != null) statusCopyButton.visible = detailPage == DetailPage.STATUS;
        if (chainBackButton != null) chainBackButton.visible = detailPage == DetailPage.GUIDE_CHAIN;
        if (chainCreateButton != null) chainCreateButton.visible = detailPage == DetailPage.GUIDE_CHAIN;
        if (storageModeButton != null) storageModeButton.active = !storageTab;
        if (craftingModeButton != null) craftingModeButton.active = storageTab;
        updateMatchModeButton();
    }

    private void updateMatchModeButton() {
        if (matchModeButton == null) return;
        CraftableRecipeInfo info = menu.getSelectedRecipeInfo();
        matchModeButton.active = !storageTab && info != null;
        matchModeButton.setMessage(info == null ? Component.literal("—")
                : Component.translatable(info.fuzzy()
                        ? "screen.ars_arcane_matrix.order_terminal.mode.fuzzy.short"
                        : "screen.ars_arcane_matrix.order_terminal.mode.strict.short"));
    }

    private void toggleCraftingGrid() {
        if (craftingExpanded) {
            expandedStorageRows = storageRows;
            craftingExpanded = false;
            storageRows = COLLAPSED_MAX_ROWS;
        } else {
            craftingExpanded = true;
            storageRows = Math.min(expandedStorageRows, EXPANDED_MAX_ROWS);
        }
        foldButton.setMessage(Component.literal(craftingExpanded ? "▦" : "□"));
        if (returnCraftingButton != null) returnCraftingButton.visible = storageTab && craftingExpanded;
        clampScroll();
        sendMenuButton(WixieOrderTerminalMenu.BUTTON_TOGGLE_CRAFTING_GRID);
    }

    private void changeRows(int change) {
        storageRows = Math.max(MIN_STORAGE_ROWS, Math.min(maxStorageRows(), storageRows + change));
        if (craftingExpanded) expandedStorageRows = storageRows;
        clampScroll();
    }

    private int maxStorageRows() {
        return craftingExpanded ? EXPANDED_MAX_ROWS : COLLAPSED_MAX_ROWS;
    }

    private void adjustCount(int buttonId) {
        applyTypedCount();
        sendMenuButton(buttonId);
        countInput.setValue(Integer.toString(menu.getRequestedCount()));
    }

    private void submitOrder() {
        applyTypedCount();
        countInput.setValue(Integer.toString(menu.getRequestedCount()));
        if (menu.getSelectedRecipeInfo() == null) return;
        detailPage = DetailPage.PLAN;
        detailScroll = 0;
        updateControlVisibility();
    }

    private void startPlannedOrder() {
        sendMenuButton(WixieOrderTerminalMenu.BUTTON_SUBMIT);
        detailPage = DetailPage.STATUS;
        detailScroll = 0;
        updateControlVisibility();
    }

    private void openStatusPage() {
        detailPage = DetailPage.STATUS;
        detailScroll = 0;
        updateControlVisibility();
    }

    private void closeDetailPage() {
        detailPage = DetailPage.NONE;
        detailScroll = 0;
        chainRootRecipeId = null;
        guideChainRows = List.of();
        updateControlVisibility();
    }

    /** Called by JEI when its transfer button is Shift-clicked on an automation recipe. */
    public boolean openGuideChainPreview(ResourceLocation rootRecipeId) {
        if (!menu.isAdvancedStorage() || minecraft == null || minecraft.level == null) return false;
        chainRootRecipeId = rootRecipeId;
        guideChainRows = buildGuideChain(rootRecipeId);
        skippedGuideRecipes.clear();
        detailPage = DetailPage.GUIDE_CHAIN;
        detailScroll = 0;
        updateControlVisibility();
        return true;
    }

    /** Previews exactly the selected EMI routes, without choosing alternate child recipes. */
    public boolean openSelectedGuideChainPreview(List<ResourceLocation> recipeIds) {
        if (!menu.isAdvancedStorage() || minecraft == null || minecraft.level == null
                || recipeIds.isEmpty()) return false;
        Set<ResourceLocation> existing = new HashSet<>();
        for (int i = 0; i < menu.getCraftableOutputs().size(); i++) {
            CraftableRecipeInfo info = menu.getCraftableRecipeInfo(i);
            if (info != null) existing.add(info.recipeId());
        }
        List<GuideChainRow> rows = new ArrayList<>();
        for (ResourceLocation id : recipeIds) {
            RecipeHolder<?> holder = RecipeAutomationSupport.find(minecraft.level.getRecipeManager(), id).orElse(null);
            if (holder == null || !RecipeAutomationSupport.supports(holder.value())) continue;
            ItemStack output = RecipeAutomationSupport.result(holder.value(), minecraft.level.registryAccess());
            if (output.isEmpty()) continue;
            rows.add(new GuideChainRow(output.copyWithCount(1), id,
                    RecipeAutomationSupport.workstation(holder.value()), 0,
                    existing.contains(id) ? GuideChainStatus.EXISTING : GuideChainStatus.NEW));
        }
        if (rows.isEmpty()) return false;
        chainRootRecipeId = recipeIds.getFirst();
        guideChainRows = List.copyOf(rows);
        skippedGuideRecipes.clear();
        detailPage = DetailPage.GUIDE_CHAIN;
        detailScroll = 0;
        updateControlVisibility();
        return true;
    }

    private void createGuideChain() {
        List<ResourceLocation> recipeIds = guideChainRows.stream()
                .filter(row -> row.status == GuideChainStatus.NEW && row.recipeId != null
                        && !skippedGuideRecipes.contains(row.recipeId))
                .map(row -> row.recipeId).distinct().limit(GuideChainEncodePayload.MAX_RECIPES).toList();
        if (recipeIds.isEmpty()) return;
        PacketDistributor.sendToServer(new GuideChainEncodePayload(menu.containerId, recipeIds));
        closeDetailPage();
    }

    private void applyTypedCount() {
        int value;
        try { value = Integer.parseInt(countInput.getValue()); }
        catch (NumberFormatException ignored) { value = 1; }
        sendMenuButton(WixieOrderTerminalMenu.BUTTON_SET_COUNT_FLAG | Math.max(1, Math.min(9999, value)));
    }

    private void sendMenuButton(int id) {
        if (minecraft == null || minecraft.player == null || minecraft.gameMode == null) return;
        menu.clickMenuButton(minecraft.player, id);
        minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }

    private List<Integer> filteredIndices() {
        String query = search == null ? "" : search.getValue().strip().toLowerCase(Locale.ROOT);
        List<Integer> result = new ArrayList<>();
        if (storageTab) {
            List<WixieOrderTerminalMenu.StorageEntry> entries = menu.getStoredEntries();
            for (int index = 0; index < entries.size(); index++) {
                if (entries.get(index).count() > 0 && matches(entries.get(index).stack(), query)) result.add(index);
            }
            for (int tank = 0; tank < WixieOrderTerminalMenu.MAX_LINKED_FLUID_TYPES; tank++) {
                int fluidType = menu.getLinkedFluidType(tank);
                int amount = menu.getLinkedFluidAmount(tank);
                if (fluidType >= 0 && amount > 0 && matchesFluid(fluidType, query)) result.add(-tank - 1);
            }
            if (menu.getNetworkSourceCapacity() > 0L && matchesSource(query)) result.add(SOURCE_INDEX);
            Comparator<Integer> byName = Comparator.comparing(this::storageName);
            Comparator<Integer> comparator = switch (sortMode) {
                case NAME -> byName;
                case COUNT -> Comparator.<Integer>comparingLong(this::storageAmount)
                        .reversed().thenComparing(byName);
                case MOD -> Comparator.<Integer, String>comparing(this::storageNamespace)
                        .thenComparing(byName);
            };
            result.sort(comparator);
        } else {
            List<ItemStack> outputs = menu.getCraftableOutputs();
            for (int index = 0; index < outputs.size(); index++) {
                if (matchesRecipe(index, query)) result.add(index);
            }
        }
        return result;
    }

    private boolean matchesRecipe(int index, String query) {
        ItemStack output = menu.getCraftableOutputs().get(index);
        if (matches(output, query) || query.isEmpty()) return true;

        CraftableRecipeInfo info = menu.getCraftableRecipeInfo(index);
        if (info == null) return false;
        String workstationName = Component.translatable(workstationTranslation(info.workstation()))
                .getString().toLowerCase(Locale.ROOT);
        return JustEnoughCharactersCompat.contains(workstationName, query)
                || info.workstation().toString().toLowerCase(Locale.ROOT).contains(query)
                || info.recipeId().toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private static boolean matches(ItemStack stack, String query) {
        return query.isEmpty()
                || JustEnoughCharactersCompat.contains(stack.getHoverName().getString(), query)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesFluid(int registryId, String query) {
        Fluid fluid = BuiltInRegistries.FLUID.byId(registryId);
        if (fluid == Fluids.EMPTY) return false;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        String name = new FluidStack(fluid, 1).getHoverName().getString().toLowerCase(Locale.ROOT);
        return query.isEmpty() || JustEnoughCharactersCompat.contains(name, query)
                || id.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesSource(String query) {
        String name = Component.translatable(
                "screen.ars_arcane_matrix.advanced_storage_lectern.source_resource")
                .getString().toLowerCase(Locale.ROOT);
        return query.isEmpty() || JustEnoughCharactersCompat.contains(name, query)
                || "ars_nouveau:source".contains(query)
                || "ars_arcane_matrix:source_network".contains(query);
    }

    private String storageName(int index) {
        if (index >= 0) return menu.getStoredEntries().get(index).stack().getHoverName()
                .getString().toLowerCase(Locale.ROOT);
        if (index == SOURCE_INDEX) return Component.translatable(
                "screen.ars_arcane_matrix.advanced_storage_lectern.source_resource")
                .getString().toLowerCase(Locale.ROOT);
        Fluid fluid = fluidForIndex(index);
        return fluid == Fluids.EMPTY ? "" : new FluidStack(fluid, 1).getHoverName()
                .getString().toLowerCase(Locale.ROOT);
    }

    private long storageAmount(int index) {
        if (index >= 0) return menu.getStoredEntries().get(index).count();
        if (index == SOURCE_INDEX) return menu.getNetworkSource();
        return menu.getLinkedFluidAmount(-index - 1);
    }

    private String storageNamespace(int index) {
        return index >= 0
                ? BuiltInRegistries.ITEM.getKey(menu.getStoredEntries().get(index).stack().getItem()).getNamespace()
                : index == SOURCE_INDEX ? "ars_nouveau"
                : BuiltInRegistries.FLUID.getKey(fluidForIndex(index)).getNamespace();
    }

    private Fluid fluidForIndex(int index) {
        if (index >= 0 || index == SOURCE_INDEX) return Fluids.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(menu.getLinkedFluidType(-index - 1));
        return fluid;
    }

    private int rows() { return storageTab ? storageRows : ORDER_ROWS; }
    private int visibleCount() { return COLUMNS * rows(); }
    private int totalRows() { return (filteredIndices().size() + COLUMNS - 1) / COLUMNS; }
    private int maxScrollRow() { return Math.max(0, totalRows() - rows()); }
    private void clampScroll() { scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow)); }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        if (detailPage != DetailPage.NONE) {
            graphics.fill(leftPos + 7, topPos + 27, leftPos + 198, topPos + 284, 0xE0282040);
            graphics.fill(leftPos + 7, topPos + 27, leftPos + 198, topPos + 29, 0xFFD0A8FF);
            return;
        }
        int gridBottom = topPos + 52 + rows() * 18 + 4;
        graphics.fill(leftPos + 7, topPos + 50, leftPos + 171, gridBottom, 0xE0282040);
        for (int row = 0; row < rows(); row++) for (int column = 0; column < COLUMNS; column++) {
            drawItemSlot(graphics, leftPos + 7 + column * 18, topPos + 53 + row * 18);
        }
        drawScrollbar(graphics);
        if (storageTab) drawItemSlot(graphics, leftPos + DEPOSIT_X - 1, topPos + DEPOSIT_Y - 1);
        if (storageTab && craftingExpanded) drawCraftingPanel(graphics);
        drawPlayerInventoryBackground(graphics);
    }

    private void drawScrollbar(GuiGraphics graphics) {
        int x = leftPos + 173;
        int y = topPos + GRID_Y;
        int height = rows() * 18;
        graphics.fill(x, y, x + 5, y + height, 0xFF080610);
        int max = maxScrollRow();
        int total = Math.max(rows(), totalRows());
        int thumbHeight = Math.max(12, height * rows() / total);
        int travel = height - thumbHeight;
        int thumbY = y + (max == 0 ? 0 : travel * scrollRow / max);
        graphics.fill(x + 1, thumbY, x + 4, thumbY + thumbHeight, 0xFFD0A8FF);
    }

    private void drawCraftingPanel(GuiGraphics graphics) {
        int panelLeft = leftPos + 7;
        int panelTop = topPos + 166;
        int panelRight = leftPos + 171;
        int panelBottom = topPos + 234;
        graphics.fill(panelLeft, panelTop, panelRight, panelBottom, 0xE0302448);
        graphics.fill(panelLeft, panelTop, panelRight, panelTop + 2, 0xFFD0A8FF);
        graphics.fill(panelLeft, panelBottom - 2, panelRight, panelBottom, 0xFF705080);
        graphics.fill(panelLeft, panelTop, panelLeft + 2, panelBottom, 0xFFD0A8FF);
        graphics.fill(panelRight - 2, panelTop, panelRight, panelBottom, 0xFF705080);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 3; column++) {
            drawItemSlot(graphics, leftPos + 25 + column * 18, topPos + 173 + row * 18);
        }
        graphics.drawString(font, Component.literal("→"), leftPos + 97, topPos + 196, 0xBBAADD, false);
        drawItemSlot(graphics, leftPos + 127, topPos + 191);
    }

    private void drawPlayerInventoryBackground(GuiGraphics graphics) {
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            drawItemSlot(graphics, leftPos + 7 + column * 18, topPos + 239 + row * 18);
        }
        for (int column = 0; column < 9; column++)
            drawItemSlot(graphics, leftPos + 7 + column * 18, topPos + 297);
    }

    private static void drawItemSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFF100C18);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        if (detailPage != DetailPage.NONE) {
            super.render(graphics, mouseX, mouseY, partialTick);
            if (detailPage == DetailPage.PLAN) renderPlanPage(graphics, mouseX, mouseY);
            else if (detailPage == DetailPage.STATUS) renderStatusPage(graphics, mouseX, mouseY);
            else renderGuideChainPage(graphics, mouseX, mouseY);
            renderDetailButtons(graphics, mouseX, mouseY, partialTick);
            return;
        }
        clampScroll();
        updateMatchModeButton();
        super.render(graphics, mouseX, mouseY, partialTick);
        List<Integer> filtered = filteredIndices();
        int first = scrollRow * COLUMNS;
        int hoveredSourceIndex = Integer.MIN_VALUE;
        ItemStack hoveredMissing = ItemStack.EMPTY;
        for (int local = 0; local < visibleCount() && first + local < filtered.size(); local++) {
            int sourceIndex = filtered.get(first + local);
            int x = leftPos + GRID_X + (local % COLUMNS) * 18;
            int y = topPos + GRID_Y + (local / COLUMNS) * 18;
            ItemStack stack = storageTab && sourceIndex >= 0
                    ? menu.getStoredEntries().get(sourceIndex).stack()
                    : !storageTab ? menu.getCraftableOutputs().get(sourceIndex) : ItemStack.EMPTY;
            if (!storageTab && sourceIndex == menu.getSelectedIndex())
                graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xA060C8FF);
            if (storageTab && sourceIndex == SOURCE_INDEX)
                graphics.renderItem(new ItemStack(ItemsRegistry.SOURCE_GEM.get()), x, y);
            else if (storageTab && sourceIndex < 0) drawFluidCell(graphics, fluidForIndex(sourceIndex), x, y);
            else graphics.renderItem(stack, x, y);
            if (!storageTab) drawRecipeTypeBadge(graphics, menu.getCraftableRecipeInfo(sourceIndex), x, y);
            if (storageTab) {
                String count = sourceIndex == SOURCE_INDEX
                        ? compactCount(menu.getNetworkSource())
                        : sourceIndex < 0
                        ? compactFluidAmount(menu.getLinkedFluidAmount(-sourceIndex - 1))
                        : compactCount(menu.getStoredEntries().get(sourceIndex).count());
                graphics.pose().pushPose();
                graphics.pose().translate(x + 16.5F, y + 11.0F, 300.0F);
                graphics.pose().scale(0.65F, 0.65F, 1.0F);
                graphics.drawString(font, count, -font.width(count), 0, 0xFFFFFF, true);
                graphics.pose().popPose();
            }
            if (inside(mouseX, mouseY, x, y, 18, 18)) hoveredSourceIndex = sourceIndex;
        }
        if (storageTab) {
            graphics.drawString(font, Component.literal("↓"), leftPos + DEPOSIT_X + 5,
                    topPos + DEPOSIT_Y + 4, 0xD8C8EE, false);
            graphics.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.rows", storageRows),
                    leftPos + 177, topPos + 164, 0xD8C8EE, false);
        } else {
            CraftableRecipeInfo selectedInfo = menu.getSelectedRecipeInfo();
            Component selected = menu.getSelectedIndex() >= 0
                    ? Component.translatable("screen.ars_arcane_matrix.order_terminal.selected",
                    menu.getCraftableOutputs().get(menu.getSelectedIndex()).getHoverName(), menu.getRequestedCount())
                    : Component.translatable("screen.ars_arcane_matrix.order_terminal.select_recipe");
            graphics.drawString(font, selected, leftPos + 8, topPos + 166, 0xFFFFFF, false);
            if (selectedInfo != null) {
                graphics.drawString(font, Component.translatable(
                                "screen.ars_arcane_matrix.order_terminal.recipe_details",
                                Component.translatable(workstationTranslation(selectedInfo.workstation())),
                                Component.translatable(selectedInfo.fuzzy()
                                        ? "tooltip.ars_arcane_matrix.crafting_guide.mode.fuzzy"
                                        : "tooltip.ars_arcane_matrix.crafting_guide.mode.strict")),
                        leftPos + 8, topPos + 178, 0xD0A8FF, false);
            }
            List<ItemStack> missing = menu.getMissingItems();
            if (!missing.isEmpty()) {
                graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.order_terminal.missing"),
                        leftPos + 8, topPos + 192, 0xFF7777, false);
                for (int index = 0; index < Math.min(8, missing.size()); index++) {
                    ItemStack stack = missing.get(index);
                    int x = leftPos + 50 + index * 18;
                    graphics.renderItem(stack, x, topPos + 188);
                    if (inside(mouseX, mouseY, x, topPos + 188, 18, 18)) hoveredMissing = stack;
                }
            }
            graphics.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.count"),
                    leftPos + 8, topPos + 209, 0xD8C8EE, false);
            OrderDiagnosticsPayload diagnostics = menu.getDiagnostics();
            Component status = Component.translatable("screen.ars_arcane_matrix.order_terminal.order_progress",
                    diagnostics.produced(), diagnostics.requested(), Component.translatable(diagnostics.stateKey()));
            String clipped = font.plainSubstrByWidth(status.getString(), 163);
            graphics.drawString(font, clipped, leftPos + 8, topPos + 225,
                    diagnostics.missingItems().isEmpty() ? 0xBFA8DD : 0xFF7777, false);
        }
        if (storageModeButton != null && craftingModeButton != null) {
            graphics.renderItem(new ItemStack(Items.CHEST), leftPos + 181, topPos + 9);
            graphics.renderItem(new ItemStack(Items.CRAFTING_TABLE), leftPos + 181, topPos + 31);
            if (storageModeButton.isHovered() && hoveredSourceIndex < 0 && hoveredMissing.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable(
                        "screen.ars_arcane_matrix.advanced_storage_lectern.storage"), mouseX, mouseY);
            } else if (craftingModeButton.isHovered() && hoveredSourceIndex < 0 && hoveredMissing.isEmpty()) {
                graphics.renderTooltip(font, Component.translatable(
                        "screen.ars_arcane_matrix.advanced_storage_lectern.crafting"), mouseX, mouseY);
            }
        }
        if (sortButton != null && sortButton.visible && sortButton.isHovered()
                && hoveredSourceIndex == Integer.MIN_VALUE && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.sort.current",
                    Component.translatable("screen.ars_arcane_matrix.advanced_storage_lectern.sort."
                            + sortMode.name().toLowerCase(Locale.ROOT))), mouseX, mouseY);
        } else if (returnCraftingButton != null && returnCraftingButton.visible
                && returnCraftingButton.isHovered() && hoveredSourceIndex == Integer.MIN_VALUE
                && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.return_crafting"), mouseX, mouseY);
        } else if (patternManagementButton != null && patternManagementButton.visible
                && patternManagementButton.isHovered()
                && hoveredSourceIndex == Integer.MIN_VALUE && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.manage_patterns"), mouseX, mouseY);
        } else if (matchModeButton != null && matchModeButton.visible
                && matchModeButton.isHovered() && hoveredSourceIndex == Integer.MIN_VALUE
                && hoveredMissing.isEmpty()) {
            CraftableRecipeInfo info = menu.getSelectedRecipeInfo();
            graphics.renderTooltip(font, Component.translatable(info == null
                    ? "screen.ars_arcane_matrix.order_terminal.mode.select_first"
                    : "screen.ars_arcane_matrix.order_terminal.mode.toggle",
                    info == null ? Component.empty() : Component.translatable(info.fuzzy()
                            ? "tooltip.ars_arcane_matrix.crafting_guide.mode.fuzzy"
                            : "tooltip.ars_arcane_matrix.crafting_guide.mode.strict")), mouseX, mouseY);
        } else if (diagnosticsButton != null && diagnosticsButton.visible
                && diagnosticsButton.isHovered() && hoveredSourceIndex == Integer.MIN_VALUE
                && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, diagnosticTooltip(), Optional.empty(), mouseX, mouseY);
        } else if (copyDiagnosticsButton != null && copyDiagnosticsButton.visible
                && copyDiagnosticsButton.isHovered() && hoveredSourceIndex == Integer.MIN_VALUE
                && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.diagnostics.copy"), mouseX, mouseY);
        }
        if (storageTab && inside(mouseX, mouseY, leftPos + DEPOSIT_X - 1,
                topPos + DEPOSIT_Y - 1, 18, 18) && hoveredSourceIndex < 0 && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.deposit"), mouseX, mouseY);
        } else if (hoveredSourceIndex == SOURCE_INDEX) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.source_network",
                    menu.getNetworkSource(), menu.getNetworkSourceCapacity(),
                    menu.getNetworkSourceJars(), menu.getNetworkSourceRelays()), mouseX, mouseY);
        } else if (!hoveredMissing.isEmpty()) graphics.renderTooltip(font, hoveredMissing, mouseX, mouseY);
        else if (hoveredSourceIndex >= 0) {
            ItemStack hovered = storageTab ? menu.getStoredEntries().get(hoveredSourceIndex).stack()
                    : menu.getCraftableOutputs().get(hoveredSourceIndex);
            if (storageTab) {
                List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered));
                tooltip.add(Component.translatable(
                        "screen.ars_arcane_matrix.advanced_storage_lectern.extract_hint"));
                graphics.renderTooltip(font, tooltip, Optional.empty(), hovered, mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, hovered, mouseX, mouseY);
            }
        } else if (hoveredSourceIndex != Integer.MIN_VALUE) {
            int tank = -hoveredSourceIndex - 1;
            Fluid fluid = fluidForIndex(hoveredSourceIndex);
            int amount = menu.getLinkedFluidAmount(tank);
            graphics.renderTooltip(font, List.of(
                    new FluidStack(fluid, 1).getHoverName(),
                    Component.translatable("screen.ars_arcane_matrix.advanced_storage_lectern.fluid_amount",
                            amount, formatBuckets(amount))), Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
        }

        // The catalogue uses virtual cells and draws its own tooltips above. Preserve the
        // vanilla tooltip pass as well so items in the real player-inventory slots still
        // show their name, components and mod-provided descriptions in both terminal modes.
        renderTooltip(graphics, mouseX, mouseY);
    }

    private void renderPlanPage(GuiGraphics graphics, int mouseX, int mouseY) {
        redrawDetailPanel(graphics);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.plan.title"), leftPos + 12, topPos + 10, 0xFFFFFF, false);
        CraftableRecipeInfo info = menu.getSelectedRecipeInfo();
        if (info == null || minecraft == null || minecraft.level == null) return;
        ItemStack target = info.output().copyWithCount(1);
        graphics.renderItem(target, leftPos + 15, topPos + 38);
        graphics.drawString(font, Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.target", target.getHoverName(), menu.getRequestedCount()),
                leftPos + 36, topPos + 42, 0xFFFFFF, false);
        graphics.drawString(font, Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.plan.workstation",
                        Component.translatable(workstationTranslation(info.workstation()))),
                leftPos + 36, topPos + 53, 0xBFA8DD, false);

        List<PlanRow> rows = buildPlanRows(info);
        boolean missing = menu.isAdvancedStorage() && rows.stream().anyMatch(row -> row.missing > 0);
        int first = Math.min(detailScroll, Math.max(0, rows.size() - 9));
        PlanRow hovered = null;
        for (int index = first; index < Math.min(rows.size(), first + 9); index++) {
            PlanRow row = rows.get(index);
            int y = topPos + 72 + (index - first) * 21;
            drawItemSlot(graphics, leftPos + 12, y - 2);
            graphics.renderItem(row.stack, leftPos + 13, y - 1);
            String name = font.plainSubstrByWidth(row.displayName().getString(), 78);
            graphics.drawString(font, name, leftPos + 34, y, 0xFFFFFF, false);
            Component counts = row.fluid != Fluids.EMPTY
                    ? row.missing > 0
                    ? Component.translatable("screen.ars_arcane_matrix.order_terminal.plan.fluid_row_missing",
                    row.required, row.available, row.missing)
                    : Component.translatable("screen.ars_arcane_matrix.order_terminal.plan.fluid_row",
                    row.required, row.available)
                    : row.missing > 0
                    ? Component.translatable("screen.ars_arcane_matrix.order_terminal.plan.row_missing",
                    row.required, row.available, row.craftable, row.missing)
                    : Component.translatable("screen.ars_arcane_matrix.order_terminal.plan.row",
                    row.required, row.available, row.craftable);
            drawStringScaledToFit(graphics, counts, leftPos + 34, y + 10, 158,
                    row.missing > 0 ? 0xFF7777 : 0xA8E6B0);
            if (inside(mouseX, mouseY, leftPos + 12, y - 2, 18, 18)) hovered = row;
        }
        if (rows.isEmpty()) graphics.drawCenteredString(font, Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.plan.no_ingredients"), leftPos + imageWidth / 2,
                topPos + 130, 0xBFA8DD);
        planStartButton.active = !missing;
        graphics.drawString(font, Component.translatable(!menu.isAdvancedStorage()
                        ? "screen.ars_arcane_matrix.order_terminal.plan.server_check"
                        : missing
                        ? "screen.ars_arcane_matrix.order_terminal.plan.missing"
                        : "screen.ars_arcane_matrix.order_terminal.plan.ready"),
                leftPos + 12, topPos + 269, missing ? 0xFF7777 : 0xA8E6B0, false);
        if (hovered != null) {
            if (hovered.fluid == Fluids.EMPTY) {
                graphics.renderTooltip(font, hovered.stack, mouseX, mouseY);
            } else {
                graphics.renderTooltip(font, List.of(
                        hovered.displayName(),
                        Component.translatable("screen.ars_arcane_matrix.order_terminal.plan.fluid_required",
                                hovered.required)), Optional.empty(), hovered.stack, mouseX, mouseY);
            }
        }
    }

    private void renderGuideChainPage(GuiGraphics graphics, int mouseX, int mouseY) {
        redrawDetailPanel(graphics);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.guide_chain.title"),
                leftPos + 12, topPos + 10, 0xFFFFFF, false);
        RecipeHolder<?> root = minecraft == null || minecraft.level == null || chainRootRecipeId == null
                ? null : RecipeAutomationSupport.find(
                minecraft.level.getRecipeManager(), chainRootRecipeId).orElse(null);
        if (root != null) {
            ItemStack output = RecipeAutomationSupport.result(root.value(), minecraft.level.registryAccess());
            graphics.renderItem(output, leftPos + 13, topPos + 35);
            drawStringScaledToFit(graphics, Component.translatable(
                            "screen.ars_arcane_matrix.order_terminal.guide_chain.target", output.getHoverName()),
                    leftPos + 35, topPos + 39, 156, 0xFFFFFF);
        }

        int newCount = (int) guideChainRows.stream()
                .filter(row -> row.status == GuideChainStatus.NEW
                        && !skippedGuideRecipes.contains(row.recipeId)).count();
        int freeSlots = Math.max(0, menu.getPatternCapacity() - menu.getUsedPatternSlots());
        int blankGuides = menu.getStoredBlankGuideCount() + clientBlankGuideCount();
        Component capacity = Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.guide_chain.capacity",
                menu.getPatternProviderCount(), menu.getUsedPatternSlots(), menu.getPatternCapacity(), blankGuides);
        drawStringScaledToFit(graphics, capacity, leftPos + 12, topPos + 57, 180, 0xBFA8DD);

        int first = Math.min(detailScroll, Math.max(0, guideChainRows.size() - 9));
        GuideChainRow hovered = null;
        for (int index = first; index < Math.min(guideChainRows.size(), first + 9); index++) {
            GuideChainRow row = guideChainRows.get(index);
            int y = topPos + 76 + (index - first) * 21;
            int x = leftPos + 11 + Math.min(3, row.depth) * 7;
            drawItemSlot(graphics, x, y - 2);
            if (!row.stack.isEmpty()) graphics.renderItem(row.stack, x + 1, y - 1);
            String name = font.plainSubstrByWidth(row.stack.isEmpty()
                    ? Component.translatable("screen.ars_arcane_matrix.order_terminal.guide_chain.unknown").getString()
                    : row.stack.getHoverName().getString(), 92 - Math.min(3, row.depth) * 7);
            graphics.drawString(font, name, x + 22, y, 0xFFFFFF, false);
            boolean skipped = skippedGuideRecipes.contains(row.recipeId);
            Component status = row.status == GuideChainStatus.NEW
                    ? Component.literal(skipped ? "[ ]" : "[x]")
                    : Component.translatable(row.status.translationKey);
            graphics.drawString(font, status, leftPos + 151, y + 5,
                    row.status.color, false);
            if (inside(mouseX, mouseY, x, y - 2, 174 - (x - leftPos), 18)) hovered = row;
        }
        if (guideChainRows.isEmpty()) {
            graphics.drawCenteredString(font, Component.translatable(
                            "screen.ars_arcane_matrix.order_terminal.guide_chain.empty"),
                    leftPos + imageWidth / 2, topPos + 130, 0xFF7777);
        }
        boolean ready = newCount > 0 && newCount <= freeSlots && newCount <= blankGuides;
        chainCreateButton.active = ready;
        Component footer = Component.translatable(ready
                        ? "screen.ars_arcane_matrix.order_terminal.guide_chain.ready"
                        : newCount == 0
                        ? "screen.ars_arcane_matrix.order_terminal.guide_chain.no_new"
                        : "screen.ars_arcane_matrix.order_terminal.guide_chain.insufficient",
                newCount, freeSlots, blankGuides);
        drawStringScaledToFit(graphics, footer, leftPos + 12, topPos + 270, 180,
                ready ? 0xA8E6B0 : 0xFF8888);
        if (hovered != null && !hovered.stack.isEmpty()) {
            List<Component> tooltip = new ArrayList<>(Screen.getTooltipFromItem(minecraft, hovered.stack));
            if (hovered.workstation != null) tooltip.add(Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.plan.workstation",
                    Component.translatable(workstationTranslation(hovered.workstation))));
            if (hovered.recipeId != null) tooltip.add(Component.literal(hovered.recipeId.toString()).withColor(0x888888));
            graphics.renderTooltip(font, tooltip, Optional.empty(), hovered.stack, mouseX, mouseY);
        }
    }

    private List<PlanRow> buildPlanRows(CraftableRecipeInfo info) {
        List<PlanRow> rows = new ArrayList<>();
        if (info == null || minecraft == null || minecraft.level == null) return rows;
        List<AvailableAmount> available = new ArrayList<>();
        List<AvailableFluidAmount> availableFluids = new ArrayList<>();
        menu.getStoredEntries().forEach(entry -> available.add(
                new AvailableAmount(entry.stack().copyWithCount(1), entry.count())));
        // The storage page renders linked fluids separately from item entries.
        // Mirror those fluids as virtual filled containers for the crafting-plan
        // precheck, matching the server-side extraction performed by the lectern.
        for (int tank = 0; tank < WixieOrderTerminalMenu.MAX_LINKED_FLUID_TYPES; tank++) {
            Fluid fluid = BuiltInRegistries.FLUID.byId(menu.getLinkedFluidType(tank));
            int amount = menu.getLinkedFluidAmount(tank);
            if (fluid == Fluids.EMPTY || amount <= 0 || fluid.getBucket() == Items.AIR) continue;
            addAvailableFluid(availableFluids, fluid, amount);
            ItemStack container = new ItemStack(fluid.getBucket());
            int unit = FluidUtil.getFluidContained(container)
                    .map(FluidStack::getAmount).orElse(1000);
            int containers = amount / Math.max(1, unit);
            if (containers > 0) addAvailable(available, container, containers);
        }
        // Imbuement pedestal catalysts survive each operation. Track their
        // physical reservation across the complete dependency tree so two
        // enchantment levels that both craft the same Essence only require one
        // reusable catalyst set.
        List<AvailableAmount> reusableReservations = new ArrayList<>();
        expandPlan(info, menu.getRequestedCount(), rows, available,
                availableFluids, reusableReservations, new HashSet<>(), 0);
        return rows;
    }

    private List<GuideChainRow> buildGuideChain(ResourceLocation rootRecipeId) {
        if (minecraft == null || minecraft.level == null) return List.of();
        RecipeHolder<?> root = RecipeAutomationSupport.find(
                minecraft.level.getRecipeManager(), rootRecipeId).orElse(null);
        if (root == null || !RecipeAutomationSupport.supports(root.value())) return List.of();

        Set<ResourceLocation> existing = new HashSet<>();
        for (int index = 0; index < menu.getCraftableOutputs().size(); index++) {
            CraftableRecipeInfo info = menu.getCraftableRecipeInfo(index);
            if (info != null) existing.add(info.recipeId());
        }
        List<RecipeHolder<?>> supported = RecipeAutomationSupport.all(
                        minecraft.level.getRecipeManager()).stream()
                .filter(holder -> RecipeAutomationSupport.supports(holder.value()))
                .sorted(Comparator.comparing(holder -> holder.id().toString()))
                .toList();
        Map<ResourceLocation, GuideChainRow> recipeRows = new LinkedHashMap<>();
        List<GuideChainRow> baseRows = new ArrayList<>();
        expandGuideChain(root, 0, existing, supported, recipeRows, baseRows, new HashSet<>());
        List<GuideChainRow> result = new ArrayList<>(recipeRows.values());
        result.addAll(baseRows);
        return List.copyOf(result.stream().limit(GuideChainEncodePayload.MAX_RECIPES).toList());
    }

    private void expandGuideChain(
            RecipeHolder<?> recipe,
            int depth,
            Set<ResourceLocation> existing,
            List<RecipeHolder<?>> supported,
            Map<ResourceLocation, GuideChainRow> recipeRows,
            List<GuideChainRow> baseRows,
            Set<ResourceLocation> path
    ) {
        if (minecraft == null || minecraft.level == null || depth > 24
                || recipeRows.size() + baseRows.size() >= GuideChainEncodePayload.MAX_RECIPES
                || !path.add(recipe.id())) return;
        ItemStack output = RecipeAutomationSupport.result(recipe.value(), minecraft.level.registryAccess());
        GuideChainStatus status = existing.contains(recipe.id())
                ? GuideChainStatus.EXISTING : GuideChainStatus.NEW;
        recipeRows.putIfAbsent(recipe.id(), new GuideChainRow(
                output.copyWithCount(1), recipe.id(), RecipeAutomationSupport.workstation(recipe.value()), depth, status));
        // An installed pattern already owns its dependency chain. Only generate what is missing.
        if (depth >= 1) {
            path.remove(recipe.id());
            return;
        }

        for (Ingredient ingredient : RecipeAutomationSupport.ingredients(
                recipe.value(), minecraft.level.registryAccess())) {
            if (ingredient.isEmpty()) continue;
            RecipeHolder<?> child = chooseGuideChainRecipe(ingredient, existing, supported, path);
            if (child != null) {
                expandGuideChain(child, depth + 1, existing, supported,
                        recipeRows, baseRows, new HashSet<>(path));
                continue;
            }
            ItemStack[] choices = ingredient.getItems();
            ItemStack base = chooseStoredIngredient(choices);
            GuideChainStatus baseStatus = base.isEmpty()
                    ? GuideChainStatus.MISSING : GuideChainStatus.BASE;
            boolean duplicate = baseRows.stream().anyMatch(row ->
                    row.status == baseStatus && ItemStack.isSameItemSameComponents(row.stack, base));
            if (!duplicate) baseRows.add(new GuideChainRow(
                    base.copyWithCount(1), null, null, depth + 1, baseStatus));
        }
        path.remove(recipe.id());
    }

    private RecipeHolder<?> chooseGuideChainRecipe(
            Ingredient ingredient,
            Set<ResourceLocation> existing,
            List<RecipeHolder<?>> supported,
            Set<ResourceLocation> path
    ) {
        if (minecraft == null || minecraft.level == null) return null;
        RecipeHolder<?> best = null;
        long bestScore = Long.MIN_VALUE;
        for (RecipeHolder<?> candidate : supported) {
            if (path.contains(candidate.id())) continue;
            ItemStack result = RecipeAutomationSupport.result(
                    candidate.value(), minecraft.level.registryAccess());
            if (result.isEmpty() || !ingredient.test(result)) continue;
            long stored = storedAmount(result);
            long score = (existing.contains(candidate.id()) ? 1_000_000_000L : 0L)
                    + Math.min(100_000L, stored) * 100L
                    + Math.max(1, result.getCount());
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private ItemStack chooseStoredIngredient(ItemStack[] choices) {
        ItemStack best = ItemStack.EMPTY;
        long bestCount = -1;
        for (ItemStack choice : choices) {
            long count = storedAmount(choice);
            if (count > bestCount) {
                best = choice;
                bestCount = count;
            }
        }
        return best;
    }

    private long storedAmount(ItemStack template) {
        if (template.isEmpty()) return 0L;
        long count = 0L;
        for (WixieOrderTerminalMenu.StorageEntry entry : menu.getStoredEntries()) {
            if (ItemStack.isSameItemSameComponents(entry.stack(), template)) count += entry.count();
        }
        if (minecraft != null && minecraft.player != null) {
            for (ItemStack stack : minecraft.player.getInventory().items) {
                if (ItemStack.isSameItemSameComponents(stack, template)) count += stack.getCount();
            }
        }
        return count;
    }

    private int clientBlankGuideCount() {
        if (minecraft == null || minecraft.player == null) return 0;
        int count = 0;
        for (ItemStack stack : minecraft.player.getInventory().items) {
            if (stack.is(dev.arsmatrix.registry.ModItems.CRAFTING_GUIDE.get())
                    && dev.arsmatrix.item.CraftingGuideItem.getRecipeId(stack) == null) {
                count += stack.getCount();
            }
        }
        return count;
    }

    private void expandPlan(
            CraftableRecipeInfo info, int requestedOutput, List<PlanRow> rows,
            List<AvailableAmount> available, List<AvailableFluidAmount> availableFluids,
            List<AvailableAmount> reusableReservations,
            Set<ResourceLocation> path, int depth
    ) {
        if (minecraft == null || minecraft.level == null || depth > 32 || !path.add(info.recipeId())) return;
        RecipeHolder<?> holder = RecipeAutomationSupport.find(
                minecraft.level.getRecipeManager(), info.recipeId()).orElse(null);
        if (holder == null) {
            path.remove(info.recipeId());
            return;
        }
        ItemStack output = RecipeAutomationSupport.result(holder.value(), minecraft.level.registryAccess());
        int replicationSeedCount = replicationSeedCount(holder.value(), output);
        int outputPerOperation = Math.max(1, output.getCount() - replicationSeedCount);
        int operations = Math.max(1, (requestedOutput + outputPerOperation - 1) / outputPerOperation);
        if (holder.value() instanceof dev.arsmatrix.compat.ArcaneReactionAutomationRecipe reaction
                && reaction.rule().inputFluidAmount() > 0) {
            Fluid fluid = BuiltInRegistries.FLUID.get(reaction.rule().inputFluid());
            int required = Math.max(0, reaction.rule().inputFluidAmount() * operations);
            int stored = consumeAvailableFluid(availableFluids, fluid, required);
            addFluidPlanRow(rows, fluid, required, stored, Math.max(0, required - stored));
        }
        List<Ingredient> ingredients = RecipeAutomationSupport.ingredients(
                holder.value(), minecraft.level.registryAccess());
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            boolean reusable = RecipeAutomationSupport.isImbuement(holder.value())
                    && ingredientIndex > 0;
            boolean replicationSeed = replicationSeedCount > 0
                    && ingredients.get(ingredientIndex).test(output);
            ItemStack choice = reusable
                    ? chooseReusablePlanIngredient(
                            ingredients.get(ingredientIndex), available, reusableReservations)
                    : choosePlanIngredient(ingredients.get(ingredientIndex), available);
            if (choice.isEmpty()) continue;
            int requiredPerOperation = Math.max(1, choice.getCount());
            if (RecipeAutomationSupport.isAvaritiaCompressor(holder.value()) && ingredientIndex == 0) {
                requiredPerOperation = Math.max(1,
                        RecipeAutomationSupport.avaritiaCompressorInputCount(holder.value()));
            }
            int required = reusable || replicationSeed
                    ? requiredPerOperation : requiredPerOperation * operations;
            if (!reusable && !replicationSeed) {
                int seedReserve = replicationSeedCountForPattern(choice, path);
                int alreadyReserved = availableAmount(reusableReservations, choice);
                int reserveNeeded = Math.max(0, seedReserve - alreadyReserved);
                if (reserveNeeded > 0) {
                    int reservedFromStorage = consumeAvailable(available, choice, reserveNeeded);
                    addAvailable(reusableReservations, choice, reserveNeeded);
                    addPlanRow(rows, choice, reserveNeeded, reservedFromStorage, 0,
                            Math.max(0, reserveNeeded - reservedFromStorage));
                }
            }
            if (reusable || replicationSeed) {
                int alreadyReserved = availableAmount(reusableReservations, choice);
                required = Math.max(0, required - alreadyReserved);
                if (required <= 0) continue;
                addAvailable(reusableReservations, choice, required);
            }
            int inStorage = consumeAvailable(available, choice, required);
            int deficit = Math.max(0, required - inStorage);
            CraftableRecipeInfo child = deficit <= 0 ? null : findPatternFor(choice, path);
            addPlanRow(rows, choice, required, inStorage,
                    child == null ? 0 : deficit, child == null ? deficit : 0);
            if (child != null) {
                expandPlan(child, deficit, rows, available, availableFluids, reusableReservations,
                        new HashSet<>(path), depth + 1);
            }
        }
        int surplus = operations * outputPerOperation - requestedOutput;
        if (surplus > 0) addAvailable(available, output, surplus);
        path.remove(info.recipeId());
    }

    private int replicationSeedCount(Recipe<?> recipe, ItemStack output) {
        if (!(recipe instanceof CraftingRecipe) || output.isEmpty()
                || minecraft == null || minecraft.level == null) return 0;
        int count = 0;
        for (Ingredient ingredient : RecipeAutomationSupport.ingredients(
                recipe, minecraft.level.registryAccess())) {
            if (ingredient.test(output)) count++;
        }
        return output.getCount() > count ? count : 0;
    }

    private int replicationSeedCountForPattern(ItemStack target, Set<ResourceLocation> path) {
        if (minecraft == null || minecraft.level == null) return 0;
        CraftableRecipeInfo pattern = findPatternFor(target, path);
        if (pattern == null) return 0;
        RecipeHolder<?> holder = RecipeAutomationSupport.find(
                minecraft.level.getRecipeManager(), pattern.recipeId()).orElse(null);
        if (holder == null) return 0;
        ItemStack output = RecipeAutomationSupport.result(
                holder.value(), minecraft.level.registryAccess());
        return replicationSeedCount(holder.value(), output);
    }

    private ItemStack choosePlanIngredient(Ingredient ingredient, List<AvailableAmount> available) {
        ItemStack best = ItemStack.EMPTY;
        int bestScore = Integer.MIN_VALUE;
        for (ItemStack candidate : ingredient.getItems()) {
            int stored = availableAmount(available, candidate);
            int score = stored * 2 + (findPatternFor(candidate, Set.of()) == null ? 0 : 1);
            if (score > bestScore) {
                best = candidate;
                bestScore = score;
            }
        }
        return best;
    }

    private ItemStack chooseReusablePlanIngredient(
            Ingredient ingredient,
            List<AvailableAmount> available,
            List<AvailableAmount> reusableReservations
    ) {
        ItemStack reserved = ItemStack.EMPTY;
        int highestReservation = 0;
        for (ItemStack candidate : ingredient.getItems()) {
            int amount = availableAmount(reusableReservations, candidate);
            if (amount > highestReservation) {
                reserved = candidate;
                highestReservation = amount;
            }
        }
        return reserved.isEmpty() ? choosePlanIngredient(ingredient, available) : reserved;
    }

    private CraftableRecipeInfo findPatternFor(ItemStack target, Set<ResourceLocation> path) {
        for (int index = 0; index < menu.getCraftableOutputs().size(); index++) {
            CraftableRecipeInfo candidate = menu.getCraftableRecipeInfo(index);
            if (candidate != null && !path.contains(candidate.recipeId())
                    && ItemStack.isSameItemSameComponents(candidate.output(), target)) return candidate;
        }
        return null;
    }

    private static void addPlanRow(
            List<PlanRow> rows, ItemStack stack, int required, int available, int craftable, int missing
    ) {
        for (PlanRow row : rows) {
            if (!ItemStack.isSameItemSameComponents(row.stack, stack)) continue;
            row.required += required;
            row.available += available;
            row.craftable += craftable;
            row.missing += missing;
            return;
        }
        rows.add(new PlanRow(stack.copyWithCount(1), Fluids.EMPTY,
                required, available, craftable, missing));
    }

    private static void addFluidPlanRow(
            List<PlanRow> rows, Fluid fluid, int required, int available, int missing
    ) {
        if (fluid == Fluids.EMPTY || required <= 0) return;
        for (PlanRow row : rows) {
            if (row.fluid != fluid) continue;
            row.required += required;
            row.available += available;
            row.missing += missing;
            return;
        }
        ItemStack icon = fluid.getBucket() == Items.AIR
                ? new ItemStack(Items.BUCKET) : new ItemStack(fluid.getBucket());
        rows.add(new PlanRow(icon, fluid, required, available, 0, missing));
    }

    private static int consumeAvailableFluid(
            List<AvailableFluidAmount> available, Fluid fluid, int requested
    ) {
        for (AvailableFluidAmount entry : available) {
            if (entry.fluid != fluid) continue;
            int consumed = Math.min(entry.amount, Math.max(0, requested));
            entry.amount -= consumed;
            return consumed;
        }
        return 0;
    }

    private static void addAvailableFluid(
            List<AvailableFluidAmount> available, Fluid fluid, int amount
    ) {
        for (AvailableFluidAmount entry : available) {
            if (entry.fluid != fluid) continue;
            entry.amount = (int) Math.min(Integer.MAX_VALUE, (long) entry.amount + amount);
            return;
        }
        available.add(new AvailableFluidAmount(fluid, amount));
    }

    private static int consumeAvailable(List<AvailableAmount> available, ItemStack template, int requested) {
        int remaining = requested;
        for (AvailableAmount entry : available) {
            if (!ItemStack.isSameItemSameComponents(entry.stack, template) || entry.amount <= 0) continue;
            int used = Math.min(remaining, entry.amount);
            entry.amount -= used;
            remaining -= used;
            if (remaining <= 0) break;
        }
        return requested - remaining;
    }

    private static int availableAmount(List<AvailableAmount> available, ItemStack template) {
        long total = 0;
        for (AvailableAmount entry : available) {
            if (ItemStack.isSameItemSameComponents(entry.stack, template)) total += entry.amount;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    private static void addAvailable(List<AvailableAmount> available, ItemStack stack, int amount) {
        for (AvailableAmount entry : available) {
            if (!ItemStack.isSameItemSameComponents(entry.stack, stack)) continue;
            entry.amount += amount;
            return;
        }
        available.add(new AvailableAmount(stack.copyWithCount(1), amount));
    }

    private void renderStatusPage(GuiGraphics graphics, int mouseX, int mouseY) {
        redrawDetailPanel(graphics);
        OrderDiagnosticsPayload diagnostics = menu.getDiagnostics();
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.status.title"), leftPos + 12, topPos + 10, 0xFFFFFF, false);
        if (!diagnostics.target().isEmpty()) {
            graphics.renderItem(diagnostics.target(), leftPos + 15, topPos + 38);
            graphics.drawString(font, diagnostics.target().getHoverName(), leftPos + 36, topPos + 40, 0xFFFFFF, false);
        }
        Component completedProgress = Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.status.progress",
                        diagnostics.produced(), diagnostics.requested());
        graphics.drawString(font, completedProgress, leftPos + 36, topPos + 51, 0xBFA8DD, false);
        int total = Math.max(1, diagnostics.requested());
        int filled = Math.min(174, (int) (174L * diagnostics.produced() / total));
        graphics.fill(leftPos + 15, topPos + 65, leftPos + 189, topPos + 72, 0xFF080610);
        graphics.fill(leftPos + 15, topPos + 65, leftPos + 15 + filled, topPos + 72, 0xFF9B62C7);
        graphics.drawString(font, Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.diagnostics.state",
                        Component.translatable(diagnostics.stateKey())), leftPos + 15, topPos + 81,
                diagnostics.missingItems().isEmpty() ? 0xFFFFFF : 0xFF7777, false);
        Component network = Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.diagnostics.network",
                        diagnostics.providers(), diagnostics.activeWorkers(), diagnostics.bufferedItems());
        graphics.drawString(font, font.plainSubstrByWidth(network.getString(), 174),
                leftPos + 15, topPos + 94, 0xD0A8FF, false);
        Component cost = Component.translatable(
                        "screen.ars_arcane_matrix.order_terminal.diagnostics.cost",
                        formatSeconds(diagnostics.elapsedTicks()), diagnostics.craftOperations(), diagnostics.sourceSpent());
        graphics.drawString(font, font.plainSubstrByWidth(cost.getString(), 174),
                leftPos + 15, topPos + 107, 0xD0A8FF, false);
        int y = topPos + 126;
        if (!diagnostics.detail().isBlank()) {
            graphics.drawString(font, font.plainSubstrByWidth(diagnostics.detail(), 174), leftPos + 15, y, 0xE8DDF4, false);
            y += 15;
        }
        if (!diagnostics.activeJobs().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.diagnostics.jobs"), leftPos + 15, y, 0xFFFFFF, false);
            y += 13;
            for (String job : diagnostics.activeJobs().stream().skip(detailScroll).limit(7).toList()) {
                graphics.drawString(font, font.plainSubstrByWidth(job, 174), leftPos + 18, y, 0xBFA8DD, false);
                y += 13;
            }
        } else if (!diagnostics.missingItems().isEmpty()) {
            graphics.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.diagnostics.missing"), leftPos + 15, y, 0xFF7777, false);
            y += 13;
            for (ItemStack stack : diagnostics.missingItems().stream().skip(detailScroll).limit(7).toList()) {
                graphics.renderItem(stack, leftPos + 16, y - 4);
                graphics.drawString(font, stack.getHoverName().copy().append(" ×" + stack.getCount()),
                        leftPos + 36, y, 0xFFAAAA, false);
                y += 19;
            }
        } else {
            graphics.drawString(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.status.no_jobs"), leftPos + 15, y, 0xBFA8DD, false);
        }
        if (inside(mouseX, mouseY, leftPos + 15, topPos + 50, 174, 23)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.order_terminal.status.progress.tooltip"), mouseX, mouseY);
        }
    }

    private void redrawDetailPanel(GuiGraphics graphics) {
        // AbstractContainerScreen renders the real player slots after renderBg.
        // Cover the complete terminal again so those slots cannot overlap the
        // plan/status pages; their controls are redrawn afterwards.
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF181028);
        graphics.fill(leftPos + 7, topPos + 27, leftPos + 198, topPos + 284, 0xFF282040);
        graphics.fill(leftPos + 7, topPos + 27, leftPos + 198, topPos + 29, 0xFFD0A8FF);
    }

    private void drawStringScaledToFit(
            GuiGraphics graphics, Component text, int x, int y, int maxWidth, int color
    ) {
        int textWidth = font.width(text);
        if (textWidth <= maxWidth) {
            graphics.drawString(font, text, x, y, color, false);
            return;
        }
        float scale = Math.max(0.65F, (float) maxWidth / textWidth);
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0.0F);
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.drawString(font, text, 0, 0, color, false);
        graphics.pose().popPose();
    }

    private void renderDetailButtons(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (detailPage == DetailPage.PLAN) {
            planBackButton.render(graphics, mouseX, mouseY, partialTick);
            planStartButton.render(graphics, mouseX, mouseY, partialTick);
        } else if (detailPage == DetailPage.STATUS) {
            statusBackButton.render(graphics, mouseX, mouseY, partialTick);
            statusCancelButton.render(graphics, mouseX, mouseY, partialTick);
            statusCopyButton.render(graphics, mouseX, mouseY, partialTick);
        } else if (detailPage == DetailPage.GUIDE_CHAIN) {
            chainBackButton.render(graphics, mouseX, mouseY, partialTick);
            chainCreateButton.render(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    protected void renderSlot(GuiGraphics graphics, Slot slot) {
        if (detailPage == DetailPage.NONE) super.renderSlot(graphics, slot);
    }

    @Override
    protected void renderSlotHighlight(
            GuiGraphics graphics, Slot slot, int mouseX, int mouseY, float partialTick
    ) {
        if (detailPage == DetailPage.NONE) {
            super.renderSlotHighlight(graphics, slot, mouseX, mouseY, partialTick);
        }
    }

    private List<Component> diagnosticTooltip() {
        OrderDiagnosticsPayload diagnostics = menu.getDiagnostics();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.title"));
        lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.state",
                Component.translatable(diagnostics.stateKey())));
        if (!diagnostics.target().isEmpty()) {
            lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.target",
                    diagnostics.target().getHoverName(), diagnostics.produced(), diagnostics.requested()));
        }
        lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.network",
                diagnostics.providers(), diagnostics.activeWorkers(), diagnostics.bufferedItems()));
        lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.cost",
                formatSeconds(diagnostics.elapsedTicks()), diagnostics.craftOperations(), diagnostics.sourceSpent()));
        if (!diagnostics.detail().isBlank()) {
            lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.detail",
                    diagnostics.detail()));
        }
        if (!diagnostics.missingItems().isEmpty()) {
            lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.missing"));
            diagnostics.missingItems().stream().limit(8).forEach(stack -> lines.add(
                    Component.literal("  ").append(stack.getHoverName()).append(" ×" + stack.getCount())));
        }
        if (!diagnostics.activeJobs().isEmpty()) {
            lines.add(Component.translatable("screen.ars_arcane_matrix.order_terminal.diagnostics.jobs"));
            diagnostics.activeJobs().stream().limit(8)
                    .forEach(job -> lines.add(Component.literal("  " + job)));
        }
        return lines;
    }

    private void copyDiagnostics() {
        if (minecraft == null || minecraft.player == null) return;
        minecraft.keyboardHandler.setClipboard(buildDiagnosticReport());
        minecraft.player.displayClientMessage(Component.translatable(
                "screen.ars_arcane_matrix.order_terminal.diagnostics.copied"), true);
    }

    private String buildDiagnosticReport() {
        OrderDiagnosticsPayload diagnostics = menu.getDiagnostics();
        StringBuilder report = new StringBuilder("Ars Arcane Matrix order diagnostics\n");
        report.append("state=").append(diagnostics.stateKey()).append('\n');
        report.append("target=").append(diagnostics.target().isEmpty() ? "none"
                : BuiltInRegistries.ITEM.getKey(diagnostics.target().getItem())).append('\n');
        report.append("progress=").append(diagnostics.produced()).append('/')
                .append(diagnostics.requested()).append('\n');
        report.append("elapsedSeconds=").append(formatSeconds(diagnostics.elapsedTicks())).append('\n');
        report.append("providers=").append(diagnostics.providers())
                .append(" activeWorkers=").append(diagnostics.activeWorkers())
                .append(" bufferedItems=").append(diagnostics.bufferedItems()).append('\n');
        report.append("craftOperations=").append(diagnostics.craftOperations())
                .append(" sourceSpent=").append(diagnostics.sourceSpent()).append('\n');
        if (!diagnostics.detail().isBlank()) report.append("detail=").append(diagnostics.detail()).append('\n');
        if (!diagnostics.missingItems().isEmpty()) {
            report.append("missing=");
            for (ItemStack stack : diagnostics.missingItems()) {
                report.append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                        .append('x').append(stack.getCount()).append(';');
            }
            report.append('\n');
        }
        if (!diagnostics.activeJobs().isEmpty()) {
            report.append("activeJobs=\n");
            diagnostics.activeJobs().forEach(job -> report.append("- ").append(job).append('\n'));
        }
        return report.toString();
    }

    private static String formatSeconds(long ticks) {
        return String.format(Locale.ROOT, "%.1f", ticks / 20.0D);
    }

    private static void drawRecipeTypeBadge(
            GuiGraphics graphics, CraftableRecipeInfo info, int x, int y
    ) {
        if (info == null) return;
        ItemStack workstation = BuiltInRegistries.ITEM.getOptional(info.workstation())
                .map(ItemStack::new).orElseGet(() -> new ItemStack(Items.CRAFTING_TABLE));
        graphics.pose().pushPose();
        graphics.pose().translate(x + 1.0F, y + 1.0F, 250.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(workstation, 0, 0);
        graphics.pose().popPose();
    }

    private static String workstationTranslation(ResourceLocation workstation) {
        if (RecipeAutomationSupport.SOURCE_STONE_FURNACE.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.furnace";
        }
        if (RecipeAutomationSupport.ENCHANTING_APPARATUS.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.apparatus";
        }
        if (RecipeAutomationSupport.IMBUEMENT_CHAMBER.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.imbuement";
        }
        if (RecipeAutomationSupport.STONECUTTER.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.stonecutter";
        }
        if (RecipeAutomationSupport.ARCANE_REACTION_VESSEL.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.reaction_vessel";
        }
        if (RecipeAutomationSupport.FARMERS_DELIGHT_COOKING_POT.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.cooking_pot";
        }
        if (RecipeAutomationSupport.AVARITIA_NEUTRON_COMPRESSOR.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.avaritia_compressor";
        }
        if (RecipeAutomationSupport.AVARITIA_SCULK_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_NETHER_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_END_CRAFTING_TABLE.equals(workstation)
                || RecipeAutomationSupport.AVARITIA_EXTREME_CRAFTING_TABLE.equals(workstation)) {
            return "screen.ars_arcane_matrix.order_terminal.workstation.avaritia_table";
        }
        return "screen.ars_arcane_matrix.order_terminal.workstation.crafting";
    }

    private static String compactCount(long count) {
        if (count >= 1_000_000) return (count / 1_000_000) + "m";
        if (count >= 1_000) return (count / 1_000) + "k";
        return Long.toString(count);
    }

    private static String compactFluidAmount(int amount) {
        if (amount >= 1_000_000) return String.format(Locale.ROOT, "%.1fkB", amount / 1_000_000.0D);
        if (amount >= 10_000) return (amount / 1_000) + "B";
        if (amount >= 1_000) return String.format(Locale.ROOT, "%.1fB", amount / 1_000.0D);
        return amount + "mB";
    }

    private static String formatBuckets(int amount) {
        return String.format(Locale.ROOT, amount % 1000 == 0 ? "%.0f" : "%.3f", amount / 1000.0D);
    }

    private static void drawFluidCell(GuiGraphics graphics, Fluid fluid, int x, int y) {
        if (fluid == Fluids.EMPTY) return;
        FluidStack stack = new FluidStack(fluid, 1);
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation texture = properties.getStillTexture(stack);
        TextureAtlasSprite sprite = Minecraft.getInstance().getModelManager()
                .getAtlas(TextureAtlas.LOCATION_BLOCKS).getSprite(texture);
        int tint = properties.getTintColor(stack);
        graphics.setColor(((tint >>> 16) & 255) / 255.0F, ((tint >>> 8) & 255) / 255.0F,
                (tint & 255) / 255.0F, ((tint >>> 24) & 255) / 255.0F);
        graphics.blit(x, y, 0, 16, 16, sprite);
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    private static boolean inside(double mouseX, double mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + height;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (detailPage != DetailPage.NONE && keyCode == GLFW.GLFW_KEY_ESCAPE) {
            closeDetailPage();
            return true;
        }
        EditBox focusedInput = search != null && search.isFocused()
                ? search
                : countInput != null && countInput.isFocused() ? countInput : null;
        if (focusedInput != null) {
            // Inventory and other gameplay key bindings must not close or replace
            // the screen while the player is typing a search or order amount.
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                focusedInput.setFocused(false);
                return true;
            }
            focusedInput.keyPressed(keyCode, scanCode, modifiers);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Most catalogue interactions are handled below before vanilla's child
        // focus dispatch runs. Explicitly leave search mode whenever the player
        // clicks outside the search field so JEI/EMI hotkeys such as U and R are
        // available again while hovering a stored item.
        boolean clickedSearch = search != null && search.visible
                && search.isMouseOver(mouseX, mouseY);
        if (search != null && search.isFocused() && !clickedSearch) {
            search.setFocused(false);
            if (getFocused() == search) setFocused(null);
        }
        if (countInput != null && countInput.isFocused()
                && !countInput.isMouseOver(mouseX, mouseY)) {
            countInput.setFocused(false);
            if (getFocused() == countInput) setFocused(null);
        }
        if (detailPage == DetailPage.GUIDE_CHAIN && button == 0) {
            int first = Math.min(detailScroll, Math.max(0, guideChainRows.size() - 9));
            for (int index = first; index < Math.min(guideChainRows.size(), first + 9); index++) {
                GuideChainRow row = guideChainRows.get(index);
                int y = topPos + 76 + (index - first) * 21;
                if (row.status == GuideChainStatus.NEW && row.recipeId != null
                        && inside(mouseX, mouseY, leftPos + 11, y - 2, 180, 18)) {
                    if (!skippedGuideRecipes.add(row.recipeId)) skippedGuideRecipes.remove(row.recipeId);
                    return true;
                }
            }
        }
        if (detailPage != DetailPage.NONE) {
            for (Button detailButton : detailButtons()) {
                if (detailButton.visible && detailButton.mouseClicked(mouseX, mouseY, button)) return true;
            }
            return true;
        }
        if (storageTab && inside(mouseX, mouseY, leftPos + DEPOSIT_X - 1,
                topPos + DEPOSIT_Y - 1, 18, 18)) {
            if (!menu.getCarried().isEmpty()) {
                sendMenuButton(WixieOrderTerminalMenu.BUTTON_STORAGE_DEPOSIT);
            }
            return true;
        }
        int scrollbarX = leftPos + 173;
        int scrollbarY = topPos + GRID_Y;
        if (inside(mouseX, mouseY, scrollbarX, scrollbarY, 5, rows() * 18)) {
            draggingScrollbar = true;
            setScrollFromMouse(mouseY);
            return true;
        }
        List<Integer> filtered = filteredIndices();
        int first = scrollRow * COLUMNS;
        for (int local = 0; local < visibleCount() && first + local < filtered.size(); local++) {
            int sourceIndex = filtered.get(first + local);
            int x = leftPos + GRID_X + (local % COLUMNS) * 18;
            int y = topPos + GRID_Y + (local / COLUMNS) * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) {
                if (storageTab) {
                    if (sourceIndex >= 0 && (button == 0 || button == 1)) {
                        sendStorageExtraction(sourceIndex, button);
                        storageDragButton = button;
                        lastStorageDragIndex = sourceIndex;
                    }
                } else if (button == 0) {
                    sendMenuButton(WixieOrderTerminalMenu.BUTTON_SELECT_OFFSET + sourceIndex);
                    updateMatchModeButton();
                }
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (detailPage != DetailPage.NONE) {
            int size = detailPage == DetailPage.PLAN
                    ? buildPlanRows(menu.getSelectedRecipeInfo()).size()
                    : detailPage == DetailPage.GUIDE_CHAIN
                    ? guideChainRows.size()
                    : Math.max(menu.getDiagnostics().activeJobs().size(), menu.getDiagnostics().missingItems().size());
            int visibleRows = detailPage == DetailPage.STATUS ? 7 : 9;
            detailScroll = Math.max(0, Math.min(Math.max(0, size - visibleRows),
                    detailScroll - (int) Math.signum(scrollY)));
            return true;
        }
        if (inside(mouseX, mouseY, leftPos + 7, topPos + 50, 171, rows() * 18 + 4)) {
            scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow - (int) Math.signum(scrollY)));
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, scrollX, scrollY);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (draggingScrollbar) {
            setScrollFromMouse(mouseY);
            return true;
        }
        if (storageTab && button == storageDragButton) {
            int sourceIndex = storedItemIndexAt(mouseX, mouseY);
            if (sourceIndex >= 0 && sourceIndex != lastStorageDragIndex) {
                sendStorageExtraction(sourceIndex, button);
                lastStorageDragIndex = sourceIndex;
            }
            return sourceIndex >= 0;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (detailPage != DetailPage.NONE) {
            detailButtons().forEach(detailButton -> detailButton.mouseReleased(mouseX, mouseY, button));
            return true;
        }
        storageDragButton = -1;
        lastStorageDragIndex = Integer.MIN_VALUE;
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private List<Button> detailButtons() {
        return detailPage == DetailPage.PLAN
                ? List.of(planBackButton, planStartButton)
                : detailPage == DetailPage.GUIDE_CHAIN
                ? List.of(chainBackButton, chainCreateButton)
                : List.of(statusBackButton, statusCancelButton, statusCopyButton);
    }

    private void sendStorageExtraction(int sourceIndex, int button) {
        if (sourceIndex < 0 || sourceIndex >= menu.getStoredEntries().size()) return;
        int mode = button == 1
                ? StorageExtractionPayload.ONE
                : Screen.hasShiftDown()
                ? StorageExtractionPayload.ALL_FITTING
                : StorageExtractionPayload.STACK;
        ItemStack clicked = menu.getStoredEntries().get(sourceIndex).stack().copyWithCount(1);
        PacketDistributor.sendToServer(
                new StorageExtractionPayload(menu.containerId, clicked, mode));
    }

    private int storedItemIndexAt(double mouseX, double mouseY) {
        List<Integer> filtered = filteredIndices();
        int first = scrollRow * COLUMNS;
        for (int local = 0; local < visibleCount() && first + local < filtered.size(); local++) {
            int sourceIndex = filtered.get(first + local);
            int x = leftPos + GRID_X + (local % COLUMNS) * 18;
            int y = topPos + GRID_Y + (local / COLUMNS) * 18;
            if (sourceIndex >= 0 && inside(mouseX, mouseY, x, y, 18, 18)) return sourceIndex;
        }
        return Integer.MIN_VALUE;
    }

    private void setScrollFromMouse(double mouseY) {
        int max = maxScrollRow();
        if (max <= 0) { scrollRow = 0; return; }
        int height = rows() * 18;
        int total = Math.max(rows(), totalRows());
        int thumbHeight = Math.max(12, height * rows() / total);
        double relative = mouseY - (topPos + GRID_Y) - thumbHeight / 2.0;
        scrollRow = Math.max(0, Math.min(max,
                (int) Math.round(relative / Math.max(1, height - thumbHeight) * max)));
    }

    /** Exposes custom catalogue cells to JEI for U/R hover shortcuts. */
    public Optional<VirtualIngredient> getVirtualIngredientUnderMouse(double mouseX, double mouseY) {
        if (detailPage == DetailPage.PLAN) {
            List<PlanRow> plan = buildPlanRows(menu.getSelectedRecipeInfo());
            int firstRow = Math.min(detailScroll, Math.max(0, plan.size() - 9));
            for (int index = firstRow; index < Math.min(plan.size(), firstRow + 9); index++) {
                int x = leftPos + 12;
                int y = topPos + 70 + (index - firstRow) * 21;
                if (inside(mouseX, mouseY, x, y, 18, 18) && !plan.get(index).stack.isEmpty()) {
                    return Optional.of(new VirtualIngredient(plan.get(index).stack.copyWithCount(1),
                            new Rect2i(x, y, 18, 18)));
                }
            }
            return Optional.empty();
        }
        if (detailPage != DetailPage.NONE) return Optional.empty();
        List<Integer> filtered = filteredIndices();
        int first = scrollRow * COLUMNS;
        for (int local = 0; local < visibleCount() && first + local < filtered.size(); local++) {
            int sourceIndex = filtered.get(first + local);
            int x = leftPos + GRID_X + (local % COLUMNS) * 18;
            int y = topPos + GRID_Y + (local / COLUMNS) * 18;
            if (inside(mouseX, mouseY, x, y, 18, 18)) {
                if (storageTab && sourceIndex < 0) return Optional.empty();
                ItemStack stack = storageTab ? menu.getStoredEntries().get(sourceIndex).stack()
                        : menu.getCraftableOutputs().get(sourceIndex);
                return Optional.of(new VirtualIngredient(stack.copyWithCount(1), new Rect2i(x, y, 18, 18)));
            }
        }
        return Optional.empty();
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {}

    private enum SortMode {
        NAME("A"), COUNT("#"), MOD("M");
        private final String label;
        SortMode(String label) { this.label = label; }
        private SortMode next() { return values()[(ordinal() + 1) % values().length]; }
    }

    private enum DetailPage { NONE, PLAN, STATUS, GUIDE_CHAIN }

    private enum GuideChainStatus {
        NEW("screen.ars_arcane_matrix.order_terminal.guide_chain.status.new", 0xA8E6B0),
        EXISTING("screen.ars_arcane_matrix.order_terminal.guide_chain.status.existing", 0x80C8FF),
        BASE("screen.ars_arcane_matrix.order_terminal.guide_chain.status.base", 0xD8C8A0),
        MISSING("screen.ars_arcane_matrix.order_terminal.guide_chain.status.missing", 0xFF7777);

        private final String translationKey;
        private final int color;

        GuideChainStatus(String translationKey, int color) {
            this.translationKey = translationKey;
            this.color = color;
        }
    }

    private record GuideChainRow(
            ItemStack stack,
            ResourceLocation recipeId,
            ResourceLocation workstation,
            int depth,
            GuideChainStatus status
    ) {}

    private static final class PlanRow {
        private final ItemStack stack;
        private final Fluid fluid;
        private int required;
        private int available;
        private int craftable;
        private int missing;

        private PlanRow(ItemStack stack, Fluid fluid,
                        int required, int available, int craftable, int missing) {
            this.stack = stack;
            this.fluid = fluid;
            this.required = required;
            this.available = available;
            this.craftable = craftable;
            this.missing = missing;
        }

        private Component displayName() {
            return fluid == Fluids.EMPTY ? stack.getHoverName()
                    : new FluidStack(fluid, 1).getHoverName();
        }
    }

    private static final class AvailableFluidAmount {
        private final Fluid fluid;
        private int amount;

        private AvailableFluidAmount(Fluid fluid, int amount) {
            this.fluid = fluid;
            this.amount = amount;
        }
    }

    private static final class AvailableAmount {
        private final ItemStack stack;
        private int amount;

        private AvailableAmount(ItemStack stack, int amount) {
            this.stack = stack;
            this.amount = amount;
        }
    }

    public record VirtualIngredient(ItemStack stack, Rect2i area) {}
}
