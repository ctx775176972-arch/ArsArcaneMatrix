package dev.arsmatrix.mixin.client;

import com.hollingsworth.arsnouveau.api.ritual.AbstractRitual;
import com.hollingsworth.arsnouveau.common.items.RitualTablet;
import dev.arsmatrix.ritual.ChunkExcavationRitual;
import dev.arsmatrix.ritual.RareCreatureSummoningRitual;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** Keeps the Forgotten Call tablet concise while its full explanation remains in documentation. */
@Mixin(RitualTablet.class)
public abstract class RitualTabletTooltipMixin {
    @Redirect(
            method = "appendHoverText",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/api/ritual/AbstractRitual;getDescriptionKey()Ljava/lang/String;"
            )
    )
    private String arsmatrix$useConciseTooltip(AbstractRitual ritual) {
        if (RareCreatureSummoningRitual.ID.equals(ritual.getRegistryName())) {
            return "tooltip.ars_arcane_matrix.rare_creature_summoning";
        }
        if (ChunkExcavationRitual.ID.equals(ritual.getRegistryName())) {
            return "tooltip.ars_arcane_matrix.chunk_excavation";
        }
        return ritual.getDescriptionKey();
    }
}
