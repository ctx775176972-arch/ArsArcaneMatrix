package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import com.hollingsworth.arsnouveau.common.entity.goal.amethyst_golem.HarvestClusterGoal;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(value = HarvestClusterGoal.class, remap = false)
public abstract class HarvestClusterGoalMixin {
    @Shadow public AmethystGolem golem;

    @ModifyArg(
            method = "harvest",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
                    remap = false
            ),
            index = 5,
            remap = false
    )
    private ItemStack arsMatrix$usePedestalTool(ItemStack original) {
        return AmethystGolemEnhancements.simulatedHarvestTool(golem, original);
    }
}
