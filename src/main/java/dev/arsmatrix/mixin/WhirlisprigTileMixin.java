package dev.arsmatrix.mixin;

import com.hollingsworth.arsnouveau.common.block.tile.WhirlisprigTile;
import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import dev.arsmatrix.compat.arsnouveau.WhirlisprigEnhancements;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

@Mixin(value = WhirlisprigTile.class, remap = false)
public abstract class WhirlisprigTileMixin {
    @Unique
    private static final String ARS_MATRIX_PENDING_OUTPUTS = "ArsMatrixPendingOutputs";
    @Unique
    private List<ItemStack> arsMatrix$pendingOutputs;

    /**
     * Native Whirlisprigs discard the remainder returned by insertItemAdjacent.
     * Flush that remainder before another production cycle can consume Source or
     * one of our woodland catalysts.
     */
    @Inject(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/common/block/tile/SummoningTile;tick()V",
                    shift = At.Shift.AFTER),
            cancellable = true,
            remap = false
    )
    private void arsMatrix$flushBlockedOutputs(CallbackInfo ci) {
        WhirlisprigTile tile = (WhirlisprigTile) (Object) this;
        Level level = tile.getLevel();
        List<ItemStack> pending = arsMatrix$pendingOutputs();
        if (level == null || level.isClientSide || pending.isEmpty()) return;

        // Rechecking every ten ticks avoids repeatedly probing a completely full
        // inventory while still reacting quickly when space becomes available.
        if (level.getGameTime() % 10L == 0L) {
            Iterator<ItemStack> iterator = pending.iterator();
            while (iterator.hasNext()) {
                ItemStack queued = iterator.next();
                ItemStack remainder = BlockUtil.insertItemAdjacent(level, tile.getBlockPos(), queued);
                if (remainder.isEmpty()) iterator.remove();
                else queued.setCount(remainder.getCount());
            }
            tile.setChanged();
            tile.updateBlock();
        }

        // Even if the final cached stack was inserted this tick, wait until the
        // next tick before allowing a new paid production cycle.
        ci.cancel();
    }

    @Inject(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/api/util/SourceUtil;takeSourceMultipleWithParticles(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;II)Ljava/util/List;"),
            cancellable = true,
            remap = false
    )
    private void arsMatrix$pauseBeforePayingForFullOutput(CallbackInfo ci) {
        WhirlisprigTile tile = (WhirlisprigTile) (Object) this;
        Level level = tile.getLevel();
        if (level != null && !level.isClientSide && !arsMatrix$hasAdjacentCapacity(level, tile.getBlockPos())) {
            ci.cancel();
        }
    }

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

    @Redirect(
            method = "tick",
            at = @At(value = "INVOKE",
                    target = "Lcom/hollingsworth/arsnouveau/api/util/BlockUtil;insertItemAdjacent(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"),
            remap = false
    )
    private ItemStack arsMatrix$cacheBlockedOutput(Level level, BlockPos pos, ItemStack stack) {
        ItemStack remainder = BlockUtil.insertItemAdjacent(level, pos, stack);
        if (!remainder.isEmpty()) {
            arsMatrix$storePending(remainder);
            WhirlisprigTile tile = (WhirlisprigTile) (Object) this;
            tile.setChanged();
            tile.updateBlock();
        }
        return ItemStack.EMPTY;
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

    @Inject(method = "saveAdditional", at = @At("TAIL"), remap = false)
    private void arsMatrix$savePendingOutputs(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        List<ItemStack> pending = arsMatrix$pendingOutputs();
        if (pending.isEmpty()) return;
        ListTag saved = new ListTag();
        for (ItemStack stack : pending) saved.add(stack.saveOptional(registries));
        tag.put(ARS_MATRIX_PENDING_OUTPUTS, saved);
    }

    @Inject(method = "loadAdditional", at = @At("TAIL"), remap = false)
    private void arsMatrix$loadPendingOutputs(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo ci) {
        List<ItemStack> pending = arsMatrix$pendingOutputs();
        pending.clear();
        ListTag saved = tag.getList(ARS_MATRIX_PENDING_OUTPUTS, Tag.TAG_COMPOUND);
        for (int index = 0; index < saved.size(); index++) {
            ItemStack stack = ItemStack.parseOptional(registries, saved.getCompound(index));
            if (!stack.isEmpty()) arsMatrix$storePending(stack);
        }
    }

    @Unique
    private List<ItemStack> arsMatrix$pendingOutputs() {
        if (arsMatrix$pendingOutputs == null) arsMatrix$pendingOutputs = new ArrayList<>();
        return arsMatrix$pendingOutputs;
    }

    @Unique
    private void arsMatrix$storePending(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        List<ItemStack> pending = arsMatrix$pendingOutputs();
        for (ItemStack stored : pending) {
            if (!ItemStack.isSameItemSameComponents(stored, remaining)) continue;
            int moved = Math.min(remaining.getCount(), stored.getMaxStackSize() - stored.getCount());
            if (moved <= 0) continue;
            stored.grow(moved);
            remaining.shrink(moved);
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            pending.add(remaining.copyWithCount(amount));
            remaining.shrink(amount);
        }
    }

    @Unique
    private static boolean arsMatrix$hasAdjacentCapacity(Level level, BlockPos origin) {
        for (Direction direction : Direction.values()) {
            BlockPos containerPos = origin.relative(direction);
            IItemHandler handler = level.getCapability(
                    Capabilities.ItemHandler.BLOCK, containerPos, direction.getOpposite());
            if (handler == null) {
                handler = level.getCapability(Capabilities.ItemHandler.BLOCK, containerPos, null);
            }
            if (handler == null) continue;
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stored = handler.getStackInSlot(slot);
                if (stored.isEmpty()
                        || stored.getCount() < Math.min(stored.getMaxStackSize(), handler.getSlotLimit(slot))) {
                    return true;
                }
            }
        }
        return false;
    }
}
