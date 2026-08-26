package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.registry.ModBlocks;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.*;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ArcaneReactionJeiCategory implements IRecipeCategory<ArcaneReactionRule> {
    public static final RecipeType<ArcaneReactionRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID, "arcane_reaction", ArcaneReactionRule.class);
    private final IDrawable icon;
    public ArcaneReactionJeiCategory(IGuiHelper helper) { icon = helper.createDrawableItemLike(ModBlocks.ARCANE_REACTION_VESSEL.get()); }
    @Override public RecipeType<ArcaneReactionRule> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.ars_arcane_matrix.arcane_reaction"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 130; }
    @Override public int getHeight() { return 66; }
    @Override public void setRecipe(IRecipeLayoutBuilder builder, ArcaneReactionRule recipe, IFocusGroup focuses) {
        for (int i=0;i<recipe.ingredients().size();i++) builder.addSlot(RecipeIngredientRole.INPUT, 5+i*20, 12)
                .addItemStacks(recipe.ingredients().get(i).displayStacks());
        if (recipe.inputFluidAmount()>0) {
            IRecipeSlotBuilder fluidInput = builder.addSlot(RecipeIngredientRole.INPUT, 45, 12);
            fluidInput.addFluidStack(net.minecraft.core.registries.BuiltInRegistries.FLUID.get(recipe.inputFluid()),
                    recipe.inputFluidAmount());
            fluidInput.setFluidRenderer(recipe.inputFluidAmount(), false, 16, 16);
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 67, 12).addItemStack(ModBlocks.ARCANE_REACTION_VESSEL.toStack());
        if (!recipe.createItemOutput().isEmpty()) builder.addSlot(RecipeIngredientRole.OUTPUT, 108, 12).addItemStack(recipe.createItemOutput());
        if (!recipe.createFluidOutput().isEmpty()) {
            var output = recipe.createFluidOutput();
            IRecipeSlotBuilder fluidOutput = builder.addSlot(RecipeIngredientRole.OUTPUT, 108, 12);
            fluidOutput.addFluidStack(output.getFluid(), output.getAmount());
            fluidOutput.setFluidRenderer(output.getAmount(), false, 16, 16);
        }
    }
    @Override public void draw(ArcaneReactionRule recipe, IRecipeSlotsView slots, GuiGraphics graphics, double mouseX, double mouseY) {
        graphics.drawString(Minecraft.getInstance().font, "→", 91, 17, 0x7A3FA0, false);
        graphics.drawString(Minecraft.getInstance().font, Component.translatable(
                "jei.ars_arcane_matrix.arcane_reaction.details", recipe.sourceCost(), recipe.processingTicks()/20.0F), 5, 48, 0x4A255F, false);
    }
    @Override public ResourceLocation getRegistryName(ArcaneReactionRule recipe) { return recipe.id(); }
}
