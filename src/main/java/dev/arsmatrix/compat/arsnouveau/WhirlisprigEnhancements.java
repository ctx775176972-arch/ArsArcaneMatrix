package dev.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.common.block.tile.ArcanePedestalTile;
import com.hollingsworth.arsnouveau.common.block.tile.WhirlisprigTile;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.hollingsworth.arsnouveau.setup.config.Config;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.items.IItemHandler;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

/** Optional, inventory-catalyzed woodland production modes for the native Whirlisprig. */
public final class WhirlisprigEnhancements {
    private static final int INPUT_RADIUS = 3;
    private static final int DIVERSITY_RADIUS = 5;
    private static final int DIVERSITY_VERTICAL_RADIUS = 2;
    private static final int MAX_DIVERSITY = 5;
    private static final long MODE_CACHE_TICKS = 10L;
    private static final long DIVERSITY_CACHE_TICKS = 40L;
    private static final TagKey<Block> LOGS = blockTag("minecraft", "logs");
    private static final TagKey<Block> LEAVES = blockTag("minecraft", "leaves");
    private static final TagKey<Block> SAPLINGS = blockTag("minecraft", "saplings");
    private static final TagKey<Block> FLOWERS = blockTag("minecraft", "flowers");
    private static final TagKey<Block> CROPS = blockTag("c", "crops");
    private static final Map<WhirlisprigTile, Mode> PRODUCTION_MODES = new WeakHashMap<>();
    private static final Map<WhirlisprigTile, CachedMode> MODE_CACHE = new WeakHashMap<>();
    private static final Map<WhirlisprigTile, CachedDiversity> DIVERSITY_CACHE = new WeakHashMap<>();
    private static final Map<WhirlisprigTile, Double> PROGRESS_REMAINDERS = new WeakHashMap<>();

    private WhirlisprigEnhancements() {}

    public enum Mode { NONE, NORMAL, ADVANCED }

    public static Mode currentMode(WhirlisprigTile tile) {
        Level level = tile.getLevel();
        if (level == null) return Mode.NONE;
        long now = level.getGameTime();
        CachedMode cached = MODE_CACHE.get(tile);
        if (cached != null && now - cached.gameTime < MODE_CACHE_TICKS) return cached.mode;
        Catalyst catalyst = findCatalyst(tile);
        Mode mode = catalyst == null ? Mode.NONE : catalyst.mode;
        MODE_CACHE.put(tile, new CachedMode(now, mode));
        return mode;
    }

    /** Builds a temporary table for this cycle without corrupting the native genTable. */
    public static Map<BlockState, Integer> productionTable(WhirlisprigTile tile, Map<BlockState, Integer> original) {
        PRODUCTION_MODES.remove(tile);
        if (original == null || original.isEmpty()) return original;
        Catalyst catalyst = findCatalyst(tile);
        if (catalyst == null) return original;

        Map<BlockState, Integer> filtered = new HashMap<>();
        for (Map.Entry<BlockState, Integer> entry : original.entrySet()) {
            if (entry.getValue() != null && entry.getValue() > 0 && isEligible(entry.getKey(), catalyst.mode)) {
                filtered.put(entry.getKey(), entry.getValue());
            }
        }
        if (filtered.isEmpty()) return original;
        PRODUCTION_MODES.put(tile, catalyst.mode);
        return filtered;
    }

    /** Called after Source was paid: exactly one catalyst from a nearby inventory per successful cycle. */
    public static void consumeForProduction(WhirlisprigTile tile) {
        if (tile.getLevel() == null || tile.getLevel().isClientSide || !PRODUCTION_MODES.containsKey(tile)) return;
        Mode expected = PRODUCTION_MODES.get(tile);
        Catalyst catalyst = findCatalyst(tile);
        if (catalyst == null || catalyst.mode != expected) {
            PRODUCTION_MODES.remove(tile);
            return;
        }
        catalyst.handler.extractItem(catalyst.slot, 1, false);
        MODE_CACHE.remove(tile);
    }

    /** Includes the native +3 guaranteed rolls, making the full result exactly 2x or 4x. */
    public static int enhancedDropRolls(WhirlisprigTile tile, int nativeDiversityRolls) {
        Mode mode = PRODUCTION_MODES.getOrDefault(tile, Mode.NONE);
        int multiplier = mode == Mode.NORMAL ? 4 : mode == Mode.ADVANCED ? 8 : 1;
        return multiplier == 1 ? nativeDiversityRolls
                : Math.max(0, Math.multiplyExact(nativeDiversityRolls + 3, multiplier) - 3);
    }

    public static void finishProductionTick(WhirlisprigTile tile) {
        PRODUCTION_MODES.remove(tile);
    }

    /** Counts nearby plant families only in catalyst modes; one tree cannot fill the bonus alone. */
    public static int diversityLevel(WhirlisprigTile tile) {
        Level level = tile.getLevel();
        if (level == null) return 0;
        if (currentMode(tile) == Mode.NONE) return 0;
        long now = level.getGameTime();
        CachedDiversity cached = DIVERSITY_CACHE.get(tile);
        if (cached != null && now - cached.gameTime < DIVERSITY_CACHE_TICKS) return cached.level;

        Set<String> families = new HashSet<>();
        BlockPos origin = tile.getBlockPos();
        for (int y = -DIVERSITY_VERTICAL_RADIUS; y <= DIVERSITY_VERTICAL_RADIUS; y++) {
            for (int x = -DIVERSITY_RADIUS; x <= DIVERSITY_RADIUS; x++) {
                for (int z = -DIVERSITY_RADIUS; z <= DIVERSITY_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockState state = level.getBlockState(origin.offset(x, y, z));
                    if (!isDiversityPlant(state)) continue;
                    ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
                    families.add(id.getNamespace() + ":" + plantFamily(id.getPath()));
                    if (families.size() >= MAX_DIVERSITY) {
                        DIVERSITY_CACHE.put(tile, new CachedDiversity(now, MAX_DIVERSITY));
                        return MAX_DIVERSITY;
                    }
                }
            }
        }
        int levelFound = families.size();
        DIVERSITY_CACHE.put(tile, new CachedDiversity(now, levelFound));
        return levelFound;
    }

    public static int progressDivisor(WhirlisprigTile tile, int nativeDivisor) {
        int diversity = diversityLevel(tile);
        if (diversity <= 0) return nativeDivisor;
        return Math.max(1, Math.round(nativeDivisor * (1.0F - diversity * 0.1F)));
    }

    /**
     * Catalyst modes are intended to reward plant-family variety rather than a huge grove.
     * Native integer division can otherwise yield zero progress for a compact, diverse farm.
     */
    public static boolean addCompactGroveProgress(WhirlisprigTile tile) {
        if (currentMode(tile) == Mode.NONE) {
            PROGRESS_REMAINDERS.remove(tile);
            return false;
        }
        int diversity = diversityLevel(tile);
        int requiredActions = requiredProgressActions(tile);
        int maximum = Config.WHIRLISPRIG_MAX_PROGRESS.get();
        double accumulated = PROGRESS_REMAINDERS.getOrDefault(tile, 0.0D)
                + maximum / (double) requiredActions;
        int wholeProgress = Math.max(1, (int) Math.floor(accumulated));
        PROGRESS_REMAINDERS.put(tile, Math.max(0.0D, accumulated - wholeProgress));
        tile.progress = Math.min(maximum, tile.progress + wholeProgress);
        tile.updateBlock();
        return true;
    }

    public static int workTimePercent(WhirlisprigTile tile) {
        return 100 - diversityLevel(tile) * 10;
    }

    public static int requiredProgressActions(WhirlisprigTile tile) {
        return Math.max(1, 6 - diversityLevel(tile));
    }

    private static boolean isDiversityPlant(BlockState state) {
        if (state.getBlock() instanceof CropBlock
                || state.is(LOGS) || state.is(LEAVES) || state.is(SAPLINGS)
                || state.is(FLOWERS) || state.is(CROPS)) return true;
        String path = BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        return path.contains("archwood") || path.contains("berry") || path.contains("crop")
                || path.contains("flower") || path.contains("sapling") || path.contains("bush")
                || path.contains("torchflower") || path.contains("pitcher");
    }

    private static String plantFamily(String path) {
        String family = path.startsWith("stripped_") ? path.substring("stripped_".length()) : path;
        String[] suffixes = {"_log", "_wood", "_leaves", "_leaf", "_sapling", "_stem",
                "_hyphae", "_flower", "_crop", "_bush", "_plant"};
        boolean changed;
        do {
            changed = false;
            for (String suffix : suffixes) {
                if (family.endsWith(suffix) && family.length() > suffix.length()) {
                    family = family.substring(0, family.length() - suffix.length());
                    changed = true;
                    break;
                }
            }
        } while (changed);
        return family;
    }

    private static TagKey<Block> blockTag(String namespace, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.fromNamespaceAndPath(namespace, path));
    }

    private static boolean isEligible(BlockState state, Mode mode) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        String path = id.getPath();
        return path.contains("archwood") && (mode != Mode.ADVANCED || path.endsWith("_log"));
    }

    private static Catalyst findCatalyst(WhirlisprigTile tile) {
        Level level = tile.getLevel();
        if (level == null) return null;
        BlockPos origin = tile.getBlockPos();
        Catalyst normal = null;
        for (int y = -INPUT_RADIUS; y <= INPUT_RADIUS; y++) {
            for (int x = -INPUT_RADIUS; x <= INPUT_RADIUS; x++) {
                for (int z = -INPUT_RADIUS; z <= INPUT_RADIUS; z++) {
                    if (x == 0 && y == 0 && z == 0) continue;
                    BlockPos pos = origin.offset(x, y, z);
                    if (level.getBlockEntity(pos) instanceof ArcanePedestalTile) continue;
                    IItemHandler handler = itemHandler(level, pos);
                    if (handler == null) continue;
                    for (int slot = 0; slot < handler.getSlots(); slot++) {
                        ItemStack stack = handler.getStackInSlot(slot);
                        if (stack.isEmpty() || handler.extractItem(slot, 1, true).isEmpty()) continue;
                        if (stack.is(ModItems.ANCIENT_GROVE_CATALYST.get())) {
                            return new Catalyst(handler, slot, Mode.ADVANCED);
                        }
                        if (normal == null && stack.is(ItemsRegistry.EARTH_ESSENCE.get())) {
                            normal = new Catalyst(handler, slot, Mode.NORMAL);
                        }
                    }
                }
            }
        }
        return normal;
    }

    private static IItemHandler itemHandler(Level level, BlockPos pos) {
        IItemHandler handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, null);
        if (handler != null) return handler;
        for (Direction side : Direction.values()) {
            handler = level.getCapability(Capabilities.ItemHandler.BLOCK, pos, side);
            if (handler != null) return handler;
        }
        return null;
    }

    private record Catalyst(IItemHandler handler, int slot, Mode mode) {}
    private record CachedMode(long gameTime, Mode mode) {}
    private record CachedDiversity(long gameTime, int level) {}
}
