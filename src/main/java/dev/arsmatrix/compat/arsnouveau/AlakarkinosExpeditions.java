package dev.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.ANFakePlayer;
import com.hollingsworth.arsnouveau.api.registry.AlakarkinosConversionRegistry;
import com.hollingsworth.arsnouveau.api.source.ISourceTile;
import com.hollingsworth.arsnouveau.api.source.ISpecialSourceProvider;
import com.hollingsworth.arsnouveau.api.util.SourceUtil;
import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.crafting.recipes.AlakarkinosRecipe;
import com.hollingsworth.arsnouveau.common.entity.Alakarkinos;
import com.hollingsworth.arsnouveau.common.entity.statemachine.SimpleStateMachine;
import com.hollingsworth.arsnouveau.common.entity.statemachine.alakarkinos.DecideCrabActionState;
import com.hollingsworth.arsnouveau.setup.config.Config;
import dev.arsmatrix.data.AlakarkinosExpeditionManager;
import dev.arsmatrix.data.AlakarkinosExpeditionRule;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Pedestal-marked, container-fed exploration cycles layered onto the native Alakarkinos. */
public final class AlakarkinosExpeditions {
    private static final int SCAN_INTERVAL = 20;
    private static final int HOME_RANGE = 3;
    private static final int SOURCE_RANGE = 5;
    private static final int NATIVE_WORK_TICKS = 40;
    private static final Map<Alakarkinos, WorkState> STATES = new WeakHashMap<>();

    private AlakarkinosExpeditions() {}

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof Alakarkinos crab)
                || !(crab.level() instanceof ServerLevel level)
                || !crab.tamed || crab.getHome() == null || !crab.hasHat()) return;

        BlockPos home = crab.getHome();
        IItemHandler output = level.getCapability(Capabilities.ItemHandler.BLOCK, home, null);
        if (output == null) {
            stop(crab);
            return;
        }

        WorkState state = STATES.computeIfAbsent(crab, ignored -> new WorkState());
        long now = level.getGameTime();
        if (now >= state.nextScan || state.task == null) {
            Set<Item> pedestalItems = pedestalItems(level, home);
            List<IItemHandler> inputs = inputHandlers(level, home);
            if (!pedestalItems.contains(Items.BRUSH)) {
                state.task = null;
            } else if (state.task == null || !isValid(state.task, pedestalItems, inputs)) {
                state.task = findCandidate(level, pedestalItems, inputs);
                state.progress = 0;
                state.pendingLoot = List.of();
            }
            state.nextScan = now + SCAN_INTERVAL;
        }

        if (state.task == null) {
            stop(crab);
            return;
        }

        if (!state.suppressingNative) {
            crab.stateMachine = new SimpleStateMachine(new DecideCrabActionState(crab));
            state.suppressingNative = true;
        }
        crab.findBlockCooldown = Math.max(crab.findBlockCooldown, SCAN_INTERVAL + 2);
        crab.getEntityData().set(Alakarkinos.BLOWING_AT, java.util.Optional.of(home));
        crab.setBlowingBubbles(true);

        if (crab.distanceToSqr(Vec3.atCenterOf(home)) > 16.0D) {
            crab.getNavigation().moveTo(home.getX() + 0.5D, home.getY() + 0.5D,
                    home.getZ() + 0.5D, 1.0D);
            return;
        }
        crab.getNavigation().stop();

        if (state.progress < state.task.workTicks()) {
            state.progress++;
            return;
        }

        Set<Item> pedestalItems = pedestalItems(level, home);
        List<IItemHandler> inputs = inputHandlers(level, home);
        if (!pedestalItems.contains(Items.BRUSH) || !isValid(state.task, pedestalItems, inputs)) {
            reset(state);
            return;
        }
        if (state.pendingLoot.isEmpty()) state.pendingLoot = createLoot(level, crab, state.task);
        if (state.pendingLoot.isEmpty() || !canFitAll(output, state.pendingLoot)
                || !canPaySource(level, home, state.task.sourceCost())) return;

        if (!paySource(level, crab, home, state.task.sourceCost())) return;
        if (!extractInputs(inputs, state.task)) return;
        for (ItemStack stack : state.pendingLoot) {
            ItemStack leftover = ItemHandlerHelper.insertItemStacked(output, stack.copy(), false);
            if (!leftover.isEmpty()) {
                net.minecraft.world.Containers.dropItemStack(level, home.getX() + 0.5D,
                        home.getY() + 1.0D, home.getZ() + 0.5D, leftover);
            }
        }
        reset(state);
        state.nextScan = now + SCAN_INTERVAL;
        crab.findBlockCooldown = Math.max(crab.findBlockCooldown, 20);
    }

    private static void reset(WorkState state) {
        state.task = null;
        state.progress = 0;
        state.pendingLoot = List.of();
    }

    private static void stop(Alakarkinos crab) {
        WorkState state = STATES.get(crab);
        if (state != null && state.suppressingNative) {
            state.suppressingNative = false;
            reset(state);
            crab.setBlowingBubbles(false);
        }
    }

    private static ExplorationTask findCandidate(ServerLevel level, Set<Item> pedestalItems,
            List<IItemHandler> inputs) {
        for (AlakarkinosExpeditionRule rule : AlakarkinosExpeditionManager.allRules()) {
            if (rule.requiresProof()
                    && !pedestalItems.contains(BuiltInRegistries.ITEM.get(rule.proof()))) continue;
            if (hasAllInputs(inputs, rule)) return ExplorationTask.custom(rule);
        }
        for (Block block : List.of(Blocks.SAND, Blocks.GRAVEL)) {
            if (countItem(inputs, block.asItem()) < 1) continue;
            AlakarkinosRecipe recipe = AlakarkinosConversionRegistry.getConversionResult(block, level.random);
            if (recipe != null) return ExplorationTask.nativeArchaeology(block, recipe);
        }
        return null;
    }

    private static boolean isValid(ExplorationTask task, Set<Item> pedestalItems,
            List<IItemHandler> inputs) {
        if (task.rule != null) {
            return (!task.rule.requiresProof()
                    || pedestalItems.contains(BuiltInRegistries.ITEM.get(task.rule.proof())))
                    && hasAllInputs(inputs, task.rule);
        }
        return task.nativeBlock != null && countItem(inputs, task.nativeBlock.asItem()) >= 1;
    }

    private static Set<Item> pedestalItems(ServerLevel level, BlockPos home) {
        Set<Item> result = new LinkedHashSet<>();
        for (int y = -HOME_RANGE; y <= HOME_RANGE; y++) {
            for (int x = -HOME_RANGE; x <= HOME_RANGE; x++) {
                for (int z = -HOME_RANGE; z <= HOME_RANGE; z++) {
                    if (level.getBlockEntity(home.offset(x, y, z)) instanceof ArcanePedestalTile pedestal) {
                        ItemStack stack = pedestal.getStack();
                        if (!stack.isEmpty()) result.add(stack.getItem());
                    }
                }
            }
        }
        return result;
    }

    private static List<IItemHandler> inputHandlers(ServerLevel level, BlockPos home) {
        Set<IItemHandler> unique = Collections.newSetFromMap(new IdentityHashMap<>());
        List<IItemHandler> result = new ArrayList<>();
        for (int y = -HOME_RANGE; y <= HOME_RANGE; y++) {
            for (int x = -HOME_RANGE; x <= HOME_RANGE; x++) {
                for (int z = -HOME_RANGE; z <= HOME_RANGE; z++) {
                    BlockPos pos = home.offset(x, y, z);
                    if (pos.equals(home) || level.getBlockEntity(pos) instanceof ArcanePedestalTile) continue;
                    IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
                    if (handler == null) {
                        for (Direction side : Direction.values()) {
                            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
                            if (handler != null) break;
                        }
                    }
                    if (handler != null && unique.add(handler)) result.add(handler);
                }
            }
        }
        return result;
    }

    private static int countItem(List<IItemHandler> handlers, Item item) {
        int count = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (stack.is(item)) count += stack.getCount();
            }
        }
        return count;
    }

    private static int countInput(List<IItemHandler> handlers,
            AlakarkinosExpeditionRule.IngredientCost cost) {
        long count = 0;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots(); slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (cost.matches(stack)) count += stack.getCount();
                if (count >= cost.count()) return cost.count();
            }
        }
        return (int) count;
    }

    private static boolean hasAllInputs(List<IItemHandler> handlers, AlakarkinosExpeditionRule rule) {
        return rule.inputs().stream().allMatch(cost -> countInput(handlers, cost) >= cost.count());
    }

    private static boolean extractInputs(List<IItemHandler> handlers, ExplorationTask task) {
        if (task.rule == null) return extractItem(handlers, task.nativeBlock.asItem(), 1);
        if (!hasAllInputs(handlers, task.rule)) return false;
        for (AlakarkinosExpeditionRule.IngredientCost cost : task.rule.inputs()) {
            if (!extractInput(handlers, cost)) return false;
        }
        return true;
    }

    private static boolean extractItem(List<IItemHandler> handlers, Item item, int count) {
        int remaining = count;
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!stack.is(item)) continue;
                remaining -= handler.extractItem(slot, Math.min(remaining, stack.getCount()), false).getCount();
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean extractInput(List<IItemHandler> handlers,
            AlakarkinosExpeditionRule.IngredientCost cost) {
        int remaining = cost.count();
        for (IItemHandler handler : handlers) {
            for (int slot = 0; slot < handler.getSlots() && remaining > 0; slot++) {
                ItemStack stack = handler.getStackInSlot(slot);
                if (!cost.matches(stack)) continue;
                remaining -= handler.extractItem(slot, Math.min(remaining, stack.getCount()), false).getCount();
            }
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static List<ItemStack> createLoot(ServerLevel level, Alakarkinos crab,
            ExplorationTask task) {
        if (task.rule != null && task.rule.isFixedOutput()) return task.rule.displayOutputStacks();
        ResourceLocation tableId = task.rule != null
                ? task.rule.lootTable() : task.nativeRecipe.table().location();
        LootTable table = level.getServer().reloadableRegistries().getLootTable(
                ResourceKey.create(Registries.LOOT_TABLE, tableId));
        LootParams params = new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, crab.position())
                .withParameter(LootContextParams.THIS_ENTITY, ANFakePlayer.getPlayer(level))
                .create(LootContextParamSets.CHEST);
        return table.getRandomItems(params, level.random).stream()
                .filter(stack -> task.rule == null
                        || !task.rule.excludedOutputs().contains(BuiltInRegistries.ITEM.getKey(stack.getItem())))
                .map(ItemStack::copy).toList();
    }

    private static boolean canPaySource(ServerLevel level, BlockPos home, int cost) {
        int remaining = cost;
        Set<ISpecialSourceProvider> providers = new LinkedHashSet<>(SourceUtil.canTakeSource(home, level, SOURCE_RANGE));
        for (ISpecialSourceProvider provider : providers) {
            ISourceTile source = provider.getSource();
            if (source == null || !source.canProvideSource()) continue;
            int offered = Math.max(0, Math.min(remaining, source.removeSource(remaining, true)));
            remaining -= offered;
            if (remaining <= 0) return true;
        }
        return false;
    }

    private static boolean paySource(ServerLevel level, Alakarkinos crab, BlockPos home, int cost) {
        return SourceUtil.takeSourceMultipleWithParticles(home, crab.blockPosition().above(),
                level, SOURCE_RANGE, cost) != null;
    }

    private static boolean canFitAll(IItemHandler handler, List<ItemStack> stacks) {
        List<ItemStack> shadow = new ArrayList<>(handler.getSlots());
        for (int slot = 0; slot < handler.getSlots(); slot++) shadow.add(handler.getStackInSlot(slot).copy());
        for (ItemStack offered : stacks) {
            ItemStack remaining = offered.copy();
            for (int slot = 0; slot < shadow.size() && !remaining.isEmpty(); slot++) {
                ItemStack present = shadow.get(slot);
                if (!present.isEmpty() && ItemStack.isSameItemSameComponents(present, remaining)) {
                    int limit = Math.min(handler.getSlotLimit(slot), present.getMaxStackSize());
                    int moved = Math.min(remaining.getCount(), Math.max(0, limit - present.getCount()));
                    if (moved > 0) { present.grow(moved); remaining.shrink(moved); }
                }
            }
            for (int slot = 0; slot < shadow.size() && !remaining.isEmpty(); slot++) {
                if (!shadow.get(slot).isEmpty() || !handler.isItemValid(slot, remaining)) continue;
                int moved = Math.min(remaining.getCount(),
                        Math.min(handler.getSlotLimit(slot), remaining.getMaxStackSize()));
                if (moved > 0) { shadow.set(slot, remaining.copyWithCount(moved)); remaining.shrink(moved); }
            }
            if (!remaining.isEmpty()) return false;
        }
        return true;
    }

    private static final class ExplorationTask {
        private final AlakarkinosExpeditionRule rule;
        private final Block nativeBlock;
        private final AlakarkinosRecipe nativeRecipe;
        private final int workTicks;
        private final int sourceCost;

        private ExplorationTask(AlakarkinosExpeditionRule rule, Block nativeBlock,
                AlakarkinosRecipe nativeRecipe, int workTicks, int sourceCost) {
            this.rule = rule;
            this.nativeBlock = nativeBlock;
            this.nativeRecipe = nativeRecipe;
            this.workTicks = workTicks;
            this.sourceCost = sourceCost;
        }

        private static ExplorationTask custom(AlakarkinosExpeditionRule rule) {
            return new ExplorationTask(rule, null, null, rule.workTicks(), rule.sourceCost());
        }

        private static ExplorationTask nativeArchaeology(Block block, AlakarkinosRecipe recipe) {
            return new ExplorationTask(null, block, recipe, NATIVE_WORK_TICKS,
                    Math.max(1, Config.ALAKARKINOS_SOURCE_COST.get()));
        }

        private int workTicks() { return workTicks; }
        private int sourceCost() { return sourceCost; }
    }

    private static final class WorkState {
        private ExplorationTask task;
        private int progress;
        private long nextScan;
        private List<ItemStack> pendingLoot = List.of();
        private boolean suppressingNative;
    }
}
