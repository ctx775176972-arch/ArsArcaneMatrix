package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.WhirlisprigTile;
import dev.arsmatrix.compat.arsnouveau.WhirlisprigEnhancements;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Map;

@Mixin(value = WhirlisprigTile.class, remap = false)
public abstract class WhirlisprigTileMixin {

    @Inject(method = "addProgress", at = @At("HEAD"), cancellable = true, remap = false)
    private void arsMatrix$applyCompactGroveProgress(CallbackInfo ci) {
        if (WhirlisprigEnhancements.addCompactGroveProgress((WhirlisprigTile) (Object) this)) {
            ci.cancel();
        }
    }

    @ModifyArg(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/api/util/DropDistribution;<init>(Ljava/util/Map;)V"),
            index = 0,
            remap = false
    )
    private Map<BlockState, Integer> arsMatrix$productionTable(Map<BlockState, Integer> original) {
        return WhirlisprigEnhancements.productionTable((WhirlisprigTile) (Object) this, original);
    }

    @Inject(method = "getDropsByDiversity", at = @At("HEAD"), remap = false)
    private void arsMatrix$consumeOncePerProduction(CallbackInfoReturnable<Integer> cir) {
        WhirlisprigEnhancements.consumeForProduction((WhirlisprigTile) (Object) this);
    }

    @Inject(method = "getDropsByDiversity", at = @At("RETURN"), remap = false, cancellable = true)
    private void arsMatrix$multiplyWholeCycle(CallbackInfoReturnable<Integer> cir) {
        cir.setReturnValue(WhirlisprigEnhancements.enhancedDropRolls(
                (WhirlisprigTile) (Object) this, cir.getReturnValue()));
    }

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void arsMatrix$finishCycle(CallbackInfo ci) {
        WhirlisprigEnhancements.finishProductionTick((WhirlisprigTile) (Object) this);
    }
}
