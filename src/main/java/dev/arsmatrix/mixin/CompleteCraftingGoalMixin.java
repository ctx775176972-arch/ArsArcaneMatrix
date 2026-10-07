package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.api.recipe.PotionCraftingManager;
import com.hollingsworth.arsnouveau.common.block.tile.WixieCauldronTile;
import com.hollingsworth.arsnouveau.common.entity.EntityWixie;
import com.hollingsworth.arsnouveau.common.entity.goal.wixie.CompleteCraftingGoal;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

/**
 * Keeps ordinary Wixie crafting unchanged while removing most of the idle wait
 * at the end of a potion brew. Ingredient and potion collection are still
 * handled by Ars Nouveau, including mixed potions supplied by potion jars.
 */
@Mixin(value = CompleteCraftingGoal.class, remap = false)
public abstract class CompleteCraftingGoalMixin {
    private static final int FAST_POTION_FINISH_TICKS = 5;

    @Shadow
    EntityWixie wixie;

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 40), remap = false)
    private int arsMatrix$shortenPotionFinishDelay(int original) {
        if (wixie == null || wixie.cauldronPos == null) {
            return original;
        }
        BlockEntity blockEntity = wixie.level().getBlockEntity(wixie.cauldronPos);
        if (blockEntity instanceof WixieCauldronTile cauldron
                && cauldron.craftManager instanceof PotionCraftingManager) {
            return FAST_POTION_FINISH_TICKS;
        }
        return original;
    }
}
