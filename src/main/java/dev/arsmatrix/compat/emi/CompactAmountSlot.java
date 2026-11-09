package dev.arsmatrix.compat.emi;

import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.widget.SlotWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import java.util.Locale;

/** Preserves the real EMI quantity; only the on-slot overlay is abbreviated. */
final class CompactAmountSlot extends SlotWidget {
    CompactAmountSlot(EmiIngredient ingredient, int x, int y) {
        super(ingredient, x, y);
        if (ingredient.getAmount() > 99) appendTooltip(Component.translatable(
                "emi.ars_arcane_matrix.exact_amount", ingredient.getAmount()));
    }

    @Override public void drawStack(GuiGraphics graphics, int mouseX, int mouseY, float delta) {
        if (getStack().getAmount() <= 99) {
            super.drawStack(graphics, mouseX, mouseY, delta);
            return;
        }
        var bounds = getBounds();
        int iconX = bounds.x() + (bounds.width() - 16) / 2;
        int iconY = bounds.y() + (bounds.height() - 16) / 2;
        getStack().render(graphics, iconX, iconY, delta,
                ~EmiIngredient.RENDER_AMOUNT);
        String text = compact(getStack().getAmount());
        var font = Minecraft.getInstance().font;
        float scale = Math.min(1F, 16F / Math.max(1, font.width(text)));
        graphics.pose().pushPose();
        graphics.pose().translate(iconX + 16, iconY + 16 - 8 * scale, 200);
        graphics.pose().scale(scale, scale, 1);
        graphics.drawString(font, text, -font.width(text), 0, 0xFFFFFF, true);
        graphics.pose().popPose();
    }

    private static String compact(long amount) {
        if (amount < 1000) return Long.toString(amount);
        double divisor = amount >= 1_000_000_000L ? 1_000_000_000D
                : amount >= 1_000_000L ? 1_000_000D : 1000D;
        String suffix = amount >= 1_000_000_000L ? "B" : amount >= 1_000_000L ? "M" : "k";
        return String.format(Locale.ROOT, "%.1f", amount / divisor).replace(".0", "") + suffix;
    }
}
