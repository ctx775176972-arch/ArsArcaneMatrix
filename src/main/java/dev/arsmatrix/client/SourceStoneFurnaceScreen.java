package dev.arsmatrix.client;

import dev.arsmatrix.menu.SourceStoneFurnaceMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class SourceStoneFurnaceScreen extends AbstractContainerScreen<SourceStoneFurnaceMenu> {
    public SourceStoneFurnaceScreen(SourceStoneFurnaceMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 198;
        inventoryLabelX = 8;
        inventoryLabelY = 106;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xF0181028);
        graphics.fill(leftPos + 5, topPos + 17, leftPos + 171, topPos + 105, 0xD02A1D42);
        drawSlot(graphics, leftPos + 79, topPos + 26);
        drawSlot(graphics, leftPos + 79, topPos + 74);
        int filled = menu.progress() * 30 / menu.maxProgress();
        graphics.fill(leftPos + 86, topPos + 47, leftPos + 90, topPos + 72, 0xFF080610);
        if (filled > 0) graphics.fill(leftPos + 86, topPos + 47,
                leftPos + 90, topPos + 47 + filled * 25 / 30, 0xFFB887FF);
        graphics.drawCenteredString(font, Component.literal("▼"), leftPos + 88, topPos + 56, 0xE8D9FF);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            drawSlot(graphics, leftPos + 7 + column * 18, topPos + 115 + row * 18);
        for (int column = 0; column < 9; column++)
            drawSlot(graphics, leftPos + 7 + column * 18, topPos + 173);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0xE8D9FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xCBBCE3, false);
    }
}
