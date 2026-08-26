package dev.arsmatrix.spell;

import com.hollingsworth.arsnouveau.api.registry.GlyphRegistry;

/** Ars Nouveau spell-part registrations owned by Ars Arcane Matrix. */
public final class ModGlyphs {
    private ModGlyphs() {
    }

    public static void register() {
        GlyphRegistry.registerSpell(EffectCapture.INSTANCE);
    }
}
