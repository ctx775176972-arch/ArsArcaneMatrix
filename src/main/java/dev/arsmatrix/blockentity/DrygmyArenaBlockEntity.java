package dev.arsmatrix.blockentity;

import com.hollingsworth.arsnouveau.api.util.BlockUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.MobJarTile;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.data.ArcaneHuntingRule;
import dev.arsmatrix.data.ArcaneHuntingRuleManager;
import dev.arsmatrix.config.MatrixConfig;
import dev.arsmatrix.registry.ModBlockEntities;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Produces data-driven special rewards for the single Mob Jar directly above it. */
public final class DrygmyArenaBlockEntity extends BlockEntity {
    private static final int OUTPUT_SLOTS = 128;
    private static final int PEDESTAL_RADIUS = 2;
    private static final int REQUIRED_PEDESTALS = 2;
    private static final int NORMAL_CATALYST_POINTS = 10;
    private static final int CONDENSED_CATALYST_POINTS = 100;

    private final List<ItemStack> pendingDrops = new ArrayList<>();
    private final IItemHandler outputHandler = new OutputHandler();
    private int progressTicks;
    private int catalystPoints;
    private long tickCounter;
    private OperatingState operatingState = OperatingState.NO_JAR;
    @Nullable private ResourceLocation targetEntityId;
    private int requiredPoints;

    public DrygmyArenaBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.DRYGMY_ARENA.get(), pos, state);
    }

    public void serverTick() {
        if (!(level instanceof ServerLevel serverLevel)) return;
        tickCounter++;
        if (tickCounter % 20 == 0) flushOutputs();

        if (level.hasNeighborSignal(worldPosition)) {
            setOperatingState(OperatingState.REDSTONE_PAUSED);
            return;
        }

        List<ArcanePedestalTile> pedestals = nearbyPedestals();
        if (pedestals.size() < REQUIRED_PEDESTALS) {
            progressTicks = 0;
            setOperatingState(OperatingState.UNFORMED);
            return;
        }

        Entity target = findJarTarget();
        if (target == null) {
            progressTicks = 0;
            updateTarget(null, 0);
            setOperatingState(level.getBlockEntity(worldPosition.above()) instanceof MobJarTile
                    ? OperatingState.INVALID_TARGET : OperatingState.NO_JAR);
            return;
        }

        ArcaneHuntingRule rule = ArcaneHuntingRuleManager.find(target.getType()).orElse(null);
        updateTarget(EntityType.getKey(target.getType()), rule == null ? 0 : rule.pointCost());
        if (rule == null) {
            progressTicks = 0;
            setOperatingState(OperatingState.NO_RULE);
            return;
        }
        if (pendingDrops.size() >= OUTPUT_SLOTS) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }

        if (catalystPoints < rule.pointCost()) {
            progressTicks = 0;
            if (tickCounter % 20 == 0) consumeCatalysts(pedestals, rule.pointCost());
            setOperatingState(catalystPoints >= rule.pointCost()
                    ? OperatingState.PROCESSING : OperatingState.NEEDS_CATALYST);
            return;
        }

        int cycleTicks = getCycleTicks();
        if (++progressTicks < cycleTicks) {
            setOperatingState(OperatingState.PROCESSING);
            if (progressTicks % 20 == 0) setChangedAndSyncClient();
            return;
        }

        List<ItemStack> outputs = rule.createOutputs();
        if (outputs.isEmpty()) {
            progressTicks = 0;
            setOperatingState(OperatingState.NO_RULE);
            return;
        }
        if (pendingDrops.size() + outputs.size() > OUTPUT_SLOTS) {
            setOperatingState(OperatingState.OUTPUT_BLOCKED);
            return;
        }
        catalystPoints -= rule.pointCost();
        outputs.forEach(this::mergePendingDrop);
        progressTicks = 0;
        flushOutputs();
        playCompletionEffect(serverLevel);
        setOperatingState(OperatingState.PROCESSING);
        setChangedAndSyncClient();
    }

    private List<ArcanePedestalTile> nearbyPedestals() {
        List<ArcanePedestalTile> found = new ArrayList<>();
        if (level == null) return found;
        for (int y = -1; y <= 2; y++) {
            for (int x = -PEDESTAL_RADIUS; x <= PEDESTAL_RADIUS; x++) {
                for (int z = -PEDESTAL_RADIUS; z <= PEDESTAL_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    if (level.getBlockEntity(worldPosition.offset(x, y, z)) instanceof ArcanePedestalTile pedestal) {
                        found.add(pedestal);
                    }
                }
            }
        }
        return found;
    }

    private void consumeCatalysts(List<ArcanePedestalTile> pedestals, int targetPoints) {
        int consumed = 0;
        for (ArcanePedestalTile pedestal : pedestals) {
            if (catalystPoints >= targetPoints || consumed >= REQUIRED_PEDESTALS) break;
            ItemStack stack = pedestal.getStack();
            int value = catalystValue(stack);
            if (value <= 0) continue;
            pedestal.removeItem(0, 1);
            consumed++;
            catalystPoints = Math.min(Integer.MAX_VALUE - value, catalystPoints) + value;
            pedestal.setChanged();
        }
        setChangedAndSyncClient();
    }

    private static int catalystValue(ItemStack stack) {
        if (stack.is(ModItems.CONDENSED_SUMMONING_CATALYST.get())) return CONDENSED_CATALYST_POINTS;
        if (stack.is(ItemsRegistry.CONJURATION_ESSENCE.get())) return NORMAL_CATALYST_POINTS;
        return 0;
    }

    @Nullable
    private Entity findJarTarget() {
        if (level == null || !(level.getBlockEntity(worldPosition.above()) instanceof MobJarTile jar)) return null;
        return jar.getEntity();
    }

    private void mergePendingDrop(ItemStack incoming) {
        ItemStack remaining = incoming.copy();
        for (ItemStack existing : pendingDrops) {
            if (ItemStack.isSameItemSameComponents(existing, remaining)) {
                int moved = Math.min(remaining.getCount(), existing.getMaxStackSize() - existing.getCount());
                existing.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) return;
            }
        }
        while (!remaining.isEmpty()) {
            int count = Math.min(remaining.getCount(), remaining.getMaxStackSize());
            pendingDrops.add(remaining.copyWithCount(count));
            remaining.shrink(count);
        }
    }

    private void flushOutputs() {
        if (level == null || pendingDrops.isEmpty()) return;
        boolean changed = false;
        for (int index = 0; index < pendingDrops.size();) {
            ItemStack stack = pendingDrops.get(index);
            ItemStack remainder = BlockUtil.insertItemAdjacent(level, worldPosition, stack.copy());
            if (remainder.getCount() != stack.getCount()) changed = true;
            if (remainder.isEmpty()) pendingDrops.remove(index);
            else { pendingDrops.set(index, remainder); index++; }
        }
        if (changed) setChangedAndSyncClient();
    }

    private void playCompletionEffect(ServerLevel serverLevel) {
        serverLevel.sendParticles(ParticleTypes.ENCHANT,
                worldPosition.getX() + 0.5D, worldPosition.getY() + 1.1D, worldPosition.getZ() + 0.5D,
                24, 0.45D, 0.35D, 0.45D, 0.08D);
        serverLevel.playSound(null, worldPosition, SoundEvents.AMETHYST_BLOCK_CHIME,
                SoundSource.BLOCKS, 0.7F, 0.8F);
    }

    private void updateTarget(@Nullable ResourceLocation id, int points) {
        if (java.util.Objects.equals(targetEntityId, id) && requiredPoints == points) return;
        targetEntityId = id;
        requiredPoints = points;
        setChangedAndSyncClient();
    }

    private void setOperatingState(OperatingState state) {
        if (operatingState == state) return;
        operatingState = state;
        setChangedAndSyncClient();
    }

    private void setChangedAndSyncClient() {
        setChanged();
        if (level != null) level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
    }

    public static boolean isStructureFormed(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos.above()) instanceof MobJarTile)) return false;
        int count = 0;
        for (int y = -1; y <= 2; y++) for (int x = -PEDESTAL_RADIUS; x <= PEDESTAL_RADIUS; x++)
            for (int z = -PEDESTAL_RADIUS; z <= PEDESTAL_RADIUS; z++)
                if (!(x == 0 && y == 0 && z == 0)
                        && level.getBlockEntity(pos.offset(x, y, z)) instanceof ArcanePedestalTile
                        && ++count >= REQUIRED_PEDESTALS) return true;
        return false;
    }

    public IItemHandler getItemHandler() { return outputHandler; }
    public int getProgressTicks() { return progressTicks; }
    public int getCycleTicks() { return MatrixConfig.DRYGMY_ARENA_CYCLE_TICKS.get(); }
    public int getBufferedItemCount() { return pendingDrops.stream().mapToInt(ItemStack::getCount).sum(); }
    public OperatingState getOperatingState() { return operatingState; }
    public int getCatalystPoints() { return catalystPoints; }
    public int getRequiredPoints() { return requiredPoints; }

    public Component getTargetDescription() {
        if (targetEntityId == null) return Component.translatable("message.ars_arcane_matrix.state.no_entity_target");
        return Component.translatable(EntityType.byString(targetEntityId.toString())
                .map(EntityType::getDescriptionId).orElse("message.ars_arcane_matrix.state.no_entity_target"));
    }

    public void dropBufferedContents() {
        if (level == null || level.isClientSide) return;
        pendingDrops.forEach(stack -> Containers.dropItemStack(level,
                worldPosition.getX() + .5D, worldPosition.getY() + .5D, worldPosition.getZ() + .5D, stack));
        pendingDrops.clear();
    }

    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.putInt("ProgressTicks", progressTicks);
        tag.putInt("CatalystPoints", catalystPoints);
        tag.putInt("RequiredPoints", requiredPoints);
        tag.putString("OperatingState", operatingState.name());
        if (targetEntityId != null) tag.putString("TargetEntity", targetEntityId.toString());
        ListTag output = new ListTag();
        pendingDrops.forEach(stack -> output.add(stack.saveOptional(registries)));
        tag.put("PendingDrops", output);
    }

    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        progressTicks = Math.max(0, tag.getInt("ProgressTicks"));
        catalystPoints = Math.max(0, tag.getInt("CatalystPoints"));
        requiredPoints = Math.max(0, tag.getInt("RequiredPoints"));
        try { operatingState = OperatingState.valueOf(tag.getString("OperatingState")); }
        catch (IllegalArgumentException ignored) { operatingState = OperatingState.NO_JAR; }
        targetEntityId = ResourceLocation.tryParse(tag.getString("TargetEntity"));
        pendingDrops.clear();
        ListTag output = tag.getList("PendingDrops", Tag.TAG_COMPOUND);
        for (int i = 0; i < output.size() && pendingDrops.size() < OUTPUT_SLOTS; i++) {
            ItemStack stack = ItemStack.parseOptional(registries, output.getCompound(i));
            if (!stack.isEmpty()) pendingDrops.add(stack);
        }
    }

    @Override public CompoundTag getUpdateTag(HolderLookup.Provider registries) { return saveWithoutMetadata(registries); }
    @Override public ClientboundBlockEntityDataPacket getUpdatePacket() { return ClientboundBlockEntityDataPacket.create(this); }

    public enum OperatingState {
        NO_JAR("message.ars_arcane_matrix.state.no_jar"),
        INVALID_TARGET("message.ars_arcane_matrix.state.invalid_target"),
        UNFORMED("message.ars_arcane_matrix.state.unformed"),
        NO_RULE("message.ars_arcane_matrix.state.no_hunting_rule"),
        NEEDS_CATALYST("message.ars_arcane_matrix.state.needs_catalyst"),
        REDSTONE_PAUSED("message.ars_arcane_matrix.state.redstone_paused"),
        OUTPUT_BLOCKED("message.ars_arcane_matrix.state.output_blocked"),
        PROCESSING("message.ars_arcane_matrix.state.processing");
        private final String translationKey;
        OperatingState(String translationKey) { this.translationKey = translationKey; }
        public String translationKey() { return translationKey; }
    }

    private final class OutputHandler implements IItemHandler {
        @Override public int getSlots() { return OUTPUT_SLOTS; }
        @Override public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < pendingDrops.size() ? pendingDrops.get(slot) : ItemStack.EMPTY;
        }
        @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) { return stack; }
        @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack stack = getStackInSlot(slot);
            if (amount <= 0 || stack.isEmpty()) return ItemStack.EMPTY;
            int count = Math.min(amount, stack.getCount());
            ItemStack extracted = stack.copyWithCount(count);
            if (!simulate) {
                stack.shrink(count);
                if (stack.isEmpty()) pendingDrops.remove(slot);
                setChangedAndSyncClient();
            }
            return extracted;
        }
        @Override public int getSlotLimit(int slot) { return 64; }
        @Override public boolean isItemValid(int slot, ItemStack stack) { return false; }
    }
}
