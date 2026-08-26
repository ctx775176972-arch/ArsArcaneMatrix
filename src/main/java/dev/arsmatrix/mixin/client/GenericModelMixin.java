package dev.arsmatrix.mixin.client;

import com.hollingsworth.arsnouveau.client.renderer.tile.GenericModel;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import software.bernie.geckolib.animatable.GeoAnimatable;

@Mixin(value = GenericModel.class, remap = false)
public abstract class GenericModelMixin {
    private static final ResourceLocation ADVANCED_IMBUEMENT_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "textures/block/advanced_imbuement_chamber.png");

    @Inject(method = "getTextureResource", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsMatrix$advancedImbuementTexture(
            GeoAnimatable animatable, CallbackInfoReturnable<ResourceLocation> callback) {
        if (animatable instanceof ImbuementTile tile
                && tile.getBlockState().is(ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get())) {
            callback.setReturnValue(ADVANCED_IMBUEMENT_TEXTURE);
        }
    }
}
