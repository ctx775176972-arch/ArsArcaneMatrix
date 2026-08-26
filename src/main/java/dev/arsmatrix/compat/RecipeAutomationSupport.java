package dev.arsmatrix.compat;

import dev.arsmatrix.ArsArcaneMatrix;
import net.minecraft.core.HolderLookup;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

/** Shared recipe metadata for guide encoding and the advanced Wixie planner. */
public final class RecipeAutomationSupport {
    public static final ResourceLocation CRAFTING_TABLE =
            ResourceLocation.withDefaultNamespace("crafting_table");
    public static final ResourceLocation SOURCE_STONE_FURNACE =
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "source_stone_furnace");

    private RecipeAutomationSupport() {}

    public static boolean supports(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe crafting) {
            return !crafting.isSpecial() || DynamicCraftingRecipeSupport.supports(crafting);
        }
        return recipe instanceof AbstractCookingRecipe;
    }

    public static boolean isCooking(Recipe<?> recipe) {
        return recipe instanceof AbstractCookingRecipe;
    }

    public static List<Ingredient> ingredients(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe crafting) {
            return DynamicCraftingRecipeSupport.ingredients(crafting);
        }
        return recipe.getIngredients();
    }

    public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (recipe instanceof CraftingRecipe crafting) {
            return DynamicCraftingRecipeSupport.result(crafting, registries);
        }
        return recipe.getResultItem(registries);
    }

    public static ResourceLocation workstation(Recipe<?> recipe) {
        return isCooking(recipe) ? SOURCE_STONE_FURNACE : CRAFTING_TABLE;
    }
}
