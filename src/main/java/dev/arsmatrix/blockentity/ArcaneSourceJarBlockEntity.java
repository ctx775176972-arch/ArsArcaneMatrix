package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.source.SourceNetworkLinking;
import dev.arsmatrix.source.SourceNetworkSavedData;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
        implements ISourceTile, ITooltipProvider, IWandable {
    public static final int CAPACITY = 1_000_000;
    public static final int PULL_RANGE = 12;
    public static final int TOTAL_PULL_PER_SECOND = 50_000;
    public static final int PER_PROVIDER_PULL = 10_000;

    private final SourceStorage storage = new SourceStorage(
            CAPACITY, TOTAL_PULL_PER_SECOND, TOTAL_PULL_PER_SECOND) {
        @Override public int receiveSource(int amount, boolean simulate) {
            int accepted = super.receiveSource(amount, simulate);
            if (!simulate && accepted > 0) recordReceived(accepted);
            return accepted;
        }
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
    private int receivedThisSecond;
    private boolean linkedCache;
    private List<BlockPos> cachedProducers = List.of();

    public ArcaneSourceJarBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_SOURCE_JAR.get(), pos, state);
    }

    public void serverTick() {
        if (level == null || level.isClientSide) return;
        ticks++;
        if (ticks == 1L || ticks % 200L == 1L) {
            SourceManager.INSTANCE.addInterface(level, provider);
        }
        if (level instanceof ServerLevel serverLevel && (ticks == 1L || ticks % 100L == 0L)) {
            cachedProducers = NearbySourceProducerScanner.scan(serverLevel, worldPosition, PULL_RANGE);
        }
        if (ticks % 20L == 0L) {
            pullFromProducers();
            int received = receivedThisSecond;
            receivedThisSecond = 0;
            if (received != lastPulled) {
                lastPulled = received;
                sync();
            }
            if (level instanceof ServerLevel serverLevel) {
                boolean linked = SourceNetworkSavedData.get(serverLevel.getServer())
                        .targetForJar(globalPos(serverLevel)) != null;
                if (linked != linkedCache) {
                    linkedCache = linked;
                    sync();
                }
            }
        }
    }

    private void recordReceived(int amount) {
        receivedThisSecond = (int) Math.min(Integer.MAX_VALUE,
                (long) receivedThisSecond + Math.max(0, amount));
    }

    private int pullFromProducers() {
        if (!(level instanceof ServerLevel serverLevel) || getSource() >= CAPACITY) return 0;
        int remaining = Math.min(TOTAL_PULL_PER_SECOND, CAPACITY - getSource());
        int pulled = 0;
        for (BlockPos producerPos : cachedProducers) {
            if (remaining <= 0) break;
            ISourceTile source = NearbySourceProducerScanner.resolve(serverLevel, producerPos);
            if (source == null || !source.canProvideSource()) continue;
            int requested = Math.min(PER_PROVIDER_PULL, remaining);
            int available = Math.max(0, Math.min(requested, source.removeSource(requested, true)));
            int acceptable = Math.max(0, Math.min(available, storage.receiveSource(available, true)));
            if (acceptable <= 0) continue;
            int extracted = Math.max(0, Math.min(acceptable, source.removeSource(acceptable, false)));
            int accepted = storage.receiveSource(extracted, false);
            if (extracted > 0) {
                NearbySourceProducerScanner.syncAfterExtraction(serverLevel, producerPos);
            }
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

    public int extractForNetwork(int amount, boolean simulate) {
        return amount <= 0 ? 0 : storage.extractSource(amount, simulate);
    }

    public boolean isLinked() {
        if (!(level instanceof ServerLevel serverLevel)) return linkedCache;
        return SourceNetworkSavedData.get(serverLevel.getServer())
                .targetForJar(globalPos(serverLevel)) != null;
    }

    private GlobalPos globalPos(Level currentLevel) {
        return GlobalPos.of(currentLevel.dimension(), worldPosition);
    }

    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        return SourceNetworkLinking.connect(this, target, player);
    }

    @Override public Result onClearConnections(Player player) {
        return SourceNetworkLinking.clear(this, player);
    }

    @Override public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.source_network.storage",
                getSource(), getMaxSource()));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_source_jar.pull",
                lastPulled, PULL_RANGE));
        tooltip.add(Component.translatable(isLinked()
                ? "tooltip.ars_arcane_matrix.source_network.linked"
                : "tooltip.ars_arcane_matrix.source_network.unlinked"));
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
        tag.putBoolean("Linked", linkedCache);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.setSource(Math.max(0, Math.min(CAPACITY, tag.getInt("Source"))));
        lastPulled = Math.max(0, tag.getInt("LastPulled"));
        linkedCache = tag.getBoolean("Linked");
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
