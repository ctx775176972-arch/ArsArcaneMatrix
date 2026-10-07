package dev.arsmatrix.mixin.client;

import net.minecraft.client.gui.screens.worldselection.CreateWorldScreen;
import net.minecraft.resources.ResourceLocation;
import java.util.function.BooleanSupplier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;

@Mixin(targets = "net.minecraft.client.gui.screens.worldselection.CreateWorldScreen$WorldTab")
public abstract class SkyblockWorldTabMixin {
    @Shadow @Final private CreateWorldScreen this$0;

    @ModifyArg(method = "<init>", index = 0, at = @At(value = "INVOKE", ordinal = 1, target =
            "Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;withIsActiveCondition(Ljava/util/function/BooleanSupplier;)Lnet/minecraft/client/gui/screens/worldselection/SwitchGrid$SwitchBuilder;"))
    private BooleanSupplier matrix$disableBonusSwitch(BooleanSupplier original) {
        return () -> {
            var preset = this$0.getUiState().getWorldType().preset();
            boolean skyblock = preset != null && preset.unwrapKey().map(key -> key.location().equals(
                    ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "skyblock"))).orElse(false);
            return original.getAsBoolean() && !skyblock;
        };
    }
}
