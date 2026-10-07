package dev.arsmatrix.compat.emi;

import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.common.crafting.recipes.ImbuementRecipe;
import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.compat.RecipeAutomationSupport;
import dev.arsmatrix.registry.ModMenus;
import dev.emi.emi.api.EmiEntrypoint;
import dev.emi.emi.api.EmiPlugin;
import dev.emi.emi.api.EmiRegistry;
import dev.emi.emi.api.recipe.EmiPlayerInventory;
import dev.emi.emi.api.recipe.EmiRecipe;
import dev.emi.emi.api.recipe.handler.EmiCraftContext;
import dev.emi.emi.api.recipe.handler.EmiRecipeHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.StonecutterRecipe;

import java.util.List;

/** Native EMI bridge for recipe types that JEMI cannot reliably route to the order terminal. */
@EmiEntrypoint
public final class ArsMatrixEmiPlugin implements EmiPlugin {
    @Override
    public void register(EmiRegistry registry) {
        registry.addStackProvider(dev.arsmatrix.client.WixieOrderTerminalScreen.class,
                (screen, x, y) -> screen.getVirtualIngredientUnderMouse(x, y)
                        .map(ingredient -> new dev.emi.emi.api.stack.EmiStackInteraction(
                                dev.emi.emi.api.stack.EmiStack.of(ingredient.stack()), null, false))
                        .orElse(dev.emi.emi.api.stack.EmiStackInteraction.EMPTY));
        registry.addCategory(ArcaneReactionEmiRecipe.CATEGORY);
        registry.addWorkstation(ArcaneReactionEmiRecipe.CATEGORY,
                dev.emi.emi.api.stack.EmiStack.of(dev.arsmatrix.registry.ModBlocks.ARCANE_REACTION_VESSEL.get()));
        for (var rule : dev.arsmatrix.data.ArcaneReactionManager.allRecipes()) {
            // Replace JEMI's bridge for this rule, rather than showing the same recipe twice.
            registry.removeRecipes(recipe -> rule.id().equals(recipe.getId())
                    && !(recipe instanceof ArcaneReactionEmiRecipe));
            registry.addRecipe(new ArcaneReactionEmiRecipe(rule));
        }
        registry.addRecipeHandler(ModMenus.WIXIE_ORDER_TERMINAL.get(), new GuideHandler());
    }

    private static final class GuideHandler
            implements EmiRecipeHandler<WixieOrderTerminalMenu> {
        @Override
        public EmiPlayerInventory getInventory(
                AbstractContainerScreen<WixieOrderTerminalMenu> screen
        ) {
            return new EmiPlayerInventory(List.of());
        }

        @Override
        public boolean supportsRecipe(EmiRecipe recipe) {
            RecipeHolder<?> backing = recipe.getBackingRecipe();
            return backing != null && (backing.value() instanceof net.minecraft.world.item.crafting.CraftingRecipe
                    || backing.value() instanceof net.minecraft.world.item.crafting.SmeltingRecipe
                    || backing.value() instanceof EnchantingApparatusRecipe
                    || backing.value() instanceof dev.arsmatrix.compat.ArcaneReactionAutomationRecipe
                        && !((dev.arsmatrix.compat.ArcaneReactionAutomationRecipe) backing.value()).rule().createItemOutput().isEmpty()
                    || backing.value() instanceof ImbuementRecipe
                    || backing.value() instanceof StonecutterRecipe
                    || RecipeAutomationSupport.isFarmersDelightCooking(backing.value())
                    || RecipeAutomationSupport.isAvaritiaCrafting(backing.value())
                    || RecipeAutomationSupport.isAvaritiaCompressor(backing.value()));
        }

        @Override
        public boolean alwaysDisplaySupport(EmiRecipe recipe) {
            return supportsRecipe(recipe);
        }

        @Override
        public boolean canCraft(
                EmiRecipe recipe, EmiCraftContext<WixieOrderTerminalMenu> context
        ) {
            return !context.getScreenHandler().isStorageCraftingActive() && supportsRecipe(recipe);
        }

        @Override
        public boolean craft(
                EmiRecipe recipe, EmiCraftContext<WixieOrderTerminalMenu> context
        ) {
            RecipeHolder<?> backing = recipe.getBackingRecipe();
            if (backing == null || !supportsRecipe(recipe)) {
                return false;
            }
            WixieOrderTerminalMenu menu = context.getScreenHandler();
            if (menu.isStorageCraftingActive()) return false;
            if (net.minecraft.client.gui.screens.Screen.hasShiftDown()
                    && context.getScreen() instanceof dev.arsmatrix.client.WixieOrderTerminalScreen screen
                    && menu.isAdvancedStorage()) {
                return screen.openGuideChainPreview(backing.id());
            }
            int buttonId = WixieOrderTerminalMenu.recipeEncodingButton(backing.id());
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player == null || minecraft.gameMode == null) return false;
            menu.clickMenuButton(minecraft.player, buttonId);
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, buttonId);
            return true;
        }
    }
}
