package dev.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.datagen.ItemTagProvider;
import com.hollingsworth.arsnouveau.common.datagen.BlockTagProvider;
import com.hollingsworth.arsnouveau.common.entity.AmethystGolem;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;

import java.util.Map;
import java.util.WeakHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/** Adds non-consuming Arcane Pedestal upgrades to Ars Nouveau's Amethyst Golem. */
public final class AmethystGolemEnhancements {
    private static final int WORK_RANGE = 10;
    private static final int WORK_SCAN_INTERVAL = 100;
    private static final int TRANSFER_INTERVAL = 5;
    private static final int COLLECTION_RANGE = 10;
    private static final int GROWTH_BATCH_SIZE = 4;
    private static final String GROWTH_CURSOR_TAG = "ars_arcane_matrix_growth_cursor";
    private static final String GROWTH_CURSOR_INITIALIZED_TAG =
            "ars_arcane_matrix_growth_cursor_initialized";

    private static final Map<AmethystGolem, EnhancementState> STATES = new WeakHashMap<>();

    private AmethystGolemEnhancements() {
    }

    public static void onEntityTick(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof AmethystGolem golem)
                || !(golem.level() instanceof ServerLevel level)
                || golem.getHome() == null) {
            return;
        }

        long gameTime = level.getGameTime();
        EnhancementState state = STATES.computeIfAbsent(golem, ignored -> new EnhancementState());
        if (gameTime >= state.nextScan || !golem.getHome().equals(state.scannedHome)) {
            scanWorkArea(level, golem, state);
            state.nextScan = gameTime + WORK_SCAN_INTERVAL;
        }

        // Ars 5.13 scans only five blocks horizontally and waits 3600 ticks before scanning
        // again. Keep its limited scan asleep; scanWorkArea maintains the public task lists.
        golem.scanCooldown = Math.max(golem.scanCooldown, WORK_SCAN_INTERVAL + 1);

        if (state.efficiency > 0) {
            // Ars decrements both values once in AmethystGolem.tick(). Extra decrements make
            // Efficiency N operate at N+1 times the ordinary cooldown rate.
            golem.growCooldown = Math.max(0, golem.growCooldown - state.efficiency);
            golem.harvestCooldown = Math.max(0, golem.harvestCooldown - state.efficiency);
        }

        // The simulated-tool mode is already an explicit opt-in. While it is active,
        // route amethyst products to the home inventory without requiring a second
        // pedestal merely to hold a Hopper.
        if (!state.tool.isEmpty() && gameTime % TRANSFER_INTERVAL == 0) {
            transferDrops(level, golem.getHome());
        }

    }

    /** Called from the harvest-goal mixin immediately before vanilla calculates block drops. */
    public static ItemStack simulatedHarvestTool(AmethystGolem golem, ItemStack fallback) {
        if (!(golem.level() instanceof ServerLevel level) || golem.getHome() == null) {
            return fallback;
        }
        EnhancementState state = STATES.computeIfAbsent(golem, ignored -> new EnhancementState());
        if (level.getGameTime() >= state.nextScan) {
            scanWorkArea(level, golem, state);
            state.nextScan = level.getGameTime() + WORK_SCAN_INTERVAL;
        }
        return state.tool.isEmpty() ? fallback : state.tool.copyWithCount(1);
    }

    public static int efficiencyLevel(AmethystGolem golem) {
        if (!(golem.level() instanceof ServerLevel level) || golem.getHome() == null) return 0;
        EnhancementState state = STATES.computeIfAbsent(golem, ignored -> new EnhancementState());
        if (level.getGameTime() >= state.nextScan) {
            scanWorkArea(level, golem, state);
            state.nextScan = level.getGameTime() + WORK_SCAN_INTERVAL;
        }
        return state.efficiency;
    }

    /** Interpolates the original action down to one second at Efficiency V. */
    public static int acceleratedActionTicks(AmethystGolem golem, int originalTicks) {
        int efficiency = Math.min(5, efficiencyLevel(golem));
        if (efficiency <= 0) return originalTicks;
        int removable = Math.max(0, originalTicks - 20);
        return Math.max(20, originalTicks - (int) Math.ceil(removable * efficiency / 5.0D));
    }

    private static void scanWorkArea(ServerLevel level, AmethystGolem golem, EnhancementState state) {
        BlockPos home = golem.getHome();
        if (home == null) return;
        state.scannedHome = home.immutable();
        state.tool = ItemStack.EMPTY;
        state.efficiency = 0;
        golem.amethystBlocks = new ArrayList<>();
        golem.buddingBlocks = new ArrayList<>();
        BlockState cluster = Blocks.AMETHYST_CLUSTER.defaultBlockState();

        for (BlockPos cursor : BlockPos.betweenClosed(
                home.offset(-WORK_RANGE, -WORK_RANGE, -WORK_RANGE),
                home.offset(WORK_RANGE, WORK_RANGE, WORK_RANGE))) {
            if (!level.hasChunkAt(cursor)) continue;
            BlockState blockState = level.getBlockState(cursor);
            if (!blockState.isAir()) {
                if (blockState.is(BlockTagProvider.BUDDING_BLOCKS)) {
                    golem.buddingBlocks.add(cursor.immutable());
                }
                for (var recipe : golem.recipes) {
                    if (!recipe.matches(blockState)) continue;
                    golem.amethystBlocks.add(cursor.immutable());
                    break;
                }
            }

            if (level.getBlockEntity(cursor) instanceof ArcanePedestalTile pedestal) {
                ItemStack offered = pedestal.getStack();
                if (offered.isEmpty()) continue;
                if (!offered.isCorrectToolForDrops(cluster)) continue;

                int candidateEfficiency = enchantmentLevel(level, offered, Enchantments.EFFICIENCY);
                boolean candidateSilk = enchantmentLevel(level, offered, Enchantments.SILK_TOUCH) > 0;
                int candidateFortune = enchantmentLevel(level, offered, Enchantments.FORTUNE);
                int currentScore = state.tool.isEmpty() ? -1
                        : state.efficiency * 100
                        + (enchantmentLevel(level, state.tool, Enchantments.SILK_TOUCH) > 0 ? 50 : 0)
                        + enchantmentLevel(level, state.tool, Enchantments.FORTUNE);
                int candidateScore = candidateEfficiency * 100 + (candidateSilk ? 50 : 0) + candidateFortune;
                if (candidateScore > currentScore) {
                    state.tool = offered.copyWithCount(1);
                    state.efficiency = candidateEfficiency;
                }
            }
        }
        initializeGrowthCursor(level, golem, home);
        // scanWorkArea rebuilds the list in deterministic coordinate order.
        state.appliedGrowthCursor = 0;
    }

    /**
     * Gives each golem a stable starting batch without removing any budding blocks
     * from its work list. Multiple golems therefore retain their full throughput,
     * while UUID order makes them prefer different blocks when enough are present.
     */
    private static void initializeGrowthCursor(ServerLevel level, AmethystGolem golem, BlockPos home) {
        if (golem.buddingBlocks.isEmpty()
                || golem.getPersistentData().getBoolean(GROWTH_CURSOR_INITIALIZED_TAG)) {
            return;
        }
        List<AmethystGolem> group = homeGroup(level, home);
        int owner = group.indexOf(golem);
        if (owner < 0) return;
        int cursor = Math.floorMod(owner * GROWTH_BATCH_SIZE, golem.buddingBlocks.size());
        golem.getPersistentData().putInt(GROWTH_CURSOR_TAG, cursor);
        golem.getPersistentData().putBoolean(GROWTH_CURSOR_INITIALIZED_TAG, true);
    }

    private static List<AmethystGolem> homeGroup(ServerLevel level, BlockPos home) {
        List<AmethystGolem> group = level.getEntitiesOfClass(
                AmethystGolem.class,
                new AABB(home).inflate(WORK_RANGE * 2),
                candidate -> candidate.isAlive() && home.equals(candidate.getHome()));
        group.sort(Comparator.comparing(AmethystGolem::getUUID));
        return group;
    }

    /**
     * Applies a one-time UUID-derived phase offset after the first growth action.
     * Later actions keep Ars Nouveau's ordinary cooldown, so stacking golems adds
     * throughput instead of making every golem pause and resume in lockstep.
     */
    public static int staggeredGrowthCooldown(AmethystGolem golem, int originalTicks) {
        if (!(golem.level() instanceof ServerLevel level) || golem.getHome() == null) {
            return originalTicks;
        }
        EnhancementState state = STATES.computeIfAbsent(golem, ignored -> new EnhancementState());
        if (state.staggerApplied) return originalTicks;
        state.staggerApplied = true;

        List<AmethystGolem> group = homeGroup(level, golem.getHome());
        int owner = group.indexOf(golem);
        if (owner <= 0 || group.size() <= 1) return originalTicks;
        return originalTicks + owner * originalTicks / group.size();
    }

    /**
     * Selects the visible target and rotates the next batch fairly. The cursor is
     * stored on the entity rather than in the transient enhancement cache, so a
     * world restart resumes the same point in the assigned list.
     */
    public static BlockPos prepareGrowthBatch(AmethystGolem golem) {
        List<BlockPos> blocks = golem.buddingBlocks;
        if (blocks == null || blocks.isEmpty()) {
            return golem.getHome() == null ? golem.blockPosition() : golem.getHome();
        }
        EnhancementState state = STATES.computeIfAbsent(golem, ignored -> new EnhancementState());
        int cursor = Math.floorMod(golem.getPersistentData().getInt(GROWTH_CURSOR_TAG), blocks.size());
        int applied = Math.floorMod(state.appliedGrowthCursor, blocks.size());
        int rotation = Math.floorMod(cursor - applied, blocks.size());
        if (rotation != 0) Collections.rotate(blocks, -rotation);
        state.appliedGrowthCursor = cursor;
        int next = Math.floorMod(cursor + Math.min(GROWTH_BATCH_SIZE, blocks.size()), blocks.size());
        golem.getPersistentData().putInt(GROWTH_CURSOR_TAG, next);
        return blocks.getFirst();
    }

    private static int enchantmentLevel(
            ServerLevel level, ItemStack stack, net.minecraft.resources.ResourceKey<Enchantment> key
    ) {
        Holder<Enchantment> enchantment = level.registryAccess()
                .lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(key);
        return Math.max(0, EnchantmentHelper.getItemEnchantmentLevel(enchantment, stack));
    }

    private static void transferDrops(ServerLevel level, BlockPos home) {
        IItemHandler target = level.getCapability(Capabilities.ItemHandler.BLOCK, home, null);
        if (target == null) return;

        AABB area = new AABB(home).inflate(COLLECTION_RANGE);
        for (ItemEntity entity : level.getEntitiesOfClass(
                ItemEntity.class, area, item -> item.isAlive() && item.getItem().is(ItemTagProvider.SHARD_TAG))) {
            ItemStack original = entity.getItem();
            ItemStack remainder = ItemHandlerHelper.insertItemStacked(target, original.copy(), false);
            if (remainder.getCount() == original.getCount()) continue;
            if (remainder.isEmpty()) entity.discard();
            else entity.setItem(remainder);
        }
    }

    private static final class EnhancementState {
        private ItemStack tool = ItemStack.EMPTY;
        private int efficiency;
        private int appliedGrowthCursor;
        private boolean staggerApplied;
        private long nextScan;
        private BlockPos scannedHome;
    }
}
