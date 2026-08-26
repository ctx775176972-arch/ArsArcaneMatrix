package dev.arsmatrix.client;

import dev.arsmatrix.menu.AutomaticStockRequesterMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

public final class AutomaticStockRequesterScreen
        extends AbstractContainerScreen<AutomaticStockRequesterMenu> {
    private Button notificationsButton;
    public AutomaticStockRequesterScreen(
            AutomaticStockRequesterMenu menu, Inventory inventory, Component title
    ) {
        super(menu, inventory, title);
        imageWidth = 230;
        imageHeight = 225;
        inventoryLabelX = 34;
        inventoryLabelY = 131;
    }

    @Override
    protected void init() {
        super.init();
        addAdjustRow(80, true);
        addAdjustRow(109, false);
        notificationsButton = addRenderableWidget(Button.builder(notificationLabel(), button ->
                        send(AutomaticStockRequesterMenu.TOGGLE_NOTIFICATIONS))
                .bounds(leftPos + 142, topPos + 5, 80, 16).build());
    }

    @Override
    protected void containerTick() {
        super.containerTick();
        if (notificationsButton != null) notificationsButton.setMessage(notificationLabel());
    }

    private Component notificationLabel() {
        return Component.translatable(menu.isNotificationsEnabled()
                ? "screen.ars_arcane_matrix.stock_requester.notifications_on"
                : "screen.ars_arcane_matrix.stock_requester.notifications_off");
    }

    private void addAdjustRow(int y, boolean minimum) {
        int first = minimum
                ? AutomaticStockRequesterMenu.MINUS_MINIMUM_16
                : AutomaticStockRequesterMenu.MINUS_REQUEST_16;
        String[] labels = {"-16", "-1", "+1", "+16"};
        for (int index = 0; index < 4; index++) {
            final int id = first + index;
            addRenderableWidget(Button.builder(Component.literal(labels[index]), button -> send(id))
                    .bounds(leftPos + 112 + index * 27, topPos + y, 25, 14).build());
        }
    }

    private void send(int id) {
        if (minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, id);
        }
    }

    @Override
    protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xED181027);
        graphics.fill(leftPos + 5, topPos + 24, leftPos + 225, topPos + 127, 0xD02A1D42);
        graphics.fill(leftPos + 31, topPos + 139, leftPos + 199, topPos + 220, 0xD0201730);
        drawSlot(graphics, leftPos + 12, topPos + 27);
        drawSlot(graphics, leftPos + 204, topPos + 27);
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                drawSlot(graphics, leftPos + 34 + column * 18, topPos + 142 + row * 18);
            }
        }
        for (int column = 0; column < 9; column++) {
            drawSlot(graphics, leftPos + 34 + column * 18, topPos + 200);
        }
        ItemStack target = menu.getTarget();
        if (!target.isEmpty()) graphics.renderFakeItem(target, leftPos + 13, topPos + 28);
    }

    private static void drawSlot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x, y, x + 18, y + 18, 0xFF080610);
        graphics.fill(x + 1, y + 1, x + 17, y + 17, 0xE0201830);
        graphics.fill(x + 1, y + 1, x + 17, y + 2, 0xFF604878);
        graphics.fill(x + 1, y + 16, x + 17, y + 17, 0xFF100C18);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics, mouseX, mouseY, partialTick);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }

    @Override
    protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, font.split(title, 128).getFirst(),
                titleLabelX, titleLabelY, 0xE8D9FF, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.stock_requester.tier",
                menu.getUpgradeTier(), menu.getAmountLimit()), 12, 18, 0xA98CC8, false);
        ItemStack target = menu.getTarget();
        Component targetLabel = Component.translatable(
                "screen.ars_arcane_matrix.stock_requester.target",
                target.isEmpty()
                        ? Component.translatable("message.ars_arcane_matrix.stock_requester.state.no_target")
                        : target.getHoverName());
        graphics.drawString(font, font.split(targetLabel, 160).getFirst(),
                36, 32, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable(
                menu.getOperatingState().translationKey()), 12, 53, 0xCBBCE3, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.stock_requester.minimum",
                menu.getMinimumStock(), menu.getCurrentStock()), 12, 69, 0xE8D9FF, false);
        graphics.drawString(font, Component.translatable(
                "screen.ars_arcane_matrix.stock_requester.request",
                menu.getRequestAmount()), 12, 98, 0xE8D9FF, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY,
                0xCBBCE3, false);
    }
}
