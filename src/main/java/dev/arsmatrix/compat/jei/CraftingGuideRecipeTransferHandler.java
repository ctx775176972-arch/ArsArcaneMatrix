package dev.arsmatrix.compat.jei;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.compat.DynamicCraftingRecipeSupport;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/** Turns JEI's transfer button into an encoder while the order terminal is open. */
public final class CraftingGuideRecipeTransferHandler
        implements IRecipeTransferHandler<WixieOrderTerminalMenu, RecipeHolder<CraftingRecipe>> {

    private final IRecipeTransferHandlerHelper helper;
    private final IRecipeTransferHandler<WixieOrderTerminalMenu, RecipeHolder<CraftingRecipe>>
            craftingGridTransfer;

    public CraftingGuideRecipeTransferHandler(IRecipeTransferHandlerHelper helper) {
        this.helper = helper;
        var transferInfo = helper.createBasicRecipeTransferInfo(
                WixieOrderTerminalMenu.class,
                ModMenus.WIXIE_ORDER_TERMINAL.get(),
                RecipeTypes.CRAFTING,
                1, 9,
                10, 36
        );
        this.craftingGridTransfer = helper.createUnregisteredRecipeTransferHandler(transferInfo);
    }

    @Override
    public Class<? extends WixieOrderTerminalMenu> getContainerClass() {
        return WixieOrderTerminalMenu.class;
    }

    @Override
    public Optional<MenuType<WixieOrderTerminalMenu>> getMenuType() {
        return Optional.of(ModMenus.WIXIE_ORDER_TERMINAL.get());
    }

    @Override
    public RecipeType<RecipeHolder<CraftingRecipe>> getRecipeType() {
        return RecipeTypes.CRAFTING;
    }

    @Nullable
    @Override
    public IRecipeTransferError transferRecipe(
            WixieOrderTerminalMenu menu,
            RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots,
            Player player,
            boolean maxTransfer,
            boolean doTransfer
    ) {
        // The advanced lectern's Storage page is a real crafting table. Let JEI move
        // ingredients into its 3x3 slots. The Orders page keeps the guide-encoding action.
        if (menu.isStorageCraftingActive()) {
            return craftingGridTransfer.transferRecipe(
                    menu, recipe, recipeSlots, player, maxTransfer, doTransfer);
        }
        if (recipe.value().isSpecial() && !DynamicCraftingRecipeSupport.supports(recipe.value())) {
            return helper.createUserErrorWithTooltip(Component.translatable(
                    "jei.ars_arcane_matrix.crafting_guide.special_unsupported"));
        }
        // Do not inspect the client inventory during JEI's availability pass.
        // Some menu/overlay timing paths expose a stale player inventory and
        // incorrectly disable the button. The authoritative server-side menu
        // validates and consumes the blank guide when the button is clicked.
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
