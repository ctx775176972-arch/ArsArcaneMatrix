package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.ArcaneSmelterCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class ArcaneSmelterCoreRenderer implements BlockEntityRenderer<ArcaneSmelterCoreBlockEntity> {
    public ArcaneSmelterCoreRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(ArcaneSmelterCoreBlockEntity core, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        StructurePreviewRenderer.renderSmelter(core.getBlockPos(), poseStack, buffers);
    }
    @Override public boolean shouldRenderOffScreen(ArcaneSmelterCoreBlockEntity core) { return true; }
    @Override public AABB getRenderBoundingBox(ArcaneSmelterCoreBlockEntity core) {
        return new AABB(core.getBlockPos()).inflate(3.0D);
    }
}
