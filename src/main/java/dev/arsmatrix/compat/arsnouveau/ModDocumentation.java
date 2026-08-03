package dev.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent;
import com.hollingsworth.arsnouveau.api.documentation.DocCategory;
import com.hollingsworth.arsnouveau.api.documentation.builder.DocEntryBuilder;
import com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry;
import dev.arsmatrix.ArsArcaneMatrix;
import dev.arsmatrix.client.documentation.MatrixStructureEntry;
import dev.arsmatrix.client.documentation.MineStructureEntry;
import dev.arsmatrix.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

/** Registers Ars Arcane Matrix entries in Ars Nouveau's current spell-book documentation system. */
public final class ModDocumentation {

    private ModDocumentation() {
    }

    public static void addEntries(ReloadDocumentationEvent.AddEntries event) {
        Item matrixCore = ModItems.MATRIX_CORE.get();
        DocCategory category = new DocCategory(
                ResourceLocation.fromNamespaceAndPath(ArsArcaneMatrix.MOD_ID, ArsArcaneMatrix.MOD_ID),
                matrixCore.getDefaultInstance(),
                1100
        );
        DocumentationRegistry.registerMainCategory(category);

        DocEntryBuilder builder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                category,
                matrixCore
        )
                .withSortNum(10)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.matrix_core.overview"),
                        Component.translatable("block.ars_arcane_matrix.matrix_core"),
                        matrixCore.getDefaultInstance()
                )
                .withCraftingPages()
                .withPage(MatrixStructureEntry.create())
                .addConnectedSearch(matrixCore);

        DocumentationRegistry.registerEntry(category, builder.build());

        Item mineCore = ModItems.ARCANE_MINE_CORE.get();
        DocEntryBuilder mineBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                category,
                mineCore
        )
                .withSortNum(20)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_mine.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_mine_core"),
                        mineCore.getDefaultInstance()
                )
                .withCraftingPages()
                .withPage(MineStructureEntry.create())
                .addConnectedSearch(mineCore);

        DocumentationRegistry.registerEntry(category, mineBuilder.build());

        Item amplifier = ModItems.ARCANE_AMPLIFIER.get();
        DocEntryBuilder amplifierBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                category,
                amplifier
        )
                .withSortNum(30)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.arcane_amplifier.overview"),
                        Component.translatable("block.ars_arcane_matrix.arcane_amplifier"),
                        amplifier.getDefaultInstance()
                )
                .withCraftingPages(
                        ResourceLocation.fromNamespaceAndPath(
                                ArsArcaneMatrix.MOD_ID,
                                "arcane_amplifier_recycling"
                        ),
                        amplifier
                )
                .addConnectedSearch(amplifier);

        DocumentationRegistry.registerEntry(category, amplifierBuilder.build());

        Item imbuementCore = ModItems.ARCANE_IMBUEMENT_CORE.get();
        DocEntryBuilder imbuementBuilder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                category,
                imbuementCore
        )
                .withSortNum(40)
                .withIntroPageNoIncrement(
                        Component.translatable(
                                "documentation.ars_arcane_matrix.arcane_imbuement_core.overview"
                        ),
                        Component.translatable("block.ars_arcane_matrix.arcane_imbuement_core"),
                        imbuementCore.getDefaultInstance()
                )
                .withCraftingPages()
                .addConnectedSearch(imbuementCore);

        DocumentationRegistry.registerEntry(category, imbuementBuilder.build());
    }
}
