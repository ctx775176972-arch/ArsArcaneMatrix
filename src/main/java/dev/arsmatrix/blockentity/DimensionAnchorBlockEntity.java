package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
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

import java.util.ArrayList;
import java.util.List;

/** Persistent loader for exactly the anchor's own chunk. */
public final class DimensionAnchorBlockEntity extends BlockEntity {
    public static final int SOURCE_COST_PER_SECOND = 100;
    public static final int SOURCE_RANGE = 8;
    public static final int EXPANDED_RADIUS = 1;
    private int sourcePaymentTicker;
    private boolean ticketActive;
    private int loadedRadius;
    private OperatingState state = OperatingState.ACTIVE;

    public DimensionAnchorBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DIMENSION_ANCHOR.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (level.hasNeighborSignal(worldPosition)) {
            sourcePaymentTicker = 0;
            releaseTickets(serverLevel);
            setState(OperatingState.REDSTONE_PAUSED);
            return;
        }
        int targetRadius = isExpandedStructure(level, worldPosition) ? EXPANDED_RADIUS : 0;
        if (ticketActive && loadedRadius != targetRadius) {
            releaseTickets(serverLevel);
            sourcePaymentTicker = 0;
        }
        if (sourcePaymentTicker <= 0) {
            if (!consumeSource(sourceCostForRadius(targetRadius))) {
                releaseTickets(serverLevel);
                setState(OperatingState.MISSING_SOURCE);
                return;
            }
            sourcePaymentTicker = 20;
        }
        sourcePaymentTicker--;
        setTickets(serverLevel, targetRadius);
        setState(OperatingState.ACTIVE);
    }

    private boolean consumeSource(int cost) {
        if (cost <= 0 || level == null) return true;
        var providers = SourceUtil.canTakeSource(worldPosition, level, SOURCE_RANGE);
        int available = 0;
        for (ISpecialSourceProvider provider : providers) {
            ISourceTile source = provider.getSource();
            if (source != null && source.canProvideSource()) {
                int needed = cost - available;
                available += Math.max(0, Math.min(needed, source.removeSource(needed, true)));
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
    public int getLoadedRadius() {
        return ticketActive ? loadedRadius : isExpandedStructure(level, worldPosition) ? EXPANDED_RADIUS : 0;
    }
    public int getLoadedChunkCount() {
        int diameter = getLoadedRadius() * 2 + 1;
        return diameter * diameter;
    }
    public int getSourceCostPerSecond() { return sourceCostForRadius(getLoadedRadius()); }

    public void releaseTicket() {
        if (level instanceof ServerLevel serverLevel) releaseTickets(serverLevel);
    }

    private void setTickets(ServerLevel serverLevel, int radius) {
        if (ticketActive && loadedRadius == radius) return;
        if (ticketActive) releaseTickets(serverLevel);
        ChunkPos center = new ChunkPos(worldPosition);
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                ModChunkLoading.DIMENSION_ANCHOR.forceChunk(serverLevel, worldPosition,
                        center.x + x, center.z + z, true, true);
            }
        }
        loadedRadius = radius;
        ticketActive = true;
        sync();
    }

    private void releaseTickets(ServerLevel serverLevel) {
        if (!ticketActive) return;
        ChunkPos center = new ChunkPos(worldPosition);
        for (int x = -loadedRadius; x <= loadedRadius; x++) {
            for (int z = -loadedRadius; z <= loadedRadius; z++) {
                ModChunkLoading.DIMENSION_ANCHOR.forceChunk(serverLevel, worldPosition,
                        center.x + x, center.z + z, false, true);
            }
        }
        ticketActive = false;
        sync();
    }

    private static int sourceCostForRadius(int radius) {
        int diameter = radius * 2 + 1;
        return SOURCE_COST_PER_SECOND * diameter * diameter;
    }

    public static List<BlockPos> expansionFramePositions(BlockPos anchor) {
        List<BlockPos> result = new ArrayList<>(8);
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) {
            if (x != 0 || z != 0) result.add(anchor.offset(x, 0, z));
        }
        return List.copyOf(result);
    }

    public static boolean isExpandedStructure(net.minecraft.world.level.Level level, BlockPos anchor) {
        if (level == null) return false;
        for (BlockPos frame : expansionFramePositions(anchor)) {
            if (!level.getBlockState(frame).is(ModBlocks.ARCANE_STRUCTURAL_FRAME.get())) return false;
        }
        return true;
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
        tag.putInt("LoadedRadius", loadedRadius);
        tag.putString("OperatingState", state.name());
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        ticketActive = tag.getBoolean("TicketActive");
        sourcePaymentTicker = Math.max(0, Math.min(20, tag.getInt("SourcePaymentTicker")));
        loadedRadius = Math.max(0, Math.min(EXPANDED_RADIUS, tag.getInt("LoadedRadius")));
        try { state = OperatingState.valueOf(tag.getString("OperatingState")); }
        catch (IllegalArgumentException ignored) { state = OperatingState.ACTIVE; }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
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
