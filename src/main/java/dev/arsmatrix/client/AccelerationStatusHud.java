package dev.arsmatrix.client;

import dev.arsmatrix.network.AccelerationStatusPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/** Short-lived server snapshot; never queries server-only state from rendering. */
public final class AccelerationStatusHud {
    private static AccelerationStatusPayload status;
    private static long receivedAt;
    private AccelerationStatusHud() {}

    public static void accept(AccelerationStatusPayload value) {
        Minecraft mc = Minecraft.getInstance();
        status = value.multiplier() > 1 && mc.level != null ? value : null;
        receivedAt = mc.level == null ? 0 : mc.level.getGameTime();
    }

    public static void render(RenderGuiEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (status == null || mc.level == null || mc.player == null || mc.screen != null
                || mc.options.hideGui) return;
        long elapsed = mc.level.getGameTime() - receivedAt;
        if (elapsed < 0 || elapsed > 20 || !mc.level.dimension().location().equals(status.dimension())) {
            status = null;
            return;
        }
        if (!(mc.hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK
                || !hit.getBlockPos().equals(status.pos())) return;
        long ticks = status.remainingTicks() - elapsed;
        if (ticks <= 0) return;
        var text = Component.translatable("hud.ars_arcane_matrix.acceleration",
                status.multiplier(), (ticks + 19) / 20);
        int x = (event.getGuiGraphics().guiWidth() - mc.font.width(text)) / 2;
        int y = event.getGuiGraphics().guiHeight() / 2 + 18;
        event.getGuiGraphics().fill(x - 4, y - 3, x + mc.font.width(text) + 4, y + 12, 0x90000000);
        event.getGuiGraphics().drawString(mc.font, text, x, y, 0xD6B4FF, true);
    }
}
