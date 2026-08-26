package dev.arsmatrix.event;

import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/** Converts the original construction materials into the Arcane Hunting Grounds. */
public final class DrygmyArenaEvents {
    private DrygmyArenaEvents() {}

    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (event.getHand() != InteractionHand.MAIN_HAND
                || !event.getItemStack().is(ItemsRegistry.DRYGMY_CHARM.get())
                || !event.getLevel().getBlockState(event.getPos()).is(Blocks.NETHERITE_BLOCK)) return;

        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(event.getLevel().isClientSide));
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        serverLevel.setBlockAndUpdate(event.getPos(), ModBlocks.DRYGMY_ARENA.get().defaultBlockState());
        if (!event.getEntity().getAbilities().instabuild) event.getItemStack().shrink(1);
        serverLevel.playSound(null, event.getPos(), SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 1.0F, 0.7F);
    }
}
