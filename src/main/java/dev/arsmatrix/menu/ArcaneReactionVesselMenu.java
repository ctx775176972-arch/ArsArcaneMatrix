package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.ArcaneReactionVesselBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.*;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class ArcaneReactionVesselMenu extends AbstractContainerMenu {
    public static final int BUTTON_CLEAR_FLUID = 0;
    private final BlockPos pos;
    private final ContainerData data;
    public ArcaneReactionVesselMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos(), null); }
    public ArcaneReactionVesselMenu(int id, Inventory inventory, ArcaneReactionVesselBlockEntity vessel) { this(id, inventory, vessel.getBlockPos(), vessel); }
    private ArcaneReactionVesselMenu(int id, Inventory player, BlockPos pos, ArcaneReactionVesselBlockEntity supplied) {
        super(ModMenus.ARCANE_REACTION_VESSEL.get(), id); this.pos = pos.immutable();
        ArcaneReactionVesselBlockEntity vessel = supplied != null ? supplied
                : player.player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity found ? found : null;
        ItemStackHandler items = vessel == null ? new ItemStackHandler(3) : vessel.items();
        // The network-side menu must keep its own writable data array. Reusing the
        // client block entity's ContainerData drops every update except progress,
        // leaving the displayed recipe duration stuck at the five-second default.
        data = supplied == null ? new SimpleContainerData(5) : supplied.menuData; addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 44, 35)); addSlot(new SlotItemHandler(items, 1, 62, 35));
        addSlot(new SlotItemHandler(items, 2, 116, 35) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(player, col+row*9+9, 8+col*18, 84+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(player, col, 8+col*18, 138));
    }
    public int data(int index) { return data.get(index); }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (id != BUTTON_CLEAR_FLUID) return false;
        if (player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity vessel) {
            vessel.clearFluid();
            return true;
        }
        return false;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); ItemStack stack=slot.getItem(); ItemStack original=stack.copy();
        boolean moved = index < 3 ? moveItemStackTo(stack,3,slots.size(),true) : moveItemStackTo(stack,0,2,false);
        if (!moved) return ItemStack.EMPTY; if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); return original;
    }
    @Override public boolean stillValid(Player player) { return player.level().getBlockState(pos).is(ModBlocks.ARCANE_REACTION_VESSEL.get()) && player.distanceToSqr(pos.getCenter()) <= 64.0D; }
}
