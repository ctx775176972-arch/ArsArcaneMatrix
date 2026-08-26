package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.StorageGridDirectoryBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class StorageGridDirectoryMenu extends AbstractContainerMenu {
    private static final int UPGRADE_SLOTS = StorageGridDirectoryBlockEntity.UPGRADE_SLOTS;
    private final BlockPos pos;
    private final ContainerData data;

    public StorageGridDirectoryMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }

    public StorageGridDirectoryMenu(int id, Inventory inventory,
                                    StorageGridDirectoryBlockEntity directory) {
        this(id, inventory, directory.getBlockPos(), directory);
    }

    private StorageGridDirectoryMenu(int id, Inventory inventory, BlockPos pos,
                                     StorageGridDirectoryBlockEntity supplied) {
        super(ModMenus.STORAGE_GRID_DIRECTORY.get(), id);
        this.pos = pos.immutable();
        StorageGridDirectoryBlockEntity directory = supplied != null ? supplied
                : inventory.player.level().getBlockEntity(pos) instanceof StorageGridDirectoryBlockEntity found
                ? found : null;
        ItemStackHandler upgrades = directory == null
                ? new ItemStackHandler(UPGRADE_SLOTS) : directory.getUpgrades();
        // The client may already have a block entity at this position, but menu data must
        // still use a writable client-side container so incoming server values are retained.
        data = supplied == null ? new SimpleContainerData(4) : serverData(directory);
        addDataSlots(data);
        for (int slot = 0; slot < UPGRADE_SLOTS; slot++) {
            final int upgradeSlot = slot;
            addSlot(new SlotItemHandler(upgrades, slot, 63 + slot * 20, 37) {
                @Override public boolean mayPickup(Player player) {
                    return directory == null || directory.canRemoveUpgrade(upgradeSlot);
                }
            });
        }
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column + row * 9 + 9, 22 + column * 18, 100 + row * 18));
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 22 + column * 18, 158));
        }
    }

    private static ContainerData serverData(StorageGridDirectoryBlockEntity directory) {
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> directory.getStoredTypeCount();
                    case 1 -> directory.getTypeCapacity();
                    case 2 -> (int) Math.min(Integer.MAX_VALUE, directory.getStoredItemCount());
                    case 3 -> (int) Math.min(Integer.MAX_VALUE, directory.getItemCapacity());
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 4; }
        };
    }

    public int getStoredTypes() { return data.get(0); }
    public int getTypeCapacity() { return data.get(1); }
    public int getStoredItems() { return data.get(2); }
    public int getItemCapacity() { return data.get(3); }

    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < UPGRADE_SLOTS) {
            moved = moveItemStackTo(source, UPGRADE_SLOTS, slots.size(), true);
        } else if (source.is(ModItems.GRID_EXPANSION_WAREHOUSE.get())) {
            moved = moveItemStackTo(source, 0, UPGRADE_SLOTS, false);
        } else {
            return ItemStack.EMPTY;
        }
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.STORAGE_GRID_DIRECTORY.get())
                && player.distanceToSqr(pos.getX() + .5D, pos.getY() + .5D, pos.getZ() + .5D) <= 64.0D;
    }
}
