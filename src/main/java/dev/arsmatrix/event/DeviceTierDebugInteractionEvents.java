package dev.arsmatrix.event;

import dev.arsmatrix.item.DeviceTierDebugToolItem;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Gives the tier debug tool exclusive ownership of block right-clicks. */
public final class DeviceTierDebugInteractionEvents {
    private DeviceTierDebugInteractionEvents() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof DeviceTierDebugToolItem tool)) return;

        // Cancel before any block-specific interaction can run. This prevents request displays
        // from selecting the tool as a target and prevents pedestals or GUIs from responding.
        event.setCanceled(true);
        InteractionResult result = tool.useOn(new UseOnContext(
                event.getEntity(), event.getHand(), event.getHitVec()));
        event.setCancellationResult(result.consumesAction()
                ? result
                : InteractionResult.sidedSuccess(event.getLevel().isClientSide));
    }
}
