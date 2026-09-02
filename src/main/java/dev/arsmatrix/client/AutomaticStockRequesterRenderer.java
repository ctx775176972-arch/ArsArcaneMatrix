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
        // Item models are centered around their origin. Keep even full block models
        // entirely above the request display instead of burying their lower half.
        poseStack.translate(0.5D, 1.28D, 0.5D);
        poseStack.mulPose(Axis.YP.rotationDegrees(
                (requester.getLevel() == null ? 0L : requester.getLevel().getGameTime()) * 2.0F));
        poseStack.scale(0.40F, 0.40F, 0.40F);
        Minecraft.getInstance().getItemRenderer().renderStatic(
                target, ItemDisplayContext.FIXED, packedLight, packedOverlay,
                poseStack, buffers, requester.getLevel(), 0);
        poseStack.popPose();
    }
}
