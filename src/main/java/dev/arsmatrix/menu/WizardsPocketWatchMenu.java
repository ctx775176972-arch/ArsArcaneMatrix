package dev.arsmatrix.menu;

import dev.arsmatrix.item.WizardsPocketWatchItem;
import dev.arsmatrix.registry.ModMenus;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

/** No movable slots: the server only edits the exact held item used to open this menu. */
public final class WizardsPocketWatchMenu extends AbstractContainerMenu {
    private final InteractionHand hand;
    private final ItemStack openedStack;
    private final ContainerData data;

    public WizardsPocketWatchMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
        this(id, inv, buffer.readEnum(InteractionHand.class));
    }
    public WizardsPocketWatchMenu(int id, Inventory inv, InteractionHand hand) {
        super(ModMenus.WIZARDS_POCKET_WATCH.get(), id);
        this.hand = hand;
        openedStack = inv.player.getItemInHand(hand);
        data = inv.player.level().isClientSide ? new SimpleContainerData(2) : new ContainerData() {
            public int get(int i) { return i == 0 ? WizardsPocketWatchItem.interval(openedStack)
                    : WizardsPocketWatchItem.enabled(openedStack) ? 1 : 0; }
            public void set(int i, int v) {}
            public int getCount() { return 2; }
        };
        addDataSlots(data);
    }
    public ItemStack watch() { return openedStack; }
    public int interval() { return data.get(0); }
    public boolean enabled() { return data.get(1) != 0; }
    @Override public boolean stillValid(Player player) {
        return player.isAlive() && player.getItemInHand(hand) == openedStack
                && openedStack.getItem() instanceof WizardsPocketWatchItem;
    }
    @Override public boolean clickMenuButton(Player player, int button) {
        if (player.level().isClientSide || !stillValid(player)) return false;
        if (button == 1) WizardsPocketWatchItem.setEnabled(openedStack, !enabled());
        else if (button >= 100 + WizardsPocketWatchItem.MIN_SECONDS && button <= 100 + WizardsPocketWatchItem.MAX_SECONDS)
            WizardsPocketWatchItem.setInterval(openedStack, button - 100);
        else return false;
        player.getInventory().setChanged();
        player.inventoryMenu.broadcastChanges();
        broadcastChanges();
        return true;
    }
    @Override public ItemStack quickMoveStack(Player player, int index) { return ItemStack.EMPTY; }
}
