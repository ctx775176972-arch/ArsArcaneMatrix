package dev.arsmatrix.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/** Identifies safe, reversible 1-to-4/9 storage-form conversions. */
public final class ReversibleStorageConversionSupport {
    private ReversibleStorageConversionSupport() {}

    public static boolean isReversible(
            RecipeHolder<?> candidate,
            RecipeManager recipes,
            HolderLookup.Provider registries
    ) {
        Conversion conversion = describe(candidate.value(), registries);
        if (conversion == null) return false;
        for (RecipeHolder<CraftingRecipe> other : recipes.getAllRecipesFor(RecipeType.CRAFTING)) {
            if (other.id().equals(candidate.id())) continue;
            Conversion inverse = describe(other.value(), registries);
            if (inverse != null && conversion.isInverseOf(inverse)) return true;
        }
        return false;
    }

    private static Conversion describe(Object value, HolderLookup.Provider registries) {
        if (!(value instanceof CraftingRecipe recipe) || recipe.isSpecial()) return null;
        List<Ingredient> ingredients = DynamicCraftingRecipeSupport.ingredients(recipe).stream()
                .filter(ingredient -> !ingredient.isEmpty())
                .toList();
        int inputCount = ingredients.size();
        if (inputCount != 1 && inputCount != 4 && inputCount != 9) return null;

        ItemStack input = ItemStack.EMPTY;
        for (Ingredient ingredient : ingredients) {
            ItemStack[] candidates = ingredient.getItems();
            // Tags and alternatives are excluded: every occupied slot must be one exact item.
            if (candidates.length != 1 || candidates[0].isEmpty()) return null;
            ItemStack current = candidates[0].copyWithCount(1);
            if (input.isEmpty()) input = current;
            else if (!ItemStack.isSameItemSameComponents(input, current)) return null;
        }

        ItemStack output = DynamicCraftingRecipeSupport.result(recipe, registries);
        if (output.isEmpty()) return null;
        int outputCount = output.getCount();
        boolean compression = (inputCount == 4 || inputCount == 9) && outputCount == 1;
        boolean decompression = inputCount == 1 && (outputCount == 4 || outputCount == 9);
        if (!compression && !decompression) return null;
        return new Conversion(input.copyWithCount(1), inputCount,
                output.copyWithCount(1), outputCount);
    }

    private record Conversion(ItemStack input, int inputCount, ItemStack output, int outputCount) {
        private boolean isInverseOf(Conversion other) {
            return inputCount == other.outputCount
                    && outputCount == other.inputCount
                    && ItemStack.isSameItemSameComponents(input, other.output)
                    && ItemStack.isSameItemSameComponents(output, other.input);
        }
    }
}
