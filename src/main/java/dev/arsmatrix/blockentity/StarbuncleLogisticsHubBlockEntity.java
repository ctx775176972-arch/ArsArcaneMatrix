package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.common.entity.Starbuncle;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.common.entity.goal.carbuncle.StarbyTransportBehavior;
import com.hollingsworth.arsnouveau.common.items.data.ItemScrollData;
import com.hollingsworth.arsnouveau.setup.registry.DataComponentRegistry;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.menu.StarbuncleLogisticsHubMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import dev.arsmatrix.item.HubFilterScrollItem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.capabilities.Capabilities;

import java.util.List;
import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jetbrains.annotations.Nullable;

/** Native Starbuncle throughput booster, filter controller, and maintenance station. */
public final class StarbuncleLogisticsHubBlockEntity extends BlockEntity implements MenuProvider, IWandable {
    public static final int RANGE = 32;
    private static final int SCAN_INTERVAL = 20;
    private static final int AUTO_RECALL_TICKS = 1200;
    public static final int MAX_UPGRADE_TIER = 4;
    private static final int[] THROUGHPUT_BY_TIER = {256, 1_024, 4_096, 16_384, 32_768};
    private static final int MAX_BULK_OPERATIONS = 512;
    public static final String PROTECTED_UNTIL_TAG = "ars_arcane_matrix:logistics_protected_until";
    private final ItemStackHandler inventory = new ItemStackHandler(18) {
        @Override protected void onContentsChanged(int slot) { sync(); }
    };
    private final ItemStackHandler filters = new ItemStackHandler(27) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) {
            if (!loadingFilterProfile) saveSelectedFilterProfile();
            sync();
        }
    };
    private UUID ownerId;
    private String ownerName = "";
    private final Set<UUID> registeredStarbuncles = new HashSet<>();
    private final Map<UUID, Integer> incompleteRouteTicks = new HashMap<>();
    private final Map<UUID, FilterProfile> filterProfiles = new HashMap<>();
    private final Set<UUID> pendingWandDetach = new HashSet<>();
    private final Map<UUID, TransitTracker> transitTrackers = new HashMap<>();
    private boolean allowList;
    private boolean automaticRecall;
    private boolean teleportOnStuck = true;
    private UUID highlightedStarbuncle;
    private long highlightActiveUntil;
    private HubFilterScrollItem.MatchMode matchMode = HubFilterScrollItem.MatchMode.ITEM;
    private int upgradeTier;
    private boolean loadingFilterProfile;
    private int nearbyOwned;
    private int tickCounter;
    private HubState state = HubState.IDLE;

    public StarbuncleLogisticsHubBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.STARBUNCLE_LOGISTICS_HUB.get(), pos, state);
    }

    @Override public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.starbuncle_logistics_hub");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inv, Player player) {
        ensureHighlightedStarbuncle(routeSnapshots());
        keepHighlightActive();
        return new StarbuncleLogisticsHubMenu(id, inv, this);
    }
    public ItemStackHandler getInventory() { return inventory; }
    public ItemStackHandler getFilters() { return filters; }
    public int getNearbyOwned() { return nearbyOwned; }
    public HubState getState() { return state; }
    public boolean isAllowList() { return allowList; }
    public boolean isAutomaticRecall() { return automaticRecall; }
    public boolean isTeleportOnStuck() { return teleportOnStuck; }
    public HubFilterScrollItem.MatchMode getMatchMode() { return matchMode; }
    public @Nullable UUID getHighlightedStarbuncle() { return highlightedStarbuncle; }
    public int getUpgradeTier() { return upgradeTier; }
    public int getSharedThroughput() { return throughputForTier(upgradeTier); }
    public static int throughputForTier(int tier) {
        return THROUGHPUT_BY_TIER[Math.max(0, Math.min(MAX_UPGRADE_TIER, tier))];
    }
    public void setUpgradeTier(int tier) {
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tier));
        sync();
    }

    public List<RouteSnapshot> routeSnapshots() {
        if (!(level instanceof ServerLevel serverLevel)) return List.of();
        return ownedStarbuncles(serverLevel).stream().map(starbuncle -> {
            if (!(starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport)) {
                return new RouteSnapshot(starbuncle.getUUID(), starbuncle.getDisplayName().getString(),
                        List.of(), List.of());
            }
            return new RouteSnapshot(starbuncle.getUUID(), starbuncle.getDisplayName().getString(),
                    transport.FROM_LIST.stream().map(pos -> routeTarget(serverLevel, pos)).toList(),
                    transport.TO_LIST.stream().map(pos -> routeTarget(serverLevel, pos)).toList());
        }).toList();
    }

    private static RouteTarget routeTarget(ServerLevel level, BlockPos pos) {
        String blockName = level.hasChunkAt(pos)
                ? level.getBlockState(pos).getBlock().getName().getString()
                : Component.translatable("screen.ars_arcane_matrix.starbuncle_hub.unloaded").getString();
        return new RouteTarget(blockName, pos.immutable());
    }

    public void writeRouteData(RegistryFriendlyByteBuf buffer) {
        List<RouteSnapshot> snapshots = routeSnapshots();
        ensureHighlightedStarbuncle(snapshots);
        buffer.writeVarInt(snapshots.size());
        for (RouteSnapshot snapshot : snapshots) {
            buffer.writeUUID(snapshot.id());
            buffer.writeUtf(snapshot.name(), 128);
            writeTargets(buffer, snapshot.inputs());
            writeTargets(buffer, snapshot.outputs());
        }
        buffer.writeBoolean(highlightedStarbuncle != null);
        if (highlightedStarbuncle != null) buffer.writeUUID(highlightedStarbuncle);
    }

    private void ensureHighlightedStarbuncle(List<RouteSnapshot> snapshots) {
        if (highlightedStarbuncle != null
                && snapshots.stream().anyMatch(route -> route.id().equals(highlightedStarbuncle))) return;
        saveSelectedFilterProfile();
        highlightedStarbuncle = snapshots.isEmpty() ? null : snapshots.getFirst().id();
        loadSelectedFilterProfile();
    }

    private static void writeTargets(RegistryFriendlyByteBuf buffer, List<RouteTarget> targets) {
        buffer.writeVarInt(targets.size());
        for (RouteTarget target : targets) {
            buffer.writeUtf(target.blockName(), 128);
            buffer.writeBlockPos(target.pos());
        }
    }

    public record RouteTarget(String blockName, BlockPos pos) {}
    public record RouteSnapshot(UUID id, String name, List<RouteTarget> inputs, List<RouteTarget> outputs) {}

    public void highlight(UUID id) {
        saveSelectedFilterProfile();
        highlightedStarbuncle = registeredStarbuncles.contains(id) ? id : null;
        loadSelectedFilterProfile();
        keepHighlightActive();
    }

    public void keepHighlightActive() {
        if (level != null) highlightActiveUntil = level.getGameTime() + 30L;
    }

    public void clearHighlight() {
        saveSelectedFilterProfile();
        highlightedStarbuncle = null;
    }

    public void setOwner(Player player) {
        ownerId = player.getUUID();
        ownerName = player.getGameProfile().getName();
        sync();
    }
    public boolean canAccess(Player player) {
        return ownerId == null || ownerId.equals(player.getUUID());
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        detachWandRegistrations(serverLevel);
        boolean maintenanceTick = tickCounter % SCAN_INTERVAL == 0;
        boostLoadedStarbuncles(serverLevel, maintenanceTick);
        if (maintenanceTick) scan(serverLevel);
    }

    private void scan(ServerLevel serverLevel) {
        int previousNearby = nearbyOwned;
        HubState previousState = state;
        List<Starbuncle> starbuncles = ownedStarbuncles(serverLevel);
        TransferBudget transferBudget = new TransferBudget(getSharedThroughput());
        nearbyOwned = starbuncles.size();
        for (Starbuncle starbuncle : starbuncles) {
            if (starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport) {
                ItemStack scroll = createFilterScroll(profile(starbuncle.getUUID()));
                if (!ItemStack.isSameItemSameComponents(transport.itemScroll, scroll)) {
                    transport.itemScroll = scroll.copy();
                    transport.syncTag();
                }
                if (hasCompleteRoute(transport)) incompleteRouteTicks.remove(starbuncle.getUUID());
                else incompleteRouteTicks.merge(starbuncle.getUUID(), SCAN_INTERVAL, Integer::sum);
                if (hasCompleteRoute(transport) && transferBudget.hasCapacity()) {
                    bulkTransfer(serverLevel, starbuncle, transport, transferBudget);
                }
                checkTransit(serverLevel, starbuncle, transport);
            }
        }
        if (automaticRecall) recallExpiredIncomplete(starbuncles);
        if (state != HubState.OUTPUT_BLOCKED) state = nearbyOwned == 0 ? HubState.IDLE : HubState.READY;
        if (previousNearby != nearbyOwned || previousState != state) sync();
    }

    private void boostLoadedStarbuncles(ServerLevel serverLevel, boolean maintenanceTick) {
        for (UUID id : registeredStarbuncles) {
            if (!(serverLevel.getEntity(id) instanceof Starbuncle starbuncle)
                    || !starbuncle.isAlive()
                    || starbuncle.distanceToSqr(worldPosition.getCenter()) > RANGE * RANGE) continue;
            starbuncle.setSilent(true);
            if (maintenanceTick) {
                starbuncle.addEffect(new MobEffectInstance(
                        MobEffects.MOVEMENT_SPEED, 40, 3, true, false, false));
                starbuncle.getPersistentData().putLong(PROTECTED_UNTIL_TAG,
                        serverLevel.getGameTime() + 40L);
                starbuncle.clearFire();
                starbuncle.fallDistance = 0.0F;
                starbuncle.setAirSupply(starbuncle.getMaxAirSupply());
                if (serverLevel.getGameTime() <= highlightActiveUntil
                        && id.equals(highlightedStarbuncle)) {
                    starbuncle.addEffect(new MobEffectInstance(
                            MobEffects.GLOWING, 30, 0, true, false, false));
                }
            }
            if (starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport) {
                transport.findItemBackoff = 0;
                transport.takeItemBackoff = 0;
                transport.berryBackoff = 0;
                transport.nextBerryBackoff = 1;
            }
        }
    }

    private void bulkTransfer(ServerLevel level, Starbuncle starbuncle,
                              StarbyTransportBehavior transport, TransferBudget transferBudget) {
        FilterProfile filter = profile(starbuncle.getUUID());
        List<TransferEndpoint> outputs = transport.TO_LIST.stream()
                .map(pos -> new TransferEndpoint(pos, handlerAt(level, pos,
                        transport.TO_DIRECTION_MAP.get(pos.hashCode()))))
                .filter(endpoint -> endpoint.handler != null).toList();
        if (outputs.isEmpty()) return;
        for (BlockPos inputPos : transport.FROM_LIST) {
            IItemHandler input = handlerAt(level, inputPos,
                    transport.FROM_DIRECTION_MAP.get(inputPos.hashCode()));
            if (input == null) continue;
            for (int slot = 0; slot < input.getSlots() && transferBudget.hasCapacity(); slot++) {
                while (transferBudget.hasCapacity()) {
                    ItemStack visible = input.getStackInSlot(slot);
                    if (visible.isEmpty() || !filterAccepts(filter, visible)) break;
                    int request = Math.min(transferBudget.remainingItems,
                            Math.max(1, visible.getMaxStackSize()));
                    ItemStack simulated = input.extractItem(slot, request, true);
                    if (simulated.isEmpty()) break;
                    ItemStack simulatedRemainder = insertAcross(
                            outputs, inputPos, simulated, true);
                    int accepted = simulated.getCount() - simulatedRemainder.getCount();
                    if (accepted <= 0) {
                        state = HubState.OUTPUT_BLOCKED;
                        break;
                    }
                    ItemStack extracted = input.extractItem(slot, accepted, false);
                    if (extracted.isEmpty()) break;
                    ItemStack remainder = insertAcross(outputs, inputPos, extracted, false);
                    int delivered = extracted.getCount() - remainder.getCount();
                    if (!remainder.isEmpty()) {
                        ItemStack rollback = ItemHandlerHelper.insertItem(input, remainder, false);
                        if (!rollback.isEmpty()) insert(rollback);
                    }
                    if (delivered <= 0) break;
                    transferBudget.remainingItems -= delivered;
                    transferBudget.operations++;
                }
            }
            if (!transferBudget.hasCapacity()) break;
        }
    }

    private static ItemStack insertAcross(List<TransferEndpoint> outputs, BlockPos inputPos,
                                          ItemStack stack, boolean simulate) {
        ItemStack remainder = stack;
        for (TransferEndpoint output : outputs) {
            if (output.pos.equals(inputPos)) continue;
            remainder = ItemHandlerHelper.insertItem(output.handler, remainder, simulate);
            if (remainder.isEmpty()) break;
        }
        return remainder;
    }

    private static IItemHandler handlerAt(ServerLevel level, BlockPos pos, @Nullable Direction face) {
        if (!level.hasChunkAt(pos)) return null;
        if (face != null) {
            IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, face);
            if (sided != null) return sided;
        }
        IItemHandler unsided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (unsided != null) return unsided;
        for (Direction direction : Direction.values()) {
            IItemHandler sided = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, direction);
            if (sided != null) return sided;
        }
        return null;
    }

    private static boolean filterAccepts(FilterProfile profile, ItemStack candidate) {
        boolean matches = profile.templates.stream().anyMatch(template ->
                filterMatches(candidate, template, profile.matchMode));
        return profile.allowList ? matches : !matches;
    }

    private static boolean filterMatches(ItemStack candidate, ItemStack template,
                                         HubFilterScrollItem.MatchMode mode) {
        if (candidate.isEmpty() || template.isEmpty()) return false;
        return switch (mode) {
            case EXACT -> ItemStack.isSameItemSameComponents(candidate, template);
            case ITEM -> candidate.is(template.getItem());
            case TAG -> {
                Set<net.minecraft.resources.ResourceLocation> tags = new HashSet<>();
                candidate.getTags().map(net.minecraft.tags.TagKey::location).forEach(tags::add);
                yield template.getTags().map(net.minecraft.tags.TagKey::location).anyMatch(tags::contains);
            }
            case MOD -> BuiltInRegistries.ITEM.getKey(candidate.getItem()).getNamespace().equals(
                    BuiltInRegistries.ITEM.getKey(template.getItem()).getNamespace());
        };
    }

    private record TransferEndpoint(BlockPos pos, IItemHandler handler) {}

    private void checkTransit(ServerLevel level, Starbuncle starbuncle,
                              StarbyTransportBehavior transport) {
        if (starbuncle.getY() < level.getMinBuildHeight() + 2) {
            teleportSafely(level, starbuncle, worldPosition.above());
            transitTrackers.remove(starbuncle.getUUID());
            return;
        }
        BlockPos target = starbuncle.getHeldStack().isEmpty()
                ? transport.getValidTakePos()
                : transport.getValidStorePos(starbuncle.getHeldStack());
        if (target == null || !level.hasChunkAt(target)) {
            transitTrackers.remove(starbuncle.getUUID());
            return;
        }
        double distance = starbuncle.distanceToSqr(target.getCenter());
        if (distance <= 4.0D) {
            transitTrackers.remove(starbuncle.getUUID());
            return;
        }
        TransitTracker tracker = transitTrackers.computeIfAbsent(starbuncle.getUUID(),
                ignored -> new TransitTracker(target, distance));
        if (!tracker.target.equals(target) || distance + 0.5D < tracker.lastDistance) {
            tracker.target = target.immutable();
            tracker.lastDistance = distance;
            tracker.stuckTicks = 0;
            tracker.repathAttempted = false;
            return;
        }
        tracker.lastDistance = distance;
        tracker.stuckTicks += SCAN_INTERVAL;
        if (!tracker.repathAttempted && tracker.stuckTicks >= 100) {
            starbuncle.getNavigation().stop();
            starbuncle.getNavigation().moveTo(
                    target.getX() + 0.5D, target.getY() + 1.0D, target.getZ() + 0.5D, 1.5D);
            tracker.repathAttempted = true;
        } else if (teleportOnStuck && tracker.stuckTicks >= 160) {
            if (teleportSafely(level, starbuncle, target)) transitTrackers.remove(starbuncle.getUUID());
        }
    }

    private static boolean teleportSafely(ServerLevel level, Starbuncle starbuncle, BlockPos target) {
        for (int y = 0; y <= 2; y++) {
            for (int radius = 1; radius <= 3; radius++) {
                for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                    BlockPos feet = target.offset(x, y, z);
                    if (!level.getBlockState(feet).isAir() || !level.getBlockState(feet.above()).isAir()
                            || !level.getBlockState(feet.below()).isFaceSturdy(
                            level, feet.below(), Direction.UP)) continue;
                    starbuncle.teleportTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D);
                    starbuncle.setDeltaMovement(0.0D, 0.0D, 0.0D);
                    return true;
                }
            }
        }
        return false;
    }

    private ItemStack createFilterScroll(FilterProfile profile) {
        ItemStack scroll = new ItemStack(ModItems.HUB_FILTER_SCROLL.get());
        List<ItemStack> templates = profile.templates.stream()
                .filter(stack -> !stack.isEmpty()).map(stack -> stack.copyWithCount(1)).toList();
        scroll.set(DataComponentRegistry.ITEM_SCROLL_DATA.get(), new ItemScrollData(templates));
        CompoundTag settings = new CompoundTag();
        settings.putBoolean("Allow", profile.allowList);
        settings.putString("Mode", profile.matchMode.name());
        scroll.set(DataComponents.CUSTOM_DATA, CustomData.of(settings));
        return scroll;
    }

    private FilterProfile profile(UUID id) {
        return filterProfiles.computeIfAbsent(id, ignored -> new FilterProfile());
    }

    private void saveSelectedFilterProfile() {
        if (loadingFilterProfile || highlightedStarbuncle == null) return;
        FilterProfile profile = profile(highlightedStarbuncle);
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            profile.templates.set(slot, filters.getStackInSlot(slot).copyWithCount(1));
        }
        profile.allowList = allowList;
        profile.matchMode = matchMode;
    }

    private void loadSelectedFilterProfile() {
        loadingFilterProfile = true;
        FilterProfile profile = highlightedStarbuncle == null
                ? new FilterProfile() : profile(highlightedStarbuncle);
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            filters.setStackInSlot(slot, profile.templates.get(slot).copy());
        }
        allowList = profile.allowList;
        matchMode = profile.matchMode;
        loadingFilterProfile = false;
        sync();
    }

    public int recallAll(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !canAccess(player)) return 0;
        if (ownerId == null) setOwner(player);
        return recallMatching(ownedStarbuncles(serverLevel), false);
    }

    public int recallIncomplete(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !canAccess(player)) return 0;
        return recallMatching(ownedStarbuncles(serverLevel), true);
    }

    public int recallHighlighted(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !canAccess(player)
                || highlightedStarbuncle == null
                || !(serverLevel.getEntity(highlightedStarbuncle) instanceof Starbuncle starbuncle)
                || !isOwned(starbuncle)) return 0;
        return recallMatching(List.of(starbuncle), false);
    }

    public void toggleAutomaticRecall() { automaticRecall = !automaticRecall; sync(); }
    public void toggleTeleportOnStuck() { teleportOnStuck = !teleportOnStuck; sync(); }
    public void toggleAllowList() { allowList = !allowList; saveSelectedFilterProfile(); sync(); }
    public void cycleMatchMode() {
        HubFilterScrollItem.MatchMode[] modes = HubFilterScrollItem.MatchMode.values();
        matchMode = modes[(matchMode.ordinal() + 1) % modes.length];
        saveSelectedFilterProfile();
        sync();
    }

    private void recallExpiredIncomplete(List<Starbuncle> candidates) {
        List<Starbuncle> expired = candidates.stream()
                .filter(starbuncle -> incompleteRouteTicks.getOrDefault(
                        starbuncle.getUUID(), 0) >= AUTO_RECALL_TICKS).toList();
        recallMatching(expired, false);
    }

    private int recallMatching(List<Starbuncle> candidates, boolean incompleteOnly) {
        int recalled = 0;
        boolean blocked = false;
        for (Starbuncle starbuncle : candidates) {
            if (incompleteOnly && starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport
                    && hasCompleteRoute(transport)) continue;
            if (!recallOne(starbuncle)) { blocked = true; continue; }
            recalled++;
        }
        nearbyOwned = Math.max(0, nearbyOwned - recalled);
        state = blocked ? HubState.OUTPUT_BLOCKED : recalled > 0 ? HubState.RECALLED
                : nearbyOwned > 0 ? HubState.READY : HubState.IDLE;
        sync();
        return recalled;
    }

    private boolean recallOne(Starbuncle starbuncle) {
        if (starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport) {
            transport.itemScroll = createFilterScroll(profile(starbuncle.getUUID()));
            transport.syncTag();
        }
        ItemStack charm = new ItemStack(ItemsRegistry.STARBUNCLE_CHARM.get());
        charm.set(DataComponentRegistry.STARBUNCLE_DATA.get(), starbuncle.data.immutable());
        ItemStack held = starbuncle.getHeldStack();
        if (!canFit(charm, held)) return false;
        insert(charm);
        if (held != null && !held.isEmpty()) insert(held.copy());
        starbuncle.setHeldStack(ItemStack.EMPTY);
        registeredStarbuncles.remove(starbuncle.getUUID());
        filterProfiles.remove(starbuncle.getUUID());
        incompleteRouteTicks.remove(starbuncle.getUUID());
        transitTrackers.remove(starbuncle.getUUID());
        starbuncle.discard();
        return true;
    }

    private static boolean hasCompleteRoute(StarbyTransportBehavior transport) {
        return !transport.FROM_LIST.isEmpty() && !transport.TO_LIST.isEmpty();
    }

    private List<Starbuncle> ownedStarbuncles(ServerLevel serverLevel) {
        return registeredStarbuncles.stream()
                .map(serverLevel::getEntity)
                .filter(Starbuncle.class::isInstance)
                .map(Starbuncle.class::cast)
                .filter(this::isOwned)
                .filter(starbuncle -> starbuncle.distanceToSqr(
                        worldPosition.getCenter()) <= RANGE * RANGE)
                .toList();
    }

    private boolean isOwned(Starbuncle starbuncle) {
        // Ars Nouveau Starbuncles do not store a player-owner UUID. The charm data's
        // "adopter" field belongs to contributor cosmetics, not taming ownership.
        // Ownership is therefore enforced on the hub itself, while its deliberately
        // local range prevents it from becoming a server-wide entity grabber.
        return starbuncle.isAlive() && starbuncle.isTamed()
                && registeredStarbuncles.contains(starbuncle.getUUID());
    }

    public int registerNearby(Player player) {
        if (!(level instanceof ServerLevel serverLevel) || !canAccess(player)) return 0;
        if (ownerId == null) setOwner(player);
        int added = 0;
        AABB area = new AABB(worldPosition).inflate(RANGE);
        for (Starbuncle starbuncle : serverLevel.getEntitiesOfClass(
                Starbuncle.class, area, entity -> entity.isAlive() && entity.isTamed())) {
            if (registeredStarbuncles.add(starbuncle.getUUID())) added++;
            profile(starbuncle.getUUID());
        }
        ensureHighlightedStarbuncle(routeSnapshots());
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.starbuncle_hub.registered_nearby", added));
        sync();
        return added;
    }

    @Override
    public Result onFirstConnection(GlobalPos targetPos, @Nullable Direction face,
                                    @Nullable LivingEntity entity, Player player) {
        return registerFromWand(entity, player);
    }

    @Override
    public Result onLastConnection(GlobalPos targetPos, @Nullable Direction face,
                                   @Nullable LivingEntity entity, Player player) {
        return registerFromWand(entity, player);
    }

    private Result registerFromWand(@Nullable LivingEntity entity, Player player) {
        if (!canAccess(player) || !(entity instanceof Starbuncle starbuncle) || !starbuncle.isTamed()) {
            return Result.FAIL;
        }
        if (ownerId == null) setOwner(player);
        registeredStarbuncles.add(starbuncle.getUUID());
        profile(starbuncle.getUUID());
        ensureHighlightedStarbuncle(routeSnapshots());
        pendingWandDetach.add(starbuncle.getUUID());
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.starbuncle_hub.registered", starbuncle.getDisplayName()));
        sync();
        return Result.SUCCESS;
    }

    private void detachWandRegistrations(ServerLevel serverLevel) {
        if (pendingWandDetach.isEmpty()) return;
        for (UUID id : List.copyOf(pendingWandDetach)) {
            if (serverLevel.getEntity(id) instanceof Starbuncle starbuncle
                    && starbuncle.dynamicBehavior instanceof StarbyTransportBehavior transport) {
                transport.FROM_LIST.removeIf(worldPosition::equals);
                transport.TO_LIST.removeIf(worldPosition::equals);
                transport.FROM_DIRECTION_MAP.remove(worldPosition.hashCode());
                transport.TO_DIRECTION_MAP.remove(worldPosition.hashCode());
                transport.syncTag();
            }
            pendingWandDetach.remove(id);
        }
    }

    public void clearRegistrations(Player player) {
        if (!canAccess(player)) return;
        releaseManagedSilence();
        registeredStarbuncles.clear();
        incompleteRouteTicks.clear();
        transitTrackers.clear();
        filterProfiles.clear();
        highlightedStarbuncle = null;
        loadSelectedFilterProfile();
        nearbyOwned = 0;
        state = HubState.IDLE;
        player.sendSystemMessage(Component.translatable(
                "message.ars_arcane_matrix.starbuncle_hub.registrations_cleared"));
        sync();
    }

    private boolean canFit(ItemStack first, ItemStack second) {
        ItemStackHandler simulation = new ItemStackHandler(inventory.getSlots());
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            simulation.setStackInSlot(slot, inventory.getStackInSlot(slot).copy());
        }
        if (!insertInto(simulation, first.copy()).isEmpty()) return false;
        return second == null || second.isEmpty() || insertInto(simulation, second.copy()).isEmpty();
    }
    private void insert(ItemStack stack) { insertInto(inventory, stack); }
    private static ItemStack insertInto(ItemStackHandler handler, ItemStack stack) {
        ItemStack remainder = stack;
        for (int slot = 0; slot < handler.getSlots() && !remainder.isEmpty(); slot++) {
            remainder = handler.insertItem(slot, remainder, false);
        }
        return remainder;
    }

    public void dropContents() {
        if (level == null || level.isClientSide) return;
        releaseManagedSilence();
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, worldPosition.getX() + .5, worldPosition.getY() + .5,
                        worldPosition.getZ() + .5, stack.copy());
                inventory.setStackInSlot(slot, ItemStack.EMPTY);
            }
        }
    }

    private void releaseManagedSilence() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        for (UUID id : registeredStarbuncles) {
            if (serverLevel.getEntity(id) instanceof Starbuncle starbuncle) starbuncle.setSilent(false);
        }
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        saveSelectedFilterProfile();
        tag.put("Inventory", inventory.serializeNBT(registries));
        tag.put("Filters", filters.serializeNBT(registries));
        if (ownerId != null) tag.putUUID("Owner", ownerId);
        tag.putString("OwnerName", ownerName);
        tag.putInt("RegisteredCount", registeredStarbuncles.size());
        int registeredIndex = 0;
        for (UUID id : registeredStarbuncles) tag.putUUID("Registered" + registeredIndex++, id);
        tag.putInt("NearbyOwned", nearbyOwned);
        tag.putString("HubState", state.name());
        tag.putBoolean("AllowList", allowList);
        tag.putBoolean("AutomaticRecall", automaticRecall);
        tag.putBoolean("TeleportOnStuck", teleportOnStuck);
        tag.putString("MatchMode", matchMode.name());
        tag.putInt("UpgradeTier", upgradeTier);
        if (highlightedStarbuncle != null) tag.putUUID("HighlightedStarbuncle", highlightedStarbuncle);
        ListTag profileList = new ListTag();
        for (Map.Entry<UUID, FilterProfile> entry : filterProfiles.entrySet()) {
            CompoundTag profileTag = new CompoundTag();
            profileTag.putUUID("Starbuncle", entry.getKey());
            profileTag.putBoolean("AllowList", entry.getValue().allowList);
            profileTag.putString("MatchMode", entry.getValue().matchMode.name());
            ItemStackHandler profileItems = new ItemStackHandler(27);
            for (int slot = 0; slot < 27; slot++) {
                profileItems.setStackInSlot(slot, entry.getValue().templates.get(slot).copy());
            }
            profileTag.put("Items", profileItems.serializeNBT(registries));
            profileList.add(profileTag);
        }
        tag.put("FilterProfiles", profileList);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        inventory.deserializeNBT(registries, tag.getCompound("Inventory"));
        filters.deserializeNBT(registries, tag.getCompound("Filters"));
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        ownerName = tag.getString("OwnerName");
        registeredStarbuncles.clear();
        int registeredCount = Math.max(0, tag.getInt("RegisteredCount"));
        for (int i = 0; i < registeredCount; i++) {
            String key = "Registered" + i;
            if (tag.hasUUID(key)) registeredStarbuncles.add(tag.getUUID(key));
        }
        nearbyOwned = Math.max(0, tag.getInt("NearbyOwned"));
        try { state = HubState.valueOf(tag.getString("HubState")); }
        catch (IllegalArgumentException ignored) { state = HubState.IDLE; }
        allowList = tag.getBoolean("AllowList");
        automaticRecall = tag.getBoolean("AutomaticRecall");
        teleportOnStuck = !tag.contains("TeleportOnStuck") || tag.getBoolean("TeleportOnStuck");
        try { matchMode = HubFilterScrollItem.MatchMode.valueOf(tag.getString("MatchMode")); }
        catch (IllegalArgumentException ignored) { matchMode = HubFilterScrollItem.MatchMode.ITEM; }
        upgradeTier = Math.max(0, Math.min(MAX_UPGRADE_TIER, tag.getInt("UpgradeTier")));
        highlightedStarbuncle = tag.hasUUID("HighlightedStarbuncle")
                ? tag.getUUID("HighlightedStarbuncle") : null;
        filterProfiles.clear();
        ListTag profileList = tag.getList("FilterProfiles", Tag.TAG_COMPOUND);
        for (int index = 0; index < profileList.size(); index++) {
            CompoundTag profileTag = profileList.getCompound(index);
            if (!profileTag.hasUUID("Starbuncle")) continue;
            FilterProfile profile = new FilterProfile();
            profile.allowList = profileTag.getBoolean("AllowList");
            try { profile.matchMode = HubFilterScrollItem.MatchMode.valueOf(
                    profileTag.getString("MatchMode")); }
            catch (IllegalArgumentException ignored) { profile.matchMode = HubFilterScrollItem.MatchMode.ITEM; }
            ItemStackHandler profileItems = new ItemStackHandler(27);
            profileItems.deserializeNBT(registries, profileTag.getCompound("Items"));
            for (int slot = 0; slot < 27; slot++) {
                profile.templates.set(slot, profileItems.getStackInSlot(slot).copyWithCount(1));
            }
            filterProfiles.put(profileTag.getUUID("Starbuncle"), profile);
        }
        // Migrate the earlier hub-wide filter to each registered Starbuncle once.
        if (profileList.isEmpty()) {
            for (UUID id : registeredStarbuncles) {
                FilterProfile profile = profile(id);
                profile.allowList = allowList;
                profile.matchMode = matchMode;
                for (int slot = 0; slot < filters.getSlots(); slot++) {
                    profile.templates.set(slot, filters.getStackInSlot(slot).copyWithCount(1));
                }
            }
        }
        if (highlightedStarbuncle != null && !registeredStarbuncles.contains(highlightedStarbuncle)) {
            highlightedStarbuncle = null;
        }
        loadSelectedFilterProfile();
    }
    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 3);
    }

    public enum HubState {
        IDLE("message.ars_arcane_matrix.starbuncle_hub.state.idle"),
        READY("message.ars_arcane_matrix.starbuncle_hub.state.ready"),
        RECALLED("message.ars_arcane_matrix.starbuncle_hub.state.recalled"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.starbuncle_hub.state.output_blocked");
        private final String key;
        HubState(String key) { this.key = key; }
        public String translationKey() { return key; }
    }

    private static final class FilterProfile {
        private final List<ItemStack> templates = new java.util.ArrayList<>(27);
        private boolean allowList;
        private HubFilterScrollItem.MatchMode matchMode = HubFilterScrollItem.MatchMode.ITEM;

        private FilterProfile() {
            for (int slot = 0; slot < 27; slot++) templates.add(ItemStack.EMPTY);
        }
    }

    private static final class TransitTracker {
        private BlockPos target;
        private double lastDistance;
        private int stuckTicks;
        private boolean repathAttempted;

        private TransitTracker(BlockPos target, double lastDistance) {
            this.target = target.immutable();
            this.lastDistance = lastDistance;
        }
    }

    private static final class TransferBudget {
        private int remainingItems;
        private int operations;

        private TransferBudget(int remainingItems) {
            this.remainingItems = remainingItems;
        }

        private boolean hasCapacity() {
            return remainingItems > 0 && operations < MAX_BULK_OPERATIONS;
        }
    }
}
