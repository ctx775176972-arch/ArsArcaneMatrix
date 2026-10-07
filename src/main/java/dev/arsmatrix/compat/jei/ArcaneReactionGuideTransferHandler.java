package dev.arsmatrix.compat.jei;

import dev.arsmatrix.data.ArcaneReactionRule;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
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

/** Encodes a data-driven Arcane Reaction recipe into a Wixie crafting guide. */
public final class ArcaneReactionGuideTransferHandler
        implements IRecipeTransferHandler<WixieOrderTerminalMenu, ArcaneReactionRule> {
    @Override public Class<? extends WixieOrderTerminalMenu> getContainerClass() {
        return WixieOrderTerminalMenu.class;
    }
    @Override public Optional<MenuType<WixieOrderTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WIXIE_ORDER_TERMINAL.get());
    }
    @Override public RecipeType<ArcaneReactionRule> getRecipeType() {
        return ArcaneReactionJeiCategory.TYPE;
    }
    @Nullable
    @Override public IRecipeTransferError transferRecipe(
            WixieOrderTerminalMenu menu, ArcaneReactionRule recipe,
            IRecipeSlotsView slots, Player player, boolean maxTransfer, boolean doTransfer
    ) {
        if (doTransfer) {
            GuideEncodingTransfer.submit(menu, player, recipe.id());
        }
        return null;
    }
}
