package dev.arsmatrix.compat.jei;

import com.hollingsworth.arsnouveau.client.jei.EnchantingApparatusRecipeCategory;
import com.hollingsworth.arsnouveau.client.jei.MultiInputCategory;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.recipe.UnbreakableApparatusRecipe;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Unbreakable;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.phys.Vec2;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;

import java.util.Arrays;
import java.util.List;

/** Synchronized before/after display for a recipe whose result is the catalyst itself. */
public final class UnbreakableRefinementJeiCategory
        extends EnchantingApparatusRecipeCategory<UnbreakableApparatusRecipe> {
    public static final RecipeType<RecipeHolder<UnbreakableApparatusRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(
                    ArsArcaneMatrix.MOD_ID, "unbreakable_refinement"));

    private final IDrawable icon;

    public UnbreakableRefinementJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper);
        this.icon = guiHelper.createDrawableItemLike(ModItems.SOURCEBOUND_COPPER_ALLOY.get());
    }

    @Override
    public RecipeType<RecipeHolder<UnbreakableApparatusRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ars_arcane_matrix.unbreakable_refinement");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder,
                          RecipeHolder<UnbreakableApparatusRecipe> holder,
                          IFocusGroup focuses) {
        UnbreakableApparatusRecipe recipe = holder.value();
        List<ItemStack> inputs = Arrays.stream(recipe.reagent().getItems())
                .map(ItemStack::copy)
                .toList();
        List<ItemStack> outputs = inputs.stream().map(stack -> {
            ItemStack result = stack.copy();
            result.set(DataComponents.UNBREAKABLE, new Unbreakable(true));
            return result;
        }).toList();

        builder.addSlot(RecipeIngredientRole.INPUT, 48, 45).addItemStacks(inputs);
        builder.addSlot(RecipeIngredientRole.OUTPUT, 86, 10).addItemStacks(outputs);

        List<net.minecraft.world.item.crafting.Ingredient> pedestals = recipe.pedestalItems();
        double step = 360.0D / pedestals.size();
        Vec2 point = new Vec2(48.0F, 13.0F);
        Vec2 center = new Vec2(48.0F, 45.0F);
        for (var ingredient : pedestals) {
            builder.addSlot(RecipeIngredientRole.INPUT, (int) point.x, (int) point.y)
                    .addIngredients(ingredient);
            point = MultiInputCategory.rotatePointAbout(point, center, step);
        }
    }
}
