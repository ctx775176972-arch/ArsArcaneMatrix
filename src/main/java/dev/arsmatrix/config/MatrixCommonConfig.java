package dev.arsmatrix.config;

import net.neoforged.neoforge.common.ModConfigSpec;

/** Installation-wide switches evaluated while loading data; recipes remain server-authoritative. */
public final class MatrixCommonConfig {
    public static final ModConfigSpec SPEC;
    public static final ModConfigSpec.BooleanValue ENABLE_UNBREAKABLE_REFINEMENT;
    public static final ModConfigSpec.BooleanValue ENABLE_ENCHANTED_GOLDEN_APPLE;
    public static final ModConfigSpec.BooleanValue ENABLE_BUDDING_AMETHYST;
    public static final ModConfigSpec.BooleanValue ENABLE_CREATURE_TOKENS;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.push("recipes");
        ENABLE_UNBREAKABLE_REFINEMENT = builder
                .comment("Enable the unbreakable refinement recipe. Restart after changing. Existing items are unaffected.",
                        "是否启用不毁强化配方。修改后重启生效；不会影响已有装备。联机以服务器配置为准。")
                .translation("config.ars_arcane_matrix.recipes.enableUnbreakableRefinement")
                .gameRestart()
                .define("enableUnbreakableRefinement", false);
        ENABLE_ENCHANTED_GOLDEN_APPLE = recipeSwitch(builder, "enableEnchantedGoldenApple", false,
                "Enable enchanted golden apple crafting. / 启用附魔金苹果合成，默认关闭。");
        ENABLE_BUDDING_AMETHYST = recipeSwitch(builder, "enableBuddingAmethyst", true,
                "Enable budding amethyst crafting. / 启用紫水晶母岩合成，默认开启。");
        ENABLE_CREATURE_TOKENS = recipeSwitch(builder, "enableCreatureTokens", true,
                "Enable the five creature token stonecutting recipes. / 启用五种魔法生物信物的切石配方，默认开启。");
        builder.pop();
        SPEC = builder.build();
    }

    private MatrixCommonConfig() {}

    private static ModConfigSpec.BooleanValue recipeSwitch(ModConfigSpec.Builder builder, String key,
                                                           boolean enabled, String comment) {
        return builder.comment(comment, "Restart after changing. / 修改后重启生效，联机以服务器配置为准。")
                .translation("config.ars_arcane_matrix.recipes." + key).gameRestart().define(key, enabled);
    }
}
