package com.tianxin.arsmatrix.compat.jei;

import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.data.ArcaneMineOreManager;
import com.tianxin.arsmatrix.registry.ModBlocks;
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
                Component.translatable("jei.ars_arcane_matrix.matrix_core.info")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_MINE_CORE.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_mine_core.info")
        );
        registration.addIngredientInfo(
                ModBlocks.ARCANE_AMPLIFIER.get(),
                Component.translatable("jei.ars_arcane_matrix.arcane_amplifier.info")
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
