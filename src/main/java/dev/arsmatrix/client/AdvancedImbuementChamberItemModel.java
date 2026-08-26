package dev.arsmatrix.client;

import com.hollingsworth.arsnouveau.client.renderer.tile.GenericModel;
import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.animatable.GeoAnimatable;

/** Native Imbuement Chamber geometry and animation with the advanced texture. */
public final class AdvancedImbuementChamberItemModel extends GenericModel<GeoAnimatable> {
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "textures/block/advanced_imbuement_chamber.png");

    public AdvancedImbuementChamberItemModel() {
        super("imbuement_chamber");
    }

    @Override
    public ResourceLocation getTextureResource(GeoAnimatable animatable) {
        return TEXTURE;
    }
}
