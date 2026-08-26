package dev.arsmatrix.compat.jei;

import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.data.ArcaneMineOreManager;
import dev.arsmatrix.registry.ModBlocks;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

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
    }

    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        registration.addRecipes(
                ArcaneMineJeiCategory.TYPE,
                ArcaneMineOreManager.allRules().stream()
                        .filter(rule -> !rule.createOutput(net.minecraft.util.RandomSource.create()).isEmpty())
                        .toList()
        );
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
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalyst(
                ModBlocks.ARCANE_MINE_CORE.get(),
                ArcaneMineJeiCategory.TYPE
        );
    }
}
