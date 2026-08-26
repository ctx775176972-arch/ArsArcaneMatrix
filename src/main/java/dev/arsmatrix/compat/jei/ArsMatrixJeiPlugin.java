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
import com.hollingsworth.arsnouveau.common.crafting.recipes.EnchantingApparatusRecipe;
import com.hollingsworth.arsnouveau.setup.registry.BlockRegistry;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@JeiPlugin
public final class ArsMatrixJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(
            ArsArcaneMatrix.MOD_ID,
            "jei"
    );
    private static List<RecipeHolder<EnchantingApparatusRecipe>> machineUpgradeRecipes = List.of();

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
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        machineUpgradeRecipes = findMachineUpgradeRecipes();
        registration.addRecipes(ArcaneMachineUpgradeJeiCategory.TYPE, machineUpgradeRecipes);
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
        registration.addIngredientInfo(
                ModBlocks.MATRIX_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.matrix_core.info"),
                Component.translatable("jei.ars_arcane_matrix.matrix_core.amplifier")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_MINE_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_mine_core.info"),
                Component.translatable("jei.ars_arcane_matrix.arcane_mine_core.tuning"),
                Component.translatable("jei.ars_arcane_matrix.arcane_mine_core.amplifier")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_AMPLIFIER.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_amplifier.info"),
                Component.translatable("jei.ars_arcane_matrix.arcane_amplifier.matrix_usage"),
                Component.translatable("jei.ars_arcane_matrix.arcane_amplifier.mine_usage"),
                Component.translatable("jei.ars_arcane_matrix.arcane_amplifier.recycling")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_IMBUEMENT_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_imbuement_core.info"),
                Component.translatable("jei.ars_arcane_matrix.arcane_imbuement_core.lapis"),
                Component.translatable("jei.ars_arcane_matrix.arcane_imbuement_core.amethyst"),
                Component.translatable("jei.ars_arcane_matrix.arcane_imbuement_core.automation")
        );
        registration.addIngredientInfo(
                ModBlocks.ADVANCED_IMBUEMENT_CHAMBER.get(),
                Component.translatable("jei.ars_arcane_matrix.advanced_imbuement_chamber.info"),
                Component.translatable("jei.ars_arcane_matrix.advanced_imbuement_chamber.passive")
        );
        registration.addIngredientInfo(
                ModItems.FORMLESS_ESSENCE.get(),
                Component.translatable("jei.ars_arcane_matrix.formless_essence.info"),
                Component.translatable("jei.ars_arcane_matrix.formless_essence.compat")
        );
        registration.addIngredientInfo(
                ModBlocks.SOURCE_STONE_GENERATOR.get(),
                Component.translatable("jei.ars_arcane_matrix.source_stone_generator.info"),
                Component.translatable("jei.ars_arcane_matrix.source_stone_generator.multiblock"),
                Component.translatable("jei.ars_arcane_matrix.source_stone_generator.automation")
        );
        registration.addIngredientInfo(
                ModBlocks.SOURCE_STONE_FURNACE.get(),
                Component.translatable("jei.ars_arcane_matrix.source_stone_furnace.info"),
                Component.translatable("jei.ars_arcane_matrix.source_stone_furnace.io")
        );
        registration.addIngredientInfo(
                ModItems.CRAFTING_GUIDE.get(),
                Component.translatable("jei.ars_arcane_matrix.crafting_guide.info"),
                Component.translatable("jei.ars_arcane_matrix.crafting_guide.fuzzy")
        );
        registration.addIngredientInfo(
                ModItems.ENCHANTED_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.source"),
                Component.translatable("jei.ars_arcane_matrix.enchanted_crystal.pity")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_SMELTER_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_smelter.info"),
                Component.translatable("jei.ars_arcane_matrix.arcane_smelter.structure"),
                Component.translatable("jei.ars_arcane_matrix.arcane_smelter.automation")
        );
        registration.addIngredientInfo(
                ModItems.ENCHANTED_ARCHWOOD_CHARCOAL.get(),
                Component.translatable("jei.ars_arcane_matrix.enchanted_archwood_charcoal.info")
        );
        registration.addIngredientInfo(
                ModItems.CASTING_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.casting_crystal.source"),
                Component.translatable("jei.ars_arcane_matrix.casting_crystal.pity")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_CRUSHER_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_crusher.info"),
                Component.translatable("jei.ars_arcane_matrix.arcane_crusher.compat"),
                Component.translatable("jei.ars_arcane_matrix.arcane_crusher.automation")
        );
        registration.addIngredientInfo(
                ModItems.ENRICHED_MINERAL_CRYSTAL.get(),
                Component.translatable("jei.ars_arcane_matrix.enriched_mineral_crystal.source")
        );
        registration.addIngredientInfo(
                ModBlocks.STARBUNCLE_LOGISTICS_HUB.get(),
                Component.translatable("jei.ars_arcane_matrix.starbuncle_hub.upgrades")
        );
        registration.addIngredientInfo(
                ModItems.SOURCEBOUND_COPPER_ALLOY.get(),
                Component.translatable("jei.ars_arcane_matrix.sourcebound_copper_alloy.info"),
                Component.translatable("jei.ars_arcane_matrix.sourcebound_copper_alloy.advanced")
        );
        registration.addIngredientInfo(
                ItemsRegistry.AMETHYST_GOLEM_CHARM.get(),
                Component.translatable("jei.ars_arcane_matrix.amethyst_golem_enhancement.tool"),
                Component.translatable("jei.ars_arcane_matrix.amethyst_golem_enhancement.hopper")
        );
        registration.addIngredientInfo(
                ItemsRegistry.EARTH_ESSENCE.get(),
                Component.translatable("jei.ars_arcane_matrix.whirlisprig.earth_mode")
        );
        registration.addIngredientInfo(
                ModItems.ANCIENT_GROVE_CATALYST.get(),
                Component.translatable("jei.ars_arcane_matrix.whirlisprig.advanced_mode"),
                Component.translatable("jei.ars_arcane_matrix.whirlisprig.consumption")
        );
        registration.addIngredientInfo(
                net.minecraft.world.item.Items.BURN_POTTERY_SHERD,
                Component.translatable("jei.ars_arcane_matrix.burn_sherd.bootstrap")
        );
        if (FeatureFlags.ARCANE_ARENA) {
            registration.addIngredientInfo(
                    ModBlocks.DRYGMY_ARENA.get(),
                    Component.translatable("jei.ars_arcane_matrix.drygmy_arena.info"),
                    Component.translatable("jei.ars_arcane_matrix.drygmy_arena.drops"),
                    Component.translatable("jei.ars_arcane_matrix.drygmy_arena.automation")
            );
        }
        registration.addIngredientInfo(
                ItemsRegistry.ALAKARKINOS_CHARM.get(),
                Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.info"),
                Component.translatable("jei.ars_arcane_matrix.alakarkinos_expedition.automation")
        );
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(BlockRegistry.ENCHANTING_APP_BLOCK.get(),
                ArcaneMachineUpgradeJeiCategory.TYPE);
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
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        // These recipes still use Ars Nouveau's apparatus at runtime, but JEI should
        // present them only in the dedicated upgrade category.
        jeiRuntime.getRecipeManager().hideRecipes(
                JEIArsNouveauPlugin.ENCHANTING_APP_RECIPE_TYPE.get(), machineUpgradeRecipes);
    }

    private static List<RecipeHolder<EnchantingApparatusRecipe>> findMachineUpgradeRecipes() {
        if (Minecraft.getInstance().level == null) return List.of();
        List<RecipeHolder<EnchantingApparatusRecipe>> result = new ArrayList<>();
        for (RecipeHolder<?> holder : Minecraft.getInstance().level.getRecipeManager().getRecipes()) {
            if (holder.value() instanceof EnchantingApparatusRecipe recipe
                    && isMachineUpgradeId(holder.id())) {
                result.add(new RecipeHolder<>(holder.id(), recipe));
            }
        }
        return List.copyOf(result);
    }

    private static boolean isMachineUpgradeId(ResourceLocation id) {
        if (!id.getNamespace().equals(ArsArcaneMatrix.MOD_ID)) return false;
        String path = id.getPath();
        return path.matches("(?:arcane_order_pedestal|wixie_pattern_provider|starbuncle_logistics_hub|automatic_stock_requester)_tier_[1-9][0-9]*");
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
