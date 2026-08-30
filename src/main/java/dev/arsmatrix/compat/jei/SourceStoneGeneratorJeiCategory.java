package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.SourceStoneGeneratorCatalyst;
import dev.arsmatrix.data.SourceStoneGeneratorRule;
import dev.arsmatrix.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class SourceStoneGeneratorJeiCategory
        implements IRecipeCategory<SourceStoneGeneratorRule> {

    public static final RecipeType<SourceStoneGeneratorRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID,
            "source_stone_generation",
            SourceStoneGeneratorRule.class
    );

    private final IDrawable icon;

    public SourceStoneGeneratorJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.SOURCE_STONE_GENERATOR.get());
    }

    @Override
    public RecipeType<SourceStoneGeneratorRule> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ars_arcane_matrix.source_stone_generation");
    }

    @Override
    public IDrawable getIcon() {
        return icon;
    }

    @Override
    public int getWidth() {
        return 114;
    }

    @Override
    public int getHeight() {
        return 108;
    }

    @Override
    public void setRecipe(
            IRecipeLayoutBuilder builder,
            SourceStoneGeneratorRule recipe,
            IFocusGroup focuses
    ) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 48, 45)
                .addItemStack(ModBlocks.SOURCE_STONE_GENERATOR.toStack());
        int catalystCount = recipe.catalysts().size();
        for (int index = 0; index < recipe.catalysts().size(); index++) {
            SourceStoneGeneratorCatalyst catalyst = recipe.catalysts().get(index);
            double angle = index * 2.0D * Math.PI / catalystCount;
            int x = (int) Math.round(48.0D + Math.sin(angle) * 32.0D);
            int y = (int) Math.round(45.0D - Math.cos(angle) * 32.0D);
            builder.addSlot(RecipeIngredientRole.INPUT, x, y)
                    .addItemStacks(catalyst.displayStacks());
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, 86, 10)
                .addItemStack(recipe.createOutput());
    }

    @Override
    public void draw(
            SourceStoneGeneratorRule recipe,
            IRecipeSlotsView recipeSlotsView,
            GuiGraphics graphics,
            double mouseX,
            double mouseY
    ) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(
                font,
                Component.translatable(
                        "jei.ars_arcane_matrix.source_stone_generation.source_cost",
                        recipe.processingCost()
                ),
                0,
                94,
                10,
                false
        );
    }

    @Override
    public ResourceLocation getRegistryName(SourceStoneGeneratorRule recipe) {
        return recipe.id();
    }
}
