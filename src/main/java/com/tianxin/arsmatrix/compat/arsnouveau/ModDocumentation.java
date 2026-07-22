package com.tianxin.arsmatrix.compat.arsnouveau;

import com.hollingsworth.arsnouveau.api.documentation.ReloadDocumentationEvent;
import com.hollingsworth.arsnouveau.api.documentation.builder.DocEntryBuilder;
import com.hollingsworth.arsnouveau.api.registry.DocumentationRegistry;
import com.tianxin.arsmatrix.ArsArcaneMatrix;
import com.tianxin.arsmatrix.client.documentation.MatrixStructureEntry;
import com.tianxin.arsmatrix.registry.ModItems;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;

/** Registers the Matrix Core in Ars Nouveau's current spell-book documentation system. */
public final class ModDocumentation {

    private ModDocumentation() {
    }

    public static void addEntries(ReloadDocumentationEvent.AddEntries event) {
        Item matrixCore = ModItems.MATRIX_CORE.get();

        DocEntryBuilder builder = new DocEntryBuilder(
                ArsArcaneMatrix.MOD_ID,
                DocumentationRegistry.SOURCE,
                matrixCore
        )
                .withSortNum(250)
                .withIntroPageNoIncrement(
                        Component.translatable("documentation.ars_arcane_matrix.matrix_core.overview"),
                        Component.translatable("block.ars_arcane_matrix.matrix_core"),
                        matrixCore.getDefaultInstance()
                )
                .withCraftingPages()
                .withPage(MatrixStructureEntry.create())
                .addConnectedSearch(matrixCore);

        DocumentationRegistry.registerEntry(DocumentationRegistry.SOURCE, builder.build());
    }
}
