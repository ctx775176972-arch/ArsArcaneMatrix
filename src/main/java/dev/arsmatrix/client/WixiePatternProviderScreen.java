package dev.arsmatrix.client;

import dev.arsmatrix.menu.WixiePatternProviderMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

/** Three rows of persistent, physical encoded guides plus the player inventory. */
public final class WixiePatternProviderScreen extends AbstractContainerScreen<WixiePatternProviderMenu> {
    private Button previousPage;
    private Button nextPage;
    private Button sortName;
    private Button sortWorkstation;

    public WixiePatternProviderScreen(
            WixiePatternProviderMenu menu,
            Inventory inventory,
            Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 168;
        inventoryLabelY = 74;
    }

    @Override
    protected void init() {
        super.init();
        previousPage = addRenderableWidget(Button.builder(Component.literal("‹"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_PREVIOUS_PAGE))
                .bounds(leftPos + 137, topPos + 4, 14, 11).build());
        nextPage = addRenderableWidget(Button.builder(Component.literal("›"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_NEXT_PAGE))
                .bounds(leftPos + 154, topPos + 4, 14, 11).build());
        sortName = addRenderableWidget(Button.builder(Component.literal("A"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_SORT_NAME))
                .bounds(leftPos + 103, topPos + 4, 14, 11).build());
        sortWorkstation = addRenderableWidget(Button.builder(Component.literal("▣"), button ->
                        sendPageButton(WixiePatternProviderMenu.BUTTON_SORT_WORKSTATION))
                .bounds(leftPos + 120, topPos + 4, 14, 11).build());
        updatePageButtons();
    }

    private void sendPageButton(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    private void updatePageButtons() {
        if (previousPage == null || nextPage == null) return;
        previousPage.visible = menu.getPageCount() > 1;
        nextPage.visible = menu.getPageCount() > 1;
        previousPage.active = menu.getPage() > 0;
        nextPage.active = menu.getPage() + 1 < menu.getPageCount();
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xED181027);
        graphics.fill(leftPos + 5, topPos + 15, leftPos + 171, topPos + 75, 0xD02A1D42);
        graphics.fill(leftPos + 5, topPos + 81, leftPos + 171, topPos + 163, 0xD0201730);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                int x = leftPos + 7 + column * 18;
                int y = topPos + 17 + row * 18;
                drawItemSlot(graphics, x, y);
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawItemSlot(graphics,
                        leftPos + 7 + column * 18,
                        topPos + 84 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawItemSlot(graphics, leftPos + 7 + column * 18, topPos + 142);
        }
    }

    private static void drawItemSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFF100C18);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updatePageButtons();
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        if (sortName != null && sortName.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.pattern_provider.sort_name.tooltip"), mouseX, mouseY);
        } else if (sortWorkstation != null && sortWorkstation.isHovered()) {
            graphics.renderTooltip(font, Component.translatable(
                    "screen.ars_arcane_matrix.pattern_provider.sort_workstation.tooltip"), mouseX, mouseY);
        }
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xE8D9FF, false);
        if (menu.getPageCount() > 1) {
            graphics.drawString(font, Component.literal(
                            (menu.getPage() + 1) + "/" + menu.getPageCount()),
                    112, titleLabelY, 0xCBBCE3, false);
        }
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0xCBBCE3, false);
    }
}
