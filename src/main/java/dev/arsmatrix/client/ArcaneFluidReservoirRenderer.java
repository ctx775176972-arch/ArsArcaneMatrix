package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arsmatrix.block.ArcaneFluidReservoirBlock;
import dev.arsmatrix.blockentity.ArcaneFluidReservoirBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

/** Displays the selected output fluid as its bucket on the reservoir's front glass. */
public final class ArcaneFluidReservoirRenderer
        implements BlockEntityRenderer<ArcaneFluidReservoirBlockEntity> {
    private final ItemRenderer itemRenderer;

    public ArcaneFluidReservoirRenderer(BlockEntityRendererProvider.Context context) {
        itemRenderer = context.getItemRenderer();
    }

    @Override public void render(ArcaneFluidReservoirBlockEntity reservoir, float partialTick,
                                 PoseStack poseStack, MultiBufferSource buffers,
                                 int packedLight, int packedOverlay) {
        Fluid fluid = reservoir.outputTankFluid();
        if (fluid == Fluids.EMPTY || reservoir.outputTankAmount() <= 0) return;
        ItemStack bucket = new ItemStack(fluid.getBucket());
        if (bucket.isEmpty()) return;

        Direction facing = reservoir.getBlockState().getValue(ArcaneFluidReservoirBlock.FACING);
        poseStack.pushPose();
        switch (facing) {
            case NORTH -> {
                poseStack.translate(0.5F, 0.54F, -0.015F);
                poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
            }
            case SOUTH -> poseStack.translate(0.5F, 0.54F, 1.015F);
            case EAST -> {
                poseStack.translate(1.015F, 0.54F, 0.5F);
                poseStack.mulPose(Axis.YP.rotationDegrees(90.0F));
            }
            case WEST -> {
                poseStack.translate(-0.015F, 0.54F, 0.5F);
                poseStack.mulPose(Axis.YP.rotationDegrees(-90.0F));
            }
            default -> poseStack.translate(0.5F, 0.54F, 0.5F);
        }
        poseStack.scale(0.38F, 0.38F, 0.38F);
        itemRenderer.renderStatic(bucket, ItemDisplayContext.FIXED, packedLight,
                OverlayTexture.NO_OVERLAY, poseStack, buffers, reservoir.getLevel(),
                reservoir.getBlockPos().hashCode());
        poseStack.popPose();
    }
}
