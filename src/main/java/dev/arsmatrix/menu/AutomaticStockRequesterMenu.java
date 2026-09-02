package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
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

public final class AutomaticStockRequesterMenu extends AbstractContainerMenu {
    public static final int MINUS_MINIMUM_16 = 0;
    public static final int MINUS_MINIMUM_1 = 1;
    public static final int PLUS_MINIMUM_1 = 2;
    public static final int PLUS_MINIMUM_16 = 3;
    public static final int MINUS_REQUEST_16 = 4;
    public static final int MINUS_REQUEST_1 = 5;
    public static final int PLUS_REQUEST_1 = 6;
    public static final int PLUS_REQUEST_16 = 7;
    public static final int TOGGLE_NOTIFICATIONS = 8;
    public static final int SET_TARGET_FROM_CARRIED = 9;
    public static final int CLEAR_TARGET = 10;

    private final AutomaticStockRequesterBlockEntity requester;
    private final BlockPos requesterPos;
    private final ContainerData data;

    public AutomaticStockRequesterMenu(
            int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer
    ) {
        this(containerId, inventory, buffer.readBlockPos(), null);
    }

    private AutomaticStockRequesterMenu(
            int containerId, Inventory inventory, BlockPos pos,
            AutomaticStockRequesterBlockEntity supplied
    ) {
        this(containerId, inventory, pos,
                supplied != null ? supplied
                        : inventory.player.level().getBlockEntity(pos)
                        instanceof AutomaticStockRequesterBlockEntity found ? found : null,
                supplied != null ? supplied.getCatalystHandler()
                        : inventory.player.level().getBlockEntity(pos)
                        instanceof AutomaticStockRequesterBlockEntity found
                                ? found.getCatalystHandler() : new ItemStackHandler(1),
                supplied == null ? new SimpleContainerData(7) : serverData(supplied));
    }

    public AutomaticStockRequesterMenu(
            int containerId, Inventory inventory, AutomaticStockRequesterBlockEntity requester
    ) {
        this(containerId, inventory, requester.getBlockPos(), requester);
    }

    private AutomaticStockRequesterMenu(
            int containerId, Inventory inventory, BlockPos pos,
            AutomaticStockRequesterBlockEntity requester,
            ItemStackHandler catalyst, ContainerData data
    ) {
        super(ModMenus.AUTOMATIC_STOCK_REQUESTER.get(), containerId);
        this.requester = requester;
        this.requesterPos = pos.immutable();
        this.data = data;
        addDataSlots(data);

        addSlot(new SlotItemHandler(catalyst, 0, 205, 28));
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        35 + column * 18, 143 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 35 + column * 18, 201));
        }
    }

    private static ContainerData serverData(AutomaticStockRequesterBlockEntity requester) {
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case 0 -> requester.getMinimumStock();
                    case 1 -> requester.getRequestAmount();
                    case 2 -> requester.getCurrentStock();
                    case 3 -> requester.getState().ordinal();
                    case 4 -> requester.isNotificationsEnabled() ? 1 : 0;
                    case 5 -> requester.getUpgradeTier();
                    case 6 -> requester.getAmountLimit();
                    default -> 0;
                };
            }

            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 7; }
        };
    }

    public ItemStack getTarget() {
        return requester == null ? ItemStack.EMPTY : requester.getTarget();
    }

    public int getMinimumStock() { return data.get(0); }
    public int getRequestAmount() { return data.get(1); }
    public int getCurrentStock() { return data.get(2); }
    public boolean isNotificationsEnabled() { return data.get(4) != 0; }
    public int getUpgradeTier() { return data.get(5); }
    public int getAmountLimit() { return data.get(6); }

    public AutomaticStockRequesterBlockEntity.OperatingState getOperatingState() {
        int index = Math.max(0, Math.min(
                AutomaticStockRequesterBlockEntity.OperatingState.values().length - 1, data.get(3)));
        return AutomaticStockRequesterBlockEntity.OperatingState.values()[index];
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (requester == null) return false;
        switch (id) {
            case MINUS_MINIMUM_16 -> requester.adjustMinimum(-16);
            case MINUS_MINIMUM_1 -> requester.adjustMinimum(-1);
            case PLUS_MINIMUM_1 -> requester.adjustMinimum(1);
            case PLUS_MINIMUM_16 -> requester.adjustMinimum(16);
            case MINUS_REQUEST_16 -> requester.adjustRequestAmount(-16);
            case MINUS_REQUEST_1 -> requester.adjustRequestAmount(-1);
            case PLUS_REQUEST_1 -> requester.adjustRequestAmount(1);
            case PLUS_REQUEST_16 -> requester.adjustRequestAmount(16);
            case TOGGLE_NOTIFICATIONS -> requester.toggleNotifications();
            case SET_TARGET_FROM_CARRIED -> {
                if (getCarried().isEmpty()) return false;
                requester.setTarget(getCarried(), player);
            }
            case CLEAR_TARGET -> requester.clearTarget();
            default -> { return false; }
        }
        return true;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved = index == 0
                ? moveItemStackTo(source, 1, 37, true)
                : moveItemStackTo(source, 0, 1, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.level().getBlockState(requesterPos).is(ModBlocks.AUTOMATIC_STOCK_REQUESTER.get())
                && player.distanceToSqr(requesterPos.getX() + 0.5D,
                        requesterPos.getY() + 0.5D, requesterPos.getZ() + 0.5D) <= 64.0D;
    }
}
