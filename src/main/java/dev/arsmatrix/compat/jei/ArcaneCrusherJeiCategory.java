package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.CrusherRecipeRule;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModItems;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** One base output plus mutually exclusive Air and Water mode additions. */
public final class ArcaneCrusherJeiCategory implements IRecipeCategory<CrusherRecipeRule> {
    public static final RecipeType<CrusherRecipeRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID, "arcane_crushing", CrusherRecipeRule.class);
    private final IDrawable icon;

    public ArcaneCrusherJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ModBlocks.ARCANE_CRUSHER_CORE.get());
    }
    @Override public RecipeType<CrusherRecipeRule> getRecipeType() { return TYPE; }
    @Override public Component getTitle() { return Component.translatable("jei.ars_arcane_matrix.arcane_crushing"); }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 166; }
    @Override public int getHeight() { return 96; }

    @Override public void setRecipe(IRecipeLayoutBuilder builder, CrusherRecipeRule recipe, IFocusGroup focuses) {
        builder.addInputSlot(8, 39).setStandardSlotBackground().addItemStack(recipe.input());
        builder.addOutputSlot(58, 39).setOutputSlotBackground().addItemStack(recipe.baseOutput())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable(
                        "jei.ars_arcane_matrix.arcane_crushing.base.tooltip").withStyle(ChatFormatting.GRAY)));
        builder.addOutputSlot(112, 18).setOutputSlotBackground().addItemStack(recipe.airBonusOutput())
                .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable(
                        "jei.ars_arcane_matrix.arcane_crushing.air_bonus.tooltip").withStyle(ChatFormatting.GOLD)));
        builder.addOutputSlot(112, 60).setOutputSlotBackground()
                .addItemStack(new ItemStack(ModItems.ENRICHED_MINERAL_CRYSTAL.get()))
                .addRichTooltipCallback((slot, tooltip) -> {
                    tooltip.add(Component.translatable(
                            "jei.ars_arcane_matrix.arcane_crushing.water_bonus.tooltip")
                            .withStyle(ChatFormatting.GOLD));
                    if (recipe.input().is(Items.ANCIENT_DEBRIS)) {
                        tooltip.add(Component.translatable(
                                "jei.ars_arcane_matrix.arcane_crushing.water_bonus.ancient_debris")
                                .withStyle(ChatFormatting.AQUA));
                    }
                });
    }

    @Override public void draw(CrusherRecipeRule recipe, IRecipeSlotsView slots, GuiGraphics graphics,
                               double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.literal("→"), 35, 44, 0x606060, false);
        graphics.drawString(font, Component.literal("+"), 86, 44, 0x606060, false);
        graphics.drawString(font, Component.translatable("jei.ars_arcane_matrix.arcane_crushing.base"),
                55, 25, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.ars_arcane_matrix.arcane_crushing.air_bonus"),
                103, 5, 0x404040, false);
        graphics.drawString(font, Component.translatable("jei.ars_arcane_matrix.arcane_crushing.or"),
                116, 43, 0x606060, false);
        graphics.drawString(font, Component.translatable("jei.ars_arcane_matrix.arcane_crushing.water_bonus"),
                103, 81, 0x404040, false);
    }

    @Override public ResourceLocation getRegistryName(CrusherRecipeRule recipe) { return recipe.id(); }
}
