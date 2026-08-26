package dev.arsmatrix.ritual;

import com.hollingsworth.arsnouveau.api.registry.RitualRegistry;

public final class ModRituals {
    public static final RareCreatureSummoningRitual RARE_CREATURE_SUMMONING =
            new RareCreatureSummoningRitual();

    private ModRituals() {}

    public static void register() {
        RitualRegistry.registerRitual(RARE_CREATURE_SUMMONING);
    }
}
