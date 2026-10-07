package dev.arsmatrix.mixin.client;

import net.minecraft.client.gui.screens.worldselection.WorldCreationUiState;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WorldCreationUiState.class)
public abstract class SkyblockWorldUiStateMixin {
    @Inject(method = "isBonusChest", at = @At("HEAD"), cancellable = true)
    private void matrix$disableBonusChest(CallbackInfoReturnable<Boolean> ci) {
        var preset = ((WorldCreationUiState)(Object)this).getWorldType().preset();
        if (preset != null && preset.unwrapKey().map(key -> key.location().equals(
                ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "skyblock"))).orElse(false))
            ci.setReturnValue(false);
    }
}
