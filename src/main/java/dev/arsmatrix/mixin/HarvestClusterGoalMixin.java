package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import com.hollingsworth.arsnouveau.common.entity.goal.amethyst_golem.HarvestClusterGoal;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import dev.arsmatrix.compat.arsnouveau.AmethystGolemEnhancements;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(value = HarvestClusterGoal.class, remap = false)
public abstract class HarvestClusterGoalMixin {
    @Shadow public AmethystGolem golem;

    @Redirect(
            method = "harvest",
            at = @At(
                    value = "INVOKE",
                    target = "Lnet/minecraft/world/level/block/Block;playerDestroy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/item/ItemStack;)V",
                    remap = false
            ),
            remap = false
    )
    private void arsMatrix$dropOnlyWhenDestroyAllowed(
            Block block,
            Level level,
            Player player,
            BlockPos pos,
            BlockState state,
            BlockEntity blockEntity,
            ItemStack originalTool
    ) {
        // Ars Nouveau calculates the drops before attempting its protected
        // block removal. If a claim rejects the fake player, that ordering can
        // repeatedly drop the same intact cluster. Match the removal permission
        // before creating any drops so protected clusters cannot be duplicated.
        if (!BlockUtil.destroyRespectsClaim(player, level, pos)) return;
        block.playerDestroy(level, player, pos, state, blockEntity,
                AmethystGolemEnhancements.simulatedHarvestTool(golem, originalTool));
    }
}
