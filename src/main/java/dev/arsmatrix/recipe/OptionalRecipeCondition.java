package dev.arsmatrix.recipe;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import dev.arsmatrix.config.MatrixCommonConfig;
import net.neoforged.neoforge.common.conditions.ICondition;

/** Unknown switches fail closed so a misspelled data-pack condition cannot enable a recipe. */
public record OptionalRecipeCondition(String feature) implements ICondition {
    public static final MapCodec<OptionalRecipeCondition> CODEC = Codec.STRING.fieldOf("feature")
            .xmap(OptionalRecipeCondition::new, OptionalRecipeCondition::feature);

    @Override
    public boolean test(IContext context) {
        return switch (feature) {
            case "enchanted_golden_apple" -> MatrixCommonConfig.ENABLE_ENCHANTED_GOLDEN_APPLE.get();
            case "budding_amethyst" -> MatrixCommonConfig.ENABLE_BUDDING_AMETHYST.get();
            case "creature_tokens" -> MatrixCommonConfig.ENABLE_CREATURE_TOKENS.get();
            default -> false;
        };
    }

    @Override
    public MapCodec<? extends ICondition> codec() {
        return CODEC;
    }
}
