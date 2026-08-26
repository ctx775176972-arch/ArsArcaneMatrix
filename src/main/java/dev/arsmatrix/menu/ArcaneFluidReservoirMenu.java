package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
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

public final class ArcaneFluidReservoirMenu extends AbstractContainerMenu {
    private final BlockPos pos;
    private final ArcaneFluidReservoirBlockEntity reservoir;
    private final ContainerData data;

    public ArcaneFluidReservoirMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }
    public ArcaneFluidReservoirMenu(int id, Inventory inventory, ArcaneFluidReservoirBlockEntity reservoir) {
        this(id, inventory, reservoir.getBlockPos(), reservoir);
    }
    private ArcaneFluidReservoirMenu(int id, Inventory inventory, BlockPos pos,
                                     ArcaneFluidReservoirBlockEntity supplied) {
        super(ModMenus.ARCANE_FLUID_RESERVOIR.get(), id);
        this.pos = pos.immutable();
        this.reservoir = supplied != null ? supplied
                : inventory.player.level().getBlockEntity(pos) instanceof ArcaneFluidReservoirBlockEntity found ? found : null;
        ItemStackHandler upgrades = reservoir == null ? new ItemStackHandler(4) : reservoir.getUpgrades();
        ItemStackHandler modules = reservoir == null ? new ItemStackHandler(2) : reservoir.getTankModules();
        data = supplied == null ? new SimpleContainerData(13) : serverData(supplied);
        addDataSlots(data);
        for (int slot = 0; slot < 4; slot++) addSlot(new SlotItemHandler(upgrades, slot, 82 + slot * 20, 126));
        for (int slot = 0; slot < 2; slot++) addSlot(new SlotItemHandler(modules, slot, 172 + slot * 20, 126));
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 40 + column * 18, 167 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 40 + column * 18, 225));
    }
    private static ContainerData serverData(ArcaneFluidReservoirBlockEntity reservoir) {
        return new ContainerData() {
            @Override public int get(int index) {
                if (index < 3) return reservoir.amount(index);
                return switch (index) {
                    case 3 -> reservoir.capacity();
                    case 4 -> reservoir.inputFluid();
                    case 5 -> reservoir.outputFluid();
                    case 6 -> reservoir.mode().ordinal();
                    case 7 -> reservoir.operatingState().ordinal();
                    case 8, 9, 10 -> reservoir.tankType(index - 8);
                    case 11 -> reservoir.unlockedTankCount();
                    case 12 -> reservoir.wirelessTier();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 13; }
        };
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (reservoir == null) return false;
        if (id == 0) reservoir.cycleInput();
        else if (id == 1) reservoir.cycleOutput();
        else if (id == 2) reservoir.cycleMode(player);
        else return false;
        return true;
    }
    public int amount(int index) { return data.get(index); }
    public int capacity() { return data.get(3); }
    public int inputFluid() { return data.get(4); }
    public int outputFluid() { return data.get(5); }
    public int mode() { return data.get(6); }
    public int state() { return data.get(7); }
    public int tankType(int tank) { return data.get(8 + tank); }
    public int unlockedTankCount() { return data.get(11); }
    public int wirelessTier() { return data.get(12); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        boolean moved;
        if (index < 6) moved = moveItemStackTo(source, 6, slots.size(), true);
        else if (isUpgrade(source)) moved = moveItemStackTo(source, 0, 4, false);
        else if (source.is(ModItems.ARCANE_FLUID_TANK.get()))
            moved = moveItemStackTo(source, 4, 6, false);
        else return ItemStack.EMPTY;
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
    private static boolean isUpgrade(ItemStack stack) {
        return stack.is(ModItems.FLUID_CAPACITY_UPGRADE.get()) || stack.is(ModItems.FLUID_RANGE_UPGRADE.get())
                || stack.is(ModItems.FLUID_SPEED_UPGRADE.get());
    }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.ARCANE_FLUID_RESERVOIR.get())
                && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
