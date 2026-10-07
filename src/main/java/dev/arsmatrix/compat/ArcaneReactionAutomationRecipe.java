package dev.arsmatrix.compat;

import dev.arsmatrix.data.ArcaneReactionRule;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

/** In-memory adapter exposing data-driven reaction rules to the Wixie recipe planner. */
public final class ArcaneReactionAutomationRecipe implements Recipe<RecipeInput> {
    private final ArcaneReactionRule rule;

    public ArcaneReactionAutomationRecipe(ArcaneReactionRule rule) {
        this.rule = rule;
    }

    public ArcaneReactionRule rule() {
        return rule;
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) {
        return rule.createItemOutput();
    }
    @Override public boolean canCraftInDimensions(int width, int height) { return true; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) {
        return rule.createItemOutput();
    }
    @Override public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> result = NonNullList.create();
        rule.ingredients().forEach(entry -> {
            ItemStack[] choices = entry.displayStacks().stream()
                    .map(stack -> stack.copyWithCount(1)).toArray(ItemStack[]::new);
            for (int count = 0; count < entry.count(); count++) {
                result.add(Ingredient.of(choices));
            }
        });
        return result;
    }
    // The adapter is never serialized by Minecraft; these vanilla values satisfy Recipe's
    // metadata contract while the rule remains owned by ArcaneReactionManager.
    @Override public RecipeSerializer<?> getSerializer() { return RecipeSerializer.STONECUTTER; }
    @Override public RecipeType<?> getType() { return RecipeType.CRAFTING; }
}
