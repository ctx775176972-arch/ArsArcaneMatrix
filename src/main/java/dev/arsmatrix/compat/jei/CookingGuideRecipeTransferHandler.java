package dev.arsmatrix.compat.jei;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Encodes smelting or smoking recipes from an Advanced Storage Lectern. */
public final class CookingGuideRecipeTransferHandler<T extends AbstractCookingRecipe>
        implements IRecipeTransferHandler<WixieOrderTerminalMenu, RecipeHolder<T>> {
    private final IRecipeTransferHandlerHelper helper;
    private final RecipeType<RecipeHolder<T>> recipeType;

    public CookingGuideRecipeTransferHandler(
            IRecipeTransferHandlerHelper helper, RecipeType<RecipeHolder<T>> recipeType) {
        this.helper = helper;
        this.recipeType = recipeType;
    }

    @Override public Class<? extends WixieOrderTerminalMenu> getContainerClass() {
        return WixieOrderTerminalMenu.class;
    }

    @Override public Optional<MenuType<WixieOrderTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WIXIE_ORDER_TERMINAL.get());
    }

    @Override public RecipeType<RecipeHolder<T>> getRecipeType() { return recipeType; }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            WixieOrderTerminalMenu menu, RecipeHolder<T> recipe, IRecipeSlotsView recipeSlots,
            Player player, boolean maxTransfer, boolean doTransfer) {
        if (!menu.isAdvancedStorage()) {
            return helper.createUserErrorWithTooltip(Component.translatable(
                    "jei.ars_arcane_matrix.crafting_guide.advanced_required"));
        }
        if (doTransfer) {
            int buttonId = WixieOrderTerminalMenu.recipeEncodingButton(recipe.id());
            menu.clickMenuButton(player, buttonId);
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.gameMode != null) {
                minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            }
        }
        return null;
    }
}
