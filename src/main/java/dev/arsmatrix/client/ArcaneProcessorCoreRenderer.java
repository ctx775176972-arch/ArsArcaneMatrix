package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.ArcaneProcessorCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class ArcaneProcessorCoreRenderer implements BlockEntityRenderer<ArcaneProcessorCoreBlockEntity> {
    public ArcaneProcessorCoreRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ArcaneProcessorCoreBlockEntity core, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        StructurePreviewRenderer.renderProcessor(core.getBlockPos(), poseStack, buffers);
    }
    @Override public boolean shouldRenderOffScreen(ArcaneProcessorCoreBlockEntity core) { return true; }
    @Override public AABB getRenderBoundingBox(ArcaneProcessorCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(2.0D);
    }
}
