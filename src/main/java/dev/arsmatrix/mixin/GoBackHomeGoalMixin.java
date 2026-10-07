package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import com.hollingsworth.arsnouveau.common.entity.goal.GoBackHomeGoal;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import net.minecraft.world.entity.Mob;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents tool-mode amethyst golems from wasting time returning to their home block. */
@Mixin(value = GoBackHomeGoal.class, remap = false)
public abstract class GoBackHomeGoalMixin {
    @Shadow Mob entity;

    @Inject(method = "canUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsMatrix$skipReturnHomeInToolMode(CallbackInfoReturnable<Boolean> callback) {
        if (arsMatrix$isToolModeActive()) {
            callback.setReturnValue(false);
        }
    }

    @Inject(method = "canContinueToUse", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsMatrix$stopReturningHomeInToolMode(CallbackInfoReturnable<Boolean> callback) {
        if (arsMatrix$isToolModeActive()) {
            entity.getNavigation().stop();
            callback.setReturnValue(false);
        }
    }

    private boolean arsMatrix$isToolModeActive() {
        return entity instanceof AmethystGolem golem
                && AmethystGolemEnhancements.isToolModeActive(golem);
    }
}
