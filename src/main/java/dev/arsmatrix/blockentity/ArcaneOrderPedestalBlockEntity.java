package dev.arsmatrix.blockentity;

import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.UUID;

public final class ArcaneOrderPedestalBlockEntity extends BlockEntity {

    public static final int MAX_UPGRADE_TIER = 3;
    private static final int[] DISPATCH_INTERVALS = {10, 5, 2, 1};
    private static final int[] MAX_PARALLEL = {1, 2, 4, Integer.MAX_VALUE};

    private ItemStack virtualTarget = ItemStack.EMPTY;
    private int virtualTargetCount;
    private BlockPos assignedTerminal;
    private UUID requester;
    private OrderState orderState = OrderState.IDLE;
    private String detail = "";
    private int upgradeTier;

    public ArcaneOrderPedestalBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_ORDER_PEDESTAL.get(), pos, state);
    }

    /** Only the order terminal may assign virtual work to this display pedestal. */
    public void assignFromTerminal(ItemStack target, int count, UUID owner, BlockPos terminalPos) {
        virtualTarget = target.copyWithCount(1);
        virtualTargetCount = Math.max(1, count);
        assignedTerminal = terminalPos.immutable();
        requester = owner;
        orderState = OrderState.QUEUED;
        detail = "";
        sync();
    }

    public void setState(OrderState state, String newDetail) {
        if (orderState != state || !detail.equals(newDetail)) {
            orderState = state;
            detail = newDetail;
            sync();
        }
    }

    /** Clears the hologram in the same server tick that the terminal owns the completed output. */
    public void complete() {
        virtualTarget = ItemStack.EMPTY;
        virtualTargetCount = 0;
        assignedTerminal = null;
        orderState = OrderState.COMPLETE;
        detail = "";
        sync();
    }

    public void cancelFromTerminal() {
        virtualTarget = ItemStack.EMPTY;
        virtualTargetCount = 0;
        assignedTerminal = null;
        requester = null;
        orderState = OrderState.IDLE;
        detail = "";
        sync();
    }

    public boolean hasOrder() {
        return !virtualTarget.isEmpty() && orderState != OrderState.COMPLETE;
    }

    public ItemStack getVirtualTarget() {
        return virtualTarget.copyWithCount(1);
    }

    public int getVirtualTargetCount() { return Math.max(1, virtualTargetCount); }

    public boolean canBeClaimedBy(BlockPos terminalPos) {
        return assignedTerminal == null || assignedTerminal.equals(terminalPos);
    }

    /** Claims legacy orders which predate terminal ownership. Server ticks are single-threaded. */
    public boolean claimFor(BlockPos terminalPos) {
        if (!canBeClaimedBy(terminalPos)) return false;
        if (assignedTerminal == null) {
            assignedTerminal = terminalPos.immutable();
            sync();
        }
        return true;
    }

    public UUID getRequester() {
        return requester;
    }

    public int getUpgradeTier() { return upgradeTier; }

    public void setUpgradeTier(int tier) {
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tier));
        sync();
    }

    public int getDispatchIntervalTicks() { return dispatchIntervalTicks(upgradeTier); }
    public int getMaxParallelJobs() { return maxParallelJobs(upgradeTier); }

    public static int dispatchIntervalTicks(int tier) {
        return DISPATCH_INTERVALS[Math.max(0, Math.min(MAX_UPGRADE_TIER, tier))];
    }

    public static int maxParallelJobs(int tier) {
        return MAX_PARALLEL[Math.max(0, Math.min(MAX_UPGRADE_TIER, tier))];
    }

    public static Component maxParallelLabel(int tier) {
        int value = maxParallelJobs(tier);
        return value == Integer.MAX_VALUE
                ? Component.translatable("tooltip.ars_arcane_matrix.order_pedestal.parallel.all")
                : Component.literal(Integer.toString(value));
    }

    public Component getStatusMessage() {
        Component status;
        if (orderState == OrderState.IDLE || orderState == OrderState.COMPLETE) {
            status = Component.translatable("message.ars_arcane_matrix.order_pedestal.idle");
        } else {
            Component state = Component.translatable(orderState.translationKey());
            status = detail.isBlank()
                    ? Component.translatable("message.ars_arcane_matrix.order_pedestal.status", state)
                    : Component.translatable("message.ars_arcane_matrix.order_pedestal.status_detail", state, detail);
        }
        return Component.translatable("message.ars_arcane_matrix.order_pedestal.tier_status",
                upgradeTier, maxParallelLabel(upgradeTier), status);
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("VirtualTarget", virtualTarget.copyWithCount(1).saveOptional(registries));
        tag.putInt("VirtualTargetCount", virtualTargetCount);
        if (assignedTerminal != null) tag.putLong("AssignedTerminal", assignedTerminal.asLong());
        if (requester != null) {
            tag.putUUID("Requester", requester);
        }
        tag.putString("OrderState", orderState.name());
        tag.putString("Detail", detail);
        tag.putInt("UpgradeTier", upgradeTier);
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        virtualTarget = ItemStack.parseOptional(registries, tag.getCompound("VirtualTarget"));
        virtualTargetCount = tag.contains("VirtualTargetCount")
                ? Math.max(0, tag.getInt("VirtualTargetCount")) : virtualTarget.getCount();
        if (!virtualTarget.isEmpty()) virtualTarget.setCount(1);
        assignedTerminal = tag.contains("AssignedTerminal")
                ? BlockPos.of(tag.getLong("AssignedTerminal")) : null;
        requester = tag.hasUUID("Requester") ? tag.getUUID("Requester") : null;
        detail = tag.getString("Detail");
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tag.getInt("UpgradeTier")));
        try {
            orderState = OrderState.valueOf(tag.getString("OrderState"));
        } catch (IllegalArgumentException ignored) {
            orderState = virtualTarget.isEmpty() ? OrderState.IDLE : OrderState.QUEUED;
        }
    }

    @Override
    public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    private void sync() {
        setChanged();
        if (level != null) {
            BlockState state = getBlockState();
            level.sendBlockUpdated(worldPosition, state, state, Block.UPDATE_CLIENTS);
        }
    }

    public enum OrderState {
        IDLE("message.ars_arcane_matrix.order_state.idle"),
        QUEUED("message.ars_arcane_matrix.order_state.queued"),
        WAITING_MATERIALS("message.ars_arcane_matrix.order_state.waiting_materials"),
        WAITING_PATTERNS("message.ars_arcane_matrix.order_state.waiting_patterns"),
        WAITING_WORKSTATION("message.ars_arcane_matrix.order_state.waiting_workstation"),
        CRAFTING("message.ars_arcane_matrix.order_state.crafting"),
        COMPLETE("message.ars_arcane_matrix.order_state.complete"),
        FAILED("message.ars_arcane_matrix.order_state.failed");

        private final String translationKey;

        OrderState(String translationKey) {
            this.translationKey = translationKey;
        }

        public String translationKey() {
            return translationKey;
        }
    }
}
