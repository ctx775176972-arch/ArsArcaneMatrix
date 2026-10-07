package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.api.imbuement_chamber.IImbuementRecipe;
import com.hollingsworth.arsnouveau.common.block.tile.ImbuementTile;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.registry.ModBlocks;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ImbuementTile.class, remap = false)
public abstract class ImbuementTileMixin {
    private static final ResourceLocation FORMLESS_RECIPE = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID, "formless_essence_imbuement");

    @Shadow
    int backoff;

    /**
     * The native chamber waits twenty ticks after an unsuccessful recipe lookup.
     * That is useful for the ordinary chamber, but makes pedestal ingredients
     * delivered by Wixie automation appear to be detected one second late. The
     * advanced chamber is intended to react immediately, so do not retain that
     * failed-lookup cooldown for this block variant.
     */
    @Inject(method = "tick", at = @At("HEAD"), remap = false)
    private void arsMatrix$removeAdvancedRecipeBackoff(CallbackInfo callback) {
        if (arsMatrix$isAdvanced() && backoff > 0) {
            backoff = 0;
        }
    }

    @ModifyConstant(method = {"tick", "setItem"}, constant = @Constant(intValue = 100), remap = false)
    private int arsMatrix$advancedCraftDelay(int original) {
        return arsMatrix$isAdvanced() ? 20 : original;
    }

    @ModifyConstant(method = "tick", constant = @Constant(intValue = 200), remap = false)
    private int arsMatrix$advancedSourceTransfer(int original) {
        return arsMatrix$isAdvanced() ? 2_000 : original;
    }

    /**
     * Native chambers only discover Source providers within Manhattan distance two.
     * The advanced chamber is intended to serve compact automated chamber banks, so
     * let several chambers share one nearby relay without widening pedestal matching.
     */
    @ModifyArg(method = "tick", at = @At(value = "INVOKE", target =
            "Lcom/hollingsworth/arsnouveau/api/util/SourceUtil;takeSourceMultiple" +
                    "(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;II)Ljava/util/List;"),
            index = 2, remap = false)
    private int arsMatrix$advancedSourceSearchRadius(int original) {
        return arsMatrix$isAdvanced() ? 5 : original;
    }

    @Inject(method = "getRecipeNow", at = @At("HEAD"), cancellable = true, remap = false)
    @SuppressWarnings({"unchecked", "rawtypes"})
    private void arsMatrix$prioritizeFormlessRecipe(
            CallbackInfoReturnable<RecipeHolder<? extends IImbuementRecipe>> callback) {
        ImbuementTile tile = (ImbuementTile) (Object) this;
        if (tile.getLevel() == null) return;
        tile.getLevel().getRecipeManager().byKey(FORMLESS_RECIPE).ifPresent(holder -> {
            if (holder.value() instanceof IImbuementRecipe recipe
                    && recipe.matches(tile, tile.getLevel())) {
                callback.setReturnValue((RecipeHolder) holder);
            }
        });
    }

    private boolean arsMatrix$isAdvanced() {
        ImbuementTile tile = (ImbuementTile) (Object) this;
        return tile.getBlockState().is(ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get());
    }
}
