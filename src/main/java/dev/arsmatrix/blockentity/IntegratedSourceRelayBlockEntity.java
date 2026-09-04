package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.source.SourceManager;
import dev.arsmatrix.registry.ModBlockEntities;
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
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Ars-compatible, on-demand view of a linked Source network. This mirrors the
 * behavior of Beyond Dimensions' Source Pathway: the relay exposes upstream
 * Source directly instead of slowly filling a local buffer first.
 */
public final class IntegratedSourceRelayBlockEntity extends BlockEntity
        implements ISourceTile, ITooltipProvider, IWandable {
    public static final int TRANSFER_RATE = Integer.MAX_VALUE;
    private final ISpecialSourceProvider provider = new ISpecialSourceProvider() {
        @Override public ISourceTile getSource() { return IntegratedSourceRelayBlockEntity.this; }
        @Override public boolean isValid() {
            return !isRemoved() && level != null && level.getBlockEntity(worldPosition)
                    == IntegratedSourceRelayBlockEntity.this;
        }
        @Override public BlockPos getCurrentPos() { return worldPosition; }
    };
    private int tickCounter;
    private boolean linkedCache;

    public IntegratedSourceRelayBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INTEGRATED_SOURCE_RELAY.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        if (++tickCounter % 20 != 0) return;
        SourceManager.INSTANCE.addInterface(level, provider);
        boolean linked = SourceNetworkSavedData.get(serverLevel.getServer())
                .sourceForRelay(globalPos(serverLevel)) != null;
        if (linkedCache != linked) { linkedCache = linked; sync(); }
    }

    @Override public void onLoad() {
        super.onLoad();
        if (level != null && !level.isClientSide) SourceManager.INSTANCE.addInterface(level, provider);
    }

    public boolean isLinked() {
        if (!(level instanceof ServerLevel serverLevel)) return linkedCache;
        return SourceNetworkSavedData.get(serverLevel.getServer())
                .sourceForRelay(globalPos(serverLevel)) != null;
    }
    private GlobalPos globalPos(Level currentLevel) {
        return GlobalPos.of(currentLevel.dimension(), worldPosition);
    }

    @Nullable
    GlobalPos linkedSourcePosition() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        return SourceNetworkSavedData.get(serverLevel.getServer())
                .sourceForRelay(globalPos(serverLevel));
    }

    @Nullable
    private BlockEntity linkedSource() {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        GlobalPos source = linkedSourcePosition();
        if (source == null) return null;
        ServerLevel sourceLevel = serverLevel.getServer().getLevel(source.dimension());
        if (sourceLevel == null || !sourceLevel.hasChunkAt(source.pos())) return null;
        return sourceLevel.getBlockEntity(source.pos());
    }

    private int linkedSourceAmount() {
        BlockEntity source = linkedSource();
        if (source instanceof ArcaneSourceJarBlockEntity jar) return jar.getSource();
        if (source instanceof SuperSourceJarCoreBlockEntity jar) return jar.getSource();
        if (source instanceof AdvancedStorageLecternBlockEntity gateway) {
            return gateway.extractNetworkSource(Integer.MAX_VALUE, true);
        }
        if (source instanceof MatrixCoreBlockEntity matrix && matrix.canProvideSource()) {
            return matrix.getSource();
        }
        return 0;
    }

    private int extractLinkedSource(int amount, boolean simulate) {
        if (amount <= 0) return 0;
        BlockEntity source = linkedSource();
        if (source instanceof ArcaneSourceJarBlockEntity jar) {
            return jar.extractForNetwork(amount, simulate);
        }
        if (source instanceof SuperSourceJarCoreBlockEntity jar) {
            return jar.extractForNetwork(amount, simulate);
        }
        if (source instanceof AdvancedStorageLecternBlockEntity gateway) {
            return gateway.extractNetworkSource(amount, simulate);
        }
        if (source instanceof MatrixCoreBlockEntity matrix) {
            return matrix.extractForNetwork(amount, simulate);
        }
        return 0;
    }

    @Override public int getTransferRate() { return TRANSFER_RATE; }
    @Override public boolean canAcceptSource() { return false; }
    @Override public boolean canProvideSource() { return getSource() > 0; }
    @Override public int getSource() { return linkedSourceAmount(); }
    @Override public int getMaxSource() { return Integer.MAX_VALUE; }
    @Override public int setSource(int source) { return getSource(); }
    @Override public int addSource(int amount) { return getSource(); }
    @Override public int addSource(int amount, boolean simulate) { return 0; }
    @Override public int removeSource(int amount) {
        return extractLinkedSource(amount, false);
    }
    @Override public int removeSource(int amount, boolean simulate) {
        return extractLinkedSource(amount, simulate);
    }

    @Override public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable(isLinked()
                ? "tooltip.ars_arcane_matrix.source_network.linked"
                : "tooltip.ars_arcane_matrix.source_network.unlinked"));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.integrated_source_relay.on_demand"));
    }

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
        tag.putBoolean("Linked", linkedCache);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        linkedCache = tag.getBoolean("Linked");
    }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
