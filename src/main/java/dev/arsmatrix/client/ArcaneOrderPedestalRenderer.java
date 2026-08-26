package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arsmatrix.blockentity.ArcaneOrderPedestalBlockEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/** Renders only the requested sample; no real item entity exists on the pedestal. */
public final class ArcaneOrderPedestalRenderer
        implements BlockEntityRenderer<ArcaneOrderPedestalBlockEntity> {

    private final ItemRenderer itemRenderer;

    public ArcaneOrderPedestalRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override
    public void render(
            ArcaneOrderPedestalBlockEntity pedestal,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        ItemStack target = pedestal.getVirtualTarget();
        if (target.isEmpty()) {
            return;
        }
        long time = pedestal.getLevel() == null ? 0L : pedestal.getLevel().getGameTime();
        float bob = (float) Math.sin((time + partialTick) / 10.0F) * 0.06F;
        float angle = (time + partialTick) * 2.5F;

        poseStack.pushPose();
        poseStack.translate(0.5F, 1.12F + bob, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.scale(0.55F, 0.55F, 0.55F);
        itemRenderer.renderStatic(
                target,
                ItemDisplayContext.GROUND,
                LightTexture.FULL_BRIGHT,
                OverlayTexture.NO_OVERLAY,
                poseStack,
                bufferSource,
                pedestal.getLevel(),
                pedestal.getBlockPos().hashCode()
        );
        poseStack.popPose();
    }
}
