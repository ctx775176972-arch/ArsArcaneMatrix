package dev.arsmatrix.menu;

import dev.arsmatrix.blockentity.StarbuncleLogisticsHubBlockEntity;
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

import java.util.ArrayList;
import java.util.List;

public final class StarbuncleLogisticsHubMenu extends AbstractContainerMenu {
    public static final int RECALL_ALL = 0;
    public static final int RECALL_INCOMPLETE = 1;
    public static final int TOGGLE_AUTO_RECALL = 2;
    public static final int TOGGLE_ALLOW_LIST = 3;
    public static final int CYCLE_MATCH_MODE = 4;
    public static final int RECALL_SELECTED = 5;
    public static final int REGISTER_NEARBY = 6;
    public static final int CLEAR_REGISTRATIONS = 7;
    public static final int TOGGLE_STUCK_TELEPORT = 8;
    public static final int SELECT_ROUTE_BASE = 100;
    private static final int FILTER_SLOTS = 27;
    private static final int BUFFER_SLOTS = 18;
    private final StarbuncleLogisticsHubBlockEntity hub;
    private final BlockPos pos;
    private final ContainerData data;
    private final List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes;
    private final int initialSelectedRoute;

    public StarbuncleLogisticsHubMenu(int id, Inventory inv, RegistryFriendlyByteBuf buffer) {
        this(id, inv, readOpenData(buffer));
    }
    private StarbuncleLogisticsHubMenu(int id, Inventory inv, OpenData openData) {
        this(id, inv, openData.pos(), null, openData.routes(), openData.selectedStarbuncle());
    }
    private StarbuncleLogisticsHubMenu(int id, Inventory inv, BlockPos pos,
                                      StarbuncleLogisticsHubBlockEntity supplied) {
        this(id, inv, pos, supplied, supplied == null ? List.of() : supplied.routeSnapshots(),
                supplied == null ? null : supplied.getHighlightedStarbuncle());
    }
    private StarbuncleLogisticsHubMenu(int id, Inventory inv, BlockPos pos,
                                      StarbuncleLogisticsHubBlockEntity supplied,
                                      List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes,
                                      java.util.UUID selectedStarbuncle) {
        this(id, inv, pos, supplied != null ? supplied
                        : inv.player.level().getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity h ? h : null,
                supplied != null ? supplied.getInventory()
                        : inv.player.level().getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity h
                        ? h.getInventory() : new ItemStackHandler(BUFFER_SLOTS),
                supplied != null ? supplied.getFilters()
                        : inv.player.level().getBlockEntity(pos) instanceof StarbuncleLogisticsHubBlockEntity h
                        ? h.getFilters() : new ItemStackHandler(FILTER_SLOTS),
                supplied == null ? new SimpleContainerData(8) : serverData(supplied), routes,
                selectedStarbuncle);
    }
    public StarbuncleLogisticsHubMenu(int id, Inventory inv, StarbuncleLogisticsHubBlockEntity hub) {
        this(id, inv, hub.getBlockPos(), hub);
    }
    private StarbuncleLogisticsHubMenu(int id, Inventory inv, BlockPos pos,
                                      StarbuncleLogisticsHubBlockEntity hub,
                                      ItemStackHandler handler, ItemStackHandler filters, ContainerData data,
                                      List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes,
                                      java.util.UUID selectedStarbuncle) {
        super(ModMenus.STARBUNCLE_LOGISTICS_HUB.get(), id);
        this.hub = hub;
        this.pos = pos.immutable();
        this.data = data;
        this.routes = List.copyOf(routes);
        int selectedIndex = -1;
        if (selectedStarbuncle != null) {
            for (int index = 0; index < routes.size(); index++) {
                if (routes.get(index).id().equals(selectedStarbuncle)) {
                    selectedIndex = index;
                    break;
                }
            }
        }
        this.initialSelectedRoute = selectedIndex < 0 ? 0 : selectedIndex;
        addDataSlots(data);
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new SlotItemHandler(filters, col + row * 9, 8 + col * 18, 35 + row * 18));
        for (int row = 0; row < 2; row++) for (int col = 0; col < 9; col++)
            addSlot(new SlotItemHandler(handler, col + row * 9, 8 + col * 18, 101 + row * 18));
        for (int row = 0; row < 3; row++) for (int col = 0; col < 9; col++)
            addSlot(new Slot(inv, col + row * 9 + 9, 8 + col * 18, 154 + row * 18));
        for (int col = 0; col < 9; col++) addSlot(new Slot(inv, col, 8 + col * 18, 212));
    }
    private static ContainerData serverData(StarbuncleLogisticsHubBlockEntity hub) {
        return new ContainerData() {
            @Override public int get(int index) { return switch (index) {
                case 0 -> hub.getNearbyOwned();
                case 1 -> hub.getState().ordinal();
                case 2 -> hub.isAllowList() ? 1 : 0;
                case 3 -> hub.getMatchMode().ordinal();
                case 4 -> hub.isAutomaticRecall() ? 1 : 0;
                case 5 -> hub.isTeleportOnStuck() ? 1 : 0;
                case 6 -> hub.getUpgradeTier();
                case 7 -> hub.getSharedThroughput();
                default -> 0;
            }; }
            @Override public void set(int index, int value) {}
            @Override public int getCount() { return 8; }
        };
    }
    public int getNearbyOwned() { return data.get(0); }
    public StarbuncleLogisticsHubBlockEntity.HubState getHubState() {
        int i = Math.max(0, Math.min(StarbuncleLogisticsHubBlockEntity.HubState.values().length - 1, data.get(1)));
        return StarbuncleLogisticsHubBlockEntity.HubState.values()[i];
    }
    public boolean isAllowList() { return data.get(2) != 0; }
    public dev.arsmatrix.item.HubFilterScrollItem.MatchMode getMatchMode() {
        var modes = dev.arsmatrix.item.HubFilterScrollItem.MatchMode.values();
        return modes[Math.max(0, Math.min(modes.length - 1, data.get(3)))];
    }
    public boolean isAutomaticRecall() { return data.get(4) != 0; }
    public boolean isTeleportOnStuck() { return data.get(5) != 0; }
    public int getUpgradeTier() { return data.get(6); }
    public int getSharedThroughput() { return data.get(7); }
    public List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> getRoutes() { return routes; }
    public int getInitialSelectedRoute() { return initialSelectedRoute; }
    @Override public boolean clickMenuButton(Player player, int id) {
        if (hub == null) return false;
        switch (id) {
            case RECALL_ALL -> hub.recallAll(player);
            case RECALL_INCOMPLETE -> hub.recallIncomplete(player);
            case TOGGLE_AUTO_RECALL -> hub.toggleAutomaticRecall();
            case TOGGLE_ALLOW_LIST -> hub.toggleAllowList();
            case CYCLE_MATCH_MODE -> hub.cycleMatchMode();
            case RECALL_SELECTED -> hub.recallHighlighted(player);
            case REGISTER_NEARBY -> hub.registerNearby(player);
            case CLEAR_REGISTRATIONS -> hub.clearRegistrations(player);
            case TOGGLE_STUCK_TELEPORT -> hub.toggleTeleportOnStuck();
            default -> {
                int routeIndex = id - SELECT_ROUTE_BASE;
                if (routeIndex < 0 || routeIndex >= routes.size()) return false;
                hub.highlight(routes.get(routeIndex).id());
            }
        }
        return true;
    }

    @Override
    public void clicked(int slotId, int button, net.minecraft.world.inventory.ClickType clickType,
                        Player player) {
        if (slotId >= 0 && slotId < FILTER_SLOTS && hub != null) {
            ItemStack carried = getCarried();
            hub.getFilters().setStackInSlot(slotId,
                    carried.isEmpty() ? ItemStack.EMPTY : carried.copyWithCount(1));
            return;
        }
        super.clicked(slotId, button, clickType, player);
    }
    @Override public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        ItemStack source = slot.getItem(), original = source.copy();
        if (index < FILTER_SLOTS) return ItemStack.EMPTY;
        int playerStart = FILTER_SLOTS + BUFFER_SLOTS;
        boolean moved = index < playerStart ? moveItemStackTo(source, playerStart, playerStart + 36, true)
                : moveItemStackTo(source, FILTER_SLOTS, playerStart, false);
        if (!moved) return ItemStack.EMPTY;
        if (source.isEmpty()) slot.set(ItemStack.EMPTY); else slot.setChanged();
        return original;
    }
    @Override public boolean stillValid(Player player) {
        return player.level().getBlockState(pos).is(ModBlocks.STARBUNCLE_LOGISTICS_HUB.get())
                && player.distanceToSqr(pos.getX() + .5, pos.getY() + .5, pos.getZ() + .5) <= 64;
    }

    @Override public void broadcastChanges() {
        super.broadcastChanges();
        if (hub != null) hub.keepHighlightActive();
    }

    private static OpenData readOpenData(RegistryFriendlyByteBuf buffer) {
        BlockPos pos = buffer.readBlockPos();
        int count = Math.min(256, buffer.readVarInt());
        List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            java.util.UUID id = buffer.readUUID();
            String name = buffer.readUtf(128);
            List<StarbuncleLogisticsHubBlockEntity.RouteTarget> inputs = readTargets(buffer);
            List<StarbuncleLogisticsHubBlockEntity.RouteTarget> outputs = readTargets(buffer);
            routes.add(new StarbuncleLogisticsHubBlockEntity.RouteSnapshot(id, name, inputs, outputs));
        }
        java.util.UUID selectedStarbuncle = buffer.readBoolean() ? buffer.readUUID() : null;
        return new OpenData(pos, routes, selectedStarbuncle);
    }

    private static List<StarbuncleLogisticsHubBlockEntity.RouteTarget> readTargets(
            RegistryFriendlyByteBuf buffer) {
        int count = Math.min(64, buffer.readVarInt());
        List<StarbuncleLogisticsHubBlockEntity.RouteTarget> targets = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            targets.add(new StarbuncleLogisticsHubBlockEntity.RouteTarget(
                    buffer.readUtf(128), buffer.readBlockPos()));
        }
        return targets;
    }

    private record OpenData(BlockPos pos,
                            List<StarbuncleLogisticsHubBlockEntity.RouteSnapshot> routes,
                            java.util.UUID selectedStarbuncle) {}
}
