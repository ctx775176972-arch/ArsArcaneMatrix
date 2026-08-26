package dev.arsmatrix.client;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.blockentity.WixieOrderTerminalBlockEntity.CraftableRecipeInfo;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

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
    private static final int SOURCE_BAR_X = 1;
    private static final int SOURCE_BAR_Y = 54;
    private static final int SOURCE_BAR_HEIGHT = 108;

    private int scrollRow;
    private int storageRows = 6;
    private int expandedStorageRows = 6;
    private boolean storageTab;
    private boolean craftingExpanded = true;
    private boolean draggingScrollbar;
    private SortMode sortMode = SortMode.NAME;
    private EditBox search;
    private EditBox countInput;
    private Button storageModeButton;
    private Button craftingModeButton;
    private Button sortButton;
    private Button foldButton;
    private Button patternManagementButton;
    private Button matchModeButton;
    private final List<Button> orderControls = new ArrayList<>();
    private final List<Button> storageControls = new ArrayList<>();

    public WixieOrderTerminalScreen(WixieOrderTerminalMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 205;
        imageHeight = 322;
        storageTab = menu.isAdvancedStorage();
    }

    @Override
    protected void init() {
        super.init();
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
        if (menu.isAdvancedStorage()) {
            patternManagementButton = addStorageControl(Button.builder(Component.literal("G"), button ->
                            sendMenuButton(WixieOrderTerminalMenu.BUTTON_MANAGE_PATTERNS))
                    .bounds(leftPos + 177, topPos + 126, 24, 14).build());
        }

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
        updateControlVisibility();
    }

    private void setMode(boolean showStorage) {
        if (storageTab == showStorage) return;
        storageTab = showStorage;
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
        orderControls.forEach(button -> button.visible = !storageTab);
        storageControls.forEach(button -> button.visible = storageTab);
        if (countInput != null) countInput.setVisible(!storageTab);
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
        sendMenuButton(WixieOrderTerminalMenu.BUTTON_SUBMIT);
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
            for (int tank = 0; tank < 3; tank++) {
                int fluidType = menu.getLinkedFluidType(tank);
                int amount = menu.getLinkedFluidAmount(tank);
                if (fluidType >= 0 && amount > 0 && matchesFluid(fluidType, query)) result.add(-tank - 1);
            }
            Comparator<Integer> byName = Comparator.comparing(this::storageName);
            Comparator<Integer> comparator = switch (sortMode) {
                case NAME -> byName;
                case COUNT -> Comparator.<Integer>comparingInt(this::storageAmount)
                        .reversed().thenComparing(byName);
                case MOD -> Comparator.<Integer, String>comparing(this::storageNamespace)
                        .thenComparing(byName);
            };
            result.sort(comparator);
        } else {
            List<ItemStack> outputs = menu.getCraftableOutputs();
            for (int index = 0; index < outputs.size(); index++) if (matches(outputs.get(index), query)) result.add(index);
        }
        return result;
    }

    private static boolean matches(ItemStack stack, String query) {
        return query.isEmpty()
                || stack.getHoverName().getString().toLowerCase(Locale.ROOT).contains(query)
                || BuiltInRegistries.ITEM.getKey(stack.getItem()).toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private boolean matchesFluid(int registryId, String query) {
        Fluid fluid = BuiltInRegistries.FLUID.byId(registryId);
        if (fluid == null || fluid == Fluids.EMPTY) return false;
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid);
        String name = new FluidStack(fluid, 1).getHoverName().getString().toLowerCase(Locale.ROOT);
        return query.isEmpty() || name.contains(query) || id.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private String storageName(int index) {
        if (index >= 0) return menu.getStoredEntries().get(index).stack().getHoverName()
                .getString().toLowerCase(Locale.ROOT);
        Fluid fluid = fluidForIndex(index);
        return fluid == Fluids.EMPTY ? "" : new FluidStack(fluid, 1).getHoverName()
                .getString().toLowerCase(Locale.ROOT);
    }

    private int storageAmount(int index) {
        return index >= 0 ? menu.getStoredEntries().get(index).count()
                : menu.getLinkedFluidAmount(-index - 1);
    }

    private String storageNamespace(int index) {
        return index >= 0
                ? BuiltInRegistries.ITEM.getKey(menu.getStoredEntries().get(index).stack().getItem()).getNamespace()
                : BuiltInRegistries.FLUID.getKey(fluidForIndex(index)).getNamespace();
    }

    private Fluid fluidForIndex(int index) {
        if (index >= 0) return Fluids.EMPTY;
        Fluid fluid = BuiltInRegistries.FLUID.byId(menu.getLinkedFluidType(-index - 1));
        return fluid == null ? Fluids.EMPTY : fluid;
    }

    private int rows() { return storageTab ? storageRows : ORDER_ROWS; }
    private int visibleCount() { return COLUMNS * rows(); }
    private int totalRows() { return (filteredIndices().size() + COLUMNS - 1) / COLUMNS; }
    private int maxScrollRow() { return Math.max(0, totalRows() - rows()); }
    private void clampScroll() { scrollRow = Math.max(0, Math.min(maxScrollRow(), scrollRow)); }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        if (menu.isAdvancedStorage()) drawSourceBar(graphics);
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

    private void drawSourceBar(GuiGraphics graphics) {
        int x = leftPos + SOURCE_BAR_X;
        int y = topPos + SOURCE_BAR_Y;
        graphics.fill(x, y, x + 4, y + SOURCE_BAR_HEIGHT, 0xFF080610);
        long capacity = menu.getNetworkSourceCapacity();
        long stored = menu.getNetworkSource();
        int filled = capacity <= 0L ? 0 : (int) Math.min(SOURCE_BAR_HEIGHT,
                stored * SOURCE_BAR_HEIGHT / capacity);
        if (filled > 0) graphics.fill(x + 1, y + SOURCE_BAR_HEIGHT - filled,
                x + 3, y + SOURCE_BAR_HEIGHT, 0xFF8A4DFF);
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
            if (storageTab && sourceIndex < 0) drawFluidCell(graphics, fluidForIndex(sourceIndex), x, y);
            else graphics.renderItem(stack, x, y);
            if (!storageTab) drawRecipeTypeBadge(graphics, menu.getCraftableRecipeInfo(sourceIndex), x, y);
            if (storageTab) {
                String count = sourceIndex < 0
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
                                Component.translatable(selectedInfo.cooking()
                                        ? "screen.ars_arcane_matrix.order_terminal.workstation.furnace"
                                        : "screen.ars_arcane_matrix.order_terminal.workstation.crafting"),
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
        if (patternManagementButton != null && patternManagementButton.visible
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
        }
        if (storageTab && inside(mouseX, mouseY, leftPos + DEPOSIT_X - 1,
                topPos + DEPOSIT_Y - 1, 18, 18) && hoveredSourceIndex < 0 && hoveredMissing.isEmpty()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.deposit"), mouseX, mouseY);
        } else if (menu.isAdvancedStorage() && inside(mouseX, mouseY,
                leftPos + SOURCE_BAR_X, topPos + SOURCE_BAR_Y, 4, SOURCE_BAR_HEIGHT)) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.advanced_storage_lectern.source_network",
                    menu.getNetworkSource(), menu.getNetworkSourceCapacity(),
                    menu.getNetworkSourceJars(), menu.getNetworkSourceRelays()), mouseX, mouseY);
        } else if (!hoveredMissing.isEmpty()) graphics.renderTooltip(font, hoveredMissing, mouseX, mouseY);
        else if (hoveredSourceIndex >= 0) {
            ItemStack hovered = storageTab ? menu.getStoredEntries().get(hoveredSourceIndex).stack()
                    : menu.getCraftableOutputs().get(hoveredSourceIndex);
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        } else if (hoveredSourceIndex != Integer.MIN_VALUE && hoveredSourceIndex < 0) {
            int tank = -hoveredSourceIndex - 1;
            Fluid fluid = fluidForIndex(hoveredSourceIndex);
            int amount = menu.getLinkedFluidAmount(tank);
            graphics.renderTooltip(font, List.of(
                    new FluidStack(fluid, 1).getHoverName(),
                    Component.translatable("screen.ars_arcane_matrix.advanced_storage_lectern.fluid_amount",
                            amount, formatBuckets(amount))), Optional.empty(), ItemStack.EMPTY, mouseX, mouseY);
        }
    }

    private static void drawRecipeTypeBadge(
            GuiGraphics graphics, CraftableRecipeInfo info, int x, int y
    ) {
        if (info == null) return;
        ItemStack workstation = new ItemStack(info.cooking()
                ? ModBlocks.SOURCE_STONE_FURNACE.get() : Items.CRAFTING_TABLE);
        graphics.pose().pushPose();
        graphics.pose().translate(x + 1.0F, y + 1.0F, 250.0F);
        graphics.pose().scale(0.5F, 0.5F, 1.0F);
        graphics.renderItem(workstation, 0, 0);
        graphics.pose().popPose();
    }

    private static String compactCount(int count) {
        if (count >= 1_000_000) return (count / 1_000_000) + "m";
        if (count >= 1_000) return (count / 1_000) + "k";
        return Integer.toString(count);
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
        if (fluid == null || fluid == Fluids.EMPTY) return;
        FluidStack stack = new FluidStack(fluid, 1);
        IClientFluidTypeExtensions properties = IClientFluidTypeExtensions.of(fluid);
        ResourceLocation texture = properties.getStillTexture(stack);
        if (texture == null) return;
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
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
                    if (sourceIndex >= 0) sendMenuButton((button == 1
                            ? WixieOrderTerminalMenu.BUTTON_STORAGE_ONE_OFFSET
                            : WixieOrderTerminalMenu.BUTTON_STORAGE_STACK_OFFSET) + sourceIndex);
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
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (draggingScrollbar) {
            draggingScrollbar = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
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

    public record VirtualIngredient(ItemStack stack, Rect2i area) {}
}
