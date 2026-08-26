package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import com.hollingsworth.arsnouveau.common.entity.goal.amethyst_golem.HarvestClusterGoal;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;

@Mixin(value = HarvestClusterGoal.class, remap = false)
public abstract class HarvestClusterGoalTimingMixin {
    @Shadow public AmethystGolem golem;

    @ModifyConstant(method = "start", constant = @Constant(intValue = 130), remap = false)
    private int arsMatrix$shortenHarvestAction(int originalTicks) {
        return AmethystGolemEnhancements.acceleratedActionTicks(golem, originalTicks);
    }
}
