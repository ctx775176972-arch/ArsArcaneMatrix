package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.common.block.tile.StorageLecternTile;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.List;
import dev.arsmatrix.source.SourceNetworkSavedData;
import dev.arsmatrix.source.SourceNetworkLinking;

/**
 * An Ars storage lectern with an embedded Wixie order engine. Extending the native tile is
 * intentional: BookwyrmCharm and EntityBookwyrm require this exact base type.
 */
public final class AdvancedStorageLecternBlockEntity extends StorageLecternTile {

    private static final String ORDER_ENGINE_TAG = "OrderEngine";
    private final WixieOrderTerminalBlockEntity orderEngine;
    private int sourceNetworkTick;
    private long cachedNetworkSource;
    private long cachedNetworkCapacity;
    private int cachedSourceJars;
    private int cachedSourceRelays;
    private GlobalPos linkedFluidReservoir;

    public AdvancedStorageLecternBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ADVANCED_STORAGE_LECTERN.get(), pos, state);
        // This is a detached logic delegate, but BlockEntity still validates its registered
        // type against the supplied block state during construction. Use the advanced lectern
        // type so loading an existing lectern never trips vanilla's invalid-state guard.
        orderEngine = new WixieOrderTerminalBlockEntity(
                ModBlockEntities.ADVANCED_STORAGE_LECTERN.get(), pos, state);
    }

    public WixieOrderTerminalBlockEntity getOrderEngine() {
        if (level != null && orderEngine.getLevel() != level) {
            orderEngine.setLevel(level);
        }
        return orderEngine;
    }

    public List<ItemStack> getCraftableOutputs() {
        return getOrderEngine().getCraftableOutputs();
    }

    public List<WixieOrderTerminalBlockEntity.CraftableRecipeInfo> getCraftableRecipeInfos() {
        return getOrderEngine().getCraftableRecipeInfos();
    }

    public void serverTick() {
        tick();
        getOrderEngine().serverTick();
        if (++sourceNetworkTick % 20 == 0) updateSourceNetworkCache();
    }

    /** Draws Source from every currently loaded super jar linked to this lectern gateway. */
    public int extractNetworkSource(int requested, boolean simulate) {
        if (!(level instanceof ServerLevel serverLevel) || requested <= 0) return 0;
        GlobalPos gateway = GlobalPos.of(level.dimension(), worldPosition);
        int remaining = requested;
        for (GlobalPos jarPos : SourceNetworkSavedData.get(serverLevel.getServer()).jarsForGateway(gateway)) {
            if (remaining <= 0) break;
            ServerLevel jarLevel = serverLevel.getServer().getLevel(jarPos.dimension());
            if (jarLevel == null || !jarLevel.hasChunkAt(jarPos.pos())) continue;
            if (jarLevel.getBlockEntity(jarPos.pos()) instanceof SuperSourceJarCoreBlockEntity jar) {
                remaining -= jar.extractForNetwork(remaining, simulate);
            }
        }
        return requested - remaining;
    }

    @Override
    public IWandable.Result onFirstConnection(GlobalPos target, @Nullable Direction face,
                                               @Nullable LivingEntity entity, Player player) {
        // An advanced lectern is also a Source gateway. Lectern chaining must win over
        // Source endpoint detection, otherwise two advanced lecterns are mistaken for
        // two gateways and the native storage-network connection is silently skipped.
        if (isStorageLectern(target, player)) {
            return super.onFirstConnection(target, face, entity, player);
        }
        // The second Source endpoint owns the symmetric link. Suppress the lectern's native
        // inventory binding only when the other endpoint belongs to the Source network.
        if (isFluidReservoir(target, player)) return linkFluidReservoir(target, player);
        if (SourceNetworkLinking.isSourceEndpoint(target, player)) return IWandable.Result.NONE;
        return super.onFirstConnection(target, face, entity, player);
    }

    @Override
    public IWandable.Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        if (isStorageLectern(target, player)) {
            return super.onLastConnection(target, face, entity, player);
        }
        if (isFluidReservoir(target, player)) return linkFluidReservoir(target, player);
        if (SourceNetworkLinking.isSourceEndpoint(target, player)) {
            return SourceNetworkLinking.connect(this, target, player);
        }
        return super.onLastConnection(target, face, entity, player);
    }

    private boolean isStorageLectern(GlobalPos target, Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null
                && targetLevel.getBlockEntity(target.pos()) instanceof StorageLecternTile;
    }

    @Override
    public IWandable.Result onClearConnections(Player player) {
        linkedFluidReservoir = null;
        SourceNetworkLinking.clear(this);
        return super.onClearConnections(player);
    }

    private boolean isFluidReservoir(GlobalPos target, Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null
                && targetLevel.getBlockEntity(target.pos()) instanceof ArcaneFluidReservoirBlockEntity;
    }

    private IWandable.Result linkFluidReservoir(GlobalPos target, Player player) {
        if (!(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer) || level == null) {
            return IWandable.Result.FAIL;
        }
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        if (targetLevel == null || !(targetLevel.getBlockEntity(target.pos())
                instanceof ArcaneFluidReservoirBlockEntity reservoir)
                || !reservoir.canWirelessReach()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.advanced_lectern.fluid_out_of_range"), true);
            return IWandable.Result.FAIL;
        }
        linkedFluidReservoir = target;
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.advanced_lectern.fluid_linked",
                target.dimension().location().toString(), target.pos().toShortString()), true);
        return IWandable.Result.SUCCESS;
    }

    private ArcaneFluidReservoirBlockEntity linkedReservoir() {
        if (!(level instanceof ServerLevel serverLevel) || linkedFluidReservoir == null) return null;
        ServerLevel targetLevel = serverLevel.getServer().getLevel(linkedFluidReservoir.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(linkedFluidReservoir.pos())) return null;
        return targetLevel.getBlockEntity(linkedFluidReservoir.pos())
                instanceof ArcaneFluidReservoirBlockEntity reservoir
                && reservoir.canWirelessReach() ? reservoir : null;
    }

    public List<ItemStack> getVirtualFluidContainers() {
        ArcaneFluidReservoirBlockEntity reservoir = linkedReservoir();
        if (reservoir == null) return List.of();
        List<ItemStack> result = new ArrayList<>();
        IFluidHandler handler = reservoir.getFluidHandler(null);
        for (int tank = 0; tank < handler.getTanks(); tank++) {
            FluidStack fluid = handler.getFluidInTank(tank);
            if (fluid.isEmpty() || fluid.getFluid().getBucket() == net.minecraft.world.item.Items.AIR) continue;
            ItemStack bucket = new ItemStack(fluid.getFluid().getBucket());
            int unit = FluidUtil.getFluidContained(bucket).map(FluidStack::getAmount).orElse(1000);
            int count = Math.min(9999, fluid.getAmount() / Math.max(1, unit));
            if (count > 0) result.add(bucket.copyWithCount(count));
        }
        return result;
    }

    public boolean consumeVirtualFluidContainer(ItemStack container) {
        ArcaneFluidReservoirBlockEntity reservoir = linkedReservoir();
        if (reservoir == null || container.isEmpty()) return false;
        FluidStack wanted = FluidUtil.getFluidContained(container).orElse(FluidStack.EMPTY);
        if (wanted.isEmpty()) return false;
        IFluidHandler handler = reservoir.getFluidHandler(null);
        FluidStack simulated = handler.drain(wanted, IFluidHandler.FluidAction.SIMULATE);
        if (simulated.getAmount() < wanted.getAmount()) return false;
        return handler.drain(wanted, IFluidHandler.FluidAction.EXECUTE).getAmount() == wanted.getAmount();
    }

    public void restoreVirtualFluidContainer(ItemStack container) {
        ArcaneFluidReservoirBlockEntity reservoir = linkedReservoir();
        if (reservoir == null || container.isEmpty()) return;
        FluidStack fluid = FluidUtil.getFluidContained(container).orElse(FluidStack.EMPTY);
        if (!fluid.isEmpty()) reservoir.getFluidHandler(null).fill(fluid, IFluidHandler.FluidAction.EXECUTE);
    }

    public int getLinkedFluidType(int tank) {
        ArcaneFluidReservoirBlockEntity reservoir = linkedReservoir();
        if (reservoir == null) return -1;
        IFluidHandler handler = reservoir.getFluidHandler(null);
        if (tank < 0 || tank >= handler.getTanks()) return -1;
        FluidStack fluid = handler.getFluidInTank(tank);
        return fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
    }

    public int getLinkedFluidAmount(int tank) {
        ArcaneFluidReservoirBlockEntity reservoir = linkedReservoir();
        if (reservoir == null) return 0;
        IFluidHandler handler = reservoir.getFluidHandler(null);
        return tank < 0 || tank >= handler.getTanks() ? 0 : handler.getFluidInTank(tank).getAmount();
    }

    public long getNetworkSource() {
        return cachedNetworkSource;
    }

    public long getNetworkCapacity() {
        return cachedNetworkCapacity;
    }

    public int getLinkedSourceJarCount() {
        return cachedSourceJars;
    }

    public int getLinkedSourceRelayCount() {
        return cachedSourceRelays;
    }

    private long[] networkTotals() {
        if (!(level instanceof ServerLevel serverLevel)) return new long[]{0L, 0L};
        long stored = 0L;
        long capacity = 0L;
        GlobalPos gateway = GlobalPos.of(level.dimension(), worldPosition);
        for (GlobalPos jarPos : SourceNetworkSavedData.get(serverLevel.getServer()).jarsForGateway(gateway)) {
            ServerLevel jarLevel = serverLevel.getServer().getLevel(jarPos.dimension());
            if (jarLevel == null || !jarLevel.hasChunkAt(jarPos.pos())) continue;
            if (jarLevel.getBlockEntity(jarPos.pos()) instanceof SuperSourceJarCoreBlockEntity jar) {
                stored += jar.getSource();
                capacity += jar.getMaxSource();
            }
        }
        return new long[]{stored, capacity};
    }

    private void updateSourceNetworkCache() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        GlobalPos gateway = GlobalPos.of(level.dimension(), worldPosition);
        SourceNetworkSavedData data = SourceNetworkSavedData.get(serverLevel.getServer());
        long[] totals = networkTotals();
        int jars = data.jarsForGateway(gateway).size();
        int relays = data.relaysForGateway(gateway).size();
        if (cachedNetworkSource == totals[0] && cachedNetworkCapacity == totals[1]
                && cachedSourceJars == jars && cachedSourceRelays == relays) return;
        cachedNetworkSource = totals[0];
        cachedNetworkCapacity = totals[1];
        cachedSourceJars = jars;
        cachedSourceRelays = relays;
        setChanged();
        level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public void dropBufferedContents() {
        getOrderEngine().dropBufferedContents();
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.advanced_storage_lectern");
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new WixieOrderTerminalMenu(containerId, inventory, this, getStoredStacks());
    }

    /**
     * Supplies the extended opening payload even when Ars Nouveau opens this inherited
     * StorageLecternTile through one of its own interaction paths.
     */
    @Override
    public void writeClientSideData(AbstractContainerMenu menu, RegistryFriendlyByteBuf data) {
        WixieOrderTerminalMenu.writeOpeningData(
                data, worldPosition, getCraftableRecipeInfos(), getStoredStacks());
    }

    public List<StoredStack> getStoredStacks() {
        List<StoredStack> result = new ArrayList<>();
        for (IItemHandler handler : getConnectedHandlers()) {
            if (handler instanceof StorageGridDirectoryBlockEntity.GridItemHandler grid) {
                for (StorageGridDirectoryBlockEntity.StoredStack entry : grid.getStoredStacks()) {
                    mergeStored(result, entry.stack(), entry.amount());
                }
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.isEmpty()) continue;
                mergeStored(result, stack, stack.getCount());
            }
        }
        result.sort(java.util.Comparator.comparing(entry -> entry.stack().getHoverName().getString()));
        return List.copyOf(result);
    }

    private static void mergeStored(List<StoredStack> result, ItemStack stack, long amount) {
        int existing = -1;
        for (int index = 0; index < result.size(); index++) {
            if (ItemStack.isSameItemSameComponents(result.get(index).stack(), stack)) {
                existing = index;
                break;
            }
        }
        int clamped = (int) Math.min(Integer.MAX_VALUE, Math.max(0L, amount));
        if (existing >= 0) {
            StoredStack previous = result.get(existing);
            result.set(existing, new StoredStack(previous.stack(),
                    (int) Math.min(Integer.MAX_VALUE, (long) previous.count() + clamped)));
        } else if (clamped > 0) {
            result.add(new StoredStack(stack.copyWithCount(1), clamped));
        }
    }

    public int extractStored(ItemStack template, int requested, Player player) {
        if (level == null || level.isClientSide || template.isEmpty() || requested <= 0) return 0;
        int remaining = requested;
        ItemStack gathered = template.copyWithCount(0);
        for (IItemHandler handler : getConnectedHandlers()) {
            if (handler instanceof StorageGridDirectoryBlockEntity.GridItemHandler grid) {
                int extracted = grid.extractMatching(template, remaining);
                if (extracted > 0) {
                    gathered.grow(extracted);
                    remaining -= extracted;
                }
                if (remaining <= 0) break;
                continue;
            }
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), template)) continue;
                ItemStack extracted = handler.extractItem(slot, remaining, false);
                if (!extracted.isEmpty()) {
                    gathered.grow(extracted.getCount());
                    remaining -= extracted.getCount();
                }
            }
            if (remaining <= 0) break;
        }
        if (!gathered.isEmpty()) ItemHandlerHelper.giveItemToPlayer(player, gathered);
        updateItems = true;
        setChanged();
        return requested - remaining;
    }

    /** Extracts one matching network item for an internal lectern action. */
    public ItemStack extractOneStoredInternal(ItemStack template) {
        if (level == null || level.isClientSide || template.isEmpty()) return ItemStack.EMPTY;
        for (IItemHandler handler : getConnectedHandlers()) {
            if (handler instanceof StorageGridDirectoryBlockEntity.GridItemHandler grid) {
                if (grid.extractMatching(template, 1) > 0) {
                    updateItems = true;
                    setChanged();
                    return template.copyWithCount(1);
                }
                continue;
            }
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                if (!ItemStack.isSameItemSameComponents(handler.getStackInSlot(slot), template)) continue;
                ItemStack extracted = handler.extractItem(slot, 1, false);
                if (!extracted.isEmpty()) {
                    updateItems = true;
                    setChanged();
                    return extracted;
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Inserts into the connected storage network without buffering a duplicate in the lectern. */
    public ItemStack insertStored(ItemStack stack) {
        if (level == null || level.isClientSide || stack.isEmpty()) return stack;
        ItemStack remainder = stack.copy();
        for (IItemHandler handler : getConnectedHandlers()) {
            remainder = ItemHandlerHelper.insertItem(handler, remainder, false);
            if (remainder.isEmpty()) break;
        }
        if (remainder.getCount() != stack.getCount()) {
            updateItems = true;
            setChanged();
        }
        return remainder;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level != null) orderEngine.setLevel(level);
    }

    @Override
    public void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(ORDER_ENGINE_TAG, orderEngine.saveEmbedded(registries));
        tag.putLong("SourceNetworkStored", cachedNetworkSource);
        tag.putLong("SourceNetworkCapacity", cachedNetworkCapacity);
        tag.putInt("SourceNetworkJars", cachedSourceJars);
        tag.putInt("SourceNetworkRelays", cachedSourceRelays);
        if (linkedFluidReservoir != null) {
            tag.putString("FluidReservoirDimension", linkedFluidReservoir.dimension().location().toString());
            tag.putLong("FluidReservoirPos", linkedFluidReservoir.pos().asLong());
        }
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(ORDER_ENGINE_TAG, CompoundTag.TAG_COMPOUND)) {
            orderEngine.loadEmbedded(tag.getCompound(ORDER_ENGINE_TAG), registries);
        } else {
            // Migration from builds where the advanced lectern directly inherited the order engine.
            orderEngine.loadEmbedded(tag, registries);
        }
        cachedNetworkSource = Math.max(0L, tag.getLong("SourceNetworkStored"));
        cachedNetworkCapacity = Math.max(0L, tag.getLong("SourceNetworkCapacity"));
        cachedSourceJars = Math.max(0, tag.getInt("SourceNetworkJars"));
        cachedSourceRelays = Math.max(0, tag.getInt("SourceNetworkRelays"));
        ResourceLocation fluidDimension = ResourceLocation.tryParse(tag.getString("FluidReservoirDimension"));
        if (fluidDimension != null && tag.contains("FluidReservoirPos")) {
            linkedFluidReservoir = GlobalPos.of(net.minecraft.resources.ResourceKey.create(
                    net.minecraft.core.registries.Registries.DIMENSION, fluidDimension),
                    BlockPos.of(tag.getLong("FluidReservoirPos")));
        } else linkedFluidReservoir = null;
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Nullable @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public record StoredStack(ItemStack stack, int count) {}
}
