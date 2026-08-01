package dev.arsmatrix.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import dev.arsmatrix.blockentity.ArcaneImbuementCoreBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/** Renders the Imbuement Core's single central Source Gem Block as a rotating focus. */
public final class ArcaneImbuementCoreRenderer
        implements BlockEntityRenderer<ArcaneImbuementCoreBlockEntity> {

    private static final ResourceLocation SOURCE_GEM_BLOCK_ID =
            ResourceLocation.fromNamespaceAndPath("ars_nouveau", "source_gem_block");

    private final BlockRenderDispatcher blockRenderer;
    private final BlockState sourceGemState;

    public ArcaneImbuementCoreRenderer(BlockEntityRendererProvider.Context context) {
        this.blockRenderer = context.getBlockRenderDispatcher();
        Block sourceGemBlock = BuiltInRegistries.BLOCK.getOptional(SOURCE_GEM_BLOCK_ID)
                .orElse(Blocks.AMETHYST_BLOCK);
        this.sourceGemState = sourceGemBlock.defaultBlockState();
    }

    @Override
    public void render(
            ArcaneImbuementCoreBlockEntity core,
            float partialTick,
            PoseStack poseStack,
            MultiBufferSource bufferSource,
            int packedLight,
            int packedOverlay
    ) {
        long gameTime = core.getLevel() == null ? 0L : core.getLevel().getGameTime();
        boolean processing = core.getOperatingState()
                == ArcaneImbuementCoreBlockEntity.OperatingState.PROCESSING;
        float speed = processing ? 3.0F : 0.8F;
        float angle = (gameTime + partialTick) * speed
                + Math.floorMod(core.getBlockPos().hashCode(), 360);

        poseStack.pushPose();
        poseStack.translate(0.5F, 0.625F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(angle));
        poseStack.mulPose(Axis.XP.rotationDegrees(15.0F));
        poseStack.scale(0.25F, 0.25F, 0.25F);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        blockRenderer.renderSingleBlock(
                sourceGemState,
                poseStack,
                bufferSource,
                packedLight,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }
}
