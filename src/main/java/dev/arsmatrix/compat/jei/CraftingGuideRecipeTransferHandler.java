package dev.arsmatrix.compat.jei;

import dev.arsmatrix.menu.WixieOrderTerminalMenu;
import dev.arsmatrix.compat.DynamicCraftingRecipeSupport;
import dev.arsmatrix.network.StorageCraftingFillPayload;
import dev.arsmatrix.registry.ModMenus;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandler;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.neoforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
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
        // Only the advanced lectern's expanded Storage page is a real crafting table.
        // Its network is virtual and invisible to JEI's ordinary inventory transfer, so
        // non-special recipes use a server-authoritative network fill. The Orders page
        // and the standalone terminal continue to encode guides below.
        if (menu.isStorageCraftingActive()) {
            if (!recipe.value().isSpecial()) {
                List<IRecipeSlotView> missing = missingNetworkIngredients(
                        menu, recipe, recipeSlots, player);
                if (!missing.isEmpty()) {
                    return helper.createUserErrorForMissingSlots(Component.translatable(
                            "jei.ars_arcane_matrix.advanced_storage_lectern.missing"), missing);
                }
                if (doTransfer) {
                    PacketDistributor.sendToServer(
                            new StorageCraftingFillPayload(menu.containerId, recipe.id(), maxTransfer));
                }
                return null;
            }
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
            GuideEncodingTransfer.submit(menu, player, recipe.id());
        }
        return null;
    }

    private static List<IRecipeSlotView> missingNetworkIngredients(
            WixieOrderTerminalMenu menu,
            RecipeHolder<CraftingRecipe> recipe,
            IRecipeSlotsView recipeSlots,
            Player player
    ) {
        List<AvailableStack> available = new ArrayList<>();
        menu.getStoredEntries().forEach(entry ->
                mergeAvailable(available, entry.stack(), entry.count()));
        for (int slot = 0; slot < player.getInventory().getContainerSize(); slot++) {
            ItemStack stack = player.getInventory().getItem(slot);
            if (!stack.isEmpty()) mergeAvailable(available, stack, stack.getCount());
        }
        // These stacks are returned to network storage before the server fills a new recipe.
        for (int slot = 1; slot <= 9; slot++) {
            ItemStack stack = menu.getSlot(slot).getItem();
            if (!stack.isEmpty()) mergeAvailable(available, stack, stack.getCount());
        }

        List<IRecipeSlotView> missing = new ArrayList<>();
        List<IRecipeSlotView> inputViews = recipeSlots
                .getSlotViews(RecipeIngredientRole.INPUT);
        List<net.minecraft.world.item.crafting.Ingredient> ingredients = recipe.value().getIngredients();
        int compactSlot = 0;
        for (int ingredientIndex = 0; ingredientIndex < ingredients.size(); ingredientIndex++) {
            net.minecraft.world.item.crafting.Ingredient ingredient = ingredients.get(ingredientIndex);
            if (ingredient.isEmpty()) continue;
            int viewIndex = compactSlot;
            if (recipe.value() instanceof net.minecraft.world.item.crafting.ShapedRecipe shaped
                    && inputViews.size() >= 9) {
                int row = ingredientIndex / shaped.getWidth();
                int column = ingredientIndex % shaped.getWidth();
                viewIndex = row * 3 + column;
            } else if (inputViews.size() == ingredients.size()) {
                viewIndex = ingredientIndex;
            }
            IRecipeSlotView recipeSlot = viewIndex < inputViews.size()
                    ? inputViews.get(viewIndex) : null;
            compactSlot++;
            boolean found = false;
            for (AvailableStack entry : available) {
                // Use the recipe's real ingredient predicate. JEI displays a
                // component-free example stack, while valid spell books and
                // equipment in the player's inventory normally carry components.
                if (entry.count <= 0 || !ingredient.test(entry.stack)) continue;
                entry.count--;
                found = true;
                break;
            }
            if (!found && recipeSlot != null) missing.add(recipeSlot);
        }
        return missing;
    }

    private static void mergeAvailable(List<AvailableStack> available, ItemStack stack, int count) {
        for (AvailableStack entry : available) {
            if (!ItemStack.isSameItemSameComponents(entry.stack, stack)) continue;
            entry.count = (int) Math.min(Integer.MAX_VALUE, (long) entry.count + count);
            return;
        }
        available.add(new AvailableStack(stack.copyWithCount(1), count));
    }

    private static final class AvailableStack {
        private final ItemStack stack;
        private int count;

        private AvailableStack(ItemStack stack, int count) {
            this.stack = stack;
            this.count = count;
        }
    }
}
