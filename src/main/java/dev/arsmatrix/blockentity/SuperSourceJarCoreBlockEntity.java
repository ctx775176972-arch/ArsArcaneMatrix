package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import com.hollingsworth.arsnouveau.common.capability.SourceStorage;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.block.SuperSourceJarCoreBlock;
import dev.arsmatrix.source.SourceNetworkSavedData;
import dev.arsmatrix.source.SourceNetworkLinking;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.ArrayList;

/** Controller for a hollow 7x7x7 receive-only Matrix Source Reservoir. */
public final class SuperSourceJarCoreBlockEntity extends BlockEntity
        implements ISourceTile, ITooltipProvider, IWandable {
    public static final int CAPACITY = 100_000_000;
    public static final int TRANSFER_RATE = 1_000_000;
    public static final int PULL_RANGE = 24;
    /** Covers the output of one fully amplified Matrix Core without backing up. */
    public static final int PER_PROVIDER_PULL = 20_000;

    private final SourceStorage storage = new SourceStorage(CAPACITY, TRANSFER_RATE, 0) {
        @Override public int receiveSource(int amount, boolean simulate) {
            int accepted = structureFormed ? super.receiveSource(amount, simulate) : 0;
            if (!simulate && accepted > 0) recordReceived(accepted);
            return accepted;
        }
        @Override public boolean canAcceptSource(int amount) {
            return structureFormed && super.canAcceptSource(amount);
        }
        @Override public boolean canReceive() { return structureFormed; }
        @Override public boolean canProvideSource(int amount) { return false; }
        @Override public int extractSource(int amount, boolean simulate) { return 0; }
        @Override public void onContentsChanged() { sync(); }
    };
    private final ISpecialSourceProvider provider = new ISpecialSourceProvider() {
        @Override public ISourceTile getSource() { return SuperSourceJarCoreBlockEntity.this; }
        @Override public boolean isValid() {
            return structureFormed && !isRemoved() && level != null && level.getBlockEntity(worldPosition)
                    == SuperSourceJarCoreBlockEntity.this;
        }
        @Override public BlockPos getCurrentPos() { return worldPosition; }
    };
    private long registerTick;
    private boolean linkedCache;
    private boolean structureFormed;
    private int lastPulled;
    private int receivedThisSecond;
    private List<BlockPos> cachedProducers = List.of();

    public SuperSourceJarCoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.SUPER_SOURCE_JAR_CORE.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        registerTick++;
        if (registerTick == 1L || registerTick % 20L == 0L) {
            boolean formed = isStructureFormed(serverLevel, worldPosition,
                    getBlockState().getValue(SuperSourceJarCoreBlock.FACING));
            if (formed != structureFormed) {
                structureFormed = formed;
                sync();
            }
        }
        if (structureFormed && registerTick % 200L == 1L) SourceManager.INSTANCE.addInterface(serverLevel, provider);
        if (structureFormed && (registerTick == 1L || registerTick % 100L == 0L)) {
            cachedProducers = NearbySourceProducerScanner.scan(serverLevel, worldPosition, PULL_RANGE);
        }
        if (structureFormed && registerTick % 20L == 0L) {
            pullFromProducers();
            int received = receivedThisSecond;
            receivedThisSecond = 0;
            if (received != lastPulled) {
                lastPulled = received;
                sync();
            }
        }
        boolean linked = SourceNetworkSavedData.get(serverLevel.getServer()).targetForJar(globalPos(serverLevel)) != null;
        if (linkedCache != linked) { linkedCache = linked; sync(); }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) {
            structureFormed = isStructureFormed(level, worldPosition,
                    getBlockState().getValue(SuperSourceJarCoreBlock.FACING));
            if (structureFormed) SourceManager.INSTANCE.addInterface(level, provider);
        }
    }

    public SourceStorage getSourceStorage() { return storage; }

    private void recordReceived(int amount) {
        receivedThisSecond = (int) Math.min(Integer.MAX_VALUE,
                (long) receivedThisSecond + Math.max(0, amount));
    }

    /** Pull only from real generators, never from another storage or machine. */
    private int pullFromProducers() {
        if (!(level instanceof ServerLevel serverLevel) || getSource() >= CAPACITY) return 0;
        int remaining = Math.min(TRANSFER_RATE, CAPACITY - getSource());
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

    public int extractForNetwork(int amount, boolean simulate) {
        if (!structureFormed) return 0;
        int extracted = Math.max(0, Math.min(amount, getSource()));
        if (!simulate && extracted > 0) setSource(getSource() - extracted);
        return extracted;
    }

    public boolean isLinked() {
        if (!(level instanceof ServerLevel serverLevel)) return linkedCache;
        return SourceNetworkSavedData.get(serverLevel.getServer())
                .targetForJar(globalPos(serverLevel)) != null;
    }

    private GlobalPos globalPos(Level currentLevel) {
        return GlobalPos.of(currentLevel.dimension(), worldPosition);
    }

    @Override public int getTransferRate() { return TRANSFER_RATE; }
    @Override public boolean canAcceptSource() { return structureFormed && getSource() < getMaxSource(); }
    @Override public boolean canProvideSource() { return false; }
    @Override public int getSource() { return storage.getSource(); }
    @Override public int getMaxSource() { return structureFormed ? CAPACITY : 0; }
    @Override public int setSource(int source) {
        storage.setSource(Math.max(0, Math.min(CAPACITY, source)));
        return storage.getSource();
    }
    @Override public int addSource(int amount) {
        if (structureFormed && amount > 0) storage.receiveSource(amount, false);
        return getSource();
    }
    @Override public int addSource(int amount, boolean simulate) {
        return !structureFormed || amount <= 0 ? 0 : storage.receiveSource(amount, simulate);
    }
    @Override public int removeSource(int amount) { return getSource(); }
    @Override public int removeSource(int amount, boolean simulate) { return 0; }

    @Override public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable(structureFormed
                ? "tooltip.ars_arcane_matrix.matrix_source_reservoir.formed"
                : "tooltip.ars_arcane_matrix.matrix_source_reservoir.incomplete"));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.source_network.storage",
                getSource(), getMaxSource()));
        tooltip.add(Component.translatable(isLinked()
                ? "tooltip.ars_arcane_matrix.source_network.linked"
                : "tooltip.ars_arcane_matrix.source_network.unlinked"));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.matrix_source_reservoir.pull",
                lastPulled, PULL_RANGE));
    }

    public boolean isStructureFormed() { return structureFormed; }
    public int getLastPulled() { return lastPulled; }

    /** The core replaces the midpoint frame on any lower outer edge; its facing points outward. */
    public static List<StructurePart> structureParts(BlockPos core, Direction facing) {
        Direction inward = facing.getOpposite();
        Direction lateral = facing.getClockWise();
        List<StructurePart> result = new ArrayList<>(218);
        for (int y = 0; y <= 6; y++) {
            for (int depth = 0; depth <= 6; depth++) {
                for (int side = -3; side <= 3; side++) {
                    boolean cap = y == 0 || y == 6;
                    boolean wall = y > 0 && y < 6 && (depth == 0 || depth == 6 || Math.abs(side) == 3);
                    if (!cap && !wall) continue;
                    BlockPos pos = core.relative(inward, depth).relative(lateral, side).above(y);
                    if (pos.equals(core)) continue;
                    result.add(new StructurePart(pos, cap ? PartKind.FRAME : PartKind.GLASS));
                }
            }
        }
        return result;
    }

    public static boolean isStructureFormed(net.minecraft.world.level.Level level, BlockPos core,
                                            Direction facing) {
        for (StructurePart part : structureParts(core, facing)) {
            BlockState state = level.getBlockState(part.pos());
            if (part.kind() == PartKind.FRAME) {
                if (!state.is(ModBlocks.ARCANE_STRUCTURAL_FRAME.get())) return false;
            } else if (!state.is(Blocks.TINTED_GLASS)) {
                return false;
            }
        }
        return true;
    }

    public record StructurePart(BlockPos pos, PartKind kind) {}
    public enum PartKind { FRAME, GLASS }

    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        return SourceNetworkLinking.connect(this, target, player);
    }

    @Override public Result onClearConnections(Player player) {
        return SourceNetworkLinking.clear(this, player);
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("Source", getSource());
        tag.putBoolean("Linked", linkedCache);
        tag.putBoolean("StructureFormed", structureFormed);
        tag.putInt("LastPulled", lastPulled);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        storage.setSource(Math.max(0, Math.min(CAPACITY, tag.getInt("Source"))));
        linkedCache = tag.getBoolean("Linked");
        structureFormed = tag.getBoolean("StructureFormed");
        lastPulled = Math.max(0, tag.getInt("LastPulled"));
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
