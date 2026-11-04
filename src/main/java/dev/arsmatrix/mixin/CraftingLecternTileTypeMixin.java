package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.CraftingLecternTile;
import dev.arsmatrix.block.AdvancedStorageLecternBlock;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

/** Keeps the advanced lectern's own block-entity ID when using the native constructor. */
@Mixin(value = CraftingLecternTile.class, remap = false)
public abstract class CraftingLecternTileTypeMixin {
    @ModifyArgs(method = "<init>", at = @At(value = "INVOKE", target =
            "Lcom/hollingsworth/arsnouveau/common/block/tile/ModdedTile;<init>(Lnet/minecraft/world/level/block/entity/BlockEntityType;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V"))
    private static void arsMatrix$useAdvancedLecternType(Args args) {
        BlockState state = args.get(2);
        if (state.getBlock() instanceof AdvancedStorageLecternBlock) {
            args.set(0, ModBlockEntities.ADVANCED_STORAGE_LECTERN.get());
        }
    }
}
