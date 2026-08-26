package dev.arsmatrix.compat.jei;

import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.AlakarkinosExpeditionRule;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.category.IRecipeCategory;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/** Displays container-fed Alakarkinos expeditions as bulk exploration recipes. */
public final class AlakarkinosExpeditionJeiCategory
        implements IRecipeCategory<AlakarkinosExpeditionRule> {
    public static final RecipeType<AlakarkinosExpeditionRule> TYPE = RecipeType.create(
            ArsArcaneMatrix.MOD_ID, "alakarkinos_expedition", AlakarkinosExpeditionRule.class);
    private final IDrawable icon;

    public AlakarkinosExpeditionJeiCategory(IGuiHelper guiHelper) {
        icon = guiHelper.createDrawableItemLike(ItemsRegistry.ALAKARKINOS_CHARM.get());
    }

    @Override public RecipeType<AlakarkinosExpeditionRule> getRecipeType() { return TYPE; }
    @Override public Component getTitle() {
        return Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition");
    }
    @Override public IDrawable getIcon() { return icon; }
    @Override public int getWidth() { return 166; }
    @Override public int getHeight() { return 94; }

    @Override
    public void setRecipe(IRecipeLayoutBuilder builder, AlakarkinosExpeditionRule recipe,
            IFocusGroup focuses) {
        ItemStack proof = recipe.proofStack();
        if (!proof.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 4, 4)
                    .setStandardSlotBackground().addItemStack(proof)
                    .addRichTooltipCallback((slot, tooltip) -> tooltip.add(Component.translatable(
                            "jei.ars_arcane_matrix.alakarkinos_expedition.proof")
                            .withStyle(ChatFormatting.GOLD)));
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 84, 32)
                .setStandardSlotBackground().addItemStack(new ItemStack(Items.BRUSH));
        builder.addSlot(RecipeIngredientRole.CATALYST, 104, 32)
                .addItemStack(new ItemStack(ItemsRegistry.ALAKARKINOS_CHARM.get()));
        var displays = recipe.inputDisplayStacks();
        for (int index = 0; index < displays.size(); index++) {
            builder.addInputSlot(4 + index % 4 * 20, 30 + index / 4 * 20)
                    .setStandardSlotBackground().addItemStacks(displays.get(index));
        }
        builder.addOutputSlot(143, 32).setOutputSlotBackground()
                .addItemStacks(recipe.displayOutputStacks());
    }

    @Override
    public void draw(AlakarkinosExpeditionRule recipe, IRecipeSlotsView slots,
            GuiGraphics graphics, double mouseX, double mouseY) {
        var font = Minecraft.getInstance().font;
        graphics.drawString(font, Component.translatable(
                "jei.ars_arcane_matrix.alakarkinos_expedition.route." + recipe.id().getPath()),
                26, 8, 0x404040, false);
        graphics.drawString(font, Component.literal("→"), 130, 37, 0x8050A0, false);
        graphics.drawString(font, Component.translatable(
                "jei.ars_arcane_matrix.alakarkinos_expedition.time",
                Math.ceilDiv(recipe.workTicks(), 20)), 4, 72, 0x404040, false);
        graphics.drawString(font, Component.translatable(
                "jei.ars_arcane_matrix.alakarkinos_expedition.source",
                recipe.sourceCost()), 82, 72, 0x404040, false);
    }

    @Override public ResourceLocation getRegistryName(AlakarkinosExpeditionRule recipe) {
        return recipe.id();
    }
}
