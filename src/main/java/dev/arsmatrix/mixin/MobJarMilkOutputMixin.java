package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.MobJarTile;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EntityType;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.common.NeoForgeMod;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Direct downward milk output, deliberately without adding a fluid tank to the jar. */
@Mixin(value = MobJarTile.class, remap = false)
public abstract class MobJarMilkOutputMixin {
    @Unique private long arsMatrix$lastMilkAttempt = Long.MIN_VALUE;

    @Inject(method = "tick", at = @At("TAIL"), remap = false)
    private void arsMatrix$outputMilk(CallbackInfo callback) {
        MobJarTile jar = (MobJarTile) (Object) this;
        var level = jar.getLevel();
        if (level == null || level.isClientSide || jar.isRemoved()) return;
        long now = level.getGameTime();
        if (arsMatrix$lastMilkAttempt != Long.MIN_VALUE && now - arsMatrix$lastMilkAttempt < 20) return;
        arsMatrix$lastMilkAttempt = now;
        var below = jar.getBlockPos().below();
        if (!level.hasChunkAt(below)) return;
        var entity = jar.getEntity();
        if (entity == null || entity.getType() != EntityType.COW) return;
        var tank = level.getCapability(Capabilities.FluidHandler.BLOCK, below, Direction.UP);
        if (tank == null) return;
        // Generate only what the destination accepts; never bank missed production.
        tank.fill(new FluidStack(NeoForgeMod.MILK.get(), 1000), IFluidHandler.FluidAction.EXECUTE);
    }
}
