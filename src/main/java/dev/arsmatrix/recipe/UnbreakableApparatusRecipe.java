package dev.arsmatrix.recipe;

import com.hollingsworth.arsnouveau.common.crafting.recipes.ApparatusRecipeInput;
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.mojang.serialization.MapCodec;
import dev.arsmatrix.registry.ModRecipeTypes;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.level.Level;

import java.util.List;

/** Adds the vanilla unbreakable component while preserving the central item. */
public final class UnbreakableApparatusRecipe extends EnchantingApparatusRecipe {
    public UnbreakableApparatusRecipe(
            Ingredient reagent,
            ItemStack result,
            List<Ingredient> pedestalItems,
            int sourceCost,
            boolean keepNbtOfReagent
    ) {
        super(reagent, result, pedestalItems, sourceCost, keepNbtOfReagent);
    }

    private static UnbreakableApparatusRecipe fromApparatus(EnchantingApparatusRecipe recipe) {
        return new UnbreakableApparatusRecipe(
                recipe.reagent(), recipe.result(), recipe.pedestalItems(),
                recipe.sourceCost(), recipe.keepNbtOfReagent());
    }

    /** Hidden only from Ars' ordinary apparatus page; our synchronized category displays it. */
    @Override
    public boolean excludeJei() {
        return true;
    }

    @Override
    public boolean doesReagentMatch(ApparatusRecipeInput input, Level level, Player player) {
        ItemStack reagent = input.catalyst();
        return super.doesReagentMatch(input, level, player)
                && reagent.getMaxDamage() > 0
                && !reagent.has(DataComponents.UNBREAKABLE);
    }

    @Override
    public ItemStack assemble(ApparatusRecipeInput input, HolderLookup.Provider registries) {
        ItemStack result = input.catalyst().copy();
        result.setCount(1);
        result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
        return result;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return ModRecipeTypes.UNBREAKABLE_APPARATUS_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<UnbreakableApparatusRecipe> {
        private static final MapCodec<UnbreakableApparatusRecipe> CODEC =
                EnchantingApparatusRecipe.Serializer.CODEC.xmap(
                        UnbreakableApparatusRecipe::fromApparatus, recipe -> recipe);
        private static final StreamCodec<RegistryFriendlyByteBuf, UnbreakableApparatusRecipe> STREAM_CODEC =
                EnchantingApparatusRecipe.Serializer.STREAM_CODEC.map(
                        UnbreakableApparatusRecipe::fromApparatus, recipe -> recipe);

        @Override
        public MapCodec<UnbreakableApparatusRecipe> codec() {
            return CODEC;
        }

        @Override
        public StreamCodec<RegistryFriendlyByteBuf, UnbreakableApparatusRecipe> streamCodec() {
            return STREAM_CODEC;
        }
    }
}
