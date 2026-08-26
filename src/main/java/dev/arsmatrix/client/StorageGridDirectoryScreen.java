package dev.arsmatrix.client;

import dev.arsmatrix.menu.StorageGridDirectoryMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class StorageGridDirectoryScreen extends AbstractContainerScreen<StorageGridDirectoryMenu> {
    public StorageGridDirectoryScreen(StorageGridDirectoryMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 205;
        imageHeight = 182;
        inventoryLabelX = 22;
        inventoryLabelY = 89;
    }

    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xED181027);
        graphics.fill(leftPos + 7, topPos + 17, leftPos + 198, topPos + 84, 0xD02A1D42);
        graphics.fill(leftPos + 19, topPos + 96, leftPos + 186, topPos + 177, 0xD0201730);
        for (int slot = 0; slot < 4; slot++) drawSlot(graphics, leftPos + 62 + slot * 20, topPos + 36);
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            drawSlot(graphics, leftPos + 21 + column * 18, topPos + 99 + row * 18);
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, leftPos + 21 + column * 18, topPos + 157);
        }
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFF100C18);
    }

    @Override public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 8, 5, 0xE8D9FF, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.storage_grid_directory.types",
                menu.getStoredTypes(), menu.getTypeCapacity()), 12, 59, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.storage_grid_directory.items",
                menu.getStoredItems(), menu.getItemCapacity()), 12, 71, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.storage_grid_directory.upgrades"), 12, 23, 0xD8B8FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xCBBCE3, false);
    }
}
