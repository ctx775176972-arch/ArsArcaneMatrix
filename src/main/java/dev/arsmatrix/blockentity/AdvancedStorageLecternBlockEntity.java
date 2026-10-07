package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.common.block.tile.StorageLecternTile;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.client.particle.ColorPos;
import com.hollingsworth.arsnouveau.client.particle.ParticleColor;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.Level;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import org.jetbrains.annotations.Nullable;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import dev.arsmatrix.source.SourceNetworkSavedData;
import dev.arsmatrix.source.SourceNetworkLinking;

/**
 * An Ars storage lectern with an embedded Wixie order engine. Extending the native tile is
 * intentional: BookwyrmCharm and EntityBookwyrm require this exact base type.
 */
public final class AdvancedStorageLecternBlockEntity extends StorageLecternTile {

    private static final String ORDER_ENGINE_TAG = "OrderEngine";
    private final WixieOrderTerminalBlockEntity orderEngine;
    /** The manual 3x3 grid belongs to the lectern, so closing a remote menu never ejects it. */
    private final List<ItemStack> craftingGrid = new ArrayList<>(java.util.Collections.nCopies(9, ItemStack.EMPTY));
    private int sourceNetworkTick;
    private long cachedNetworkSource;
    private long cachedNetworkCapacity;
    private int cachedSourceJars;
    private int cachedSourceRelays;
    /** Fluid storage uses the same external-node budget as native Bookwyrm item storage. */
    private final List<FluidStorageNode> linkedFluidNodes = new ArrayList<>();

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
            BlockEntity blockEntity = jarLevel.getBlockEntity(jarPos.pos());
            if (blockEntity instanceof ArcaneSourceJarBlockEntity jar) {
                remaining -= jar.extractForNetwork(remaining, simulate);
            } else if (blockEntity instanceof SuperSourceJarCoreBlockEntity jar) {
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
        if (SourceNetworkLinking.isSourceEndpoint(target, player)) return IWandable.Result.NONE;
        if (hasFluidCapability(target, face, player)) return toggleFluidNode(target, face, player);
        return super.onFirstConnection(target, face, entity, player);
    }

    @Override
    public IWandable.Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        if (isStorageLectern(target, player)) {
            return super.onLastConnection(target, face, entity, player);
        }
        if (SourceNetworkLinking.isSourceEndpoint(target, player)) {
            return SourceNetworkLinking.connect(this, target, player);
        }
        if (hasFluidCapability(target, face, player)) {
            boolean itemStorage = hasItemCapability(target, face, player);
            if (itemStorage) {
                IWandable.Result result = super.onLastConnection(target, face, entity, player);
                syncFluidNodeWithNativeStorage(target, face);
                return result;
            }
            return toggleFluidNode(target, face, player);
        }
        return super.onLastConnection(target, face, entity, player);
    }

    private boolean isStorageLectern(GlobalPos target, Player player) {
        if (target == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null
                && targetLevel.getBlockEntity(target.pos()) instanceof StorageLecternTile;
    }

    @Override
    public IWandable.Result onClearConnections(Player player) {
        linkedFluidNodes.clear();
        SourceNetworkLinking.clear(this);
        return super.onClearConnections(player);
    }

    private boolean hasFluidCapability(GlobalPos target, @Nullable Direction face, Player player) {
        if (target == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null && targetLevel.hasChunkAt(target.pos())
                && targetLevel.getCapability(Capabilities.FluidHandler.BLOCK, target.pos(), face) != null;
    }

    private boolean hasItemCapability(GlobalPos target, @Nullable Direction face, Player player) {
        if (target == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null) return false;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        return targetLevel != null && targetLevel.hasChunkAt(target.pos())
                && targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), face) != null;
    }

    private IWandable.Result toggleFluidNode(GlobalPos target, @Nullable Direction face, Player player) {
        if (target == null || !(player instanceof net.minecraft.server.level.ServerPlayer serverPlayer)
                || serverPlayer.getServer() == null || level == null) {
            return IWandable.Result.FAIL;
        }
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        if (targetLevel == null || !target.dimension().equals(level.dimension())
                || !targetLevel.hasChunkAt(target.pos())
                || targetLevel.getCapability(Capabilities.FluidHandler.BLOCK, target.pos(), face) == null
                || worldPosition.distSqr(target.pos()) > (double) getLecternLinkRange() * getLecternLinkRange()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.advanced_lectern.fluid_out_of_range"), true);
            return IWandable.Result.FAIL;
        }

        int existing = fluidNodeIndex(target);
        if (existing >= 0) {
            linkedFluidNodes.remove(existing);
            setChanged();
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.advanced_lectern.fluid_removed"), true);
            return IWandable.Result.SUCCESS;
        }

        if (!player.isCreative() && connectedStorageNodeCount() >= super.getMaxConnectedInventories()) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.advanced_lectern.storage_full"), true);
            return IWandable.Result.FAIL;
        }
        linkedFluidNodes.add(new FluidStorageNode(target, face));
        setChanged();
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.advanced_lectern.fluid_linked",
                target.dimension().location().toString(), target.pos().toShortString()), true);
        return IWandable.Result.SUCCESS;
    }

    private int getLecternLinkRange() {
        return com.hollingsworth.arsnouveau.setup.config.ServerConfig.LECTERN_LINK_RANGE.get();
    }

    private int fluidNodeIndex(GlobalPos target) {
        for (int index = 0; index < linkedFluidNodes.size(); index++) {
            if (linkedFluidNodes.get(index).pos().equals(target)) return index;
        }
        return -1;
    }

    private boolean isNativeStoragePosition(GlobalPos target) {
        if (level == null || !target.dimension().equals(level.dimension())) return false;
        StorageLecternTile main = getMainLectern();
        return main != null && main.handlerPosList.stream().anyMatch(entry -> entry.pos().equals(target.pos()));
    }

    private int connectedStorageNodeCount() {
        StorageLecternTile main = getMainLectern();
        int itemNodes = main == null ? 0 : main.handlerPosList.size();
        int fluidOnlyNodes = (int) linkedFluidNodes.stream()
                .filter(node -> !isNativeStoragePosition(node.pos())).count();
        return itemNodes + fluidOnlyNodes;
    }

    private void syncFluidNodeWithNativeStorage(GlobalPos target, @Nullable Direction face) {
        int existing = fluidNodeIndex(target);
        if (isNativeStoragePosition(target)) {
            if (existing < 0) linkedFluidNodes.add(new FluidStorageNode(target, face));
        } else if (existing >= 0) {
            linkedFluidNodes.remove(existing);
        }
        setChanged();
    }

    @Override
    public int getMaxConnectedInventories() {
        int fluidOnlyNodes = (int) linkedFluidNodes.stream()
                .filter(node -> !isNativeStoragePosition(node.pos())).count();
        return Math.max(0, super.getMaxConnectedInventories() - fluidOnlyNodes);
    }

    @Override
    public List<ColorPos> getWandHighlight(List<ColorPos> highlights) {
        super.getWandHighlight(highlights);
        if (level == null) return highlights;
        for (FluidStorageNode node : linkedFluidNodes) {
            if (node.pos().dimension().equals(level.dimension()) && !isNativeStoragePosition(node.pos())) {
                highlights.add(ColorPos.centered(node.pos().pos(), ParticleColor.FROM_HIGHLIGHT));
            }
        }
        return highlights;
    }

    @Override
    public void getTooltip(List<Component> tooltip) {
        if (mainLecternPos != null) {
            tooltip.add(Component.translatable("ars_nouveau.storage.lectern_chained",
                    mainLecternPos.getX(), mainLecternPos.getY(), mainLecternPos.getZ()));
            return;
        }
        tooltip.add(Component.translatable("ars_nouveau.storage.num_connected", connectedStorageNodeCount()));
        tooltip.add(Component.translatable("ars_nouveau.storage.num_bookwyrms", bookwyrmUUIDs.size()));
    }

    private List<IFluidHandler> getConnectedFluidHandlers() {
        if (!(level instanceof ServerLevel serverLevel)) return List.of();
        List<IFluidHandler> result = new ArrayList<>();
        java.util.Set<BlockPos> visited = new java.util.HashSet<>();
        for (FluidStorageNode node : linkedFluidNodes) {
            ServerLevel targetLevel = serverLevel.getServer().getLevel(node.pos().dimension());
            if (targetLevel == null || !targetLevel.hasChunkAt(node.pos().pos())) continue;
            IFluidHandler handler = fluidHandlerAt(targetLevel, node.pos().pos(), node.face());
            if (handler != null && visited.add(node.pos().pos())) result.add(handler);
        }
        // A reservoir also exposes upgrade item slots. Depending on which endpoint
        // the Dominion Wand selected first, Ars may record it only in the native
        // lectern inventory list. Treat every native node with a fluid capability as
        // fluid storage too, so the connection order cannot hide buckets from orders.
        StorageLecternTile main = getMainLectern();
        if (main != null) {
            for (var entry : main.handlerPosList) {
                BlockPos pos = entry.pos();
                if (!serverLevel.hasChunkAt(pos) || visited.contains(pos)) continue;
                IFluidHandler handler = fluidHandlerAt(serverLevel, pos, null);
                if (handler != null) {
                    visited.add(pos);
                    result.add(handler);
                }
            }
        }
        return result;
    }

    private static IFluidHandler fluidHandlerAt(
            ServerLevel level, BlockPos pos, @Nullable Direction preferredFace
    ) {
        IFluidHandler handler = level.getCapability(
                Capabilities.FluidHandler.BLOCK, pos, preferredFace);
        if (handler != null) return handler;
        handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, null);
        if (handler != null) return handler;
        for (Direction direction : Direction.values()) {
            handler = level.getCapability(Capabilities.FluidHandler.BLOCK, pos, direction);
            if (handler != null) return handler;
        }
        return null;
    }

    private List<FluidStack> getStoredFluids() {
        Map<net.minecraft.world.level.material.Fluid, Integer> totals = new LinkedHashMap<>();
        for (IFluidHandler handler : getConnectedFluidHandlers()) {
            for (int tank = 0; tank < handler.getTanks(); tank++) {
                FluidStack stack = handler.getFluidInTank(tank);
                if (!stack.isEmpty()) totals.merge(stack.getFluid(), stack.getAmount(),
                        (left, right) -> (int) Math.min(Integer.MAX_VALUE, (long) left + right));
            }
        }
        return totals.entrySet().stream().map(entry -> new FluidStack(entry.getKey(), entry.getValue())).toList();
    }

    public List<ItemStack> getVirtualFluidContainers() {
        List<ItemStack> result = new ArrayList<>();
        for (FluidStack fluid : getStoredFluids()) {
            if (fluid.isEmpty() || fluid.getFluid().getBucket() == net.minecraft.world.item.Items.AIR) continue;
            ItemStack bucket = new ItemStack(fluid.getFluid().getBucket());
            int unit = FluidUtil.getFluidContained(bucket).map(FluidStack::getAmount).orElse(1000);
            int count = Math.min(9999, fluid.getAmount() / Math.max(1, unit));
            if (count > 0) result.add(bucket.copyWithCount(count));
        }
        return result;
    }

    public boolean consumeVirtualFluidContainer(ItemStack container) {
        if (container.isEmpty()) return false;
        FluidStack wanted = FluidUtil.getFluidContained(container).orElse(FluidStack.EMPTY);
        if (wanted.isEmpty()) return false;
        return drainConnectedFluid(wanted);
    }

    public void restoreVirtualFluidContainer(ItemStack container) {
        if (container.isEmpty()) return;
        FluidStack fluid = FluidUtil.getFluidContained(container).orElse(FluidStack.EMPTY);
        if (!fluid.isEmpty()) fillConnectedFluid(fluid);
    }

    /** Atomically moves one requested fluid amount from the linked controller into a machine. */
    public boolean supplyLinkedFluid(IFluidHandler target, ResourceLocation fluidId, int amount) {
        if (amount <= 0) return true;
        if (target == null) return false;
        var fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
        if (fluid == null) return false;
        FluidStack requested = new FluidStack(fluid, amount);
        if (countConnectedFluid(requested) < amount
                || target.fill(requested, IFluidHandler.FluidAction.SIMULATE) != amount
                || !drainConnectedFluid(requested)) return false;
        int filled = target.fill(requested, IFluidHandler.FluidAction.EXECUTE);
        if (filled != amount) {
            if (filled > 0) target.drain(new FluidStack(fluid, filled), IFluidHandler.FluidAction.EXECUTE);
            fillConnectedFluid(requested);
            return false;
        }
        return true;
    }

    /** Server-authoritative amount visible to orders and crafting-plan validation. */
    public int countLinkedFluid(ResourceLocation fluidId) {
        var fluid = BuiltInRegistries.FLUID.getOptional(fluidId).orElse(null);
        return fluid == null ? 0 : countConnectedFluid(new FluidStack(fluid, Integer.MAX_VALUE));
    }

    public int getLinkedFluidType(int tank) {
        List<FluidStack> fluids = getStoredFluids();
        if (tank < 0 || tank >= fluids.size()) return -1;
        FluidStack fluid = fluids.get(tank);
        return fluid.isEmpty() ? -1 : BuiltInRegistries.FLUID.getId(fluid.getFluid());
    }

    public int getLinkedFluidAmount(int tank) {
        List<FluidStack> fluids = getStoredFluids();
        return tank < 0 || tank >= fluids.size() ? 0 : fluids.get(tank).getAmount();
    }

    private int countConnectedFluid(FluidStack wanted) {
        int total = 0;
        for (IFluidHandler handler : getConnectedFluidHandlers()) {
            total = (int) Math.min(Integer.MAX_VALUE, (long) total
                    + handler.drain(wanted, IFluidHandler.FluidAction.SIMULATE).getAmount());
        }
        return total;
    }

    private boolean drainConnectedFluid(FluidStack wanted) {
        if (countConnectedFluid(wanted) < wanted.getAmount()) return false;
        int remaining = wanted.getAmount();
        List<FluidStack> drained = new ArrayList<>();
        for (IFluidHandler handler : getConnectedFluidHandlers()) {
            if (remaining <= 0) break;
            FluidStack part = handler.drain(new FluidStack(wanted.getFluid(), remaining),
                    IFluidHandler.FluidAction.EXECUTE);
            if (!part.isEmpty()) {
                drained.add(part);
                remaining -= part.getAmount();
            }
        }
        if (remaining <= 0) return true;
        drained.forEach(this::fillConnectedFluid);
        return false;
    }

    private int fillConnectedFluid(FluidStack fluid) {
        int remaining = fluid.getAmount();
        for (IFluidHandler handler : getConnectedFluidHandlers()) {
            if (remaining <= 0) break;
            remaining -= handler.fill(new FluidStack(fluid.getFluid(), remaining),
                    IFluidHandler.FluidAction.EXECUTE);
        }
        return fluid.getAmount() - remaining;
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
            BlockEntity blockEntity = jarLevel.getBlockEntity(jarPos.pos());
            if (blockEntity instanceof ArcaneSourceJarBlockEntity jar) {
                stored += jar.getSource();
                capacity += jar.getMaxSource();
            } else if (blockEntity instanceof SuperSourceJarCoreBlockEntity jar && jar.isStructureFormed()) {
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
        if (level != null) {
            for (int slot = 0; slot < craftingGrid.size(); slot++) {
                ItemStack stack = craftingGrid.get(slot);
                if (!stack.isEmpty()) {
                    Containers.dropItemStack(level, worldPosition.getX() + 0.5,
                            worldPosition.getY() + 0.5, worldPosition.getZ() + 0.5, stack);
                    craftingGrid.set(slot, ItemStack.EMPTY);
                }
            }
        }
    }

    public List<ItemStack> getCraftingGrid() {
        return craftingGrid.stream().map(ItemStack::copy).toList();
    }

    public void setCraftingGrid(List<ItemStack> stacks) {
        for (int slot = 0; slot < craftingGrid.size(); slot++) {
            craftingGrid.set(slot, slot < stacks.size() ? stacks.get(slot).copy() : ItemStack.EMPTY);
        }
        setChanged();
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
                data, worldPosition, level == null ? Level.OVERWORLD : level.dimension(),
                getCraftableRecipeInfos(), getStoredStacks());
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
        return extractStoredInternal(template, 1);
    }

    /** Extracts matching network items without routing them through a player's inventory. */
    public ItemStack extractStoredInternal(ItemStack template, int requested) {
        if (level == null || level.isClientSide || template.isEmpty() || requested <= 0) {
            return ItemStack.EMPTY;
        }
        ItemStack gathered = template.copyWithCount(0);
        int remaining = requested;
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
        }
        if (!gathered.isEmpty()) {
            updateItems = true;
            setChanged();
        }
        return gathered;
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
        ListTag crafting = new ListTag();
        for (int slot = 0; slot < craftingGrid.size(); slot++) {
            ItemStack stack = craftingGrid.get(slot);
            if (stack.isEmpty()) continue;
            CompoundTag entry = new CompoundTag();
            entry.putByte("Slot", (byte) slot);
            entry.put("Stack", stack.saveOptional(registries));
            crafting.add(entry);
        }
        tag.put("CraftingGrid", crafting);
        ListTag fluidNodes = new ListTag();
        for (FluidStorageNode node : linkedFluidNodes) {
            CompoundTag nodeTag = new CompoundTag();
            nodeTag.putString("Dimension", node.pos().dimension().location().toString());
            nodeTag.putLong("Pos", node.pos().pos().asLong());
            if (node.face() != null) nodeTag.putString("Face", node.face().getName());
            fluidNodes.add(nodeTag);
        }
        tag.put("FluidStorageNodes", fluidNodes);
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
        java.util.Collections.fill(craftingGrid, ItemStack.EMPTY);
        ListTag crafting = tag.getList("CraftingGrid", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < crafting.size(); index++) {
            CompoundTag entry = crafting.getCompound(index);
            int slot = entry.getByte("Slot") & 255;
            if (slot < craftingGrid.size()) {
                craftingGrid.set(slot, ItemStack.parseOptional(registries, entry.getCompound("Stack")));
            }
        }
        linkedFluidNodes.clear();
        ListTag fluidNodes = tag.getList("FluidStorageNodes", CompoundTag.TAG_COMPOUND);
        for (int index = 0; index < fluidNodes.size(); index++) {
            CompoundTag nodeTag = fluidNodes.getCompound(index);
            ResourceLocation dimension = ResourceLocation.tryParse(nodeTag.getString("Dimension"));
            if (dimension == null || !nodeTag.contains("Pos")) continue;
            linkedFluidNodes.add(new FluidStorageNode(GlobalPos.of(
                    net.minecraft.resources.ResourceKey.create(
                            net.minecraft.core.registries.Registries.DIMENSION, dimension),
                    BlockPos.of(nodeTag.getLong("Pos"))), Direction.byName(nodeTag.getString("Face"))));
        }
        // Migrate the pre-0.5.6 single-controller link without invalidating existing worlds.
        if (linkedFluidNodes.isEmpty()) {
            ResourceLocation fluidDimension = ResourceLocation.tryParse(tag.getString("FluidReservoirDimension"));
            if (fluidDimension != null && tag.contains("FluidReservoirPos")) {
                linkedFluidNodes.add(new FluidStorageNode(GlobalPos.of(
                        net.minecraft.resources.ResourceKey.create(
                                net.minecraft.core.registries.Registries.DIMENSION, fluidDimension),
                        BlockPos.of(tag.getLong("FluidReservoirPos"))), null));
            }
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) {
        return saveWithoutMetadata(registries);
    }

    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public record StoredStack(ItemStack stack, int count) {}

    private record FluidStorageNode(GlobalPos pos, @Nullable Direction face) {}
}
