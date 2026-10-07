package dev.arsmatrix.compat;

import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantmentRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.ArcaneReactionManager;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/** Shared recipe metadata for guide encoding and the advanced Wixie planner. */
public final class RecipeAutomationSupport {
    public static final ResourceLocation CRAFTING_TABLE =
            ResourceLocation.withDefaultNamespace("crafting_table");
    public static final ResourceLocation SOURCE_STONE_FURNACE =
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "source_stone_furnace");
    public static final ResourceLocation ENCHANTING_APPARATUS =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "enchanting_apparatus");
    public static final ResourceLocation IMBUEMENT_CHAMBER =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "imbuement_chamber");
    public static final ResourceLocation STONECUTTER =
            ResourceLocation.withDefaultNamespace("stonecutter");
    public static final ResourceLocation ARCANE_REACTION_VESSEL =
            ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "arcane_reaction_vessel");
    public static final ResourceLocation FARMERS_DELIGHT_COOKING_POT =
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking_pot");
    public static final ResourceLocation AVARITIA_SCULK_CRAFTING_TABLE =
            ResourceLocation.fromNamespaceAndPath("avaritia", "sculk_crafting_table");
    public static final ResourceLocation AVARITIA_NETHER_CRAFTING_TABLE =
            ResourceLocation.fromNamespaceAndPath("avaritia", "nether_crafting_table");
    public static final ResourceLocation AVARITIA_END_CRAFTING_TABLE =
            ResourceLocation.fromNamespaceAndPath("avaritia", "end_crafting_table");
    public static final ResourceLocation AVARITIA_EXTREME_CRAFTING_TABLE =
            ResourceLocation.fromNamespaceAndPath("avaritia", "extreme_crafting_table");
    public static final ResourceLocation AVARITIA_NEUTRON_COMPRESSOR =
            ResourceLocation.fromNamespaceAndPath("avaritia", "neutron_compressor");

    private static final ResourceLocation FARMERS_DELIGHT_COOKING =
            ResourceLocation.fromNamespaceAndPath("farmersdelight", "cooking");
    private static final ResourceLocation AVARITIA_CRAFTING =
            ResourceLocation.fromNamespaceAndPath("avaritia", "crafting_table_recipe");
    private static final ResourceLocation AVARITIA_COMPRESSOR =
            ResourceLocation.fromNamespaceAndPath("avaritia", "compressor_recipe");

    private RecipeAutomationSupport() {}

    public static boolean supports(Recipe<?> recipe) {
        if (recipe instanceof CraftingRecipe crafting) {
            return !crafting.isSpecial() || DynamicCraftingRecipeSupport.supports(crafting);
        }
        return recipe instanceof AbstractCookingRecipe
                || recipe instanceof EnchantingApparatusRecipe
                || recipe instanceof ImbuementRecipe
                || recipe instanceof StonecutterRecipe
                || recipe instanceof ArcaneReactionAutomationRecipe
                || isFarmersDelightCooking(recipe)
                || isAvaritiaCrafting(recipe)
                || isAvaritiaCompressor(recipe);
    }

    public static boolean isCooking(Recipe<?> recipe) {
        return recipe instanceof AbstractCookingRecipe;
    }

    public static boolean isApparatus(Recipe<?> recipe) {
        return recipe instanceof EnchantingApparatusRecipe;
    }

    public static boolean isImbuement(Recipe<?> recipe) {
        return recipe instanceof ImbuementRecipe;
    }

    public static boolean isStonecutting(Recipe<?> recipe) {
        return recipe instanceof StonecutterRecipe;
    }

    public static boolean isReaction(Recipe<?> recipe) {
        return recipe instanceof ArcaneReactionAutomationRecipe;
    }

    public static boolean isFarmersDelightCooking(Recipe<?> recipe) {
        return FARMERS_DELIGHT_COOKING.equals(recipeTypeId(recipe));
    }

    public static boolean isAvaritiaCrafting(Recipe<?> recipe) {
        return AVARITIA_CRAFTING.equals(recipeTypeId(recipe));
    }

    public static boolean isAvaritiaCompressor(Recipe<?> recipe) {
        return AVARITIA_COMPRESSOR.equals(recipeTypeId(recipe));
    }

    /** Reads Re-Avaritia's declared material count without making it a required dependency. */
    public static int avaritiaCompressorInputCount(Recipe<?> recipe) {
        if (!isAvaritiaCompressor(recipe)) return 1;
        try {
            Object value = recipe.getClass().getMethod("getInputCount").invoke(recipe);
            return value instanceof Number number ? Math.max(1, number.intValue()) : 1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return 1;
        }
    }

    /** Re-Avaritia exposes tiers 1..4 for its 3x3, 5x5, 7x7 and 9x9 tables. */
    public static int avaritiaTier(Recipe<?> recipe) {
        if (!isAvaritiaCrafting(recipe)) return -1;
        try {
            Object value = recipe.getClass().getMethod("getTier").invoke(recipe);
            return value instanceof Number number ? Math.max(1, Math.min(4, number.intValue())) : -1;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return -1;
        }
    }

    public static ItemStack farmersDelightContainer(Recipe<?> recipe) {
        if (!isFarmersDelightCooking(recipe)) return ItemStack.EMPTY;
        try {
            Object value = recipe.getClass().getMethod("getOutputContainer").invoke(recipe);
            return value instanceof ItemStack stack ? stack.copyWithCount(1) : ItemStack.EMPTY;
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            return ItemStack.EMPTY;
        }
    }

    public static List<Ingredient> ingredients(
            Recipe<?> recipe, HolderLookup.Provider registries
    ) {
        if (isAvaritiaCrafting(recipe)) {
            return recipe.getIngredients().stream().map(ingredient -> {
                ItemStack[] choices = ingredient.getItems();
                if (choices.length == 1 && BuiltInRegistries.ITEM.getKey(choices[0].getItem())
                        .equals(ResourceLocation.fromNamespaceAndPath("avaritia", "singularity"))) {
                    return DataComponentIngredient.of(true, choices[0]);
                }
                return ingredient;
            }).toList();
        }
        if (recipe instanceof CraftingRecipe crafting) {
            return DynamicCraftingRecipeSupport.ingredients(crafting);
        }
        if (recipe instanceof EnchantmentRecipe enchantment) {
            List<Ingredient> ingredients = new ArrayList<>(enchantment.pedestalItems().size() + 1);
            ingredients.add(DataComponentIngredient.of(true,
                    enchantedBook(enchantment, Math.max(0, enchantment.enchantLevel - 1), registries)));
            ingredients.addAll(enchantment.pedestalItems());
            return List.copyOf(ingredients);
        }
        if (isFarmersDelightCooking(recipe)) {
            List<Ingredient> ingredients = new ArrayList<>(recipe.getIngredients());
            ItemStack container = farmersDelightContainer(recipe);
            if (!container.isEmpty()) ingredients.add(Ingredient.of(container));
            return List.copyOf(ingredients);
        }
        return recipe.getIngredients();
    }

    public static ItemStack result(Recipe<?> recipe, HolderLookup.Provider registries) {
        if (recipe instanceof CraftingRecipe crafting) {
            return DynamicCraftingRecipeSupport.result(crafting, registries);
        }
        if (recipe instanceof EnchantmentRecipe enchantment) {
            return enchantedBook(enchantment, enchantment.enchantLevel, registries);
        }
        if (recipe instanceof EnchantingApparatusRecipe apparatus) {
            return apparatus.result().copy();
        }
        return recipe.getResultItem(registries);
    }

    public static ResourceLocation workstation(Recipe<?> recipe) {
        if (isCooking(recipe)) return SOURCE_STONE_FURNACE;
        if (isApparatus(recipe)) return ENCHANTING_APPARATUS;
        if (isImbuement(recipe)) return IMBUEMENT_CHAMBER;
        if (isStonecutting(recipe)) return STONECUTTER;
        if (isReaction(recipe)) return ARCANE_REACTION_VESSEL;
        if (isFarmersDelightCooking(recipe)) return FARMERS_DELIGHT_COOKING_POT;
        if (isAvaritiaCompressor(recipe)) return AVARITIA_NEUTRON_COMPRESSOR;
        if (isAvaritiaCrafting(recipe)) {
            return switch (avaritiaTier(recipe)) {
                case 1 -> AVARITIA_SCULK_CRAFTING_TABLE;
                case 2 -> AVARITIA_NETHER_CRAFTING_TABLE;
                case 3 -> AVARITIA_END_CRAFTING_TABLE;
                default -> AVARITIA_EXTREME_CRAFTING_TABLE;
            };
        }
        return CRAFTING_TABLE;
    }

    private static ResourceLocation recipeTypeId(Recipe<?> recipe) {
        return recipe == null ? null : BuiltInRegistries.RECIPE_TYPE.getKey(recipe.getType());
    }

    public static Optional<RecipeHolder<?>> find(RecipeManager manager, ResourceLocation id) {
        Optional<RecipeHolder<?>> vanilla = manager.byKey(id);
        if (vanilla.isPresent()) return vanilla;
        return ArcaneReactionManager.find(id)
                .filter(rule -> !rule.createItemOutput().isEmpty())
                .map(rule -> new RecipeHolder<>(id, new ArcaneReactionAutomationRecipe(rule)));
    }

    public static List<RecipeHolder<?>> all(RecipeManager manager) {
        Map<ResourceLocation, RecipeHolder<?>> result = new LinkedHashMap<>();
        manager.getRecipes().forEach(holder -> result.put(holder.id(), holder));
        ArcaneReactionManager.allRecipes().stream()
                .filter(rule -> !rule.createItemOutput().isEmpty())
                .forEach(rule -> result.putIfAbsent(rule.id(),
                        new RecipeHolder<>(rule.id(), new ArcaneReactionAutomationRecipe(rule))));
        return List.copyOf(result.values());
    }

    private static ItemStack enchantedBook(
            EnchantmentRecipe recipe, int level, HolderLookup.Provider registries
    ) {
        if (level <= 0) return new ItemStack(Items.BOOK);
        ItemStack result = new ItemStack(Items.ENCHANTED_BOOK);
        var enchantment = registries.lookupOrThrow(Registries.ENCHANTMENT)
                .getOrThrow(recipe.enchantmentKey);
        ItemEnchantments.Mutable enchantments = new ItemEnchantments.Mutable(
                EnchantmentHelper.getEnchantmentsForCrafting(result));
        enchantments.set(enchantment, level);
        EnchantmentHelper.setEnchantments(result, enchantments.toImmutable());
        return result;
    }
}
