package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.DimensionAnchorBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class DimensionAnchorRenderer implements BlockEntityRenderer<DimensionAnchorBlockEntity> {
    public DimensionAnchorRenderer(BlockEntityRendererProvider.Context context) {}

    @Override public void render(DimensionAnchorBlockEntity anchor, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        StructurePreviewRenderer.renderDimensionAnchor(anchor.getBlockPos(), poseStack, buffers);
    }

    @Override public boolean shouldRenderOffScreen(DimensionAnchorBlockEntity anchor) { return true; }

    @Override public AABB getRenderBoundingBox(DimensionAnchorBlockEntity anchor) {
        return new AABB(anchor.getBlockPos()).inflate(2.0D);
    }
}
