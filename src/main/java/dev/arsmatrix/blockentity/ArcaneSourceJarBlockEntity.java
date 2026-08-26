package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.common.block.tile.SourcelinkTile;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A one-million Source buffer intended to make the native Sourcelink stage practical.
 * It actively pulls once per second, but only from true producers, never from
 * another jar or a machine, so multiple reservoirs cannot create transfer loops.
 */
public final class ArcaneSourceJarBlockEntity extends BlockEntity
        implements ISourceTile, ITooltipProvider {
    public static final int CAPACITY = 1_000_000;
    public static final int PULL_RANGE = 12;
    public static final int TOTAL_PULL_PER_SECOND = 50_000;
    public static final int PER_PROVIDER_PULL = 10_000;

    private final SourceStorage storage = new SourceStorage(
            CAPACITY, TOTAL_PULL_PER_SECOND, TOTAL_PULL_PER_SECOND) {
        @Override public void onContentsChanged() { sync(); }
    };
    private final ISpecialSourceProvider provider = new ISpecialSourceProvider() {
        @Override public ISourceTile getSource() { return ArcaneSourceJarBlockEntity.this; }
        @Override public boolean isValid() {
            return !isRemoved() && level != null
                    && level.getBlockEntity(worldPosition) == ArcaneSourceJarBlockEntity.this;
        }
        @Override public BlockPos getCurrentPos() { return worldPosition; }
    };

    private long ticks;
    private int lastPulled;

    public ArcaneSourceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_SOURCE_JAR.get(), pos, state);
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        ticks++;
        if (ticks == 1L || ticks % 200L == 1L) {
            SourceManager.INSTANCE.addInterface(level, provider);
        }
        if (ticks % 20L == 0L) {
            int pulled = pullFromProducers();
            if (pulled != lastPulled) {
                lastPulled = pulled;
                sync();
            }
        }
    }

    private int pullFromProducers() {
        if (level == null || getSource() >= CAPACITY) return 0;
        int remaining = Math.min(TOTAL_PULL_PER_SECOND, CAPACITY - getSource());
        int pulled = 0;
        double rangeSquared = (double) PULL_RANGE * PULL_RANGE;
        for (ISpecialSourceProvider candidate : SourceManager.INSTANCE.getCopySetForLevel(level)) {
            if (remaining <= 0) break;
            if (!candidate.isValid() || worldPosition.distSqr(candidate.getCurrentPos()) > rangeSquared) continue;
            ISourceTile source = candidate.getSource();
            if (!(source instanceof SourcelinkTile) && !(source instanceof MatrixCoreBlockEntity)) continue;
            int requested = Math.min(PER_PROVIDER_PULL, remaining);
            int available = Math.max(0, Math.min(requested, source.removeSource(requested, true)));
            int acceptable = Math.max(0, Math.min(available, storage.receiveSource(available, true)));
            if (acceptable <= 0) continue;
            int extracted = Math.max(0, Math.min(acceptable, source.removeSource(acceptable, false)));
            int accepted = storage.receiveSource(extracted, false);
            pulled += accepted;
            remaining -= accepted;
        }
        return pulled;
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) SourceManager.INSTANCE.addInterface(level, provider);
    }

    @Override public int getTransferRate() { return TOTAL_PULL_PER_SECOND; }
    @Override public boolean canAcceptSource() { return getSource() < CAPACITY; }
    @Override public boolean canProvideSource() { return getSource() > 0; }
    @Override public int getSource() { return storage.getSource(); }
    @Override public int getMaxSource() { return CAPACITY; }
    @Override public int setSource(int source) {
        storage.setSource(Math.max(0, Math.min(CAPACITY, source)));
        return getSource();
    }
    @Override public int addSource(int amount) {
        if (amount > 0) storage.receiveSource(amount, false);
        return getSource();
    }
    @Override public int addSource(int amount, boolean simulate) {
        return amount <= 0 ? 0 : storage.receiveSource(amount, simulate);
    }
    @Override public int removeSource(int amount) {
        if (amount > 0) storage.extractSource(amount, false);
        return getSource();
    }
    @Override public int removeSource(int amount, boolean simulate) {
        return amount <= 0 ? 0 : storage.extractSource(amount, simulate);
    }

    public int getLastPulled() { return lastPulled; }

    @Override public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.source_network.storage",
                getSource(), getMaxSource()));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_source_jar.pull",
                lastPulled, PULL_RANGE));
    }

    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Source", getSource());
        tag.putInt("LastPulled", lastPulled);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.setSource(Math.max(0, Math.min(CAPACITY, tag.getInt("Source"))));
        lastPulled = Math.max(0, tag.getInt("LastPulled"));
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
