package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.ArcanePedestal;
import com.hollingsworth.arsnouveau.common.items.SpellBook;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.ItemInteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Prevents an equipped Ars Nouveau spell book from being placed on a pedestal by mistake. */
@Mixin(value = ArcanePedestal.class, remap = false)
public abstract class ArcanePedestalSpellBookMixin {

    @Inject(method = "useItemOn", at = @At("HEAD"), cancellable = true)
    private void arsMatrix$rejectSpellBooks(ItemStack stack, BlockState state, Level level,
                                             BlockPos pos, Player player, InteractionHand hand,
                                             BlockHitResult hit,
                                             CallbackInfoReturnable<ItemInteractionResult> cir) {
        if (stack.getItem() instanceof SpellBook) {
            cir.setReturnValue(ItemInteractionResult.SUCCESS);
        }
    }
}
