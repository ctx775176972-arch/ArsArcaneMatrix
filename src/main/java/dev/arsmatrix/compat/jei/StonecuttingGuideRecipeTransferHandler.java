package dev.arsmatrix.compat.jei;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Encodes a vanilla stonecutting recipe into a Wixie crafting guide. */
public final class StonecuttingGuideRecipeTransferHandler
        implements IRecipeTransferHandler<WixieOrderTerminalMenu, RecipeHolder<StonecutterRecipe>> {
    @Override public Class<? extends WixieOrderTerminalMenu> getContainerClass() {
        return WixieOrderTerminalMenu.class;
    }

    @Override public Optional<MenuType<WixieOrderTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WIXIE_ORDER_TERMINAL.get());
    }

    @Override public RecipeType<RecipeHolder<StonecutterRecipe>> getRecipeType() {
        return RecipeTypes.STONECUTTING;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            WixieOrderTerminalMenu menu, RecipeHolder<StonecutterRecipe> recipe,
            IRecipeSlotsView recipeSlots, Player player, boolean maxTransfer, boolean doTransfer
    ) {
        if (doTransfer) {
            GuideEncodingTransfer.submit(menu, player, recipe.id());
        }
        return null;
    }
}
