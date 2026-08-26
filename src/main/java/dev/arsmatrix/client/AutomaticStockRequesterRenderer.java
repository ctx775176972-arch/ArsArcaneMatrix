package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arsmatrix.blockentity.AutomaticStockRequesterBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

public final class AutomaticStockRequesterRenderer
        implements BlockEntityRenderer<AutomaticStockRequesterBlockEntity> {
    public AutomaticStockRequesterRenderer(BlockEntityRendererProvider.Context context) {}

    @Override
    public void render(
            AutomaticStockRequesterBlockEntity requester, float partialTick,
            PoseStack poseStack, MultiBufferSource buffers,
            int packedLight, int packedOverlay
    ) {
        ItemStack target = requester.getTarget();
        if (target.isEmpty()) return;
        poseStack.pushPose();
        poseStack.translate(0.5D, 1.05D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                (requester.getLevel() == null ? 0L : requester.getLevel().getGameTime()) * 2.0F));
        poseStack.scale(0.45F, 0.45F, 0.45F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                target, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                poseStack, buffers, requester.getLevel(), 0);
        poseStack.popPose();
    }
}
