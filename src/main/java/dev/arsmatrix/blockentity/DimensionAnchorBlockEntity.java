package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.world.ModChunkLoading;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Persistent loader for exactly the anchor's own chunk. */
public final class DimensionAnchorBlockEntity extends BlockEntity {
    public static final int SOURCE_COST_PER_SECOND = 100;
    public static final int SOURCE_RANGE = 8;
    private int sourcePaymentTicker;
    private boolean ticketActive;
    private OperatingState state = OperatingState.ACTIVE;

    public DimensionAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIMENSION_ANCHOR.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.hasNeighborSignal(worldPosition)) {
            sourcePaymentTicker = 0;
            setTicket(serverLevel, false);
            setState(OperatingState.REDSTONE_PAUSED);
            return;
        }
        if (sourcePaymentTicker <= 0) {
            if (!consumeSource(SOURCE_COST_PER_SECOND)) {
                setTicket(serverLevel, false);
                setState(OperatingState.MISSING_SOURCE);
                return;
            }
            sourcePaymentTicker = 20;
        }
        sourcePaymentTicker--;
        setTicket(serverLevel, true);
        setState(OperatingState.ACTIVE);
    }

    private boolean consumeSource(int cost) {
        if (cost <= 0 || level == null) return true;
        var providers = SourceUtil.canTakeSource(worldPosition, level, SOURCE_RANGE);
        int available = 0;
        for (ISpecialSourceProvider provider : providers) {
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) {
                available += Math.max(0,
                        source.removeSource(cost - Math.min(cost, available), true));
                if (available >= cost) break;
            }
        }
        if (available < cost) return false;
        int remaining = cost;
        for (ISpecialSourceProvider provider : providers) {
            if (remaining <= 0) break;
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) {
                remaining -= Math.max(0,
                        Math.min(remaining, source.removeSource(remaining, false)));
            }
        }
        return remaining == 0;
    }

    public boolean isTicketActive() { return ticketActive; }
    public OperatingState getState() { return state; }

    public void releaseTicket() {
        if (level instanceof ServerLevel serverLevel) setTicket(serverLevel, false);
    }

    private void setTicket(ServerLevel serverLevel, boolean active) {
        if (ticketActive == active) return;
        ChunkPos chunk = new ChunkPos(worldPosition);
        ModChunkLoading.DIMENSION_ANCHOR.forceChunk(serverLevel, worldPosition,
                chunk.x, chunk.z, active, true);
        ticketActive = active;
        sync();
    }

    private void setState(OperatingState newState) {
        if (state == newState) return;
        state = newState;
        sync();
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition,
                getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putBoolean("TicketActive", ticketActive);
        tag.putInt("SourcePaymentTicker", sourcePaymentTicker);
        tag.putString("OperatingState", state.name());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticketActive = tag.getBoolean("TicketActive");
        sourcePaymentTicker = Math.max(0, Math.min(20, tag.getInt("SourcePaymentTicker")));
        try { state = OperatingState.valueOf(tag.getString("OperatingState")); }
        catch (IllegalArgumentException ignored) { state = OperatingState.ACTIVE; }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public enum OperatingState {
        ACTIVE("message.ars_arcane_matrix.dimension_anchor.state.active"),
        MISSING_SOURCE("message.ars_arcane_matrix.dimension_anchor.state.missing_source"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.dimension_anchor.state.redstone_paused");
        private final String translationKey;
        OperatingState(String translationKey) { this.translationKey = translationKey; }
        public String translationKey() { return translationKey; }
    }
}
