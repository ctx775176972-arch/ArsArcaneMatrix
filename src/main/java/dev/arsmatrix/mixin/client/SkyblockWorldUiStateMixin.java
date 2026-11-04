package dev.arsmatrix.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import dev.arsmatrix.config.MatrixCommonConfig;
import java.util.List;

@Mixin(WorldCreationUiState.class)
public abstract class SkyblockWorldUiStateMixin {
    /** Keep the preset registered for existing saves, but hide it from new-world menus when disabled. */
    @Inject(method = {"getNormalPresetList", "getAltPresetList"}, at = @At("RETURN"), cancellable = true)
    private void matrix$hideDisabledSkyblockPreset(
            CallbackInfoReturnable<List<WorldCreationUiState.WorldTypeEntry>> callback) {
        if (MatrixCommonConfig.SPEC.isLoaded() && MatrixCommonConfig.ENABLE_SKYBLOCK.get()) return;
        callback.setReturnValue(callback.getReturnValue().stream().filter(entry ->
                entry.preset() == null || entry.preset().unwrapKey().map(key -> !key.location().equals(
                        ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "skyblock")))
                        .orElse(true)).toList());
    }

    @Inject(method = "isBonusChest", at = @At("HEAD"), cancellable = true)
    private void matrix$disableBonusChest(CallbackInfoReturnable<Boolean> ci) {
        var preset = ((WorldCreationUiState)(Object)this).getWorldType().preset();
        if (preset != null && preset.unwrapKey().map(key -> key.location().equals(
                ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "skyblock"))).orElse(false))
            ci.setReturnValue(false);
    }
}
