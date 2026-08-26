package dev.arsmatrix.compat;

import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;

import java.util.List;

/** Small, dependency-free adapters for dynamic recipes that expose no ingredient list. */
public final class DynamicCraftingRecipeSupport {
    private static final ResourceLocation FARMERS_DELIGHT_DOUGH =
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "dough");

    private DynamicCraftingRecipeSupport() {}

    public static boolean supports(CraftingRecipe recipe) {
        return FARMERS_DELIGHT_DOUGH.equals(
                BuiltInRegistries.RECIPE_SERIALIZER.getKey(recipe.getSerializer()));
    }

    public static List<Ingredient> ingredients(CraftingRecipe recipe) {
        if (!recipe.getIngredients().isEmpty()) return recipe.getIngredients();
        if (supports(recipe)) return List.of(Ingredient.of(Items.WHEAT), Ingredient.of(Items.WATER_BUCKET));
        return List.of();
    }

    public static ItemStack result(CraftingRecipe recipe, HolderLookup.Provider registries) {
        ItemStack declared = recipe.getResultItem(registries);
        if (!declared.isEmpty() || !supports(recipe)) return declared;
        CraftingInput sample = CraftingInput.of(2, 2, List.of(
                new ItemStack(Items.WHEAT), new ItemStack(Items.WATER_BUCKET),
                ItemStack.EMPTY, ItemStack.EMPTY));
        return recipe.assemble(sample, registries);
    }
}
