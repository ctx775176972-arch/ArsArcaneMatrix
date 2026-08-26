package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.ArcaneVacuumHopperBlockEntity;
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

public final class ArcaneVacuumHopperMenu extends AbstractContainerMenu {
    public static final int MACHINE_SLOTS = 29;
    private final BlockPos pos;
    private final ArcaneVacuumHopperBlockEntity hopper;
    private final ContainerData data;

    public ArcaneVacuumHopperMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(id, inventory, buffer.readBlockPos(), null);
    }
    public ArcaneVacuumHopperMenu(int id, Inventory inventory, ArcaneVacuumHopperBlockEntity hopper) {
        this(id, inventory, hopper.getBlockPos(), hopper);
    }
    private ArcaneVacuumHopperMenu(int id, Inventory inventory, BlockPos pos,
                                   ArcaneVacuumHopperBlockEntity supplied) {
        super(ModMenus.ARCANE_VACUUM_HOPPER.get(), id);
        this.pos = pos.immutable();
        hopper = supplied != null ? supplied
                : inventory.player.level().getBlockEntity(pos) instanceof ArcaneVacuumHopperBlockEntity found ? found : null;
        ItemStackHandler filterHandler = hopper == null ? new ItemStackHandler(9) : hopper.filters();
        ItemStackHandler dropHandler = hopper == null ? new ItemStackHandler(18) : hopper.drops();
        ItemStackHandler gemHandler = hopper == null ? new ItemStackHandler(2) : hopper.gems();
        data = supplied == null ? new SimpleContainerData(12) : serverData(supplied);
        addDataSlots(data);
        for (int column = 0; column < 9; column++)
            addSlot(new SlotItemHandler(filterHandler, column, 38 + column * 18, 71));
        for (int row = 0; row < 2; row++) for (int column = 0; column < 9; column++)
            addSlot(new SlotItemHandler(dropHandler, column + row * 9, 38 + column * 18, 104 + row * 18));
        for (int slot = 0; slot < 2; slot++) addSlot(new SlotItemHandler(gemHandler, slot, 209, 104 + slot * 18) {
            @Override public boolean mayPlace(ItemStack stack) { return false; }
        });
        for (int row = 0; row < 3; row++) for (int column = 0; column < 9; column++)
            addSlot(new Slot(inventory, column + row * 9 + 9, 38 + column * 18, 168 + row * 18));
        for (int column = 0; column < 9; column++) addSlot(new Slot(inventory, column, 38 + column * 18, 226));
    }
    private static ContainerData serverData(ArcaneVacuumHopperBlockEntity hopper) {
        return new ContainerData() {
            @Override public int get(int index) {
                return switch (index) {
                    case 0 -> hopper.experience();
                    case 1 -> hopper.collectsItems() ? 1 : 0;
                    case 2 -> hopper.collectsExperience() ? 1 : 0;
                    case 3 -> hopper.filterMode().ordinal();
                    case 4 -> hopper.gemMode().ordinal();
                    case 5 -> hopper.itemOutputMode().ordinal();
                    case 6 -> hopper.gemOutputMode().ordinal();
                    case 7 -> hopper.bindChannel().ordinal();
                    case 8 -> (hopper.hasItemTarget() ? 1 : 0) | (hopper.hasGemTarget() ? 2 : 0);
                    case 9 -> hopper.destroysMatches() ? 1 : 0;
                    case 10 -> hopper.strictComponents() ? 1 : 0;
                    case 11 -> hopper.rangeMode().ordinal();
                    default -> 0;
                };
            }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 12; }
        };
    }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (hopper == null) return false;
        switch (id) {
            case 0 -> hopper.toggleItems();
            case 1 -> hopper.toggleExperience();
            case 2 -> hopper.cycleFilterMode();
            case 3 -> hopper.cycleGemMode();
            case 4 -> hopper.cycleItemOutputMode();
            case 5 -> hopper.cycleGemOutputMode();
            case 6 -> hopper.cycleBindChannel();
            case 7 -> hopper.toggleDestroyMatches();
            case 8 -> hopper.toggleStrictComponents();
            case 9 -> hopper.cycleRangeMode();
            case 20 -> hopper.depositExperience(player, 3);
            case 21 -> hopper.depositExperience(player, 12);
            case 22 -> hopper.depositExperience(player, 120);
            case 23 -> hopper.depositExperience(player, 768);
            case 24 -> hopper.depositAllExperience(player);
            default -> { return false; }
        }
        return true;
    }
    public int experience() { return data.get(0); }
    public boolean collectsItems() { return data.get(1) != 0; }
    public boolean collectsExperience() { return data.get(2) != 0; }
    public int filterMode() { return data.get(3); }
    public int gemMode() { return data.get(4); }
    public int itemOutputMode() { return data.get(5); }
    public int gemOutputMode() { return data.get(6); }
    public int bindChannel() { return data.get(7); }
    public boolean itemBound() { return (data.get(8) & 1) != 0; }
    public boolean gemBound() { return (data.get(8) & 2) != 0; }
    public boolean destroysMatches() { return data.get(9) != 0; }
    public boolean strictComponents() { return data.get(10) != 0; }
    public int rangeMode() { return data.get(11); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        boolean moved;
        if (index < MACHINE_SLOTS) moved = moveItemStackTo(source, MACHINE_SLOTS, slots.size(), true);
        else moved = moveItemStackTo(source, 9, 27, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.ARCANE_VACUUM_HOPPER.get())
                && player.distanceToSqr(pos.getCenter()) <= 64.0D;
    }
}
