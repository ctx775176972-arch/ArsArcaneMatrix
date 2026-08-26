package dev.arsmatrix.mixin;

import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Ars Nouveau's ImbuementTile constructor is intentionally tied to its own
 * block-entity type. Allow our drop-in chamber block to use that native type so
 * it retains the original inventory, automation, GeckoLib renderer, and save
 * format without failing BlockEntity's constructor validation.
 */
@Mixin(BlockEntityType.class)
public abstract class BlockEntityTypeMixin {
    private static final ResourceLocation IMBUEMENT_TILE_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "imbuement_chamber");
    private static final ResourceLocation ADVANCED_CHAMBER_ID =
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "advanced_imbuement_chamber");

    @Inject(method = "isValid", at = @At("HEAD"), cancellable = true)
    private void arsMatrix$allowAdvancedImbuementChamber(
            BlockState state,
            CallbackInfoReturnable<Boolean> callback
    ) {
        // Registry IDs can be queried safely while mod registries are still being
        // constructed. Never dereference either mod's DeferredHolder here: this
        // method also runs for vanilla render-only block entities during startup.
        ResourceLocation blockId = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (ADVANCED_CHAMBER_ID.equals(blockId)
                && IMBUEMENT_TILE_ID.equals(BuiltInRegistries.BLOCK_ENTITY_TYPE.getKey(
                        (BlockEntityType<?>) (Object) this))) {
            callback.setReturnValue(true);
        }
    }
}
