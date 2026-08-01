package dev.arsmatrix.client;

import dev.arsmatrix.registry.ModBlockEntities;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

public final class ClientModEvents {

    private ClientModEvents() {
    }

    public static void registerLayerDefinitions(EntityRenderersEvent.RegisterLayerDefinitions event) {
        event.registerLayerDefinition(MatrixCoreRenderer.MODEL_LAYER, MatrixCoreRenderer::createCubeLayer);
    }

    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(ModBlockEntities.MATRIX_CORE.get(), MatrixCoreRenderer::new);
        event.registerBlockEntityRenderer(ModBlockEntities.ARCANE_MINE_CORE.get(), ArcaneMineCoreRenderer::new);
        event.registerBlockEntityRenderer(
                ModBlockEntities.ARCANE_IMBUEMENT_CORE.get(),
                ArcaneImbuementCoreRenderer::new
        );
    }
}
