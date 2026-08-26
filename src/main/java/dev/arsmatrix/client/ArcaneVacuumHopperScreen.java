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
    private Button itemOutput, gemOutput, bindChannel, destroyMatches;
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
        gemMode = addButton(8, 37, 70, 16, 3);
        nbtMode = addButton(82, 37, 70, 16, 8);
        rangeMode = addButton(156, 37, 80, 16, 9);
        itemOutput = addButton(8, 54, 70, 16, 4);
        gemOutput = addButton(82, 54, 70, 16, 5);
        destroyMatches = addButton(156, 54, 80, 16, 7);
        bindChannel = addButton(8, 139, 95, 16, 6);
        int[] values = {3, 12, 120, 768};
        for (int i = 0; i < values.length; i++) {
            int value = values[i];
            int id = 20 + i;
            addRenderableWidget(Button.builder(Component.literal("+" + value), button -> click(id))
                    .bounds(leftPos + 106 + i * 28, topPos + 139, 26, 16).build());
        }
        addRenderableWidget(Button.builder(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.all"),
                button -> click(24)).bounds(leftPos + 218, topPos + 139, 20, 16).build());
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
        for (int slot = 0; slot < 2; slot++) drawSlot(graphics, leftPos + 208, topPos + 103 + slot * 18);
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
        gemOutput.setMessage(mode("gem_output", ArcaneVacuumHopperBlockEntity.OutputMode.values()[
                Math.floorMod(menu.gemOutputMode(), ArcaneVacuumHopperBlockEntity.OutputMode.values().length)].name()));
        destroyMatches.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.destroy",
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper."
                        + (menu.destroysMatches() ? "on" : "off"))));
        ArcaneVacuumHopperBlockEntity.BindChannel channel = ArcaneVacuumHopperBlockEntity.BindChannel.values()[
                Math.floorMod(menu.bindChannel(), ArcaneVacuumHopperBlockEntity.BindChannel.values().length)];
        bindChannel.setMessage(Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.bind",
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.channel." + channel.name().toLowerCase())));
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
}
