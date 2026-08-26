package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.SourceStoneFurnaceBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
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

public final class SourceStoneFurnaceMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ContainerData data;

    public SourceStoneFurnaceMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }

    public SourceStoneFurnaceMenu(int id, Inventory inventory, SourceStoneFurnaceBlockEntity furnace) {
        this(id, inventory, furnace.getBlockPos(), furnace);
    }

    private SourceStoneFurnaceMenu(int id, Inventory playerInventory, BlockPos pos,
                                   SourceStoneFurnaceBlockEntity supplied) {
        super(ModMenus.SOURCE_STONE_FURNACE.get(), id);
        this.pos = pos.immutable();
        SourceStoneFurnaceBlockEntity furnace = supplied != null ? supplied
                : playerInventory.player.level().getBlockEntity(pos) instanceof SourceStoneFurnaceBlockEntity found
                ? found : null;
        ItemStackHandler items = furnace == null ? new ItemStackHandler(2) : furnace.inventory();
        data = furnace == null ? new SimpleContainerData(2) : furnace.menuData;
        addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 80, 27));
        addSlot(new SlotItemHandler(items, 1, 80, 75) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column + row * 9 + 9, 8 + column * 18, 116 + row * 18));
        for (int column = 0; column < 9; column++)
            addSlot(new Slot(playerInventory, column, 8 + column * 18, 174));
    }

    public int progress() { return data.get(0); }
    public int maxProgress() { return Math.max(1, data.get(1)); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved;
        if (index < 2) moved = moveItemStackTo(source, 2, slots.size(), true);
        else moved = moveItemStackTo(source, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }

    @Override public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.SOURCE_STONE_FURNACE.get())
                && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
