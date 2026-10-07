package dev.arsmatrix.compat.jei;

import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Recipe-guide transfer bridge for optional workstations without hard mod dependencies. */
public final class OptionalGuideRecipeTransferHandler<T extends Recipe<?>>
        implements IRecipeTransferHandler<WixieOrderTerminalMenu, RecipeHolder<T>> {
    private final RecipeType<RecipeHolder<T>> recipeType;

    public OptionalGuideRecipeTransferHandler(RecipeType<RecipeHolder<T>> recipeType) {
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
            Player player, boolean maxTransfer, boolean doTransfer
    ) {
        if (!RecipeAutomationSupport.supports(recipe.value())) return null;
        if (doTransfer) {
            GuideEncodingTransfer.submit(menu, player, recipe.id());
        }
        return null;
    }
}
