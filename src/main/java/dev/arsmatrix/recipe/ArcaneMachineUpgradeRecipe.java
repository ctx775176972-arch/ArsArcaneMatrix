package dev.arsmatrix.recipe;

import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.mojang.serialization.MapCodec;
import dev.arsmatrix.registry.ModRecipeTypes;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;

import java.util.List;

/**
 * An apparatus recipe with its own recipe type, mirroring Ars Nouveau's armor
 * upgrades. It executes in the normal Enchanting Apparatus but remains separate
 * from ordinary apparatus recipes in recipe viewers.
 */
public final class ArcaneMachineUpgradeRecipe extends EnchantingApparatusRecipe {
    public ArcaneMachineUpgradeRecipe(
            Ingredient reagent,
            ItemStack result,
            List<Ingredient> pedestalItems,
            int sourceCost,
            boolean keepNbtOfReagent
    ) {
        super(reagent, result, pedestalItems, sourceCost, keepNbtOfReagent);
    }

    private static ArcaneMachineUpgradeRecipe fromApparatus(EnchantingApparatusRecipe recipe) {
        return new ArcaneMachineUpgradeRecipe(
                recipe.reagent(), recipe.result(), recipe.pedestalItems(),
                recipe.sourceCost(), recipe.keepNbtOfReagent());
    }

    /** Ars Nouveau collects every apparatus-compatible type for its default JEI page. */
    @Override
    public boolean excludeJei() {
        return true;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.ARCANE_MACHINE_UPGRADE_SERIALIZER.get();
    }

    @Override
    public RecipeType<?> getType() {
        return ModRecipeTypes.ARCANE_MACHINE_UPGRADE_TYPE.get();
    }

    public static final class Serializer implements RecipeSerializer<ArcaneMachineUpgradeRecipe> {
        private static final MapCodec<ArcaneMachineUpgradeRecipe> CODEC =
                EnchantingApparatusRecipe.Serializer.CODEC.xmap(
                        ArcaneMachineUpgradeRecipe::fromApparatus,
                        recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, ArcaneMachineUpgradeRecipe> STREAM_CODEC =
                EnchantingApparatusRecipe.Serializer.STREAM_CODEC.map(
                        ArcaneMachineUpgradeRecipe::fromApparatus,
                        recipe -> recipe);

        @Override
        public MapCodec<ArcaneMachineUpgradeRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, ArcaneMachineUpgradeRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
