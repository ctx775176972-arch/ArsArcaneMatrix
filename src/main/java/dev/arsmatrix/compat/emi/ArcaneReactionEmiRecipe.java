package dev.arsmatrix.compat.emi;

import dev.arsmatrix.compat.ArcaneReactionAutomationRecipe;
import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.registry.ModBlocks;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.EmiRecipeCategory;
import dev.emi.emi.api.stack.EmiIngredient;
import dev.emi.emi.api.stack.EmiStack;
import dev.emi.emi.api.widget.WidgetHolder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;

/** Native fluid-aware display; the backing adapter uses the existing server order planner. */
public final class ArcaneReactionEmiRecipe implements EmiRecipe {
    public static final EmiRecipeCategory CATEGORY = new EmiRecipeCategory(
            ResourceLocation.fromNamespaceAndPath("ars_arcane_matrix", "arcane_reaction"),
            EmiStack.of(ModBlocks.ARCANE_REACTION_VESSEL.get())) {
        @Override public Component getName() {
            return Component.translatable("jei.ars_arcane_matrix.arcane_reaction");
        }
    };
    private final ArcaneReactionRule rule;
    private final List<EmiIngredient> inputs;
    private final List<EmiStack> outputs;

    public ArcaneReactionEmiRecipe(ArcaneReactionRule rule) {
        this.rule = rule;
        List<EmiIngredient> ingredients = new ArrayList<>();
        for (var ingredient : rule.ingredients()) {
            ingredients.add(EmiIngredient.of(ingredient.displayStacks().stream()
                    .map(stack -> EmiStack.of(stack.copyWithCount(1))).toList(), ingredient.count()));
        }
        if (rule.inputFluidAmount() > 0) {
            ingredients.add(EmiStack.of(BuiltInRegistries.FLUID.get(rule.inputFluid()), rule.inputFluidAmount()));
        }
        inputs = List.copyOf(ingredients);
        outputs = rule.createItemOutput().isEmpty()
                ? List.of(EmiStack.of(rule.createFluidOutput().getFluid(), rule.outputFluidAmount()))
                : List.of(EmiStack.of(rule.createItemOutput()));
    }

    @Override public EmiRecipeCategory getCategory() { return CATEGORY; }
    @Override public ResourceLocation getId() { return rule.id(); }
    @Override public List<EmiIngredient> getInputs() { return inputs; }
    @Override public List<EmiStack> getOutputs() { return outputs; }
    @Override public List<EmiIngredient> getCatalysts() {
        return List.of(EmiStack.of(ModBlocks.ARCANE_REACTION_VESSEL.get()));
    }
    @Override public int getDisplayWidth() { return 160; }
    @Override public int getDisplayHeight() { return 68; }
    @Override public RecipeHolder<?> getBackingRecipe() {
        return new RecipeHolder<>(rule.id(), new ArcaneReactionAutomationRecipe(rule));
    }
    @Override public boolean supportsRecipeTree() { return !rule.createItemOutput().isEmpty(); }
    @Override public void addWidgets(WidgetHolder widgets) {
        for (int i = 0; i < inputs.size(); i++) widgets.addSlot(inputs.get(i), 4 + i * 22, 10);
        widgets.addFillingArrow(80, 11, rule.processingTicks() * 50);
        widgets.addSlot(outputs.getFirst(), 116, 10).recipeContext(this);
        widgets.addText(Component.translatable("jei.ars_arcane_matrix.arcane_reaction.details",
                rule.sourceCost(), rule.processingTicks() / 20.0F), 4, 46, 0x4A255F, false);
    }
}
