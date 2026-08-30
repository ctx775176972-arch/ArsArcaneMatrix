package dev.arsmatrix.compat.jei;

import com.hollingsworth.arsnouveau.client.jei.EnchantingApparatusRecipeCategory;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.recipe.ArcaneMachineUpgradeRecipe;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModDataComponents;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeHolder;

/** Ars-style apparatus display dedicated to upgrading an existing Matrix machine. */
public final class ArcaneMachineUpgradeJeiCategory
        extends EnchantingApparatusRecipeCategory<ArcaneMachineUpgradeRecipe> {

    public static final RecipeType<RecipeHolder<ArcaneMachineUpgradeRecipe>> TYPE =
            RecipeType.createRecipeHolderType(ResourceLocation.fromNamespaceAndPath(
                    ArsArcaneMatrix.MOD_ID, "arcane_machine_upgrade"));

    private final IDrawable upgradeIcon;

    public ArcaneMachineUpgradeJeiCategory(IGuiHelper guiHelper) {
        super(guiHelper);
        // Ars' pedestal circle reaches y=95 for four- and eight-input recipes.
        // Reserve a footer beneath it for our tier and Source labels.
        this.background = guiHelper.createBlankDrawable(114, 128);
        this.upgradeIcon = guiHelper.createDrawableItemLike(ModBlocks.ARCANE_ORDER_PEDESTAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<ArcaneMachineUpgradeRecipe>> getRecipeType() {
        return TYPE;
    }

    @Override
    public Component getTitle() {
        return Component.translatable("jei.ars_arcane_matrix.arcane_machine_upgrade");
    }

    @Override
    public IDrawable getIcon() {
        return upgradeIcon;
    }

    @Override
    public void draw(RecipeHolder<ArcaneMachineUpgradeRecipe> holder, IRecipeSlotsView slots,
                     GuiGraphics graphics, double mouseX, double mouseY) {
        ArcaneMachineUpgradeRecipe recipe = holder.value();
        ItemStack[] reagentOptions = recipe.reagent().getItems();
        int from = reagentOptions.length == 0 ? 0 : tierOf(reagentOptions[0]);
        int to = tierOf(recipe.result());
        graphics.drawString(Minecraft.getInstance().font,
                Component.translatable("jei.ars_arcane_matrix.arcane_machine_upgrade.tier", from, to),
                0, 98, 0x74509A, false);
        if (recipe.consumesSource()) {
            graphics.drawString(Minecraft.getInstance().font,
                    Component.translatable("ars_nouveau.source", recipe.sourceCost()),
                    0, 113, 0x74509A, false);
        }
    }

    private static int tierOf(ItemStack stack) {
        if (stack.is(ModBlocks.ARCANE_ORDER_PEDESTAL.get().asItem())) {
            return stack.getOrDefault(ModDataComponents.ORDER_PEDESTAL_TIER.get(), 0);
        }
        if (stack.is(ModBlocks.WIXIE_PATTERN_PROVIDER.get().asItem())) {
            return stack.getOrDefault(ModDataComponents.PATTERN_PROVIDER_TIER.get(), 0);
        }
        if (stack.is(ModBlocks.STARBUNCLE_LOGISTICS_HUB.get().asItem())) {
            return stack.getOrDefault(ModDataComponents.LOGISTICS_HUB_TIER.get(), 0);
        }
        if (stack.is(ModBlocks.AUTOMATIC_STOCK_REQUESTER.get().asItem())) {
            return stack.getOrDefault(ModDataComponents.STOCK_REQUESTER_TIER.get(), 0);
        }
        return 0;
    }
}
