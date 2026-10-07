package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.PotionMelderTile;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Speeds up the native potion melder without changing its recipes, fluid
 * amounts, Source cost, duplicate-effect checks, or jar connections.
 */
@Mixin(value = PotionMelderTile.class, remap = false)
public abstract class PotionMelderTileMixin {
    private static final int FAST_MIX_TICKS = 40;
    private static final int FAST_GLOW_TICKS = 20;

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 160), remap = false)
    private int arsMatrix$shortenPotionMix(int original) {
        return FAST_MIX_TICKS;
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 120), remap = false)
    private int arsMatrix$startPotionGlowEarlier(int original) {
        return FAST_GLOW_TICKS;
    }
}
