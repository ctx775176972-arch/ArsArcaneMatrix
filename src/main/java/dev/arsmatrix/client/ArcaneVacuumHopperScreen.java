package dev.arsmatrix.client;

import dev.arsmatrix.blockentity.ArcaneVacuumHopperBlockEntity;
import dev.arsmatrix.menu.ArcaneVacuumHopperMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class ArcaneVacuumHopperScreen extends AbstractContainerScreen<ArcaneVacuumHopperMenu> {
    private Button itemToggle, xpToggle, filterMode, gemMode, nbtMode, rangeMode;
    private Button itemOutput, destroyMatches;
    private Button collectionTab, outputTab, experienceTab;
    private Tab selectedTab = Tab.COLLECTION;
    public ArcaneVacuumHopperScreen(ArcaneVacuumHopperMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 244;
        imageHeight = 249;
        inventoryLabelX = 38;
        inventoryLabelY = 157;
    }
    @Override protected void init() {
        super.init();
        itemToggle = addButton(8, 20, 70, 16, 0);
        xpToggle = addButton(82, 20, 70, 16, 1);
        filterMode = addButton(156, 20, 80, 16, 2);
        nbtMode = addButton(8, 37, 70, 16, 8);
        rangeMode = addButton(82, 37, 70, 16, 9);
        destroyMatches = addButton(156, 37, 80, 16, 7);

        itemOutput = addButton(8, 20, 228, 16, 4);

        gemMode = addButton(8, 20, 228, 16, 3);

        collectionTab = addTabButton(0,
                "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.collection.button", Tab.COLLECTION);
        outputTab = addTabButton(1,
                "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.output.button", Tab.OUTPUT);
        experienceTab = addTabButton(2,
                "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.experience.button", Tab.EXPERIENCE);
        updateTabVisibility();
    }

    private Button addTabButton(int row, String translationKey, Tab tab) {
        return addRenderableWidget(Button.builder(Component.translatable(translationKey), button -> {
                    selectedTab = tab;
                    updateTabVisibility();
                }).bounds(leftPos - 36, topPos + 20 + row * 21, 34, 19).build());
    }

    private void updateTabVisibility() {
        boolean collection = selectedTab == Tab.COLLECTION;
        itemToggle.visible = collection;
        xpToggle.visible = collection;
        filterMode.visible = collection;
        nbtMode.visible = collection;
        rangeMode.visible = collection;
        destroyMatches.visible = collection;

        boolean output = selectedTab == Tab.OUTPUT;
        itemOutput.visible = output;

        boolean experience = selectedTab == Tab.EXPERIENCE;
        gemMode.visible = experience;

        collectionTab.active = !collection;
        outputTab.active = !output;
        experienceTab.active = !experience;
    }
    private Button addButton(int x, int y, int width, int height, int id) {
        return addRenderableWidget(Button.builder(Component.empty(), button -> click(id))
                .bounds(leftPos + x, topPos + y, width, height).build());
    }
    private void click(int id) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        graphics.fill(leftPos + 5, topPos + 16, leftPos + 239, topPos + 159, 0xD02A1D42);
        graphics.fill(leftPos + 35, topPos + 164, leftPos + 205, topPos + 245, 0xD0201730);
        for (int column = 0; column < 9; column++) drawSlot(graphics, leftPos + 37 + column * 18, topPos + 70);
        for (int row = 0; row < 2; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, leftPos + 37 + column * 18, topPos + 103 + row * 18);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, leftPos + 37 + column * 18, topPos + 167 + row * 18);
        for (int column = 0; column < 9; column++) drawSlot(graphics, leftPos + 37 + column * 18, topPos + 225);
    }
    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
    }
    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateButtons();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
    private void updateButtons() {
        itemToggle.setMessage(toggle("items", menu.collectsItems()));
        xpToggle.setMessage(toggle("experience", menu.collectsExperience()));
        filterMode.setMessage(mode("filter", ArcaneVacuumHopperBlockEntity.FilterMode.values()[
                Math.floorMod(menu.filterMode(), ArcaneVacuumHopperBlockEntity.FilterMode.values().length)].name()));
        gemMode.setMessage(mode("gems", ArcaneVacuumHopperBlockEntity.GemMode.values()[
                Math.floorMod(menu.gemMode(), ArcaneVacuumHopperBlockEntity.GemMode.values().length)].name()));
        nbtMode.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.nbt",
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.nbt."
                        + (menu.strictComponents() ? "strict" : "ignore"))));
        ArcaneVacuumHopperBlockEntity.RangeMode selectedRange = ArcaneVacuumHopperBlockEntity.RangeMode.values()[
                Math.floorMod(menu.rangeMode(), ArcaneVacuumHopperBlockEntity.RangeMode.values().length)];
        rangeMode.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.range",
                Component.translatable(selectedRange.translationKey())));
        itemOutput.setMessage(mode("item_output", ArcaneVacuumHopperBlockEntity.OutputMode.values()[
                Math.floorMod(menu.itemOutputMode(), ArcaneVacuumHopperBlockEntity.OutputMode.values().length)].name()));
        destroyMatches.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.destroy",
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper."
                        + (menu.destroysMatches() ? "on" : "off"))));
    }
    private static Component toggle(String key, boolean active) {
        return Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper." + key,
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper." + (active ? "on" : "off")));
    }
    private static Component mode(String key, String value) {
        return Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper." + key,
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.mode." + value.toLowerCase()));
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0xE8D9FF, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.xp",
                menu.experience(), ArcaneVacuumHopperBlockEntity.MAX_EXPERIENCE), 82, 91, 0xD8B8FF, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.filters"), 8, 74, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.buffer"), 8, 107, 0xCBBCE3, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xCBBCE3, false);
    }

    @Override
    protected void renderTooltip(GuiGraphics graphics, int mouseX, int mouseY) {
        super.renderTooltip(graphics, mouseX, mouseY);
        if (collectionTab != null && collectionTab.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.collection"), mouseX, mouseY);
        } else if (outputTab != null && outputTab.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.output"), mouseX, mouseY);
        } else if (experienceTab != null && experienceTab.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.arcane_vacuum_hopper.tab.experience"), mouseX, mouseY);
        }
    }

    private enum Tab { COLLECTION, OUTPUT, EXPERIENCE }
}
