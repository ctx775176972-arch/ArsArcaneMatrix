package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.WixiePatternProviderBlockEntity;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModMenus;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;

public final class WixiePatternProviderMenu extends AbstractContainerMenu {
    public static final int PAGE_SIZE = WixiePatternProviderBlockEntity.GUIDE_SLOTS_PER_TIER;
    public static final int BUTTON_PREVIOUS_PAGE = 0;
    public static final int BUTTON_NEXT_PAGE = 1;
    public static final int BUTTON_SORT_NAME = 2;
    public static final int BUTTON_SORT_WORKSTATION = 3;
    private static final int PLAYER_SLOTS = 36;

    private final BlockPos providerPos;
    private final BlockPos accessPos;
    private final int guideSlots;
    private final DataSlot page = DataSlot.standalone();

    public WixiePatternProviderMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf data) {
        this(containerId, inventory, data.readBlockPos(), data.readVarInt(), data.readBlockPos());
    }

    private WixiePatternProviderMenu(
            int containerId, Inventory inventory, BlockPos pos, int advertisedSlots, BlockPos accessPos
    ) {
        this(containerId, inventory,
                inventory.player.level().getBlockEntity(pos) instanceof WixiePatternProviderBlockEntity provider
                        ? provider.getGuideHandler()
                        : new ItemStackHandler(Math.max(PAGE_SIZE, advertisedSlots)),
                pos, accessPos, Math.max(PAGE_SIZE, advertisedSlots));
    }

    public WixiePatternProviderMenu(
            int containerId, Inventory inventory, WixiePatternProviderBlockEntity provider
    ) {
        this(containerId, inventory, provider.getGuideHandler(), provider.getBlockPos(), provider.getBlockPos(),
                provider.getGuideCapacity());
    }

    private WixiePatternProviderMenu(
            int containerId, Inventory inventory, ItemStackHandler guides,
            BlockPos pos, BlockPos accessPos, int capacity
    ) {
        super(ModMenus.WIXIE_PATTERN_PROVIDER.get(), containerId);
        providerPos = pos.immutable();
        this.accessPos = accessPos.immutable();
        guideSlots = Math.min(guides.getSlots(), Math.max(PAGE_SIZE, capacity));
        addDataSlot(page);

        for (int slot = 0; slot < guideSlots; slot++) {
            int pageSlot = slot % PAGE_SIZE;
            int row = pageSlot / 9;
            int column = pageSlot % 9;
            final int pageIndex = slot / PAGE_SIZE;
            addSlot(new SlotItemHandler(guides, slot, 8 + column * 18, 18 + row * 18) {
                @Override
                public int getMaxStackSize() {
                    return 1;
                }

                @Override
                public boolean isActive() {
                    return getPage() == pageIndex;
                }
            });
        }
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9,
                        8 + column * 18, 85 + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 143));
        }
    }

    public int getPage() {
        return Math.max(0, Math.min(getPageCount() - 1, page.get()));
    }

    public int getPageCount() {
        return Math.max(1, (guideSlots + PAGE_SIZE - 1) / PAGE_SIZE);
    }

    public int getGuideSlots() {
        return guideSlots;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id == BUTTON_PREVIOUS_PAGE) {
            page.set(Math.max(0, getPage() - 1));
            return true;
        }
        if (id == BUTTON_NEXT_PAGE) {
            page.set(Math.min(getPageCount() - 1, getPage() + 1));
            return true;
        }
        if (!player.level().isClientSide
                && player.level().getBlockEntity(providerPos) instanceof WixiePatternProviderBlockEntity provider) {
            if (id == BUTTON_SORT_NAME) {
                provider.sortGuidesByName();
                return true;
            }
            if (id == BUTTON_SORT_WORKSTATION) {
                provider.sortGuidesByWorkstation();
                return true;
            }
        }
        return false;
    }

    @Override
    public void clicked(int slotId, int button, ClickType clickType, Player player) {
        if (slotId >= 0 && slotId < guideSlots && button == 1
                && clickType == ClickType.PICKUP && getCarried().isEmpty()
                && player.level().getBlockEntity(providerPos) instanceof WixiePatternProviderBlockEntity provider
                && provider.toggleGuideMode(slotId)) {
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem();
        ItemStack original = source.copy();
        boolean moved = index < guideSlots
                ? moveItemStackTo(source, guideSlots, guideSlots + PLAYER_SLOTS, true)
                : moveItemStackTo(source, 0, guideSlots, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY);
        else slot.setChanged();
        slot.onTake(player, source);
        return original;
    }

    @Override
    public boolean stillValid(Player player) {
        boolean providerValid = player.level().getBlockState(providerPos)
                .is(ModBlocks.WIXIE_PATTERN_PROVIDER.get());
        boolean accessValid = accessPos.equals(providerPos)
                ? providerValid
                : player.level().getBlockState(accessPos).is(ModBlocks.ADVANCED_STORAGE_LECTERN.get());
        return providerValid && accessValid && player.distanceToSqr(accessPos.getX() + 0.5D,
                accessPos.getY() + 0.5D, accessPos.getZ() + 0.5D) <= 64.0D;
    }
}
