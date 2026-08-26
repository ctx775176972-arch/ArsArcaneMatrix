package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.SuperSourceJarCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

/** Renders the empty-hand construction projection for the Matrix Source Reservoir. */
public final class SuperSourceJarCoreRenderer implements BlockEntityRenderer<SuperSourceJarCoreBlockEntity> {
    public SuperSourceJarCoreRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(SuperSourceJarCoreBlockEntity core, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int packedLight, int packedOverlay) {
        StructurePreviewRenderer.renderMatrixSourceReservoir(core.getBlockPos(), poseStack, buffers);
    }

    @Override public boolean shouldRenderOffScreen(SuperSourceJarCoreBlockEntity core) { return true; }

    @Override public AABB getRenderBoundingBox(SuperSourceJarCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(8.0D);
    }
}
