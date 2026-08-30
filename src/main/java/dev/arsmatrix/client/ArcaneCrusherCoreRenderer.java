package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.ArcaneCrusherCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class ArcaneCrusherCoreRenderer implements BlockEntityRenderer<ArcaneCrusherCoreBlockEntity> {
    public ArcaneCrusherCoreRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ArcaneCrusherCoreBlockEntity core, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        StructurePreviewRenderer.renderCrusher(core.getBlockPos(), poseStack, buffers);
    }
    @Override public boolean shouldRenderOffScreen(ArcaneCrusherCoreBlockEntity core) { return true; }
    @Override public AABB getRenderBoundingBox(ArcaneCrusherCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(5.0D);
    }
}
