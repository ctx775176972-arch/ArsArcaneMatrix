package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.arsmatrix.blockentity.DrygmyArenaBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.AABB;

public final class DrygmyArenaRenderer implements BlockEntityRenderer<DrygmyArenaBlockEntity> {
    public DrygmyArenaRenderer(BlockEntityRendererProvider.Context context) {}
    @Override public void render(DrygmyArenaBlockEntity arena, float partialTick, PoseStack poseStack,
                                 MultiBufferSource buffers, int light, int overlay) {
        StructurePreviewRenderer.renderHuntingGrounds(arena.getBlockPos(), poseStack, buffers);
    }
    @Override public boolean shouldRenderOffScreen(DrygmyArenaBlockEntity arena) { return true; }
    @Override public AABB getRenderBoundingBox(DrygmyArenaBlockEntity arena) {
        return new AABB(arena.getBlockPos()).inflate(3.0D);
    }
}
