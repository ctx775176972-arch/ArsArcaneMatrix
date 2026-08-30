package dev.arsmatrix.util;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/** Capability-based access for the fixed consumable-container positions in mineral multiblocks. */
public final class StructureInventoryAccess {
    private StructureInventoryAccess() {}

    @Nullable
    public static IItemHandler at(@Nullable Level level, BlockPos pos) {
        if (level == null || !level.hasChunkAt(pos)) return null;
        IItemHandler unsided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (sided != null) return sided;
        }
        return null;
    }

    public static int firstSlot(@Nullable IItemHandler handler, Predicate<ItemStack> predicate) {
        if (handler == null) return -1;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (!stack.isEmpty() && predicate.test(stack)) return slot;
        }
        return -1;
    }

    public static boolean hasAnyItem(@Nullable IItemHandler handler) {
        return firstSlot(handler, stack -> true) >= 0;
    }
}
