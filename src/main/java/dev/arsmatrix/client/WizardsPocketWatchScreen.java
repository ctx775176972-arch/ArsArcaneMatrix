package dev.arsmatrix.client;

import dev.arsmatrix.item.WizardsPocketWatchItem;
import dev.arsmatrix.menu.WizardsPocketWatchMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;

public final class WizardsPocketWatchScreen extends AbstractContainerScreen<WizardsPocketWatchMenu> {
    private EditBox seconds;
    private Button toggle;
    private Button apply;
    private boolean initialized;

    public WizardsPocketWatchScreen(WizardsPocketWatchMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 250;
        imageHeight = 202;
    }
    private static Component text(String key, Object... args) {
        return Component.translatable("gui.ars_arcane_matrix.watch." + key, args);
    }
    @Override protected void init() {
        super.init();
        initialized = false;
        seconds = new EditBox(font, leftPos + 62, topPos + 120, 64, 20, text("seconds"));
        seconds.setMaxLength(4);
        seconds.setFilter(s -> s.matches("[0-9]*"));
        seconds.setValue(Integer.toString(menu.interval() > 0 ? menu.interval() : 31));
        addRenderableWidget(seconds);
        addRenderableWidget(Button.builder(Component.literal("−"), b -> adjust(-1))
                .bounds(leftPos + 34, topPos + 120, 24, 20).build());
        addRenderableWidget(Button.builder(Component.literal("+"), b -> adjust(1))
                .bounds(leftPos + 130, topPos + 120, 24, 20).build());
        apply = addRenderableWidget(Button.builder(text("apply"), b -> send(100 + value()))
                .bounds(leftPos + 170, topPos + 120, 64, 20).build());
        toggle = addRenderableWidget(Button.builder(text(menu.enabled() ? "enabled" : "paused"), b -> send(1))
                .bounds(leftPos + 16, topPos + 170, 106, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.done"), b -> onClose())
                .bounds(leftPos + 128, topPos + 170, 106, 20).build());
    }
    private int value() {
        try { return Integer.parseInt(seconds.getValue()); }
        catch (NumberFormatException ignored) { return 0; }
    }
    private void adjust(int delta) {
        seconds.setValue(Integer.toString(Math.clamp(value() + delta, 5, 3600)));
    }
    private void send(int button) {
        if (minecraft != null && minecraft.gameMode != null)
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, button);
    }
    @Override protected void containerTick() {
        super.containerTick();
        if (!initialized && menu.interval() > 0) {
            seconds.setValue(Integer.toString(menu.interval()));
            initialized = true;
        }
        toggle.setMessage(text(menu.enabled() ? "enabled" : "paused"));
        apply.active = value() >= 5 && value() <= 3600;
    }
    @Override protected void renderBg(GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        graphics.fill(leftPos, topPos, leftPos + imageWidth, topPos + imageHeight, 0xFF554164);
        graphics.fill(leftPos + 3, topPos + 3, leftPos + imageWidth - 3, topPos + imageHeight - 3, 0xFFE5D5B6);
    }
    @Override protected void renderLabels(GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, 12, 12, 0x352638, false);
        graphics.drawWordWrap(font, text("scribe"), 12, 29, 226, 0x594F43);
        if (menu.watch().getItem() instanceof WizardsPocketWatchItem item) {
            var spell = item.getSpellCaster(menu.watch()).getSpell();
            var lines = font.split(spell.isEmpty() ? text("empty") : Component.literal(spell.getDisplayString()), 226);
            for (int i = 0; i < Math.min(3, lines.size()); i++)
                graphics.drawString(font, lines.get(i), 12, 60 + i * 10, 0x48324E, false);
            graphics.drawString(font, text("cost", spell.getCost()), 12, 94, 0x352638, false);
        }
        graphics.drawString(font, text("seconds"), 12, 108, 0x352638, false);
        graphics.drawString(font, text("interval", menu.interval()), 12, 148, 0x594F43, false);
    }
    @Override public boolean keyPressed(int key, int scan, int modifiers) {
        if (seconds.isFocused() && key != 256) return seconds.keyPressed(key, scan, modifiers);
        return super.keyPressed(key, scan, modifiers);
    }
}
