package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.client.ITooltipProvider;
import com.hollingsworth.arsnouveau.api.item.IWandable;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.menu.ArcaneVacuumHopperMenu;
import dev.arsmatrix.registry.ModBlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.neoforged.neoforge.items.ItemStackHandler;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/** Direct area collector with separate item and XP-gem routing. Touhou P-point entities are never queried. */
public final class ArcaneVacuumHopperBlockEntity extends BlockEntity
        implements MenuProvider, IWandable, ITooltipProvider {
    public static final int DROP_SLOTS = 18;
    public static final int GEM_SLOTS = 2;
    public static final int FILTER_SLOTS = 9;
    public static final int MAX_EXPERIENCE = 10_000_000;

    private final ItemStackHandler drops = new ItemStackHandler(DROP_SLOTS) {
        @Override protected void onContentsChanged(int slot) { sync(); }
    };
    private final ItemStackHandler gems = new ItemStackHandler(GEM_SLOTS) {
        @Override protected void onContentsChanged(int slot) { sync(); }
    };
    private final ItemStackHandler filters = new ItemStackHandler(FILTER_SLOTS) {
        @Override public int getSlotLimit(int slot) { return 1; }
        @Override protected void onContentsChanged(int slot) { sync(); }
    };
    /**
     * Automation-facing view of the item buffer. Matching insertions are accepted
     * and voided only when the explicitly configured destroy toggle is enabled;
     * every other operation behaves exactly like the ordinary buffer.
     */
    private final IItemHandler automationItems = new IItemHandler() {
        @Override public int getSlots() { return drops.getSlots(); }
        @Override public ItemStack getStackInSlot(int slot) { return drops.getStackInSlot(slot); }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            if (!stack.isEmpty() && automationDestroyActive() && matchesFilter(stack)) {
                return ItemStack.EMPTY;
            }
            return drops.insertItem(slot, stack, simulate);
        }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return drops.extractItem(slot, amount, simulate);
        }
        @Override public int getSlotLimit(int slot) { return drops.getSlotLimit(slot); }
        @Override public boolean isItemValid(int slot, ItemStack stack) {
            return (!stack.isEmpty() && automationDestroyActive() && matchesFilter(stack))
                    || drops.isItemValid(slot, stack);
        }
    };
    private int experience;
    private boolean collectItems = true;
    private boolean collectExperience = true;
    private boolean destroyMatches;
    private boolean strictComponents;
    private FilterMode filterMode = FilterMode.DISABLED;
    private GemMode gemMode = GemMode.PAUSED;
    private RangeMode rangeMode = RangeMode.R12;
    private OutputMode itemOutputMode = OutputMode.AUTO;
    private OutputMode gemOutputMode = OutputMode.AUTO;
    private BindChannel bindChannel = BindChannel.ITEMS;
    private GlobalPos itemTarget;
    private Direction itemTargetFace;
    private GlobalPos gemTarget;
    private Direction gemTargetFace;
    private int tickCounter;

    public ArcaneVacuumHopperBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.ARCANE_VACUUM_HOPPER.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        if (level.hasNeighborSignal(worldPosition)) return;
        if (tickCounter % rangeMode.scanIntervalTicks() == 0) {
            collectInRange(serverLevel);
        }
        if (tickCounter % 5 == 0) {
            pushBuffer(drops, itemTarget, itemTargetFace, itemOutputMode);
            pushBuffer(gems, gemTarget, gemTargetFace, gemOutputMode);
        }
        if (tickCounter % 10 == 0) convertExperience();
    }

    private void collectInRange(ServerLevel serverLevel) {
        AABB bounds = collectionBounds(serverLevel);
        int processed = 0;
        if (collectItems || destroyMatches) {
            for (ItemEntity item : serverLevel.getEntitiesOfClass(ItemEntity.class, bounds,
                    entity -> entity.isAlive() && !entity.getItem().isEmpty())) {
                if (processed++ >= 128) break;
                if (destroyMatches && matchesFilter(item.getItem())) {
                    item.discard();
                    continue;
                }
                if (!collectItems) continue;
                if (!accepts(item.getItem())) continue;
                collectItem(item);
            }
        }
        if (collectExperience && experience < MAX_EXPERIENCE && processed < 128) {
            for (ExperienceOrb orb : serverLevel.getEntitiesOfClass(ExperienceOrb.class, bounds,
                    Entity::isAlive)) {
                if (processed++ >= 128) break;
                collectOrb(orb);
            }
        }
    }

    private AABB collectionBounds(ServerLevel serverLevel) {
        if (rangeMode == RangeMode.CHUNK) {
            ChunkPos chunk = new ChunkPos(worldPosition);
            return new AABB(chunk.getMinBlockX(), serverLevel.getMinBuildHeight(), chunk.getMinBlockZ(),
                    chunk.getMaxBlockX() + 1.0D, serverLevel.getMaxBuildHeight(), chunk.getMaxBlockZ() + 1.0D);
        }
        return new AABB(worldPosition).inflate(rangeMode.radius());
    }

    private void collectItem(ItemEntity entity) {
        ItemStack original = entity.getItem();
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(drops, original.copy(), false);
        if (remainder.getCount() == original.getCount()) return;
        if (remainder.isEmpty()) entity.discard(); else entity.setItem(remainder);
        sync();
    }

    private void collectOrb(ExperienceOrb orb) {
        int accepted = Math.min(orb.getValue(), MAX_EXPERIENCE - experience);
        if (accepted <= 0) return;
        experience += accepted;
        // Vanilla XP orbs are indivisible. Capacity is large enough that partial acceptance is exceptional;
        // keep the excess as a fresh orb rather than deleting experience.
        int remainder = orb.getValue() - accepted;
        Vec3 position = orb.position();
        orb.discard();
        if (remainder > 0 && level instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(new ExperienceOrb(serverLevel, position.x, position.y, position.z, remainder));
        }
        sync();
    }

    private boolean accepts(ItemStack stack) {
        if (filterMode == FilterMode.DISABLED) return true;
        boolean matched = matchesFilter(stack);
        return (filterMode == FilterMode.WHITELIST) == matched;
    }

    private boolean matchesFilter(ItemStack stack) {
        for (int slot = 0; slot < filters.getSlots(); slot++) {
            ItemStack filter = filters.getStackInSlot(slot);
            if (!filter.isEmpty() && (strictComponents
                    ? ItemStack.isSameItemSameComponents(filter, stack)
                    : ItemStack.isSameItem(filter, stack))) return true;
        }
        return false;
    }

    private boolean automationDestroyActive() {
        return destroyMatches && level != null && !level.hasNeighborSignal(worldPosition);
    }

    private void convertExperience() {
        if (gemMode == GemMode.PAUSED || experience < 3) return;
        if (gemMode == GemMode.GREATER) {
            int made = createGems(new ItemStack(ItemsRegistry.GREATER_EXPERIENCE_GEM.get()), experience / 12);
            experience -= made * 12;
            sync();
            return;
        }
        if (gemMode == GemMode.NORMAL) {
            int made = createGems(new ItemStack(ItemsRegistry.EXPERIENCE_GEM.get()), experience / 3);
            experience -= made * 3;
        }
        sync();
    }

    private int createGems(ItemStack template, int requested) {
        int remaining = Math.min(requested, template.getMaxStackSize());
        if (remaining <= 0) return 0;
        ItemStack stack = template.copyWithCount(remaining);
        ItemStack remainder = ItemHandlerHelper.insertItemStacked(gems, stack, false);
        return remaining - remainder.getCount();
    }

    private void pushBuffer(ItemStackHandler buffer, @Nullable GlobalPos target,
                            @Nullable Direction targetFace, OutputMode outputMode) {
        if (outputMode == OutputMode.OFF || level == null) return;
        IItemHandler handler = null;
        if ((outputMode == OutputMode.AUTO || outputMode == OutputMode.BOUND_ONLY) && target != null
                && level.getServer() != null) {
            ServerLevel targetLevel = level.getServer().getLevel(target.dimension());
            if (targetLevel != null && targetLevel.hasChunkAt(target.pos())) {
                handler = targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), targetFace);
            }
        }
        if (handler == null && (outputMode == OutputMode.AUTO || outputMode == OutputMode.BELOW_ONLY)
                && level.hasChunkAt(worldPosition.below())) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, worldPosition.below(), Direction.UP);
        }
        if (handler == null) return;
        for (int slot = 0; slot < buffer.getSlots(); slot++) {
            ItemStack current = buffer.getStackInSlot(slot);
            if (current.isEmpty()) continue;
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(handler, current.copy(), false);
            if (remainder.getCount() != current.getCount()) buffer.setStackInSlot(slot, remainder);
        }
    }

    public int depositAllExperience(Player player) {
        int available = Math.max(0, player.totalExperience);
        int accepted = Math.min(available, MAX_EXPERIENCE - experience);
        if (accepted <= 0) return 0;
        player.giveExperiencePoints(-accepted);
        experience += accepted;
        sync();
        return accepted;
    }

    public int depositExperience(Player player, int requested) {
        int accepted = Math.min(Math.max(0, requested),
                Math.min(Math.max(0, player.totalExperience), MAX_EXPERIENCE - experience));
        if (accepted <= 0) return 0;
        player.giveExperiencePoints(-accepted);
        experience += accepted;
        sync();
        return accepted;
    }

    @Override public Result onFirstConnection(GlobalPos target, @Nullable Direction face,
                                               @Nullable LivingEntity entity, Player player) {
        return bind(target, face, player);
    }
    @Override public Result onLastConnection(GlobalPos target, @Nullable Direction face,
                                              @Nullable LivingEntity entity, Player player) {
        return bind(target, face, player);
    }
    private Result bind(GlobalPos target, @Nullable Direction face, Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || target == null
                || serverPlayer.getServer() == null) return Result.FAIL;
        ServerLevel targetLevel = serverPlayer.getServer().getLevel(target.dimension());
        if (targetLevel == null || !targetLevel.hasChunkAt(target.pos())
                || targetLevel.getCapability(Capabilities.ItemHandler.BLOCK, target.pos(), face) == null) {
            player.displayClientMessage(Component.translatable(
                    "message.ars_arcane_matrix.arcane_vacuum_hopper.invalid_target"), true);
            return Result.FAIL;
        }
        if (bindChannel == BindChannel.ITEMS) { itemTarget = target; itemTargetFace = face; }
        else { gemTarget = target; gemTargetFace = face; }
        sync();
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_vacuum_hopper.bound",
                Component.translatable("screen.ars_arcane_matrix.arcane_vacuum_hopper.channel."
                        + bindChannel.name().toLowerCase())), true);
        return Result.SUCCESS;
    }
    @Override public Result onClearConnections(Player player) {
        itemTarget = null; itemTargetFace = null; gemTarget = null; gemTargetFace = null; sync();
        player.displayClientMessage(Component.translatable(
                "message.ars_arcane_matrix.arcane_vacuum_hopper.cleared"), true);
        return Result.SUCCESS;
    }

    public void cycleGemMode() { gemMode = gemMode.next(); sync(); }
    public void cycleFilterMode() { filterMode = filterMode.next(); sync(); }
    public void cycleItemOutputMode() { itemOutputMode = itemOutputMode.next(); sync(); }
    public void cycleGemOutputMode() { gemOutputMode = gemOutputMode.next(); sync(); }
    public void toggleItems() { collectItems = !collectItems; sync(); }
    public void toggleExperience() { collectExperience = !collectExperience; sync(); }
    public void toggleDestroyMatches() { destroyMatches = !destroyMatches; sync(); }
    public void toggleStrictComponents() { strictComponents = !strictComponents; sync(); }
    public void cycleRangeMode() { rangeMode = rangeMode.next(); sync(); }
    public void cycleBindChannel() { bindChannel = bindChannel.next(); sync(); }

    public ItemStackHandler drops() { return drops; }
    public IItemHandler automationItems() { return automationItems; }
    public ItemStackHandler gems() { return gems; }
    public ItemStackHandler filters() { return filters; }
    public int experience() { return experience; }
    public boolean collectsItems() { return collectItems; }
    public boolean collectsExperience() { return collectExperience; }
    public boolean destroysMatches() { return destroyMatches; }
    public boolean strictComponents() { return strictComponents; }
    public FilterMode filterMode() { return filterMode; }
    public GemMode gemMode() { return gemMode; }
    public RangeMode rangeMode() { return rangeMode; }
    public OutputMode itemOutputMode() { return itemOutputMode; }
    public OutputMode gemOutputMode() { return gemOutputMode; }
    public BindChannel bindChannel() { return bindChannel; }
    public boolean hasItemTarget() { return itemTarget != null; }
    public boolean hasGemTarget() { return gemTarget != null; }

    @Override public Component getDisplayName() {
        return Component.translatable("block.ars_arcane_matrix.arcane_vacuum_hopper");
    }
    @Override public AbstractContainerMenu createMenu(int id, Inventory inventory, Player player) {
        return new ArcaneVacuumHopperMenu(id, inventory, this);
    }
    @Override public void getTooltip(List<Component> tooltip) {
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_vacuum_hopper.range",
                Component.translatable(rangeMode.translationKey()), rangeMode.scanIntervalTicks() / 20.0D));
        tooltip.add(Component.translatable("tooltip.ars_arcane_matrix.arcane_vacuum_hopper.experience",
                experience, MAX_EXPERIENCE));
    }

    private void sync() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put("Drops", drops.serializeNBT(registries));
        tag.put("Gems", gems.serializeNBT(registries));
        tag.put("Filters", filters.serializeNBT(registries));
        tag.putInt("Experience", experience);
        tag.putBoolean("CollectItems", collectItems);
        tag.putBoolean("CollectExperience", collectExperience);
        tag.putBoolean("DestroyMatches", destroyMatches);
        tag.putBoolean("StrictComponents", strictComponents);
        tag.putInt("FilterMode", filterMode.ordinal());
        tag.putInt("GemMode", gemMode.ordinal());
        tag.putInt("RangeMode", rangeMode.ordinal());
        tag.putInt("ItemOutputMode", itemOutputMode.ordinal());
        tag.putInt("GemOutputMode", gemOutputMode.ordinal());
        tag.putInt("BindChannel", bindChannel.ordinal());
        saveTarget(tag, "Item", itemTarget, itemTargetFace);
        saveTarget(tag, "Gem", gemTarget, gemTargetFace);
    }
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains("Drops")) drops.deserializeNBT(registries, tag.getCompound("Drops"));
        if (tag.contains("Gems")) gems.deserializeNBT(registries, tag.getCompound("Gems"));
        if (tag.contains("Filters")) filters.deserializeNBT(registries, tag.getCompound("Filters"));
        experience = Math.max(0, Math.min(MAX_EXPERIENCE, tag.getInt("Experience")));
        collectItems = !tag.contains("CollectItems") || tag.getBoolean("CollectItems");
        collectExperience = !tag.contains("CollectExperience") || tag.getBoolean("CollectExperience");
        destroyMatches = tag.getBoolean("DestroyMatches");
        strictComponents = tag.getBoolean("StrictComponents");
        filterMode = value(FilterMode.values(), tag.getInt("FilterMode"));
        gemMode = value(GemMode.values(), tag.getInt("GemMode"));
        rangeMode = tag.contains("RangeMode") ? value(RangeMode.values(), tag.getInt("RangeMode")) : RangeMode.R12;
        itemOutputMode = value(OutputMode.values(), tag.getInt("ItemOutputMode"));
        gemOutputMode = value(OutputMode.values(), tag.getInt("GemOutputMode"));
        bindChannel = value(BindChannel.values(), tag.getInt("BindChannel"));
        Target item = loadTarget(tag, "Item"); itemTarget = item.pos; itemTargetFace = item.face;
        Target gem = loadTarget(tag, "Gem"); gemTarget = gem.pos; gemTargetFace = gem.face;
    }
    private static void saveTarget(CompoundTag tag, String prefix, @Nullable GlobalPos pos, @Nullable Direction face) {
        if (pos == null) return;
        tag.putString(prefix + "Dimension", pos.dimension().location().toString());
        tag.putLong(prefix + "Pos", pos.pos().asLong());
        if (face != null) tag.putInt(prefix + "Face", face.get3DDataValue());
    }
    private static Target loadTarget(CompoundTag tag, String prefix) {
        net.minecraft.resources.ResourceLocation id = net.minecraft.resources.ResourceLocation.tryParse(
                tag.getString(prefix + "Dimension"));
        if (id == null || !tag.contains(prefix + "Pos")) return new Target(null, null);
        GlobalPos pos = GlobalPos.of(net.minecraft.resources.ResourceKey.create(
                net.minecraft.core.registries.Registries.DIMENSION, id), BlockPos.of(tag.getLong(prefix + "Pos")));
        Direction face = tag.contains(prefix + "Face") ? Direction.from3DDataValue(tag.getInt(prefix + "Face")) : null;
        return new Target(pos, face);
    }
    private static <T> T value(T[] values, int ordinal) { return values[Math.floorMod(ordinal, values.length)]; }
    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    private record Target(@Nullable GlobalPos pos, @Nullable Direction face) {}
    public enum FilterMode { DISABLED, WHITELIST, BLACKLIST; public FilterMode next(){ return value(values(), ordinal()+1); } }
    public enum GemMode { PAUSED, NORMAL, GREATER; public GemMode next(){ return value(values(), ordinal()+1); } }
    public enum RangeMode {
        R4(4, 5, "screen.ars_arcane_matrix.arcane_vacuum_hopper.range.4"),
        R8(8, 10, "screen.ars_arcane_matrix.arcane_vacuum_hopper.range.8"),
        R12(12, 20, "screen.ars_arcane_matrix.arcane_vacuum_hopper.range.12"),
        CHUNK(0, 40, "screen.ars_arcane_matrix.arcane_vacuum_hopper.range.chunk");
        private final int radius;
        private final int scanIntervalTicks;
        private final String translationKey;
        RangeMode(int radius, int scanIntervalTicks, String translationKey) {
            this.radius = radius;
            this.scanIntervalTicks = scanIntervalTicks;
            this.translationKey = translationKey;
        }
        public int radius() { return radius; }
        public int scanIntervalTicks() { return scanIntervalTicks; }
        public String translationKey() { return translationKey; }
        public RangeMode next() { return value(values(), ordinal() + 1); }
    }
    public enum OutputMode { AUTO, BOUND_ONLY, BELOW_ONLY, OFF; public OutputMode next(){ return value(values(), ordinal()+1); } }
    public enum BindChannel { ITEMS, GEMS; public BindChannel next(){ return value(values(), ordinal()+1); } }
}
