package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.FeatureFlags;
import dev.arsmatrix.data.ArcaneMineOreManager;
import dev.arsmatrix.data.SourceStoneGeneratorRecipeManager;
import dev.arsmatrix.data.CrusherRecipeResolver;
import dev.arsmatrix.data.ArcaneHuntingRuleManager;
import dev.arsmatrix.data.AlakarkinosExpeditionManager;
import dev.arsmatrix.data.ArcaneReactionManager;
import dev.arsmatrix.registry.ModBlocks;
import dev.arsmatrix.registry.ModRecipeTypes;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import mezz.jei.api.registration.IGuiHandlerRegistration;
import mezz.jei.api.gui.handlers.IGuiContainerHandler;
import mezz.jei.api.runtime.IClickableIngredient;
import mezz.jei.api.constants.RecipeTypes;
import dev.arsmatrix.client.WixieOrderTerminalScreen;
import dev.arsmatrix.registry.ModItems;
import com.hollingsworth.arsnouveau.setup.registry.ItemsRegistry;
import com.hollingsworth.arsnouveau.client.jei.JEIArsNouveauPlugin;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;
import java.util.Optional;

@JeiPlugin
public final class ArsMatrixJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID,
            "jei"
    );
    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        registration.addRecipeCategories(new ArcaneMineJeiCategory(
                registration.getJeiHelpers().getGuiHelper()
        ));
        registration.addRecipeCategories(new SourceStoneGeneratorJeiCategory(
                registration.getJeiHelpers().getGuiHelper()
        ));
        registration.addRecipeCategories(new ArcaneCrusherJeiCategory(
                registration.getJeiHelpers().getGuiHelper()
        ));
        if (FeatureFlags.ARCANE_ARENA) {
            registration.addRecipeCategories(new ArcaneHuntingJeiCategory(
                    registration.getJeiHelpers().getGuiHelper()));
        }
        registration.addRecipeCategories(new AlakarkinosExpeditionJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new ArcaneMachineUpgradeJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new ArcaneReactionJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
        registration.addRecipeCategories(new UnbreakableRefinementJeiCategory(
                registration.getJeiHelpers().getGuiHelper()));
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                ArcaneMachineUpgradeJeiCategory.TYPE,
                Minecraft.getInstance().level == null
                        ? List.of()
                        : Minecraft.getInstance().level.getRecipeManager().getAllRecipesFor(
                                ModRecipeTypes.ARCANE_MACHINE_UPGRADE_TYPE.get()));
        registration.addRecipes(
                ArcaneMineJeiCategory.TYPE,
                ArcaneMineOreManager.allRules().stream()
                        .filter(rule -> !rule.createOutput(net.minecraft.util.RandomSource.create()).isEmpty())
                        .toList()
        );
        registration.addRecipes(
                SourceStoneGeneratorJeiCategory.TYPE,
                SourceStoneGeneratorRecipeManager.allRecipes()
        );
        registration.addRecipes(ArcaneCrusherJeiCategory.TYPE, CrusherRecipeResolver.allRules());
        if (FeatureFlags.ARCANE_ARENA) {
            registration.addRecipes(ArcaneHuntingJeiCategory.TYPE, ArcaneHuntingRuleManager.allRules());
        }
        registration.addRecipes(AlakarkinosExpeditionJeiCategory.TYPE,
                AlakarkinosExpeditionManager.allRules());
        registration.addRecipes(ArcaneReactionJeiCategory.TYPE, ArcaneReactionManager.allRecipes());
        List<net.minecraft.world.item.crafting.RecipeHolder<dev.arsmatrix.recipe.UnbreakableApparatusRecipe>>
                unbreakableRecipes = List.of();
        if (Minecraft.getInstance().level != null) {
            var holder = Minecraft.getInstance().level.getRecipeManager().byKey(
                    ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, "unbreakable_item"));
            if (holder.isPresent()
                    && holder.get().value() instanceof dev.arsmatrix.recipe.UnbreakableApparatusRecipe recipe) {
                unbreakableRecipes = List.of(new net.minecraft.world.item.crafting.RecipeHolder<>(
                        holder.get().id(), recipe));
            }
        }
        registration.addRecipes(UnbreakableRefinementJeiCategory.TYPE, unbreakableRecipes);
        registration.addIngredientInfo(
                ModItems.ENCHANTED_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.source"),
                Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.pity")
        );
        registration.addIngredientInfo(
                ModItems.CASTING_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.casting_crystal.source"),
                Component.translatable("jei.ars_arcane_matrix.casting_crystal.pity")
        );
        registration.addIngredientInfo(
                ModItems.ENRICHED_MINERAL_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.enriched_mineral_crystal.source")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(BlockRegistry.ENCHANTING_APP_BLOCK.get(),
                ArcaneMachineUpgradeJeiCategory.TYPE);
        registration.addRecipeCatalyst(BlockRegistry.ENCHANTING_APP_BLOCK.get(),
                UnbreakableRefinementJeiCategory.TYPE);
        registration.addRecipeCatalyst(ModItems.CRAFTING_GUIDE.get(), RecipeTypes.CRAFTING);
        registration.addRecipeCatalyst(ModBlocks.ARCANE_SMELTER_CORE.get(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(ModBlocks.SOURCE_STONE_FURNACE.get(), RecipeTypes.SMELTING);
        registration.addRecipeCatalyst(ModBlocks.SOURCE_STONE_FURNACE.get(), RecipeTypes.SMOKING);
        registration.addRecipeCatalyst(
                ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get(),
                JEIArsNouveauPlugin.IMBUEMENT_RECIPE_TYPE.get());
        registration.addRecipeCatalyst(
                ModBlocks.ARCANE_MINE_CORE.get(),
                ArcaneMineJeiCategory.TYPE
        );
        registration.addRecipeCatalyst(
                ModBlocks.SOURCE_STONE_GENERATOR.get(),
                SourceStoneGeneratorJeiCategory.TYPE
        );
        registration.addRecipeCatalyst(ModBlocks.ARCANE_CRUSHER_CORE.get(), ArcaneCrusherJeiCategory.TYPE);
        if (FeatureFlags.ARCANE_ARENA) {
            registration.addRecipeCatalyst(ModBlocks.DRYGMY_ARENA.get(), ArcaneHuntingJeiCategory.TYPE);
        }
        registration.addRecipeCatalyst(ItemsRegistry.ALAKARKINOS_CHARM.get(),
                AlakarkinosExpeditionJeiCategory.TYPE);
        registration.addRecipeCatalyst(ModBlocks.ARCANE_REACTION_VESSEL.get(), ArcaneReactionJeiCategory.TYPE);
        registration.addRecipeCatalyst(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "water_essence")),
                ArcaneCrusherJeiCategory.TYPE);
        registration.addRecipeCatalyst(
                net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                        ResourceLocation.fromNamespaceAndPath("ars_nouveau", "air_essence")),
                ArcaneCrusherJeiCategory.TYPE);
    }

    @Override
    public void registerRecipeTransferHandlers(IRecipeTransferRegistration registration) {
        registration.addRecipeTransferHandler(
                new CraftingGuideRecipeTransferHandler(registration.getTransferHelper()),
                RecipeTypes.CRAFTING
        );
        registration.addRecipeTransferHandler(
                new CookingGuideRecipeTransferHandler<>(
                        registration.getTransferHelper(), RecipeTypes.SMELTING),
                RecipeTypes.SMELTING
        );
        registration.addRecipeTransferHandler(
                new CookingGuideRecipeTransferHandler<>(
                        registration.getTransferHelper(), RecipeTypes.SMOKING),
                RecipeTypes.SMOKING
        );
    }

    @Override
    public void registerGuiHandlers(IGuiHandlerRegistration registration) {
        var ingredientManager = registration.getJeiHelpers().getIngredientManager();
        registration.addGuiContainerHandler(
                WixieOrderTerminalScreen.class,
                new IGuiContainerHandler<>() {
                    @Override
                    public Optional<IClickableIngredient<?>> getClickableIngredientUnderMouse(
                            WixieOrderTerminalScreen screen, double mouseX, double mouseY
                    ) {
                        return screen.getVirtualIngredientUnderMouse(mouseX, mouseY)
                                .flatMap(virtual -> ingredientManager.createClickableIngredient(
                                        virtual.stack(), virtual.area(), false))
                                .map(ingredient -> (IClickableIngredient<?>) ingredient);
                    }
                }
        );
    }
}
