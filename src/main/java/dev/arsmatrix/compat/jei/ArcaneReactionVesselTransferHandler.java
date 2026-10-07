package dev.arsmatrix.compat.jei;

import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.menu.ArcaneReactionVesselMenu;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Moves the item portion of a JEI reaction recipe into the vessel's two input slots. */
public final class ArcaneReactionVesselTransferHandler
        implements IRecipeTransferHandler<ArcaneReactionVesselMenu, ArcaneReactionRule> {
    @Override public Class<? extends ArcaneReactionVesselMenu> getContainerClass() {
        return ArcaneReactionVesselMenu.class;
    }
    @Override public Optional<MenuType<ArcaneReactionVesselMenu>> getMenuType() {
        return Optional.of(ModMenus.ARCANE_REACTION_VESSEL.get());
    }
    @Override public RecipeType<ArcaneReactionRule> getRecipeType() {
        return ArcaneReactionJeiCategory.TYPE;
    }
    @Nullable
    @Override public IRecipeTransferError transferRecipe(
            ArcaneReactionVesselMenu menu, ArcaneReactionRule recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer
    ) {
        if (doTransfer) {
            int buttonId = ArcaneReactionVesselMenu.recipeTransferButton(recipe.id());
            menu.clickMenuButton(player, buttonId);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            }
        }
        return null;
    }
}
