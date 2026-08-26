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
    private final BlockPos pos;
    private final ContainerData data;
    public ArcaneReactionVesselMenu(int id, Inventory inventory, RegistryFriendlyByteBuf buffer) { this(id, inventory, buffer.readBlockPos(), null); }
    public ArcaneReactionVesselMenu(int id, Inventory inventory, ArcaneReactionVesselBlockEntity vessel) { this(id, inventory, vessel.getBlockPos(), vessel); }
    private ArcaneReactionVesselMenu(int id, Inventory player, BlockPos pos, ArcaneReactionVesselBlockEntity supplied) {
        super(ModMenus.ARCANE_REACTION_VESSEL.get(), id); this.pos = pos.immutable();
        ArcaneReactionVesselBlockEntity vessel = supplied != null ? supplied
                : player.player.level().getBlockEntity(pos) instanceof ArcaneReactionVesselBlockEntity found ? found : null;
        ItemStackHandler items = vessel == null ? new ItemStackHandler(3) : vessel.items();
        data = vessel == null ? new SimpleContainerData(7) : vessel.menuData; addDataSlots(data);
        addSlot(new SlotItemHandler(items, 0, 44, 35)); addSlot(new SlotItemHandler(items, 1, 62, 35));
        addSlot(new SlotItemHandler(items, 2, 116, 35) { @Override public boolean mayPlace(ItemStack stack) { return false; } });
        for (int row=0; row<3; row++) for (int col=0; col<9; col++) addSlot(new Slot(player, col+row*9+9, 8+col*18, 84+row*18));
        for (int col=0; col<9; col++) addSlot(new Slot(player, col, 8+col*18, 142));
    }
    public int data(int index) { return data.get(index); }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        if (index < 0 || index >= slots.size() || !slots.get(index).hasItem()) return ItemStack.EMPTY;
        Slot slot=slots.get(index); ItemStack stack=slot.getItem(); ItemStack original=stack.copy();
        boolean moved = index < 3 ? moveItemStackTo(stack,3,slots.size(),true) : moveItemStackTo(stack,0,2,false);
        if (!moved) return ItemStack.EMPTY; if (stack.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged(); return original;
    }
    @Override public boolean stillValid(Player player) { return player.level().getBlockState(pos).is(ModBlocks.ARCANE_REACTION_VESSEL.get()) && player.distanceToSqr(pos.getCenter()) <= 64.0D; }
}
