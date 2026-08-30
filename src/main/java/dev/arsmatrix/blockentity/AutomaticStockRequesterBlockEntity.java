package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.menu.AutomaticStockRequesterMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;
import java.util.List;

/** Maintains one configured stock target by submitting guarded Wixie orders. */
public final class AutomaticStockRequesterBlockEntity extends BlockEntity
        implements MenuProvider, IWandable {
    public static final int MAX_AMOUNT = 9999;
    public static final int MAX_UPGRADE_TIER = 3;
    private static final int[] AMOUNT_LIMITS = {64, 256, 1024, MAX_AMOUNT};
    private static final int CHECK_INTERVAL = 20;
    private static final int SETTLE_TICKS = 100;

    private final ItemStackHandler catalyst = new ItemStackHandler(1) {
        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return stack.is(ItemsRegistry.MANIPULATION_ESSENCE.get());
        }

        @Override
        protected void onContentsChanged(int slot) {
            sync();
        }
    };
    private ItemStack target = ItemStack.EMPTY;
    private GlobalPos targetContainer;
    private Direction targetFace;
    private GlobalPos orderTerminal;
    private int minimumStock = 64;
    private int requestAmount = 64;
    private int upgradeTier;
    private int currentStock;
    private int tickCounter;
    private int settleTicks;
    private boolean requestPending;
    private long automaticRequestStartGameTime;
    private int automaticRequestedAmount;
    private UUID notificationPlayer;
    private boolean notificationsEnabled = true;
    private OperatingState state = OperatingState.NO_TARGET;

    public AutomaticStockRequesterBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AUTOMATIC_STOCK_REQUESTER.get(), pos, state);
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.automatic_stock_requester");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        notificationPlayer = player.getUUID();
        setChanged();
        return new AutomaticStockRequesterMenu(containerId, inventory, this);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel) || ++tickCounter % CHECK_INTERVAL != 0) return;
        if (level.hasNeighborSignal(worldPosition)) {
            setState(OperatingState.REDSTONE_PAUSED);
            return;
        }
        if (target.isEmpty()) {
            currentStock = 0;
            requestPending = false;
            setState(OperatingState.NO_TARGET);
            return;
        }
        IItemHandler inventory = resolveTargetHandler();
        WixieOrderTerminalBlockEntity terminal = resolveTerminal();
        if (inventory == null || terminal == null) {
            currentStock = inventory == null ? 0 : countMatching(inventory, target);
            setState(OperatingState.MISSING_BINDING);
            return;
        }

        currentStock = countMatching(inventory, target);
        if (currentStock >= minimumStock) {
            boolean completedAutomaticRequest = requestPending;
            requestPending = false;
            settleTicks = 0;
            setState(OperatingState.STOCKED);
            if (completedAutomaticRequest) notifyDeliveryComplete();
            return;
        }

        if (requestPending) {
            if (terminal.hasActiveOrderFor(target)) {
                settleTicks = 0;
                setState(OperatingState.WAITING_ORDER);
                return;
            }
            WixieOrderTerminalBlockEntity.AutomationTransferResult transfer =
                    terminal.transferStoredForAutomation(
                            target, Math.max(1, minimumStock - currentStock), inventory);
            if (transfer.moved() > 0) {
                currentStock = countMatching(inventory, target);
                settleTicks = 0;
                if (currentStock >= minimumStock) {
                    requestPending = false;
                    setState(OperatingState.STOCKED);
                    notifyDeliveryComplete();
                } else {
                    setState(OperatingState.DELIVERING);
                }
                return;
            }
            if (transfer.available()) {
                setState(OperatingState.OUTPUT_BLOCKED);
                return;
            }
            settleTicks += CHECK_INTERVAL;
            if (settleTicks < SETTLE_TICKS) {
                setState(OperatingState.SETTLING);
                return;
            }
            requestPending = false;
            settleTicks = 0;
        }

        // Do not consume a catalyst or create another order when the destination
        // cannot accept even one result. Orders that were already accepted are
        // handled above and remain safely buffered until space becomes available.
        ItemStack insertionProbe = target.copyWithCount(1);
        if (!ItemHandlerHelper.insertItemStacked(inventory, insertionProbe, true).isEmpty()) {
            setState(OperatingState.OUTPUT_BLOCKED);
            return;
        }

        if (catalyst.getStackInSlot(0).isEmpty()) {
            ItemStack supplied = terminal.takeOneStoredForAutomation(
                    new ItemStack(ItemsRegistry.MANIPULATION_ESSENCE.get()));
            if (!supplied.isEmpty()) catalyst.insertItem(0, supplied, false);
        }
        if (catalyst.getStackInSlot(0).isEmpty()) {
            setState(OperatingState.NO_CATALYST);
            return;
        }
        WixieOrderTerminalBlockEntity.AutomaticRequestResult result =
                terminal.requestAutomatically(target, requestAmount);
        switch (result) {
            case ACCEPTED -> {
                catalyst.extractItem(0, 1, false);
                requestPending = true;
                settleTicks = 0;
                automaticRequestStartGameTime = level.getGameTime();
                automaticRequestedAmount = requestAmount;
                setState(OperatingState.REQUESTED);
                notifyPlayer("message.ars_arcane_matrix.stock_requester.submitted",
                        target.getHoverName(), requestAmount);
            }
            case BUSY -> setState(OperatingState.TERMINAL_BUSY);
            case RECIPE_UNAVAILABLE -> setState(OperatingState.RECIPE_UNAVAILABLE);
            case NO_PEDESTAL -> setState(OperatingState.NO_PEDESTAL);
        }
    }

    public ItemStackHandler getCatalystHandler() {
        return catalyst;
    }

    public ItemStack getTarget() {
        return target.copy();
    }

    public void setTarget(ItemStack stack) {
        target = stack.isEmpty() ? ItemStack.EMPTY : stack.copyWithCount(1);
        requestPending = false;
        settleTicks = 0;
        automaticRequestStartGameTime = 0L;
        automaticRequestedAmount = 0;
        sync();
    }

    public void setTarget(ItemStack stack, Player player) {
        notificationPlayer = player.getUUID();
        setTarget(stack);
    }

    public void setNotificationPlayer(Player player) {
        notificationPlayer = player.getUUID();
        sync();
    }

    public void clearTarget() {
        setTarget(ItemStack.EMPTY);
    }

    public int getMinimumStock() { return minimumStock; }
    public int getRequestAmount() { return requestAmount; }
    public int getCurrentStock() { return currentStock; }
    public int getUpgradeTier() { return upgradeTier; }
    public int getAmountLimit() { return amountLimitForTier(upgradeTier); }
    public OperatingState getState() { return state; }
    public boolean hasTargetContainer() { return targetContainer != null; }
    public boolean hasOrderTerminal() { return orderTerminal != null; }
    public boolean isNotificationsEnabled() { return notificationsEnabled; }

    public static int amountLimitForTier(int tier) {
        return AMOUNT_LIMITS[Math.max(0, Math.min(MAX_UPGRADE_TIER, tier))];
    }

    public void setUpgradeTier(int tier) {
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tier));
        int limit = getAmountLimit();
        minimumStock = Math.max(1, Math.min(limit, minimumStock));
        requestAmount = Math.max(1, Math.min(limit, requestAmount));
        sync();
    }

    public void toggleNotifications() {
        notificationsEnabled = !notificationsEnabled;
        sync();
    }

    public void adjustMinimum(int delta) {
        minimumStock = Math.max(1, Math.min(getAmountLimit(), minimumStock + delta));
        requestPending = false;
        settleTicks = 0;
        automaticRequestStartGameTime = 0L;
        automaticRequestedAmount = 0;
        sync();
    }

    public void adjustRequestAmount(int delta) {
        requestAmount = Math.max(1, Math.min(getAmountLimit(), requestAmount + delta));
        sync();
    }

    private IItemHandler resolveTargetHandler() {
        if (targetContainer == null || level == null || level.getServer() == null) return null;
        ServerLevel targetLevel = level.getServer().getLevel(targetContainer.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(targetContainer.pos())) return null;
        if (targetFace != null) {
            IItemHandler sided = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, targetContainer.pos(), targetFace);
            if (sided != null) return sided;
        }
        IItemHandler unsided = targetLevel.getCapability(
                Capabilities.ItemHandler.BLOCK, targetContainer.pos(), null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            IItemHandler sided = targetLevel.getCapability(
                    Capabilities.ItemHandler.BLOCK, targetContainer.pos(), direction);
            if (sided != null) return sided;
        }
        return null;
    }

    private WixieOrderTerminalBlockEntity resolveTerminal() {
        if (orderTerminal == null || level == null || level.getServer() == null) return null;
        ServerLevel terminalLevel = level.getServer().getLevel(orderTerminal.dimension());
        if (terminalLevel == null || !terminalLevel.hasChunkAt(orderTerminal.pos())) return null;
        BlockEntity blockEntity = terminalLevel.getBlockEntity(orderTerminal.pos());
        if (blockEntity instanceof WixieOrderTerminalBlockEntity terminal) return terminal;
        if (blockEntity instanceof AdvancedStorageLecternBlockEntity lectern) {
            return lectern.getOrderEngine();
        }
        return null;
    }

    private static int countMatching(IItemHandler handler, ItemStack template) {
        long count = 0L;
        for (int slot = 0; slot < handler.getSlots(); slot++) {
            ItemStack stack = handler.getStackInSlot(slot);
            if (ItemStack.isSameItemSameComponents(stack, template)) count += stack.getCount();
        }
        return (int) Math.min(Integer.MAX_VALUE, count);
    }

    @Override
    public Result onFirstConnection(
            GlobalPos targetPos, @Nullable Direction face,
            @Nullable LivingEntity entity, Player player
    ) {
        return bind(targetPos, face, player);
    }

    @Override
    public Result onLastConnection(
            GlobalPos targetPos, @Nullable Direction face,
            @Nullable LivingEntity entity, Player player
    ) {
        return bind(targetPos, face, player);
    }

    private Result bind(GlobalPos targetPos, @Nullable Direction face, Player player) {
        if (level == null || level.getServer() == null
                || targetPos.dimension().equals(level.dimension())
                && targetPos.pos().equals(worldPosition)) return Result.FAIL;
        ServerLevel targetLevel = level.getServer().getLevel(targetPos.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(targetPos.pos())) return Result.FAIL;
        notificationPlayer = player.getUUID();
        BlockEntity blockEntity = targetLevel.getBlockEntity(targetPos.pos());
        if (blockEntity instanceof WixieOrderTerminalBlockEntity
                || blockEntity instanceof AdvancedStorageLecternBlockEntity) {
            orderTerminal = targetPos;
            player.sendSystemMessage(Component.translatable(
                    "message.ars_arcane_matrix.stock_requester.terminal_bound"));
            sync();
            return Result.SUCCESS;
        }
        if (targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, targetPos.pos(), face) != null
                || targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, targetPos.pos(), null) != null) {
            targetContainer = targetPos;
            targetFace = face;
            player.sendSystemMessage(Component.translatable(
                    "message.ars_arcane_matrix.stock_requester.container_bound"));
            sync();
            return Result.SUCCESS;
        }
        return Result.FAIL;
    }

    @Override
    public Result onClearConnections(Player player) {
        targetContainer = null;
        targetFace = null;
        orderTerminal = null;
        requestPending = false;
        settleTicks = 0;
        automaticRequestStartGameTime = 0L;
        automaticRequestedAmount = 0;
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.stock_requester.bindings_cleared"));
        sync();
        return Result.SUCCESS;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        ItemStack stack = catalyst.getStackInSlot(0);
        if (!stack.isEmpty()) {
            Containers.dropItemStack(level, worldPosition.getX() + 0.5D,
                    worldPosition.getY() + 0.5D, worldPosition.getZ() + 0.5D, stack.copy());
            catalyst.setStackInSlot(0, ItemStack.EMPTY);
        }
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Catalyst", catalyst.serializeNBT(registries));
        tag.put("Target", target.saveOptional(registries));
        tag.putInt("MinimumStock", minimumStock);
        tag.putInt("RequestAmount", requestAmount);
        tag.putInt("UpgradeTier", upgradeTier);
        tag.putInt("CurrentStock", currentStock);
        tag.putBoolean("RequestPending", requestPending);
        tag.putInt("SettleTicks", settleTicks);
        tag.putLong("AutomaticRequestStartGameTime", automaticRequestStartGameTime);
        tag.putInt("AutomaticRequestedAmount", automaticRequestedAmount);
        tag.putBoolean("NotificationsEnabled", notificationsEnabled);
        tag.putString("OperatingState", state.name());
        if (targetContainer != null) tag.put("TargetContainer", saveGlobalPos(targetContainer));
        if (targetFace != null) tag.putString("TargetFace", targetFace.getSerializedName());
        if (orderTerminal != null) tag.put("OrderTerminal", saveGlobalPos(orderTerminal));
        if (notificationPlayer != null) tag.putUUID("NotificationPlayer", notificationPlayer);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        catalyst.deserializeNBT(registries, tag.getCompound("Catalyst"));
        target = ItemStack.parseOptional(registries, tag.getCompound("Target"));
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tag.getInt("UpgradeTier")));
        int amountLimit = getAmountLimit();
        minimumStock = Math.max(1, Math.min(amountLimit, tag.getInt("MinimumStock")));
        requestAmount = Math.max(1, Math.min(amountLimit, tag.getInt("RequestAmount")));
        currentStock = Math.max(0, tag.getInt("CurrentStock"));
        requestPending = tag.getBoolean("RequestPending");
        settleTicks = Math.max(0, tag.getInt("SettleTicks"));
        automaticRequestStartGameTime = Math.max(0L,
                tag.getLong("AutomaticRequestStartGameTime"));
        automaticRequestedAmount = Math.max(0, tag.getInt("AutomaticRequestedAmount"));
        notificationsEnabled = !tag.contains("NotificationsEnabled")
                || tag.getBoolean("NotificationsEnabled");
        targetContainer = tag.contains("TargetContainer")
                ? loadGlobalPos(tag.getCompound("TargetContainer")) : null;
        targetFace = tag.contains("TargetFace")
                ? Direction.byName(tag.getString("TargetFace")) : null;
        orderTerminal = tag.contains("OrderTerminal")
                ? loadGlobalPos(tag.getCompound("OrderTerminal")) : null;
        notificationPlayer = tag.hasUUID("NotificationPlayer")
                ? tag.getUUID("NotificationPlayer") : null;
        try {
            state = OperatingState.valueOf(tag.getString("OperatingState"));
        } catch (IllegalArgumentException ignored) {
            state = target.isEmpty() ? OperatingState.NO_TARGET : OperatingState.MISSING_BINDING;
        }
    }

    private static CompoundTag saveGlobalPos(GlobalPos pos) {
        CompoundTag tag = new CompoundTag();
        tag.putString("Dimension", pos.dimension().location().toString());
        tag.putLong("Pos", pos.pos().asLong());
        return tag;
    }

    private static GlobalPos loadGlobalPos(CompoundTag tag) {
        ResourceLocation id = ResourceLocation.tryParse(tag.getString("Dimension"));
        if (id == null) id = Level.OVERWORLD.location();
        return GlobalPos.of(ResourceKey.create(Registries.DIMENSION, id),
                BlockPos.of(tag.getLong("Pos")));
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void setState(OperatingState next) {
        if (state != next) {
            state = next;
            sync();
            if (isNotifiableProblem(next)) notifyPlayer(next.translationKey());
        } else {
            setChanged();
        }
    }

    private static boolean isNotifiableProblem(OperatingState state) {
        return switch (state) {
            case MISSING_BINDING, NO_CATALYST, TERMINAL_BUSY,
                    RECIPE_UNAVAILABLE, NO_PEDESTAL, OUTPUT_BLOCKED -> true;
            default -> false;
        };
    }

    private void notifyPlayer(String translationKey, Object... arguments) {
        if (!notificationsEnabled || !(level instanceof ServerLevel serverLevel)) return;
        UUID recipient = resolveNotificationPlayer();
        if (recipient == null) return;
        ServerPlayer player = serverLevel.getServer().getPlayerList().getPlayer(recipient);
        if (player != null) player.displayClientMessage(
                Component.translatable(translationKey, arguments), false);
    }

    private void notifyDeliveryComplete() {
        long elapsedTicks = automaticRequestStartGameTime <= 0L || level == null
                ? 0L : Math.max(0L, level.getGameTime() - automaticRequestStartGameTime);
        long elapsedSeconds = Math.max(1L, (elapsedTicks + 19L) / 20L);
        notifyPlayer("message.ars_arcane_matrix.stock_requester.delivered",
                target.getHoverName(), Math.max(1, automaticRequestedAmount), elapsedSeconds);
        automaticRequestStartGameTime = 0L;
        automaticRequestedAmount = 0;
        setChanged();
    }

    @Nullable
    private UUID resolveNotificationPlayer() {
        if (!(level instanceof ServerLevel serverLevel)) return notificationPlayer;
        if (notificationPlayer != null) return notificationPlayer;
        List<ServerPlayer> online = serverLevel.getServer().getPlayerList().getPlayers();
        if (online.size() == 1) {
            notificationPlayer = online.getFirst().getUUID();
            setChanged();
        }
        return notificationPlayer;
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    public enum OperatingState {
        NO_TARGET("message.ars_arcane_matrix.stock_requester.state.no_target"),
        MISSING_BINDING("message.ars_arcane_matrix.stock_requester.state.missing_binding"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        STOCKED("message.ars_arcane_matrix.stock_requester.state.stocked"),
        NO_CATALYST("message.ars_arcane_matrix.stock_requester.state.no_catalyst"),
        TERMINAL_BUSY("message.ars_arcane_matrix.stock_requester.state.terminal_busy"),
        RECIPE_UNAVAILABLE("message.ars_arcane_matrix.stock_requester.state.recipe_unavailable"),
        NO_PEDESTAL("message.ars_arcane_matrix.stock_requester.state.no_pedestal"),
        REQUESTED("message.ars_arcane_matrix.stock_requester.state.requested"),
        WAITING_ORDER("message.ars_arcane_matrix.stock_requester.state.waiting_order"),
        DELIVERING("message.ars_arcane_matrix.stock_requester.state.delivering"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.stock_requester.state.output_blocked"),
        SETTLING("message.ars_arcane_matrix.stock_requester.state.settling");

        private final String translationKey;

        OperatingState(String translationKey) { this.translationKey = translationKey; }
        public String translationKey() { return translationKey; }
    }
}
