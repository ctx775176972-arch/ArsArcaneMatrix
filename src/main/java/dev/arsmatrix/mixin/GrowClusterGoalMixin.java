package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import com.hollingsworth.arsnouveau.common.entity.goal.amethyst_golem.GrowClusterGoal;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.Redirect;

import java.util.List;

@Mixin(value = GrowClusterGoal.class, remap = false)
public abstract class GrowClusterGoalMixin {
    @Shadow public AmethystGolem golem;

    @ModifyConstant(method = "start", constant = @Constant(intValue = 120), remap = false)
    private int arsMatrix$shortenGrowthAction(int originalTicks) {
        return AmethystGolemEnhancements.acceleratedActionTicks(golem, originalTicks);
    }

    @Redirect(
            method = "start",
            at = @At(
                    value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/common/util/ArrayUtil;getRandomElement(Ljava/util/List;)Ljava/lang/Object;"
            ),
            remap = false
    )
    private Object arsMatrix$selectPersistentGrowthBatch(List<?> ignored) {
        return AmethystGolemEnhancements.prepareGrowthBatch(golem);
    }

    @ModifyConstant(method = "growCluster", constant = @Constant(intValue = 300), remap = false)
    private int arsMatrix$staggerGrowthCooldown(int originalTicks) {
        return AmethystGolemEnhancements.staggeredGrowthCooldown(golem, originalTicks);
    }

    @Redirect(
            method = "growCluster",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/state/BlockState;randomTick(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/util/RandomSource;)V",
                    remap = false
            ),
            remap = false
    )
    private void arsMatrix$repeatGrowthAttempts(
            BlockState state, ServerLevel level, BlockPos pos, RandomSource random
    ) {
        int attempts = 1 + AmethystGolemEnhancements.efficiencyLevel(golem);
        for (int attempt = 0; attempt < attempts; attempt++) {
            state.randomTick(level, pos, random);
        }
    }
}
