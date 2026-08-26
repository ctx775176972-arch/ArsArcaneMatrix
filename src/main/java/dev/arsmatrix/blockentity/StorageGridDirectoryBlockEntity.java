package dev.arsmatrix.blockentity;

import dev.arsmatrix.menu.StorageGridDirectoryMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import java.util.ArrayList;
import java.util.List;

/** Compact, type-indexed item store expanded by warehouse modules installed in its GUI. */
public final class StorageGridDirectoryBlockEntity extends BlockEntity implements MenuProvider {
    public static final int UPGRADE_SLOTS = 4;
    public static final int BASE_TYPES = 16;
    public static final int TYPES_PER_UPGRADE = 64;
    public static final long BASE_ITEMS = 4_096L;
    public static final long ITEMS_PER_UPGRADE = 262_144L;

    private final List<StoredEntry> entries = new ArrayList<>();
    private final ItemStackHandler upgrades = new ItemStackHandler(UPGRADE_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ModItems.GRID_EXPANSION_WAREHOUSE.get());
        }
        @Override protected void onContentsChanged(int slot) { setChanged(); }
    };
    private final GridItemHandler storage = new GridItemHandler();

    public StorageGridDirectoryBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STORAGE_GRID_DIRECTORY.get(), pos, state);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.storage_grid_directory");
    }

    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new StorageGridDirectoryMenu(id, inventory, this);
    }

    public ItemStackHandler getUpgrades() { return upgrades; }
    public GridItemHandler getStorage() { return storage; }
    public int getUpgradeCount() {
        int count = 0;
        for (int slot = 0; slot < upgrades.getSlots(); slot++) {
            if (!upgrades.getStackInSlot(slot).isEmpty()) count++;
        }
        return count;
    }
    public boolean canRemoveUpgrade(int slot) {
        if (slot < 0 || slot >= upgrades.getSlots() || upgrades.getStackInSlot(slot).isEmpty()) return true;
        int remaining = Math.max(0, getUpgradeCount() - 1);
        int typeCapacity = BASE_TYPES + remaining * TYPES_PER_UPGRADE;
        long itemCapacity = BASE_ITEMS + (long) remaining * ITEMS_PER_UPGRADE;
        return getStoredTypeCount() <= typeCapacity && getStoredItemCount() <= itemCapacity;
    }
    public int getTypeCapacity() { return BASE_TYPES + getUpgradeCount() * TYPES_PER_UPGRADE; }
    public long getItemCapacity() { return BASE_ITEMS + getUpgradeCount() * ITEMS_PER_UPGRADE; }
    public int getStoredTypeCount() { return entries.size(); }
    public long getStoredItemCount() {
        long total = 0L;
        for (StoredEntry entry : entries) total += entry.amount;
        return total;
    }
    public List<StoredStack> getStoredStacks() {
        return entries.stream().map(entry -> new StoredStack(entry.template.copy(), entry.amount)).toList();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Upgrades", upgrades.serializeNBT(registries));
        ListTag stored = new ListTag();
        for (StoredEntry entry : entries) {
            if (entry.amount <= 0L || entry.template.isEmpty()) continue;
            CompoundTag item = new CompoundTag();
            item.put("Stack", entry.template.copyWithCount(1).saveOptional(registries));
            item.putLong("Amount", entry.amount);
            stored.add(item);
        }
        tag.put("StoredItems", stored);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        upgrades.deserializeNBT(registries, tag.getCompound("Upgrades"));
        entries.clear();
        ListTag stored = tag.getList("StoredItems", Tag.TAG_COMPOUND);
        for (int index = 0; index < stored.size(); index++) {
            CompoundTag item = stored.getCompound(index);
            ItemStack stack = ItemStack.parseOptional(registries, item.getCompound("Stack"));
            long amount = Math.max(0L, item.getLong("Amount"));
            if (!stack.isEmpty() && amount > 0L) entries.add(new StoredEntry(stack.copyWithCount(1), amount));
        }
    }

    public record StoredStack(ItemStack stack, long amount) {}

    private static final class StoredEntry {
        private final ItemStack template;
        private long amount;
        private StoredEntry(ItemStack template, long amount) {
            this.template = template;
            this.amount = amount;
        }
    }

    /** Slot-shaped compatibility facade backed by compact long-count entries. */
    public final class GridItemHandler implements IItemHandler {
        public List<StoredStack> getStoredStacks() {
            return StorageGridDirectoryBlockEntity.this.getStoredStacks();
        }

        public int extractMatching(ItemStack template, int requested) {
            if (template.isEmpty() || requested <= 0) return 0;
            for (int index = 0; index < entries.size(); index++) {
                StoredEntry entry = entries.get(index);
                if (!ItemStack.isSameItemSameComponents(entry.template, template)) continue;
                int extracted = (int) Math.min((long) requested, entry.amount);
                entry.amount -= extracted;
                if (entry.amount <= 0L) entries.remove(index);
                setChanged();
                return extracted;
            }
            return 0;
        }

        @Override public int getSlots() { return getTypeCapacity(); }

        @Override public ItemStack getStackInSlot(int slot) {
            if (slot < 0 || slot >= entries.size()) return ItemStack.EMPTY;
            StoredEntry entry = entries.get(slot);
            // Bulk handlers such as storage lecterns total the count returned by each slot.
            // Expose the complete compact count here; actual extraction below remains capped
            // to a legal item stack so pipes and players never receive an oversized stack.
            return entry.template.copyWithCount((int) Math.min(entry.amount, Integer.MAX_VALUE));
        }

        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= getSlots()) return stack;
            StoredEntry matching = entries.stream().filter(entry ->
                    ItemStack.isSameItemSameComponents(entry.template, stack)).findFirst().orElse(null);
            if (matching == null && slot < entries.size()) return stack;
            if (matching == null && entries.size() >= getTypeCapacity()) return stack;
            long free = Math.max(0L, getItemCapacity() - getStoredItemCount());
            int accepted = (int) Math.min(stack.getCount(), free);
            if (accepted <= 0) return stack;
            if (!simulate) {
                if (matching == null) entries.add(new StoredEntry(stack.copyWithCount(1), accepted));
                else matching.amount += accepted;
                setChanged();
            }
            ItemStack remainder = stack.copy();
            remainder.shrink(accepted);
            return remainder;
        }

        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            if (slot < 0 || slot >= entries.size() || amount <= 0) return ItemStack.EMPTY;
            StoredEntry entry = entries.get(slot);
            int extracted = (int) Math.min(Math.min((long) amount, entry.amount),
                    entry.template.getMaxStackSize());
            if (extracted <= 0) return ItemStack.EMPTY;
            ItemStack result = entry.template.copyWithCount(extracted);
            if (!simulate) {
                entry.amount -= extracted;
                if (entry.amount <= 0L) entries.remove(slot);
                setChanged();
            }
            return result;
        }

        @Override public int getSlotLimit(int slot) { return Integer.MAX_VALUE; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return !stack.isEmpty(); }
    }
}
