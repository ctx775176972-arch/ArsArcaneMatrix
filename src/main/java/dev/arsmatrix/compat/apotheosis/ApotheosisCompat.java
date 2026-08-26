package dev.arsmatrix.compat.apotheosis;

import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Optional, reflection-based compatibility that keeps Apotheosis a soft dependency. */
public final class ApotheosisCompat {

    private static final Bridge BRIDGE = Bridge.load();
    private static boolean warned;

    private ApotheosisCompat() {
    }

    /** Rebuilds affix equipment with a fresh item and affixes at the same rarity. */
    public static List<ItemStack> rerollAffixDrops(
            List<ItemStack> drops,
            ServerLevel level,
            Player player,
            BlockPos pos
    ) {
        if (BRIDGE == null) {
            return drops;
        }

        List<ItemStack> rerolled = new ArrayList<>(drops.size());
        for (ItemStack stack : drops) {
            try {
                RandomSource itemRandom = RandomSource.create(level.random.nextLong());
                rerolled.add(BRIDGE.reroll(stack, itemRandom, player, pos));
            } catch (ReflectiveOperationException | RuntimeException exception) {
                if (!warned) {
                    warned = true;
                    ArsArcaneMatrix.LOGGER.warn(
                            "Arcane Arena could not reroll Apotheosis affix equipment; preserving original drops",
                            exception
                    );
                }
                rerolled.add(stack);
            }
        }
        return rerolled;
    }

    private record Bridge(
            Method hasAffixes,
            Method getRarity,
            Method holderGet,
            Method createContext,
            Method createRandomLootItem,
            Method createLootItem
    ) {

        private static Bridge load() {
            try {
                Class<?> affixHelper = Class.forName(
                        "dev.shadowsoffire.apotheosis.affix.AffixHelper"
                );
                Class<?> lootController = Class.forName(
                        "dev.shadowsoffire.apotheosis.loot.LootController"
                );
                Class<?> lootRarity = Class.forName(
                        "dev.shadowsoffire.apotheosis.loot.LootRarity"
                );
                Class<?> genContext = Class.forName(
                        "dev.shadowsoffire.apotheosis.tiers.GenContext"
                );
                Class<?> dynamicHolder = Class.forName(
                        "dev.shadowsoffire.placebo.reload.DynamicHolder"
                );
                Bridge bridge = new Bridge(
                        affixHelper.getMethod("hasAffixes", ItemStack.class),
                        affixHelper.getMethod("getRarity", ItemStack.class),
                        dynamicHolder.getMethod("get"),
                        genContext.getMethod(
                                "forPlayerAtPos",
                                RandomSource.class,
                                Player.class,
                                BlockPos.class
                        ),
                        lootController.getMethod(
                                "createRandomLootItem",
                                genContext,
                                lootRarity
                        ),
                        lootController.getMethod(
                                "createLootItem",
                                ItemStack.class,
                                lootRarity,
                                genContext
                        )
                );
                ArsArcaneMatrix.LOGGER.info(
                        "Arcane Arena enabled Apotheosis affix reroll compatibility"
                );
                return bridge;
            } catch (ClassNotFoundException exception) {
                return null;
            } catch (ReflectiveOperationException exception) {
                ArsArcaneMatrix.LOGGER.warn(
                        "Apotheosis is installed, but its affix reroll API is incompatible; "
                                + "Arcane Arena drops will be preserved unchanged",
                        exception
                );
                return null;
            }
        }

        private ItemStack reroll(
                ItemStack original,
                RandomSource random,
                Player player,
                BlockPos pos
        ) throws ReflectiveOperationException {
            if (!(boolean) hasAffixes.invoke(null, original)) {
                return original;
            }

            Object rarityHolder = getRarity.invoke(null, original);
            Object rarity = holderGet.invoke(rarityHolder);
            for (int attempt = 0; attempt < 4; attempt++) {
                Object context = createContext.invoke(null, random, player, pos);
                Object result = createRandomLootItem.invoke(null, context, rarity);
                if (result instanceof ItemStack stack
                        && !stack.isEmpty()
                        && !ItemStack.isSameItemSameComponents(stack, original)) {
                    return stack;
                }
            }

            // A dimension may have no eligible affix equipment entries. In that
            // case, retain the base item but still try to reroll its affixes.
            for (int attempt = 0; attempt < 4; attempt++) {
                Object context = createContext.invoke(null, random, player, pos);
                ItemStack cleanBase = new ItemStack(original.getItem(), original.getCount());
                Object result = createLootItem.invoke(null, cleanBase, rarity, context);
                if (result instanceof ItemStack stack
                        && !stack.isEmpty()
                        && !ItemStack.isSameItemSameComponents(stack, original)) {
                    return stack;
                }
            }
            return original;
        }
    }
}
