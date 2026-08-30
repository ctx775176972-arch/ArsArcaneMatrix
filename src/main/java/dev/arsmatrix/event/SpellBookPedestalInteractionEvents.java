package dev.arsmatrix.event;

import com.hollingsworth.arsnouveau.common.block.ArcanePedestal;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Lets a held spell book cast at an Arcane Pedestal instead of being inserted into it. */
public final class SpellBookPedestalInteractionEvents {
    private SpellBookPedestalInteractionEvents() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getItemStack().getItem() instanceof SpellBook)
                || !(event.getLevel().getBlockState(event.getPos()).getBlock() instanceof ArcanePedestal)) {
            return;
        }

        // Suppress only the pedestal's item placement interaction. The spell book must
        // still receive the click so Touch -> Break and other targeted spells can cast.
        event.setUseBlock(TriState.FALSE);
        event.setUseItem(TriState.TRUE);
    }
}
