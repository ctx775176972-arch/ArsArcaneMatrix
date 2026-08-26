package dev.arsmatrix.util;

import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;

/** Small FIFO buffer whose capacity is measured in items and may contain mixed stack types. */
public final class MixedItemBuffer {
    private final int capacity;
    private final List<ItemStack> stacks = new ArrayList<>();

    public MixedItemBuffer(int capacity) { this.capacity = Math.max(1, capacity); }
    public int count() { return stacks.stream().mapToInt(ItemStack::getCount).sum(); }
    public int remaining() { return Math.max(0, capacity - count()); }
    public boolean isEmpty() { return stacks.isEmpty(); }
    public ItemStack first() { return stacks.isEmpty() ? ItemStack.EMPTY : stacks.getFirst(); }
    public List<ItemStack> stacks() { return List.copyOf(stacks); }

    public void insert(ItemStack incoming) {
        ItemStack remaining = incoming.copyWithCount(Math.min(incoming.getCount(), remaining()));
        if (remaining.isEmpty()) return;
        for (ItemStack existing : stacks) {
            if (!ItemStack.isSameItemSameComponents(existing, remaining)) continue;
            int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
            existing.grow(moved); remaining.shrink(moved);
            if (remaining.isEmpty()) return;
        }
        while (!remaining.isEmpty()) {
            int amount = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            stacks.add(remaining.copyWithCount(amount)); remaining.shrink(amount);
        }
    }

    public ItemStack removeOne() {
        if (stacks.isEmpty()) return ItemStack.EMPTY;
        ItemStack first = stacks.getFirst();
        ItemStack removed = first.copyWithCount(1);
        first.shrink(1);
        if (first.isEmpty()) stacks.removeFirst();
        return removed;
    }

    public void clear() { stacks.clear(); }
}
